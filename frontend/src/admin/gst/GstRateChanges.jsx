import React, { useState, useEffect, useCallback } from "react";
import api from "../../api/axios";

const card = { background: "#fff", border: "1px solid #e5e7eb", borderRadius: 10, padding: "1.25rem" };
const btn = { padding: "0.4rem 0.85rem", border: "none", borderRadius: 6, cursor: "pointer", fontSize: "0.8rem", fontWeight: 500 };
const pct = (r) => (r === null || r === undefined ? "none" : `${r}%`);

/**
 * Rate changes drafted from government (CBIC) notifications, waiting for a decision.
 *
 * The system reads each new rate notification by rule and lists what it changes here. Nothing
 * on this list is charged to anyone until it is accepted, so the panel shows the old rate
 * beside the new one, the words of the notification it was read from, and any reason the
 * reading should be double-checked. It hides itself when there is nothing to decide.
 */
const GstRateChanges = ({ onChanged }) => {
  const [changes, setChanges] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(null);

  const load = useCallback(async () => {
    try {
      const r = await api.get("/api/admin/gst-rates/changes");
      setChanges(Array.isArray(r.data?.changes) ? r.data.changes : []);
    } catch (e) {
      setError(e.response?.data?.error || "The drafted rate changes could not be loaded.");
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const act = async (change, action) => {
    let note = null;
    if (action === "accept") {
      const warning = change.needsAttention
        ? `\n\nCheck first: ${change.attentionReason}`
        : "";
      if (!window.confirm(`Charge ${pct(change.proposedRate)} GST on ${change.hsnCode} from ${change.effectiveFrom}?${warning}`)) return;
    } else {
      note = window.prompt(action === "reject"
        ? "Why is this change being rejected?"
        : "What was done about this clause?");
      if (!note || !note.trim()) return;
    }
    setBusy(change.id); setError(""); setNotice("");
    try {
      await api.post(`/api/admin/gst-rates/changes/${change.id}/${action}`, { note });
      setNotice(action === "accept"
        ? `${change.hsnCode} will be charged ${pct(change.proposedRate)} from ${change.effectiveFrom}.`
        : "Recorded.");
      await load();
      if (onChanged) onChanged();
    } catch (e) {
      setError(e.response?.data?.error || "That could not be saved. Please try again.");
    } finally {
      setBusy(null);
    }
  };

  if (changes.length === 0 && !error) return null;

  const byNotification = changes.reduce((groups, c) => {
    (groups[c.notification] = groups[c.notification] || []).push(c);
    return groups;
  }, {});

  return (
    <div style={{ ...card, borderColor: "#f59e0b", marginBottom: "1.5rem" }}>
      <h2 style={{ fontSize: "1.1rem", fontWeight: 700, margin: "0 0 4px" }}>
        Rate changes waiting for your decision ({changes.length})
      </h2>
      <p style={{ margin: "0 0 1rem", color: "#6b7280", fontSize: "0.85rem" }}>
        Read automatically from new government notifications. No rate changes until you accept it.
        Compare each one with the notification text shown before accepting.
      </p>
      {error && <div style={{ background: "#fef2f2", color: "#b91c1c", padding: "0.6rem 0.8rem", borderRadius: 6, fontSize: "0.85rem", marginBottom: "0.75rem" }}>{error}</div>}
      {notice && <div style={{ background: "#f0fdf4", color: "#166534", padding: "0.6rem 0.8rem", borderRadius: 6, fontSize: "0.85rem", marginBottom: "0.75rem" }}>{notice}</div>}

      {Object.entries(byNotification).map(([notification, rows]) => (
        <div key={notification} style={{ marginBottom: "1rem" }}>
          <h3 style={{ fontSize: "0.95rem", fontWeight: 600, margin: "0 0 0.5rem" }}>
            Notification {notification}
            {rows[0].effectiveFrom && <span style={{ fontWeight: 400, color: "#6b7280" }}> · in force from {rows[0].effectiveFrom}</span>}
          </h3>
          {rows.map((c) => (
            <div key={c.id} style={{ border: "1px solid #e5e7eb", borderLeft: `4px solid ${c.needsAttention ? "#f59e0b" : "#16a34a"}`, borderRadius: 6, padding: "0.75rem", marginBottom: "0.5rem" }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem", flexWrap: "wrap", alignItems: "flex-start" }}>
                <div style={{ minWidth: 0, flex: 1 }}>
                  {c.hsnCode ? (
                    <div style={{ fontSize: "0.95rem", fontWeight: 600 }}>
                      HSN {c.hsnCode}: {pct(c.currentRate)} → {pct(c.proposedRate)}
                      {c.currentRate === c.proposedRate && <span style={{ fontWeight: 400, color: "#6b7280" }}> (no change to what is charged)</span>}
                    </div>
                  ) : (
                    <div style={{ fontSize: "0.95rem", fontWeight: 600 }}>A clause that needs reading by hand</div>
                  )}
                  {c.description && <div style={{ fontSize: "0.85rem", color: "#374151", marginTop: 2 }}>{c.description}</div>}
                  <div style={{ fontSize: "0.8rem", color: "#6b7280", marginTop: 4 }}>
                    {c.affectedProductCount > 0
                      ? `${c.affectedProductCount} product(s) on sale use this code: ${c.affectedProducts.join(", ")}${c.affectedProductCount > c.affectedProducts.length ? " and more" : ""}`
                      : c.hsnCode ? "No product on sale uses this code." : ""}
                  </div>
                </div>
                <div style={{ display: "flex", gap: "0.4rem", flexShrink: 0 }}>
                  {c.canAccept && (
                    <button disabled={busy === c.id} onClick={() => act(c, "accept")} style={{ ...btn, background: "#16a34a", color: "#fff" }}>Accept</button>
                  )}
                  {c.hsnCode ? (
                    <button disabled={busy === c.id} onClick={() => act(c, "reject")} style={{ ...btn, background: "#fee2e2", color: "#b91c1c" }}>Reject</button>
                  ) : (
                    <button disabled={busy === c.id} onClick={() => act(c, "acknowledge")} style={{ ...btn, background: "#e5e7eb", color: "#111827" }}>Mark as dealt with</button>
                  )}
                </div>
              </div>
              {c.needsAttention && c.attentionReason && (
                <div style={{ background: "#fffbeb", color: "#92400e", padding: "0.5rem 0.7rem", borderRadius: 6, fontSize: "0.8rem", marginTop: "0.5rem" }}>
                  <strong>Check before accepting:</strong> {c.attentionReason}
                </div>
              )}
              <details style={{ marginTop: "0.5rem" }}>
                <summary style={{ cursor: "pointer", fontSize: "0.8rem", color: "#6b7280" }}>Notification text this was read from</summary>
                <p style={{ fontSize: "0.8rem", color: "#374151", margin: "6px 0 0", whiteSpace: "pre-wrap" }}>{c.clause}</p>
              </details>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
};

export default GstRateChanges;
