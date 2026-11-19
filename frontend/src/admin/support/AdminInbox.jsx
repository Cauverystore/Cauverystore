import React, { useState, useEffect, useCallback } from "react";
import api from "../../api/axios";
import { showMessage } from "../../components/ConfirmDialog";

const TEAL = "#0E5C5C";

const fmt = (iso) => {
  if (!iso) return "-";
  const d = new Date(iso);
  return isNaN(d) ? iso : d.toLocaleString("en-IN", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
};

/** Admin -> Inbox: emails people sent to support@cauverystore.in and the store's other addresses. */
const AdminInbox = () => {
  const [data, setData] = useState({ items: [], unread: 0, total: 0, page: 0, totalPages: 0 });
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchList = useCallback(async () => {
    try { setData((await api.get(`/api/admin/inbox?page=${page}`)).data); } catch { showMessage("Could not load the inbox"); }
  }, [page]);
  useEffect(() => { fetchList(); }, [fetchList]);

  const openEmail = async (id) => {
    setLoading(true);
    try { setOpen((await api.get(`/api/admin/inbox/${id}`)).data); fetchList(); }
    catch { showMessage("Could not open this email"); }
    finally { setLoading(false); }
  };

  const markUnread = async () => {
    try { await api.put(`/api/admin/inbox/${open.id}/read`, { read: false }); setOpen(null); fetchList(); } catch { showMessage("Failed"); }
  };

  const remove = async () => {
    if (!window.confirm("Delete this email from the inbox? This cannot be undone.")) return;
    try { await api.delete(`/api/admin/inbox/${open.id}`); setOpen(null); fetchList(); } catch { showMessage("Failed"); }
  };

  const replyHref = (e) => {
    const subj = e.subject && /^re:/i.test(e.subject) ? e.subject : `Re: ${e.subject || ""}`;
    return `mailto:${encodeURIComponent(e.fromAddress || "")}?subject=${encodeURIComponent(subj)}`;
  };

  const btn = (bg) => ({ padding: "0.35rem 0.9rem", background: bg, color: "#fff", border: "none", borderRadius: 4, cursor: "pointer", fontSize: "0.8rem", textDecoration: "none", display: "inline-block" });

  if (open) {
    return (
      <div>
        <button onClick={() => setOpen(null)} style={{ ...btn("#6b7280"), marginBottom: "1rem" }}>&larr; Back to Inbox</button>
        <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: 10, padding: "1.25rem" }}>
          <h1 style={{ fontSize: "1.25rem", fontWeight: 700, margin: "0 0 0.75rem" }}>{open.subject || "(no subject)"}</h1>
          <div style={{ fontSize: "0.85rem", color: "#4b5563", lineHeight: 1.7 }}>
            <div><strong>From:</strong> {open.fromHeader}</div>
            <div><strong>To:</strong> {open.toAddresses}</div>
            {open.ccAddresses && <div><strong>Cc:</strong> {open.ccAddresses}</div>}
            <div><strong>Received:</strong> {fmt(open.receivedAt)}</div>
            {open.forwardedTo && <div><strong>Forwarded to:</strong> {open.forwardedTo}</div>}
            {open.authSummary && <div style={{ color: "#6b7280" }}><strong>Sender checks:</strong> {open.authSummary}</div>}
            {open.attachmentNames && <div><strong>Attachments:</strong> {open.attachmentNames.split("\n").join(", ")} <span style={{ color: "#6b7280" }}>(sent with the forwarded copy)</span></div>}
          </div>
          <div style={{ display: "flex", gap: "0.5rem", margin: "1rem 0" }}>
            <a href={replyHref(open)} style={btn(TEAL)}>Reply</a>
            <button onClick={markUnread} style={btn("#3b82f6")}>Mark unread</button>
            <button onClick={remove} style={btn("#dc2626")}>Delete</button>
          </div>
          {open.htmlBody ? (
            // sandbox with no permissions: no scripts, no forms, links can't act on this page.
            <iframe title="Email" sandbox="" srcDoc={open.htmlBody} style={{ width: "100%", minHeight: 480, border: "1px solid #e5e7eb", borderRadius: 6, background: "#fff" }} />
          ) : (
            <pre style={{ whiteSpace: "pre-wrap", fontFamily: "inherit", fontSize: "0.9rem", background: "#f9fafb", padding: "1rem", borderRadius: 6 }}>{open.textBody || "(empty message)"}</pre>
          )}
        </div>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1.5rem", flexWrap: "wrap", gap: "0.5rem" }}>
        <h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: 0 }}>Inbox {data.unread > 0 && <span style={{ fontSize: "0.85rem", background: TEAL, color: "#fff", borderRadius: 12, padding: "2px 10px", marginLeft: 8 }}>{data.unread} unread</span>}</h1>
        <button onClick={fetchList} style={btn(TEAL)}>Refresh</button>
      </div>
      <p style={{ color: "#6b7280", fontSize: "0.85rem", marginTop: "-0.75rem" }}>Emails sent to support@cauverystore.in, including customers' replies to store emails.</p>
      <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: 10, overflow: "hidden" }}>
        {data.items.length === 0 && <div style={{ padding: "2rem", textAlign: "center", color: "#94a3b8" }}>No emails yet</div>}
        {data.items.map(e => (
          <div key={e.id} onClick={() => !loading && openEmail(e.id)} style={{ padding: "12px 14px", borderBottom: "1px solid #f3f4f6", cursor: "pointer", background: e.read ? "#fff" : "#f0fdfa" }}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem" }}>
              <span style={{ fontWeight: e.read ? 400 : 700, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{e.fromHeader || e.fromAddress}</span>
              <span style={{ color: "#6b7280", fontSize: "0.8rem", whiteSpace: "nowrap" }}>{e.hasAttachments ? "📎 " : ""}{fmt(e.receivedAt)}</span>
            </div>
            <div style={{ fontWeight: e.read ? 400 : 600, fontSize: "0.9rem", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{e.subject || "(no subject)"}</div>
            <div style={{ color: "#6b7280", fontSize: "0.8rem", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{e.preview}</div>
          </div>
        ))}
      </div>
      {data.totalPages > 1 && (
        <div style={{ display: "flex", gap: "0.5rem", justifyContent: "center", marginTop: "1rem" }}>
          <button disabled={page === 0} onClick={() => setPage(p => p - 1)} style={btn(page === 0 ? "#d1d5db" : TEAL)}>Newer</button>
          <span style={{ alignSelf: "center", fontSize: "0.85rem" }}>Page {page + 1} of {data.totalPages}</span>
          <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)} style={btn(page + 1 >= data.totalPages ? "#d1d5db" : TEAL)}>Older</button>
        </div>
      )}
    </div>
  );
};
export default AdminInbox;
