import React, { useState, useEffect, useCallback } from "react";
import api from "../../api/axios";
import { confirmAction, promptText, showMessage } from "../../components/ConfirmDialog";

// Records money already sent to a seller at the bank. It does not work out what is owed.
const today = () => new Date().toISOString().slice(0, 10);
const emptyForm = () => ({ sellerId: "", amount: "", paidOn: today(), reference: "", method: "BANK_TRANSFER", periodFrom: "", periodTo: "", note: "" });
const METHOD_LABELS = { BANK_TRANSFER: "Bank transfer", UPI: "UPI", OTHER: "Other" };
const rupees = (n) => "₹" + Number(n || 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const dateText = (d) => (d ? new Date(d).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "—");

const inp = { width: "100%", padding: "0.5rem 0.65rem", border: "1px solid #d1d5db", borderRadius: 6, fontSize: "0.9rem", boxSizing: "border-box" };
const lbl = { fontSize: "0.85rem", fontWeight: 500, display: "block", marginBottom: 4 };
const th = { padding: "10px 12px", textAlign: "left", fontSize: "0.8rem", color: "#6b7280", fontWeight: 600 };
const td = { padding: "10px 12px", fontSize: "0.9rem", borderTop: "1px solid #f1f5f9", verticalAlign: "top" };

const AdminSellerPayouts = () => {
  const [sellers, setSellers] = useState([]);
  const [payouts, setPayouts] = useState([]);
  const [filter, setFilter] = useState("");
  const [form, setForm] = useState(emptyForm);
  const [showForm, setShowForm] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const r = await api.get("/api/admin/seller-payouts", { params: filter ? { sellerId: filter } : {} });
      setPayouts(Array.isArray(r.data) ? r.data : []);
      setError("");
    } catch (e) {
      setError(e.response?.data?.error || "The payouts could not be loaded.");
    }
  }, [filter]);

  useEffect(() => {
    api.get("/api/admin/seller-payouts/sellers").then((r) => setSellers(Array.isArray(r.data) ? r.data : [])).catch(() => {});
  }, []);
  useEffect(() => { load(); }, [load]);

  const change = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    const seller = sellers.find((s) => String(s.id) === String(form.sellerId));
    if (!(await confirmAction(`Record ${rupees(form.amount)} paid to ${seller ? seller.name : "this seller"} on ${dateText(form.paidOn)}, reference ${form.reference}?\n\nThe seller will see this on their Payouts page.`, { confirmLabel: "Record payment" }))) return;
    setSaving(true);
    try {
      await api.post("/api/admin/seller-payouts", form);
      setForm(emptyForm());
      setShowForm(false);
      showMessage("Payment recorded successfully");
      load();
    } catch (err) {
      showMessage(err.response?.data?.error || "Could not record the payment");
    }
    setSaving(false);
  };

  const voidPayout = async (p) => {
    const reason = await promptText(`Void the ${rupees(p.amount)} payment to ${p.sellerName} (reference ${p.reference})?\n\nThe entry stays on record, marked void, and stops counting in the seller's total. Reason:`, "", { confirmLabel: "Void entry", danger: true });
    if (reason === null) return;
    if (!reason.trim()) { showMessage("A reason is needed to void an entry."); return; }
    try {
      await api.post(`/api/admin/seller-payouts/${p.id}/void`, { reason });
      load();
    } catch (err) {
      showMessage(err.response?.data?.error || "Could not void the entry");
    }
  };

  const total = payouts.filter((p) => !p.voided).reduce((sum, p) => sum + Number(p.amount || 0), 0);

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.5rem", gap: "1rem", flexWrap: "wrap" }}>
        <h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: 0 }}>Seller Payouts</h1>
        <button onClick={() => setShowForm(!showForm)} style={{ padding: "0.5rem 1rem", background: "#0E5C5C", color: "#fff", border: "none", borderRadius: 6, cursor: "pointer", fontWeight: 600 }}>
          {showForm ? "Close" : "+ Record a payment"}
        </button>
      </div>
      <p style={{ color: "#6b7280", fontSize: "0.9rem", margin: "0 0 1.5rem" }}>
        Record each payment after it has been sent from the bank. Sellers see these on their Payouts page.
      </p>

      {showForm && (
        <form onSubmit={submit} style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: 10, padding: "1.5rem", marginBottom: "1.5rem" }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill,minmax(200px,1fr))", gap: "1rem", marginBottom: "1rem" }}>
            <div>
              <label style={lbl} htmlFor="po-seller">Seller</label>
              <select id="po-seller" name="sellerId" value={form.sellerId} onChange={change} style={inp} required>
                <option value="">Choose a seller</option>
                {sellers.map((s) => <option key={s.id} value={s.id}>{s.name} ({s.email})</option>)}
              </select>
            </div>
            <div><label style={lbl} htmlFor="po-amount">Amount paid (₹)</label><input id="po-amount" name="amount" type="number" min="0.01" step="0.01" value={form.amount} onChange={change} style={inp} required /></div>
            <div><label style={lbl} htmlFor="po-date">Date sent</label><input id="po-date" name="paidOn" type="date" max={today()} value={form.paidOn} onChange={change} style={inp} required /></div>
            <div><label style={lbl} htmlFor="po-ref">Bank / UPI reference (UTR)</label><input id="po-ref" name="reference" maxLength={64} value={form.reference} onChange={change} style={inp} required /></div>
            <div>
              <label style={lbl} htmlFor="po-method">Method</label>
              <select id="po-method" name="method" value={form.method} onChange={change} style={inp}>
                {Object.entries(METHOD_LABELS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
              </select>
            </div>
            <div><label style={lbl} htmlFor="po-from">Sales from (optional)</label><input id="po-from" name="periodFrom" type="date" value={form.periodFrom} onChange={change} style={inp} /></div>
            <div><label style={lbl} htmlFor="po-to">Sales to (optional)</label><input id="po-to" name="periodTo" type="date" value={form.periodTo} onChange={change} style={inp} /></div>
          </div>
          <div style={{ marginBottom: "1rem" }}>
            <label style={lbl} htmlFor="po-note">Note for the seller (optional)</label>
            <textarea id="po-note" name="note" rows={2} value={form.note} onChange={change} style={inp} />
          </div>
          <button type="submit" disabled={saving} style={{ padding: "0.55rem 1.25rem", background: "#0E5C5C", color: "#fff", border: "none", borderRadius: 6, cursor: "pointer", fontWeight: 600 }}>
            {saving ? "Saving..." : "Record payment"}
          </button>
        </form>
      )}

      <div style={{ display: "flex", alignItems: "center", gap: "1rem", marginBottom: "1rem", flexWrap: "wrap" }}>
        <select aria-label="Show payouts for" value={filter} onChange={(e) => setFilter(e.target.value)} style={{ ...inp, width: "auto", minWidth: 220 }}>
          <option value="">All sellers</option>
          {sellers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
        <span style={{ fontSize: "0.9rem", color: "#374151" }}>Total paid: <strong>{rupees(total)}</strong></span>
      </div>

      {error && <p style={{ color: "#dc2626" }}>{error}</p>}

      <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: 10, overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr style={{ background: "#f8fafc" }}>
            <th style={th}>Date sent</th><th style={th}>Seller</th><th style={th}>Amount</th><th style={th}>Reference</th>
            <th style={th}>Method</th><th style={th}>Sales period</th><th style={th}>Recorded by</th><th style={th}></th>
          </tr></thead>
          <tbody>
            {payouts.length === 0 ? (
              <tr><td style={{ ...td, textAlign: "center", color: "#6b7280", padding: "2rem" }} colSpan={8}>No payments recorded yet.</td></tr>
            ) : payouts.map((p) => (
              <tr key={p.id} style={p.voided ? { color: "#9ca3af" } : undefined}>
                <td style={td}>{dateText(p.paidOn)}</td>
                <td style={td}>{p.sellerName}</td>
                <td style={{ ...td, fontWeight: 600, textDecoration: p.voided ? "line-through" : "none" }}>{rupees(p.amount)}</td>
                <td style={td}>{p.reference}</td>
                <td style={td}>{METHOD_LABELS[p.method] || p.method}</td>
                <td style={td}>{p.periodFrom || p.periodTo ? `${dateText(p.periodFrom)} to ${dateText(p.periodTo)}` : "—"}</td>
                <td style={td}>{p.recordedBy || "—"}</td>
                <td style={td}>
                  {p.voided
                    ? <span title={p.voidReason || ""}>Void: {p.voidReason}</span>
                    : <button onClick={() => voidPayout(p)} style={{ padding: "0.3rem 0.7rem", background: "#fff", color: "#dc2626", border: "1px solid #fecaca", borderRadius: 6, cursor: "pointer", fontSize: "0.8rem" }}>Void</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default AdminSellerPayouts;
