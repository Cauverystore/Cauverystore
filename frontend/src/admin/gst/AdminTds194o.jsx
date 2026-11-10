import React, { useState, useEffect, useCallback, Fragment } from "react";
import api from "../../api/axios";

const card = { background: "#fff", border: "1px solid #e5e7eb", borderRadius: 10, padding: "1.25rem" };
const th = { textAlign: "left", padding: "0.5rem 0.6rem", fontSize: "0.72rem", color: "#6b7280", fontWeight: 600, textTransform: "uppercase", borderBottom: "1px solid #e5e7eb" };
const td = { padding: "0.55rem 0.6rem", fontSize: "0.85rem", borderBottom: "1px solid #f3f4f6", verticalAlign: "top" };
const num = { ...td, textAlign: "right", fontVariantNumeric: "tabular-nums" };

const rupees = (v) => `₹${Number(v || 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

// The Indian financial year containing today, as "2026-27", and the two before it.
const financialYears = () => {
  const now = new Date();
  const start = now.getMonth() >= 3 ? now.getFullYear() : now.getFullYear() - 1;
  return [0, 1, 2].map((i) => `${start - i}-${String(start - i + 1).slice(2)}`);
};

/**
 * Income-tax TDS (section 194-O, now section 393(1) Sl. No. 8(v)) the marketplace should hold
 * back from each seller in a financial year. Read-only: it records nothing, does not touch
 * payouts, and the tax is deposited and returned outside this system.
 */
const AdminTds194o = () => {
  const years = financialYears();
  const [fy, setFy] = useState(years[0]);
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [open, setOpen] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const r = await api.get("/api/admin/tds-194o", { params: { fy } });
      setReport(r.data);
      setError("");
    } catch (e) {
      setReport(null);
      setError(e.response?.data?.error || "The TDS report could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [fy]);

  useEffect(() => { load(); }, [load]);

  const sellers = report?.sellers || [];

  return (
    <div>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: 0 }}>TDS on Seller Sales (194-O)</h1>
      <p style={{ margin: "4px 0 1.25rem", color: "#6b7280", fontSize: "0.85rem" }}>
        How much income-tax TDS should be held back from each seller's payouts. This report only
        calculates it: depositing the tax and filing the TDS return are done separately with your CA.
      </p>

      <div style={{ display: "flex", alignItems: "center", gap: "0.6rem", marginBottom: "1rem" }}>
        <label htmlFor="tds-fy" style={{ fontSize: "0.85rem", fontWeight: 600 }}>Financial year</label>
        <select id="tds-fy" value={fy} onChange={(e) => setFy(e.target.value)}
          style={{ padding: "0.4rem 0.6rem", border: "1px solid #d1d5db", borderRadius: 6, fontSize: "0.85rem" }}>
          {years.map((y) => <option key={y} value={y}>{y}</option>)}
        </select>
      </div>

      {error && (
        <div style={{ ...card, borderColor: "#fecaca", background: "#fef2f2", color: "#b91c1c", marginBottom: "1rem", fontSize: "0.85rem" }}>
          {error}
        </div>
      )}

      {loading && <p style={{ color: "#6b7280", fontSize: "0.85rem" }}>Loading…</p>}

      {report && !loading && (
        <>
          <div style={{ ...card, marginBottom: "1rem", display: "flex", justifyContent: "space-between", alignItems: "baseline", flexWrap: "wrap", gap: "0.5rem" }}>
            <div>
              <div style={{ fontSize: "0.75rem", color: "#6b7280", textTransform: "uppercase", fontWeight: 600 }}>
                Total TDS to deduct, {report.financialYear}
              </div>
              <div style={{ fontSize: "1.6rem", fontWeight: 700 }}>{rupees(report.totalTdsToDeduct)}</div>
            </div>
            <div style={{ fontSize: "0.8rem", color: "#6b7280" }}>{report.from} to {report.to}</div>
          </div>

          {(report.issues || []).map((m) => (
            <div key={m} style={{ ...card, borderColor: "#f59e0b", background: "#fffbeb", color: "#92400e", marginBottom: "1rem", fontSize: "0.85rem" }}>
              {m}
            </div>
          ))}

          <div style={{ ...card, padding: 0, overflowX: "auto", marginBottom: "1rem" }}>
            {sellers.length === 0 ? (
              <p style={{ padding: "1.25rem", margin: 0, color: "#6b7280", fontSize: "0.85rem" }}>
                No seller sales were recorded in this financial year.
              </p>
            ) : (
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr>
                    <th style={th}>Seller</th>
                    <th style={{ ...th, textAlign: "right" }}>Sales</th>
                    <th style={{ ...th, textAlign: "right" }}>Returns</th>
                    <th style={{ ...th, textAlign: "right" }}>Net sales</th>
                    <th style={{ ...th, textAlign: "right" }}>TDS to deduct</th>
                    <th style={th} />
                  </tr>
                </thead>
                <tbody>
                  {sellers.map((s) => (
                    <Fragment key={s.sellerId}>
                      <tr>
                        <td style={td}>
                          <div style={{ fontWeight: 600 }}>{s.businessName || `Seller #${s.sellerId}`}</div>
                          {(s.flags || []).map((f) => (
                            <div key={f} style={{ color: "#92400e", fontSize: "0.75rem", marginTop: 2 }}>{f}</div>
                          ))}
                        </td>
                        <td style={num}>{rupees(s.grossSales)}</td>
                        <td style={num}>{rupees(s.returns)}</td>
                        <td style={num}>{rupees(s.netSales)}</td>
                        <td style={{ ...num, fontWeight: 700 }}>{rupees(s.tdsToDeduct)}</td>
                        <td style={td}>
                          <button type="button" onClick={() => setOpen(open === s.sellerId ? null : s.sellerId)}
                            style={{ border: "none", background: "none", color: "#00788C", cursor: "pointer", fontSize: "0.8rem", fontWeight: 600 }}>
                            {open === s.sellerId ? "Hide" : "Details"}
                          </button>
                        </td>
                      </tr>
                      {open === s.sellerId && (
                        <tr>
                          <td colSpan={6} style={{ padding: "0.5rem 0.6rem 1rem", background: "#f9fafb" }}>
                            <table style={{ width: "100%", borderCollapse: "collapse" }}>
                              <thead>
                                <tr>
                                  <th style={th}>Date</th>
                                  <th style={th}>Type</th>
                                  <th style={{ ...th, textAlign: "right" }}>Value</th>
                                  <th style={{ ...th, textAlign: "right" }}>Rate</th>
                                  <th style={{ ...th, textAlign: "right" }}>TDS</th>
                                  <th style={th}>Note</th>
                                </tr>
                              </thead>
                              <tbody>
                                {(s.lines || []).map((l, i) => (
                                  <tr key={l.id ?? i}>
                                    <td style={td}>{l.date}</td>
                                    <td style={td}>{l.reversal ? "Return" : "Sale"}</td>
                                    <td style={num}>{rupees(l.taxable)}</td>
                                    <td style={num}>{l.ratePercent}%</td>
                                    <td style={num}>{rupees(l.tds)}</td>
                                    <td style={{ ...td, color: "#6b7280", fontSize: "0.78rem" }}>{l.note || ""}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <div style={{ ...card, background: "#f9fafb" }}>
            <div style={{ fontSize: "0.8rem", fontWeight: 600, marginBottom: "0.4rem" }}>Read before using these figures</div>
            <ul style={{ margin: 0, paddingLeft: "1.1rem", color: "#4b5563", fontSize: "0.8rem", lineHeight: 1.6 }}>
              {(report.notes || []).map((n) => <li key={n}>{n}</li>)}
            </ul>
          </div>
        </>
      )}
    </div>
  );
};

export default AdminTds194o;
