import React, { useCallback, useEffect, useState } from "react";
import api from "../api/axios";
import { confirmAction, showMessage } from "../components/ConfirmDialog";

// The seller's team: who can work in this store, pending invitations, and the roles on offer.
const box = { background: "#fff", padding: "1.25rem 1.5rem", borderRadius: 8, boxShadow: "0 1px 3px rgba(0,0,0,0.1)", marginBottom: "1.5rem" };
const inp = { padding: "0.5rem 0.65rem", border: "1px solid #d1d5db", borderRadius: 6, fontSize: "0.875rem", boxSizing: "border-box" };
const btn = { padding: "0.5rem 1.1rem", background: "#0E5C5C", color: "#fff", border: "none", borderRadius: 6, cursor: "pointer", fontSize: "0.875rem", fontWeight: 600 };
const linkBtn = (color) => ({ background: "none", border: "none", color, cursor: "pointer", fontSize: "0.8rem", padding: "0 4px" });
const cell = { padding: "0.6rem 0.4rem", fontSize: "0.875rem", borderTop: "1px solid #f1f5f9", verticalAlign: "top" };
const dateText = (d) => (d ? new Date(d).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "—");

const SellerTeam = () => {
  const [members, setMembers] = useState([]);
  const [invites, setInvites] = useState([]);
  const [roles, setRoles] = useState([]);
  const [perms, setPerms] = useState([]);
  const [invite, setInvite] = useState({ email: "", roleId: "" });
  const [roleForm, setRoleForm] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [m, i, r, p] = await Promise.all([
        api.get("/api/seller/team/members"),
        api.get("/api/seller/team/invitations"),
        api.get("/api/seller/team/roles"),
        api.get("/api/seller/team/permissions"),
      ]);
      setMembers(m.data || []); setInvites(i.data || []); setRoles(r.data || []); setPerms(p.data || []);
      setError("");
    } catch (e) {
      setError(e.response?.data?.error || "The team could not be loaded.");
    }
  }, []);
  useEffect(() => { load(); }, [load]);


  const sendInvite = async (e) => {
    e.preventDefault();
    if (!invite.roleId) { setError("Choose a role for the person you are inviting."); return; }
    setBusy(true);
    try {
      await api.post("/api/seller/team/invitations", { email: invite.email, roleId: Number(invite.roleId) });
      setInvite({ email: "", roleId: "" });
      showMessage(`Invitation sent to ${invite.email}. It works for 7 days.`, { title: "Done" });
      load();
    } catch (err) { setError(err.response?.data?.error || "The invitation could not be sent."); }
    setBusy(false);
  };

  const cancelInvite = async (i) => {
    if (!(await confirmAction(`Cancel the invitation to ${i.email}?`, { confirmLabel: "Cancel invitation", cancelLabel: "Keep it", danger: true }))) return;
    try { await api.delete(`/api/seller/team/invitations/${i.id}`); load(); }
    catch (err) { setError(err.response?.data?.error || "Could not cancel the invitation."); }
  };

  const changeRole = async (m, roleId) => {
    try { await api.put(`/api/seller/team/members/${m.id}`, { roleId: Number(roleId) }); load(); }
    catch (err) { setError(err.response?.data?.error || "Could not change the role."); }
  };

  const removeMember = async (m) => {
    if (!(await confirmAction(`Remove ${m.name} from the team?\n\nThey lose access to this store straight away.`, { confirmLabel: "Remove", danger: true }))) return;
    try { await api.delete(`/api/seller/team/members/${m.id}`); load(); }
    catch (err) { setError(err.response?.data?.error || "Could not remove them."); }
  };

  const saveRole = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      const body = { name: roleForm.name, description: roleForm.description, permissions: roleForm.permissions };
      if (roleForm.id) await api.put(`/api/seller/team/roles/${roleForm.id}`, body);
      else await api.post("/api/seller/team/roles", body);
      setRoleForm(null); load();
    } catch (err) { setError(err.response?.data?.error || "The role could not be saved."); }
    setBusy(false);
  };

  const deleteRole = async (r) => {
    if (!(await confirmAction(`Delete the role "${r.name}"?`, { confirmLabel: "Delete", danger: true }))) return;
    try { await api.delete(`/api/seller/team/roles/${r.id}`); load(); }
    catch (err) { setError(err.response?.data?.error || "Could not delete the role."); }
  };

  const togglePerm = (code) => {
    const has = roleForm.permissions.includes(code);
    setRoleForm({ ...roleForm, permissions: has ? roleForm.permissions.filter((p) => p !== code) : [...roleForm.permissions, code] });
  };

  return (
    <div style={{ padding: "2rem", maxWidth: 960 }}>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 700, marginBottom: "0.25rem" }}>Team</h1>
      <p style={{ color: "#6b7280", fontSize: "0.9rem", margin: "0 0 1.5rem" }}>
        Invite people to help run this store. Each person signs in with their own account and sees only what their role allows.
      </p>
      {error && <div style={{ background: "#fee2e2", color: "#dc2626", padding: "0.6rem 0.8rem", borderRadius: 6, marginBottom: "1rem", fontSize: "0.875rem" }}>{error}</div>}

      <div style={box}>
        <h2 style={{ fontSize: "1.05rem", fontWeight: 700, margin: "0 0 0.75rem" }}>Invite someone</h2>
        <form onSubmit={sendInvite} style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
          <input type="email" required placeholder="Their email address" aria-label="Email address to invite" value={invite.email}
            onChange={(e) => setInvite({ ...invite, email: e.target.value })} style={{ ...inp, flex: "1 1 240px" }} />
          <select aria-label="Role" value={invite.roleId} onChange={(e) => setInvite({ ...invite, roleId: e.target.value })} style={{ ...inp, flex: "0 1 220px" }}>
            <option value="">Choose a role</option>
            {roles.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
          </select>
          <button type="submit" disabled={busy} style={{ ...btn, opacity: busy ? 0.7 : 1 }}>Send invitation</button>
        </form>
        {invite.roleId && (
          <p style={{ fontSize: "0.8rem", color: "#6b7280", margin: "0.6rem 0 0" }}>
            {roles.find((r) => String(r.id) === invite.roleId)?.description}
          </p>
        )}
      </div>

      <div style={box}>
        <h2 style={{ fontSize: "1.05rem", fontWeight: 700, margin: "0 0 0.5rem" }}>People</h2>
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <tbody>
              {members.map((m) => (
                <tr key={m.id ?? "owner"}>
                  <td style={cell}><div style={{ fontWeight: 600 }}>{m.name}</div><div style={{ color: "#6b7280", fontSize: "0.8rem" }}>{m.email}</div></td>
                  <td style={cell}>
                    {m.accountOwner ? <span style={{ fontWeight: 600 }}>Account owner</span> : (
                      <select aria-label={`Role for ${m.name}`} value={m.roleId || ""} onChange={(e) => changeRole(m, e.target.value)} style={inp}>
                        {roles.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
                      </select>
                    )}
                  </td>
                  <td style={{ ...cell, color: "#6b7280", fontSize: "0.8rem" }}>{m.accountOwner ? "" : `Joined ${dateText(m.joinedAt)}`}</td>
                  <td style={{ ...cell, textAlign: "right" }}>{!m.accountOwner && <button type="button" onClick={() => removeMember(m)} style={linkBtn("#dc2626")}>Remove</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {invites.length > 0 && (
          <>
            <h3 style={{ fontSize: "0.95rem", fontWeight: 600, margin: "1.25rem 0 0.25rem" }}>Waiting to accept</h3>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <tbody>
                {invites.map((i) => (
                  <tr key={i.id}>
                    <td style={cell}>{i.email}</td>
                    <td style={cell}>{i.roleName}</td>
                    <td style={{ ...cell, color: i.expired ? "#b91c1c" : "#6b7280", fontSize: "0.8rem" }}>{i.expired ? "Expired - invite again" : `Expires ${dateText(i.expiresAt)}`}</td>
                    <td style={{ ...cell, textAlign: "right" }}><button type="button" onClick={() => cancelInvite(i)} style={linkBtn("#dc2626")}>Cancel</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </div>

      <div style={box}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.5rem" }}>
          <h2 style={{ fontSize: "1.05rem", fontWeight: 700, margin: 0 }}>Roles</h2>
          {!roleForm && <button type="button" style={{ ...btn, padding: "0.35rem 0.9rem" }} onClick={() => setRoleForm({ name: "", description: "", permissions: ["dashboard.view"] })}>+ New role</button>}
        </div>

        {roleForm && (
          <form onSubmit={saveRole} style={{ background: "#f9fafb", padding: "1rem", borderRadius: 6, marginBottom: "1rem" }}>
            <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", marginBottom: "0.75rem" }}>
              <input required maxLength={80} placeholder="Role name" aria-label="Role name" value={roleForm.name} onChange={(e) => setRoleForm({ ...roleForm, name: e.target.value })} style={{ ...inp, flex: "1 1 200px" }} />
              <input maxLength={255} placeholder="What this role is for (optional)" aria-label="Role description" value={roleForm.description || ""} onChange={(e) => setRoleForm({ ...roleForm, description: e.target.value })} style={{ ...inp, flex: "2 1 300px" }} />
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: "0.35rem" }}>
              {perms.map((p) => (
                <label key={p.code} style={{ fontSize: "0.85rem", display: "flex", gap: 6, alignItems: "flex-start" }}>
                  <input type="checkbox" checked={roleForm.permissions.includes(p.code)} disabled={p.code === "dashboard.view"} onChange={() => togglePerm(p.code)} />
                  {p.label}
                </label>
              ))}
            </div>
            <div style={{ marginTop: "0.75rem" }}>
              <button type="submit" disabled={busy} style={btn}>{roleForm.id ? "Save role" : "Create role"}</button>
              <button type="button" onClick={() => setRoleForm(null)} style={{ ...btn, background: "#fff", color: "#374151", border: "1px solid #d1d5db", marginLeft: 8 }}>Cancel</button>
            </div>
          </form>
        )}

        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <tbody>
            {roles.map((r) => (
              <tr key={r.id}>
                <td style={{ ...cell, width: "28%" }}><div style={{ fontWeight: 600 }}>{r.name}</div><div style={{ color: "#6b7280", fontSize: "0.78rem" }}>{r.builtIn ? "Built-in" : "Your role"}</div></td>
                <td style={{ ...cell, color: "#4b5563", fontSize: "0.8rem" }}>{perms.filter((p) => (r.permissions || []).includes(p.code)).map((p) => p.label).join(" · ")}</td>
                <td style={{ ...cell, textAlign: "right", whiteSpace: "nowrap" }}>
                  {!r.builtIn && <>
                    <button type="button" style={linkBtn("#2563eb")} onClick={() => setRoleForm({ id: r.id, name: r.name, description: r.description, permissions: [...(r.permissions || [])] })}>Edit</button>
                    <button type="button" style={linkBtn("#dc2626")} onClick={() => deleteRole(r)}>Delete</button>
                  </>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default SellerTeam;
