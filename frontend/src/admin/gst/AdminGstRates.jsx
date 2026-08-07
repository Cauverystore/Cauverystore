import React, { useState, useEffect, useCallback } from "react";
import api from "../../api/axios";

const inp = { width: "100%", padding: "0.5rem", border: "1px solid #d1d5db", borderRadius: 6, fontSize: "0.85rem" };
const card = { background: "#fff", border: "1px solid #e5e7eb", borderRadius: 10, padding: "1.25rem" };
const btn = { padding: "0.4rem 0.85rem", border: "none", borderRadius: 6, cursor: "pointer", fontSize: "0.8rem", fontWeight: 500 };

const emptyRate = {
  hsnCode: "", gstRate: "", effectiveFrom: "", effectiveTo: "",
  conditionType: "NONE", thresholdAmount: "", thresholdUnit: "piece",
  conditionText: "", source: "",
};

/**
 * Review desk for GST rates.
 *
 * Products whose rate is not verified are taxed at the fallback rate, which is a compliance
 * risk that shows up only at audit - so this screen leads with the count still waiting, and
 * shows every rate of a heading together, because what makes a rate undecidable is almost
 * always the other rates published against the same code.
 */
const AdminGstRates = () => {
  const [summary, setSummary] = useState(null);
  const [headings, setHeadings] = useState([]);
  const [status, setStatus] = useState("UNVERIFIED");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(emptyRate);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [s, r] = await Promise.all([
        api.get("/api/admin/gst-rates/summary"),
        api.get("/api/admin/gst-rates/review", { params: { status } }),
      ]);
      setSummary(s.data);
      setHeadings(r.data || []);
      setError("");
    } catch {
      setError("Could not load GST rates.");
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => { load(); }, [load]);

  // The backend rejects changes that would leave the rate table unusable and explains why
  // in the message - surfacing that verbatim is the whole point, so never swallow it.
  const act = async (fn, successMessage) => {
    setError(""); setNotice("");
    try {
      await fn();
      setNotice(successMessage);
      load();
    } catch (e) {
      setError(e?.response?.data?.error || "The change could not be saved.");
    }
  };

  const verify = (rate) => {
    const note = window.prompt(
      `Approve ${rate.gstRate}% for HSN ${rate.hsnCode}?\n\n` +
      "This rate will start being charged to customers. Note the notification or reasoning:");
    if (note === null) return;
    act(() => api.post(`/api/admin/gst-rates/${rate.id}/verify`, { note }),
        `HSN ${rate.hsnCode} approved at ${rate.gstRate}%.`);
  };

  const unverify = (rate) => {
    const reason = window.prompt(
      `Withdraw the approved ${rate.gstRate}% on HSN ${rate.hsnCode}?\n\n` +
      "Products on this code fall back to the default rate until it is approved again.\n" +
      "Reason (required):");
    if (!reason) return;
    act(() => api.post(`/api/admin/gst-rates/${rate.id}/unverify`, { reason }),
        `HSN ${rate.hsnCode} withdrawn from use.`);
  };

  const supersede = (rate) => {
    const date = window.prompt(
      `Close off ${rate.gstRate}% on HSN ${rate.hsnCode}.\n\n` +
      "Last day this rate applied (YYYY-MM-DD). Older invoices keep resolving at it:");
    if (!date) return;
    act(() => api.post(`/api/admin/gst-rates/${rate.id}/supersede`, { lastDayInForce: date }),
        `HSN ${rate.hsnCode} closed off on ${date}.`);
  };

  const isValueBanded = form.conditionType === "VALUE_UPTO" || form.conditionType === "VALUE_ABOVE";

  const submitNew = (e) => {
    e.preventDefault();
    const payload = {
      hsnCode: form.hsnCode.trim(),
      gstRate: parseFloat(form.gstRate),
      effectiveFrom: form.effectiveFrom || null,
      effectiveTo: form.effectiveTo || null,
      conditionType: form.conditionType,
      thresholdAmount: isValueBanded && form.thresholdAmount
        ? parseFloat(form.thresholdAmount) : null,
      thresholdUnit: isValueBanded ? form.thresholdUnit : null,
      conditionText: form.conditionText || null,
      source: form.source || null,
    };
    act(async () => {
      await api.post("/api/admin/gst-rates", payload);
      setShowForm(false);
      setForm(emptyRate);
    }, "Rate added. It still needs approving before it is charged.");
  };

  const change = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  // Standalone importer API (optional). When set, the button runs the Python
  // importer via VITE_GST_IMPORTER_URL/update-gst-master first and only falls
  // back to the store's own refresh endpoint if it is unreachable.
  const IMPORTER_URL = (import.meta.env.VITE_GST_IMPORTER_URL || "").replace(/\/+$/, "");
  const IMPORTER_TOKEN = import.meta.env.VITE_GST_IMPORTER_TOKEN || "";

  /**
   * Re-applies the GST master files (HSN, units, states, rates) to the database.
   * Tries the standalone importer API first, then the backend's own refresh -
   * the backend returns the fresh summary plus the import log so we can say what moved.
   */
  const refreshMaster = async () => {
    setError(""); setNotice("");
    setRefreshing(true);
    let importerNote = "";
    try {
      if (IMPORTER_URL) {
        try {
          const controller = new AbortController();
          const timer = setTimeout(() => controller.abort(), 120000);
          const res = await fetch(`${IMPORTER_URL}/update-gst-master`, {
            method: "POST",
            headers: IMPORTER_TOKEN ? { Authorization: `Bearer ${IMPORTER_TOKEN}` } : {},
            signal: controller.signal,
          });
          clearTimeout(timer);
          const body = await res.json().catch(() => ({}));
          if (res.ok && body.status === "success") {
            setNotice(body.summary
              ? `GST Master Codes updated via importer — ${body.summary}`
              : "GST Master Codes updated via importer.");
            return;
          }
          throw new Error(body.error || `Importer API responded ${res.status}`);
        } catch (impErr) {
          importerNote = `Importer API unreachable (${impErr.message}) — fell back to the store's own refresh.`;
        }
      }

      const res = await api.post("/api/admin/gst-rates/refresh");
      setSummary(res.data.summary);
      const lines = (res.data.imports || [])
        .filter((l) => l.rowsInserted > 0 || l.rowsUpdated > 0)
        .slice(0, 4)
        .map((l) => `${l.fileName}: ${l.rowsInserted || 0} inserted, ${l.rowsUpdated || 0} updated`);
      setNotice(lines.length
        ? "Master codes updated — " + lines.join(" · ")
        : "Master codes are up to date — nothing changed.");
    } catch (e) {
      setError((importerNote ? importerNote + " " : "") + (e?.response?.data?.error || "Could not refresh master codes."));
    } finally {
      setRefreshing(false);
    }
  };

  const conditionLabel = (r) => {
    switch (r.conditionType) {
      case "VALUE_UPTO":
        return `applies up to ₹${r.thresholdAmount} per ${r.thresholdUnit || "piece"}`;
      case "VALUE_ABOVE":
        return `applies above ₹${r.thresholdAmount} per ${r.thresholdUnit || "piece"}`;
      case "PRE_PACKAGED":
        return "applies when pre-packaged and labelled";
      case "NOT_PRE_PACKAGED":
        return "applies when sold loose";
      default:
        return null;
    }
  };


  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1.5rem" }}>
        <div>
          <h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: 0 }}>GST Rates</h1>
          <p style={{ margin: "4px 0 0", color: "#6b7280", fontSize: "0.85rem" }}>
            Only approved rates are charged. Anything waiting here is being taxed at the fallback rate.
          </p>
        </div>
        <div style={{ display: "flex", gap: "0.5rem" }}>
          <button
            onClick={refreshMaster}
            disabled={refreshing}
            style={{ ...btn, background: "#2563eb", color: "#fff", padding: "0.5rem 1rem", fontSize: "0.85rem", opacity: refreshing ? 0.6 : 1 }}>
            {refreshing ? "Updating…" : "Update GST Master Codes"}
          </button>
          <button
            onClick={() => { setShowForm(!showForm); setForm(emptyRate); }}
            style={{ ...btn, background: "#16a34a", color: "#fff", padding: "0.5rem 1rem", fontSize: "0.85rem" }}>
            {showForm ? "Cancel" : "Add Rate"}
          </button>
        </div>
      </div>

      {error && (
        <div style={{ ...card, borderColor: "#fecaca", background: "#fef2f2", color: "#b91c1c", marginBottom: "1rem", fontSize: "0.85rem" }}>
          {error}
        </div>
      )}
      {notice && (
        <div style={{ ...card, borderColor: "#bbf7d0", background: "#f0fdf4", color: "#15803d", marginBottom: "1rem", fontSize: "0.85rem" }}>
          {notice}
        </div>
      )}

      {summary && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(180px,1fr))", gap: "1rem", marginBottom: "1.5rem" }}>
          <div style={card}>
            <div style={{ fontSize: "0.75rem", color: "#6b7280" }}>Approved and in use</div>
            <div style={{ fontSize: "1.75rem", fontWeight: 700, color: "#16a34a" }}>{summary.verified}</div>
          </div>
          <div style={card}>
            <div style={{ fontSize: "0.75rem", color: "#6b7280" }}>Rates awaiting review</div>
            <div style={{ fontSize: "1.75rem", fontWeight: 700, color: "#b45309" }}>{summary.unverified}</div>
          </div>
          <div style={card}>
            <div style={{ fontSize: "0.75rem", color: "#6b7280" }}>Headings to decide</div>
            <div style={{ fontSize: "1.75rem", fontWeight: 700 }}>{summary.headingsAwaitingReview}</div>
          </div>
          <div style={card}>
            <div style={{ fontSize: "0.75rem", color: "#6b7280" }}>Last import</div>
            <div style={{ fontSize: "0.85rem", fontWeight: 600, marginTop: 6 }}>
              {summary.lastImport ? (summary.lastImport.version || "—") : "Not yet run"}
            </div>
          </div>
        </div>
      )}

      {showForm && (
        <form onSubmit={submitNew} style={{ ...card, marginBottom: "1.5rem" }}>
          <h3 style={{ fontSize: "1.05rem", fontWeight: 600, marginTop: 0 }}>Add a rate</h3>
          <p style={{ fontSize: "0.8rem", color: "#6b7280", marginTop: 0 }}>
            Usually the missing half of a value band — for example footwear above ₹2,500 a pair,
            which the government extract never published. It arrives unapproved, so check it
            before putting it into use.
          </p>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill,minmax(180px,1fr))", gap: "1rem", marginBottom: "1rem" }}>
            <div><label style={lbl}>HSN code</label><input name="hsnCode" value={form.hsnCode} onChange={change} style={inp} required /></div>
            <div><label style={lbl}>GST %</label><input name="gstRate" type="number" step="0.01" value={form.gstRate} onChange={change} style={inp} required /></div>
            <div><label style={lbl}>Effective from</label><input name="effectiveFrom" type="date" value={form.effectiveFrom} onChange={change} style={inp} required /></div>
            <div><label style={lbl}>Effective to (blank = still in force)</label><input name="effectiveTo" type="date" value={form.effectiveTo} onChange={change} style={inp} /></div>
            <div>
              <label style={lbl}>What decides this rate?</label>
              <select name="conditionType" value={form.conditionType} onChange={change} style={inp}>
                <option value="NONE">Nothing — one rate always</option>
                <option value="VALUE_UPTO">Price at or below a threshold</option>
                <option value="VALUE_ABOVE">Price above a threshold</option>
                <option value="PRE_PACKAGED">Sold pre-packaged and labelled</option>
                <option value="NOT_PRE_PACKAGED">Sold loose</option>
              </select>
            </div>
            {isValueBanded && (
              <>
                <div><label style={lbl}>Threshold (₹)</label><input name="thresholdAmount" type="number" step="0.01" value={form.thresholdAmount} onChange={change} style={inp} required /></div>
                <div>
                  <label style={lbl}>Per</label>
                  <select name="thresholdUnit" value={form.thresholdUnit} onChange={change} style={inp}>
                    <option value="piece">piece</option>
                    <option value="pair">pair</option>
                    <option value="unit">unit</option>
                  </select>
                </div>
              </>
            )}
            <div><label style={lbl}>Source (notification)</label><input name="source" value={form.source} onChange={change} style={inp} placeholder="Notification 09/2025-CT(Rate)" /></div>
          </div>
          <div style={{ marginBottom: "1rem" }}>
            <label style={lbl}>Wording from the notification</label>
            <textarea name="conditionText" value={form.conditionText} onChange={change} style={{ ...inp, minHeight: 60 }} />
          </div>
          <button type="submit" style={{ ...btn, background: "#16a34a", color: "#fff" }}>Save</button>
        </form>
      )}

      <div style={{ display: "flex", gap: "0.5rem", marginBottom: "1rem" }}>
        {["UNVERIFIED", "VERIFIED"].map((s) => (
          <button key={s} onClick={() => setStatus(s)} style={{
            ...btn,
            background: status === s ? "#111827" : "#fff",
            color: status === s ? "#fff" : "#374151",
            border: "1px solid #d1d5db",
          }}>
            {s === "UNVERIFIED" ? "Awaiting review" : "In use"}
          </button>
        ))}
      </div>

      {loading && <p style={{ color: "#6b7280" }}>Loading…</p>}

      {!loading && headings.length === 0 && (
        <div style={{ ...card, textAlign: "center", color: "#6b7280" }}>
          {status === "UNVERIFIED"
            ? "Nothing waiting — every rate in the master has been reviewed."
            : "No approved rates yet."}
        </div>
      )}

      {headings.map((h) => (
        <div key={h.hsnCode} style={{ ...card, marginBottom: "1rem" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: "1rem", flexWrap: "wrap" }}>
            <div>
              <span style={{ fontWeight: 700, fontSize: "1.05rem" }}>HSN {h.hsnCode}</span>
              {h.description && (
                <span style={{ color: "#6b7280", fontSize: "0.85rem", marginLeft: 10 }}>{h.description}</span>
              )}
            </div>
          </div>

          {h.reviewNote && (
            <p style={{ fontSize: "0.8rem", color: "#92400e", background: "#fffbeb", border: "1px solid #fde68a", borderRadius: 6, padding: "0.5rem 0.75rem", margin: "0.75rem 0" }}>
              {h.reviewNote}
            </p>
          )}

          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85rem", minWidth: 640 }}>
              <thead>
                <tr style={{ textAlign: "left", color: "#6b7280", fontSize: "0.75rem" }}>
                  <th style={th}>Rate</th>
                  <th style={th}>Applies</th>
                  <th style={th}>In force</th>
                  <th style={th}>Status</th>
                  <th style={th}></th>
                </tr>
              </thead>
              <tbody>
                {(h.rates || []).map((r) => (
                  <tr key={r.id} style={{ borderTop: "1px solid #f3f4f6" }}>
                    <td style={{ ...td, fontWeight: 600 }}>{r.gstRate}%</td>
                    <td style={td}>
                      {conditionLabel(r) || "at any price"}
                      {r.conditionText && (
                        <div style={{ color: "#6b7280", fontSize: "0.75rem", marginTop: 2 }}>{r.conditionText}</div>
                      )}
                    </td>
                    <td style={td}>
                      {r.effectiveFrom} → {r.effectiveTo || "current"}
                    </td>
                    <td style={td}>
                      <span style={{
                        padding: "2px 8px", borderRadius: 999, fontSize: "0.72rem", fontWeight: 600,
                        background: r.status === "VERIFIED" ? "#dcfce7" : "#fef3c7",
                        color: r.status === "VERIFIED" ? "#15803d" : "#92400e",
                      }}>
                        {r.status === "VERIFIED" ? "In use" : "Awaiting review"}
                      </span>
                      {r.verifiedBy && (
                        <div style={{ color: "#6b7280", fontSize: "0.72rem", marginTop: 3 }}>by {r.verifiedBy}</div>
                      )}
                    </td>
                    <td style={{ ...td, whiteSpace: "nowrap" }}>
                      {r.status === "VERIFIED" ? (
                        <>
                          <button onClick={() => unverify(r)} style={{ ...btn, background: "#fff", color: "#b91c1c", border: "1px solid #fecaca", marginRight: 6 }}>Withdraw</button>
                          <button onClick={() => supersede(r)} style={{ ...btn, background: "#fff", color: "#374151", border: "1px solid #d1d5db" }}>Close off</button>
                        </>
                      ) : (
                        <button onClick={() => verify(r)} style={{ ...btn, background: "#16a34a", color: "#fff" }}>Approve</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <HistoryNotes heading={h} />
        </div>
      ))}
    </div>
  );
};

const lbl = { fontSize: "0.8rem", fontWeight: 500, display: "block", marginBottom: 4 };
const th = { padding: "0.5rem 0.5rem 0.5rem 0", fontWeight: 500 };
const td = { padding: "0.6rem 0.5rem 0.6rem 0", verticalAlign: "top" };

/** Whatever the importer or a previous reviewer recorded about why a rate is where it is. */
const HistoryNotes = ({ heading }) => {
  const notes = (heading.rates || []).map((r) => r.notes).filter(Boolean);
  if (notes.length === 0) return null;
  return (
    <details style={{ marginTop: "0.75rem" }}>
      <summary style={{ cursor: "pointer", fontSize: "0.8rem", color: "#6b7280" }}>History</summary>
      <ul style={{ fontSize: "0.78rem", color: "#6b7280", marginTop: 6 }}>
        {notes.map((n, i) => <li key={i}>{n}</li>)}
      </ul>
    </details>
  );
};

export default AdminGstRates;
