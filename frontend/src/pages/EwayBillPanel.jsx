import React, { useEffect, useState } from "react";
import api from "../api/axios";

/**
 * Invoices whose goods need an e-way bill (Rule 138) that has not been recorded yet.
 * The seller downloads the portal's bulk file, generates the bills at ewaybillgst.gov.in,
 * and types each 12-digit number back here.
 */
const EwayBillPanel = () => {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState({});
  const [numbers, setNumbers] = useState({});
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const load = async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/gst/ewaybill/pending");
      setRows(Array.isArray(res.data) ? res.data : []);
    } catch {
      setError("Could not load invoices that need an e-way bill.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const ids = Object.keys(selected).filter((k) => selected[k]);

  const downloadBulk = async () => {
    setError(""); setMessage("");
    try {
      const res = await api.get("/api/gst/ewaybill/bulk-json", {
        params: { ids: ids.join(",") }, responseType: "blob",
      });
      const url = window.URL.createObjectURL(new Blob([res.data], { type: "application/json" }));
      const a = document.createElement("a");
      a.href = url;
      a.download = `ewaybill-bulk-${new Date().toISOString().slice(0, 10)}.json`;
      document.body.appendChild(a); a.click(); document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (e) {
      let msg = "Could not build the file.";
      try { msg = JSON.parse(await e.response.data.text()).error || msg; } catch { /* keep default */ }
      setError(msg);
    }
  };

  const record = async (id) => {
    setError(""); setMessage("");
    const n = (numbers[id] || "").replace(/\s/g, "");
    if (!/^\d{12}$/.test(n)) { setError("An e-way bill number is 12 digits."); return; }
    try {
      await api.post(`/api/gst/invoices/${id}/ewaybill`, { ewayBillNumber: n });
      setMessage(`E-way bill ${n} recorded.`);
      setRows((r) => r.filter((x) => x.id !== id));
    } catch (e) {
      setError(e.response?.data?.error || "Could not record the e-way bill.");
    }
  };

  return (
    <div className="gst-form" style={{ maxWidth: "100%" }}>
      <h3>E-way bills to generate</h3>
      <div className="static-info-box" style={{ marginBottom: "1rem" }}>
        Goods worth more than &#8377;50,000 (value plus GST) need an e-way bill before they are handed
        to the courier. Select invoices, download the bulk file, upload it at ewaybillgst.gov.in
        (Generate New EWB &rarr; Bulk), then enter each 12-digit number below.
      </div>
      {error && <p style={{ color: "#dc2626" }}>{error}</p>}
      {message && <p style={{ color: "#16a34a" }}>{message}</p>}
      {loading ? (
        <p style={{ color: "#94a3b8" }}>Loading...</p>
      ) : rows.length === 0 ? (
        <p style={{ color: "#94a3b8" }}>Nothing waiting for an e-way bill.</p>
      ) : (
        <>
          <button className="gst-btn gst-btn-primary gst-btn-sm" disabled={ids.length === 0}
                  onClick={downloadBulk} style={{ marginBottom: "0.75rem" }}>
            Download bulk file ({ids.length})
          </button>
          <div style={{ overflowX: "auto" }}>
            <table className="gst-table">
              <thead>
                <tr><th></th><th>Invoice</th><th>Date</th><th>Buyer</th><th>Consignment value</th><th>E-way bill number</th><th></th></tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id}>
                    <td><input type="checkbox" aria-label={`Select ${r.invoiceNumber}`} checked={!!selected[r.id]}
                               onChange={(e) => setSelected((s) => ({ ...s, [r.id]: e.target.checked }))} /></td>
                    <td>{r.invoiceNumber}</td>
                    <td>{r.invoiceDate}</td>
                    <td>{r.buyerName}</td>
                    <td>&#8377;{Number(r.consignmentValue || 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                    <td>
                      <input inputMode="numeric" maxLength={14} placeholder="12 digits" value={numbers[r.id] || ""}
                             aria-label={`E-way bill number for ${r.invoiceNumber}`}
                             onChange={(e) => setNumbers((m) => ({ ...m, [r.id]: e.target.value }))}
                             style={{ width: "9rem", padding: "0.3rem" }} />
                    </td>
                    <td><button className="gst-btn gst-btn-outline gst-btn-sm" onClick={() => record(r.id)}>Save</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
};

export default EwayBillPanel;
