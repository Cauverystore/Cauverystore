import React, { useState, useEffect, useCallback } from "react";
import api from "../../api/axios";
import { showMessage } from "../../components/ConfirmDialog";
import useAltText from "../../hooks/useAltText";

const TEAL = "#0E5C5C";
const TYPE_STYLE = {
  FEEDBACK: { label: "Feedback", bg: "#e0f2fe", color: "#075985" },
  COMPLAINT: { label: "Complaint", bg: "#fee2e2", color: "#991b1b" },
  SUGGESTION: { label: "Suggestion", bg: "#fef3c7", color: "#92400e" },
};

const fmt = (iso) => {
  if (!iso) return "-";
  const d = new Date(iso);
  return isNaN(d) ? iso : d.toLocaleString("en-IN", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
};

const btn = (bg) => ({ padding: "0.35rem 0.9rem", background: bg, color: "#fff", border: "none", borderRadius: 4, cursor: "pointer", fontSize: "0.8rem", textDecoration: "none", display: "inline-block" });
const chip = (on) => ({ padding: "0.3rem 0.8rem", borderRadius: 999, border: `1px solid ${on ? TEAL : "#d1d5db"}`, background: on ? TEAL : "#fff", color: on ? "#fff" : "#374151", cursor: "pointer", fontSize: "0.8rem" });

/** Photo is fetched with the staff sign-in, then shown from a local blob link. */
const Photo = ({ id }) => {
  const altText = useAltText();
  const [src, setSrc] = useState(null);
  useEffect(() => {
    let url;
    api.get(`/api/admin/feedback/${id}/photo`, { responseType: "blob" })
      .then((r) => { url = URL.createObjectURL(r.data); setSrc(url); })
      .catch(() => setSrc(""));
    return () => { if (url) URL.revokeObjectURL(url); };
  }, [id]);
  if (src === null) return <div style={{ color: "#6b7280", fontSize: "0.85rem" }}>Loading photo...</div>;
  if (!src) return <div style={{ color: "#b91c1c", fontSize: "0.85rem" }}>Could not load the photo.</div>;
  return (
    <a href={src} target="_blank" rel="noreferrer">
      <img src={src} alt={altText.customerPhoto()} style={{ maxWidth: "100%", maxHeight: 320, borderRadius: 8, border: "1px solid #e5e7eb" }} />
    </a>
  );
};

/** Admin -> Feedback: messages from the "Feedback" tab on the shop pages. */
const AdminFeedback = () => {
  const [data, setData] = useState({ items: [], total: 0, page: 0, totalPages: 0, newCount: 0 });
  const [status, setStatus] = useState("NEW");
  const [type, setType] = useState("ALL");
  const [page, setPage] = useState(0);
  const [openId, setOpenId] = useState(null);

  const fetchList = useCallback(async () => {
    try { setData((await api.get(`/api/admin/feedback?status=${status}&type=${type}&page=${page}`)).data); }
    catch { showMessage("Could not load feedback"); }
  }, [status, type, page]);
  useEffect(() => { fetchList(); }, [fetchList]);

  const setItemStatus = async (id, s) => {
    try { await api.put(`/api/admin/feedback/${id}/status`, { status: s }); setOpenId(null); fetchList(); }
    catch { showMessage("Failed"); }
  };

  const remove = async (id) => {
    if (!window.confirm("Delete this feedback? This cannot be undone.")) return;
    try { await api.delete(`/api/admin/feedback/${id}`); setOpenId(null); fetchList(); }
    catch { showMessage("Failed"); }
  };

  const filter = (setter, value) => { setter(value); setPage(0); setOpenId(null); };

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1.5rem", flexWrap: "wrap", gap: "0.5rem" }}>
        <h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: 0 }}>
          Feedback {data.newCount > 0 && <span style={{ fontSize: "0.85rem", background: TEAL, color: "#fff", borderRadius: 12, padding: "2px 10px", marginLeft: 8 }}>{data.newCount} new</span>}
        </h1>
        <button onClick={fetchList} style={btn(TEAL)}>Refresh</button>
      </div>
      <p style={{ color: "#6b7280", fontSize: "0.85rem", marginTop: "-0.75rem" }}>Messages customers sent with the "Feedback" tab on the website. Each one is also emailed to support.</p>

      <div style={{ display: "flex", gap: "1.25rem", flexWrap: "wrap", marginBottom: "1rem" }}>
        <div style={{ display: "flex", gap: "0.4rem", flexWrap: "wrap" }}>
          {[["NEW", "To read"], ["DONE", "Done"], ["ALL", "All"]].map(([v, l]) => (
            <button key={v} style={chip(status === v)} onClick={() => filter(setStatus, v)}>{l}</button>
          ))}
        </div>
        <div style={{ display: "flex", gap: "0.4rem", flexWrap: "wrap" }}>
          {[["ALL", "All types"], ["FEEDBACK", "Feedback"], ["COMPLAINT", "Complaints"], ["SUGGESTION", "Suggestions"]].map(([v, l]) => (
            <button key={v} style={chip(type === v)} onClick={() => filter(setType, v)}>{l}</button>
          ))}
        </div>
      </div>

      <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: 10, overflow: "hidden" }}>
        {data.items.length === 0 && <div style={{ padding: "2rem", textAlign: "center", color: "#94a3b8" }}>Nothing here</div>}
        {data.items.map((f) => {
          const ts = TYPE_STYLE[f.type] || TYPE_STYLE.FEEDBACK;
          const isOpen = openId === f.id;
          return (
            <div key={f.id} style={{ borderBottom: "1px solid #f3f4f6", background: f.status === "NEW" ? "#f0fdfa" : "#fff" }}>
              <div onClick={() => setOpenId(isOpen ? null : f.id)} style={{ padding: "12px 14px", cursor: "pointer" }}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem", alignItems: "center" }}>
                  <span style={{ display: "flex", gap: 8, alignItems: "center", minWidth: 0 }}>
                    <span style={{ background: ts.bg, color: ts.color, fontSize: "0.72rem", fontWeight: 700, borderRadius: 4, padding: "1px 7px", whiteSpace: "nowrap" }}>{ts.label}</span>
                    <span style={{ fontWeight: f.status === "NEW" ? 700 : 400, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {f.name || "Visitor"}{f.email ? ` <${f.email}>` : ""}
                    </span>
                  </span>
                  <span style={{ color: "#6b7280", fontSize: "0.8rem", whiteSpace: "nowrap" }}>{f.photoType ? "📷 " : ""}{fmt(f.createdAt)}</span>
                </div>
                {!isOpen && <div style={{ color: "#4b5563", fontSize: "0.85rem", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", marginTop: 2 }}>{f.message}</div>}
              </div>
              {isOpen && (
                <div style={{ padding: "0 14px 14px" }}>
                  <pre style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere", fontFamily: "inherit", fontSize: "0.9rem", background: "#f9fafb", padding: "0.9rem", borderRadius: 6, margin: "0 0 0.75rem" }}>{f.message}</pre>
                  {f.photoType && <div style={{ marginBottom: "0.75rem" }}><Photo id={f.id} /></div>}
                  <div style={{ fontSize: "0.8rem", color: "#6b7280", marginBottom: "0.75rem", lineHeight: 1.6 }}>
                    {f.pageUrl && f.pageUrl.startsWith("/") && !f.pageUrl.startsWith("//") && <div>Sent from page: <a href={f.pageUrl} target="_blank" rel="noreferrer">{f.pageUrl}</a></div>}
                    <div>{f.userId ? `Signed-in customer (user no. ${f.userId})` : "Not signed in"}</div>
                  </div>
                  <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
                    {f.email && <a href={`mailto:${encodeURIComponent(f.email)}?subject=${encodeURIComponent("Your feedback to Cauvery Store")}`} style={btn(TEAL)}>Reply by email</a>}
                    {f.status === "NEW"
                      ? <button onClick={() => setItemStatus(f.id, "DONE")} style={btn("#16a34a")}>Mark done</button>
                      : <button onClick={() => setItemStatus(f.id, "NEW")} style={btn("#3b82f6")}>Mark to read</button>}
                    <button onClick={() => remove(f.id)} style={btn("#dc2626")}>Delete</button>
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </div>

      {data.totalPages > 1 && (
        <div style={{ display: "flex", gap: "0.5rem", justifyContent: "center", marginTop: "1rem" }}>
          <button disabled={page === 0} onClick={() => setPage((p) => p - 1)} style={btn(page === 0 ? "#d1d5db" : TEAL)}>Newer</button>
          <span style={{ alignSelf: "center", fontSize: "0.85rem" }}>Page {page + 1} of {data.totalPages}</span>
          <button disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)} style={btn(page + 1 >= data.totalPages ? "#d1d5db" : TEAL)}>Older</button>
        </div>
      )}
    </div>
  );
};

export default AdminFeedback;
