import React, { useState, useEffect, useCallback } from "react";
import api from "../../api/axios";
import { promptText } from "../../components/ConfirmDialog";

const card = { background: "#fff", border: "1px solid #e5e7eb", borderRadius: 10, padding: "1.25rem" };
const hint = { fontSize: "0.75rem", color: "#6b7280", marginTop: 3 };
const btn = { padding: "0.4rem 0.8rem", border: "none", borderRadius: 6, cursor: "pointer", fontSize: "0.8rem", fontWeight: 500 };

const STATUS = {
  NEW: { label: "Not read", bg: "#fef3c7", fg: "#92400e" },
  ACTION_NEEDED: { label: "Action needed", bg: "#fee2e2", fg: "#b91c1c" },
  REVIEWED: { label: "Reviewed", bg: "#dcfce7", fg: "#166534" },
  NOT_RELEVANT: { label: "Not relevant", bg: "#f3f4f6", fg: "#4b5563" },
};

const FILTERS = [
  ["open", "To read or act on"],
  ["all", "All"],
];

/**
 * GST rule changes: CBIC notifications that are not rate notifications, CBIC circulars, and
 * GSTN advisories from gst.gov.in. Each one waits here until a person has read it and recorded
 * whether the store must act.
 */
const GstRuleUpdates = () => {
  const [data, setData] = useState(null);
  const [openText, setOpenText] = useState(null);
  const [filter, setFilter] = useState("open");
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    try {
      const { data } = await api.get("/api/admin/gst/rule-updates");
      setData(data);
    } catch (e) {
      setError(e.response?.data?.error || "Could not load the GST rule changes.");
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const checkNow = async () => {
    setBusy("check");
    setError("");
    setNotice("");
    try {
      const { data: r } = await api.post("/api/admin/gst/rule-updates/check");
      if (!r.reachable) {
        setError("Neither CBIC nor GSTN could be read, so nothing was checked. " + (r.problems || []).join(" "));
      } else {
        setNotice(r.found > 0
          ? `${r.found} new rule change(s) found.`
          : "Checked CBIC and GSTN: nothing new." +((r.problems || []).length ? " Some sources failed: " + r.problems.join(" ") : ""));
      }
      await load();
    } catch (e) {
      setError(e.response?.data?.error || "The check could not run.");
    } finally {
      setBusy(null);
    }
  };

  const openDocument = async (u) => {
    setBusy("doc" + u.id);
    setError("");
    try {
      const res = await api.get(`/api/admin/gst/rule-updates/${u.id}/document`, { responseType: "blob" });
      const url = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }));
      window.open(url, "_blank", "noopener");
      setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch (e) {
      setError("CBIC did not return the document. Open it on taxinformation.cbic.gov.in instead.");
    } finally {
      setBusy(null);
    }
  };

  const review = async (u, status) => {
    let note = null;
    if (status === "ACTION_NEEDED") {
      note = await promptText(`What does the store need to do about ${u.number}?`, u.reviewNote || "");
      if (note === null) return;
    } else if (status !== "NEW") {
      note = await promptText(`Anything to note about ${u.number}? (optional)`, u.reviewNote || "");
      if (note === null) return;
    }
    setBusy("rev" + u.id);
    setError("");
    try {
      await api.post(`/api/admin/gst/rule-updates/${u.id}/review`, { status, note });
      await load();
    } catch (e) {
      setError(e.response?.data?.error || "Could not save that.");
    } finally {
      setBusy(null);
    }
  };

  const items = (data?.items || []).filter((u) =>
    filter === "all" || u.status === "NEW" || u.status === "ACTION_NEEDED");

  return (
    <div style={{ ...card, marginBottom: "1.5rem" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "1rem", flexWrap: "wrap" }}>
        <div>
          <h3 style={{ marginTop: 0, marginBottom: 4, fontSize: "1.05rem", fontWeight: 600 }}>
            GST rule changes
          </h3>
          <p style={{ ...hint, marginTop: 0, maxWidth: 640 }}>
            New notifications and circulars from CBIC that change rules rather than product rates
            (returns, invoicing, registration and e-commerce duties), and GSTN advisories from
            gst.gov.in on portal changes. Checked every morning. Read each one and record whether
            the store needs to act. How-to manuals:{" "}
            <a href="https://www.gst.gov.in/help/helpmodules/" target="_blank" rel="noopener noreferrer">GST help modules</a>.
          </p>
          {data && (
            <p style={{ ...hint, marginTop: 4 }}>
              {data.new} not read · {data.actionNeeded} need action
              {data.lastRunAt ? ` · last checked ${String(data.lastRunAt).replace("T", " ").slice(0, 16)}` : " · not checked since the server started"}
            </p>
          )}
        </div>
        <button type="button" onClick={checkNow} disabled={busy === "check"}
                style={{ ...btn, background: "#0f172a", color: "#fff", whiteSpace: "nowrap" }}>
          {busy === "check" ? "Checking…" : "Check now"}
        </button>
      </div>

      {error && <p style={{ color: "#b91c1c", fontSize: "0.85rem" }}>{error}</p>}
      {notice && <p style={{ color: "#166534", fontSize: "0.85rem" }}>{notice}</p>}
      {data?.lastProblems?.length > 0 && (
        <p style={{ color: "#b45309", fontSize: "0.8rem" }}>
          Last check could not read: {data.lastProblems.join("; ")}
        </p>
      )}

      <div style={{ display: "flex", gap: 6, margin: "0.75rem 0" }}>
        {FILTERS.map(([key, label]) => (
          <button key={key} type="button" onClick={() => setFilter(key)}
                  style={{ ...btn, background: filter === key ? "#0E5C5C" : "#f3f4f6", color: filter === key ? "#fff" : "#374151" }}>
            {label}
          </button>
        ))}
      </div>

      {!data ? (
        <p style={hint}>Loading…</p>
      ) : items.length === 0 ? (
        <p style={hint}>{filter === "open" ? "Nothing waiting to be read." : "No rule changes recorded yet. Use “Check now”."}</p>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {items.map((u) => {
            const s = STATUS[u.status] || STATUS.NEW;
            return (
              <div key={u.id} style={{ border: "1px solid #e5e7eb", borderRadius: 8, padding: "0.75rem" }}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
                  <div style={{ fontWeight: 600, fontSize: "0.9rem" }}>
                    {u.number}
                    <span style={{ fontWeight: 400, color: "#6b7280" }}>
                      {u.issuedOn ? ` · ${u.issuedOn}` : ""}{u.category ? ` · ${u.category}` : ""}
                    </span>
                  </div>
                  <span style={{ background: s.bg, color: s.fg, borderRadius: 999, padding: "2px 10px", fontSize: "0.75rem", fontWeight: 600 }}>
                    {s.label}
                  </span>
                </div>
                <div style={{ fontSize: "0.85rem", color: "#374151", marginTop: 4 }}>{u.title}</div>
                {openText === u.id && (
                  <div style={{ whiteSpace: "pre-wrap", fontSize: "0.8rem", color: "#374151", background: "#f9fafb",
                                border: "1px solid #e5e7eb", borderRadius: 6, padding: "0.6rem", marginTop: 6,
                                maxHeight: 360, overflowY: "auto" }}>
                    {u.content || "GSTN gave no text for this advisory. Open it on gst.gov.in."}
                  </div>
                )}
                {u.reviewNote && (
                  <div style={{ fontSize: "0.8rem", color: "#4b5563", marginTop: 4 }}>
                    <strong>Note:</strong> {u.reviewNote}
                    {u.reviewedBy && <span style={{ color: "#9ca3af" }}> — {u.reviewedBy}</span>}
                  </div>
                )}
                <div style={{ display: "flex", gap: 6, marginTop: 8, flexWrap: "wrap" }}>
                  {u.kind === "ADVISORY" ? (
                    <>
                      <button type="button" onClick={() => setOpenText(openText === u.id ? null : u.id)}
                              style={{ ...btn, background: "#e0f2f1", color: "#0E5C5C" }}>
                        {openText === u.id ? "Hide advisory" : "Read advisory"}
                      </button>
                      <a href={`https://www.gst.gov.in/newsandupdates/read/${u.cbicDocumentId}`} target="_blank" rel="noopener noreferrer"
                         style={{ ...btn, background: "#f3f4f6", color: "#374151", textDecoration: "none" }}>On gst.gov.in</a>
                      {/^https:\/\//.test(u.documentPath || "") && (
                        <a href={u.documentPath} target="_blank" rel="noopener noreferrer"
                           style={{ ...btn, background: "#f3f4f6", color: "#374151", textDecoration: "none" }}>Attachment</a>
                      )}
                    </>
                  ) : (
                    <button type="button" onClick={() => openDocument(u)} disabled={busy === "doc" + u.id}
                            style={{ ...btn, background: "#e0f2f1", color: "#0E5C5C" }}>
                      {busy === "doc" + u.id ? "Opening…" : "Read PDF"}
                    </button>
                  )}
                  {u.status !== "ACTION_NEEDED" && (
                    <button type="button" onClick={() => review(u, "ACTION_NEEDED")} disabled={busy === "rev" + u.id}
                            style={{ ...btn, background: "#fee2e2", color: "#b91c1c" }}>Needs action</button>
                  )}
                  {u.status !== "REVIEWED" && (
                    <button type="button" onClick={() => review(u, "REVIEWED")} disabled={busy === "rev" + u.id}
                            style={{ ...btn, background: "#dcfce7", color: "#166534" }}>
                      {u.status === "ACTION_NEEDED" ? "Done" : "Reviewed"}
                    </button>
                  )}
                  {u.status !== "NOT_RELEVANT" && (
                    <button type="button" onClick={() => review(u, "NOT_RELEVANT")} disabled={busy === "rev" + u.id}
                            style={{ ...btn, background: "#f3f4f6", color: "#4b5563" }}>Not relevant</button>
                  )}
                  {u.status !== "NEW" && (
                    <button type="button" onClick={() => review(u, "NEW")} disabled={busy === "rev" + u.id}
                            style={{ ...btn, background: "transparent", color: "#6b7280", textDecoration: "underline" }}>Reopen</button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
      {data?.sources && <p style={{ ...hint, marginTop: 10 }}>Source: {data.sources}, last {data.lookbackDays} days.</p>}
    </div>
  );
};

export default GstRuleUpdates;
