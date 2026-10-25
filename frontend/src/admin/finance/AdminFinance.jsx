import React, { useState, useEffect, useCallback } from "react";
import api from "../../api/axios";
import { confirmAction, promptText, showMessage } from "../../components/ConfirmDialog";

// The finance desk: what sellers are owed, payout runs with a second approver, reserves,
// adjustments, and checks of the books against the bank. Every figure comes from the ledger on
// the server; this page only displays it and sends the person's decisions back.

const rupees = (n) => "₹" + Number(n || 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const dateText = (d) => (d ? new Date(d).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "—");
const today = () => new Date().toISOString().slice(0, 10);
const errText = (e, fallback) => e.response?.data?.error || fallback;

const card = { background: "#fff", border: "1px solid #e5e7eb", borderRadius: 10, padding: "1rem 1.25rem" };
const inp = { padding: "0.45rem 0.6rem", border: "1px solid #d1d5db", borderRadius: 6, fontSize: "0.9rem", boxSizing: "border-box" };
const th = { padding: "10px 12px", textAlign: "left", fontSize: "0.8rem", color: "#6b7280", fontWeight: 600 };
const td = { padding: "10px 12px", fontSize: "0.9rem", borderTop: "1px solid #f1f5f9", verticalAlign: "top" };
const btn = (primary, danger) => ({ padding: "0.45rem 0.9rem", borderRadius: 6, fontSize: "0.85rem", fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid #d1d5db", background: danger ? "#dc2626" : primary ? "#1d4ed8" : "#fff", color: primary || danger ? "#fff" : "#111827" });
const STATUS = { DRAFT: ["Draft", "#b45309", "#fef3c7"], APPROVED: ["Approved", "#1d4ed8", "#dbeafe"], PAID: ["Paid", "#15803d", "#dcfce7"], CANCELLED: ["Cancelled", "#6b7280", "#f3f4f6"],
  PAYABLE: ["To pay", "#1d4ed8", "#dbeafe"], HELD: ["Held", "#b45309", "#fef3c7"] };
const Pill = ({ s }) => { const [t, c, bg] = STATUS[s] || [s, "#374151", "#f3f4f6"]; return <span style={{ color: c, background: bg, borderRadius: 999, padding: "2px 10px", fontSize: "0.75rem", fontWeight: 600 }}>{t}</span>; };
const Stat = ({ label, value, hint }) => (
  <div style={{ ...card, minWidth: 190 }}>
    <div style={{ fontSize: "0.8rem", color: "#6b7280" }}>{label}</div>
    <div style={{ fontSize: "1.4rem", fontWeight: 700 }}>{value}</div>
    {hint && <div style={{ fontSize: "0.75rem", color: "#6b7280", marginTop: 2 }}>{hint}</div>}
  </div>
);

const TABS = ["Overview", "Payables", "Payout runs", "Ledger", "Reconcile"];

export default function AdminFinance() {
  const [tab, setTab] = useState("Overview");
  return (
    <div style={{ padding: "2rem" }}>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: 0 }}>Finance</h1>
      <p style={{ color: "#6b7280", margin: "0.25rem 0 1rem", fontSize: "0.9rem" }}>
        What sellers are owed, who has been paid, and whether the books match the bank.
      </p>
      <div style={{ display: "flex", gap: 6, borderBottom: "1px solid #e5e7eb", marginBottom: "1.25rem", flexWrap: "wrap" }}>
        {TABS.map((t) => (
          <button key={t} onClick={() => setTab(t)} style={{ padding: "0.5rem 1rem", border: "none", background: "none", cursor: "pointer", fontWeight: 600,
            fontSize: "0.9rem", color: tab === t ? "#1d4ed8" : "#6b7280", borderBottom: tab === t ? "2px solid #1d4ed8" : "2px solid transparent" }}>{t}</button>
        ))}
      </div>
      {tab === "Overview" && <Overview />}
      {tab === "Payables" && <Payables />}
      {tab === "Payout runs" && <Runs />}
      {tab === "Ledger" && <Ledger />}
      {tab === "Reconcile" && <Reconcile />}
    </div>
  );
}

function Overview() {
  const [o, setO] = useState(null);
  const [checks, setChecks] = useState([]);
  const [start, setStart] = useState(today());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const [a, b] = await Promise.all([api.get("/api/admin/finance/overview"), api.get("/api/admin/finance/integrity")]);
      setO(a.data); setChecks(Array.isArray(b.data) ? b.data : []); setError("");
    } catch (e) { setError(errText(e, "The overview could not be loaded.")); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const saveStart = async () => {
    if (!(await confirmAction(`Start booking delivered orders from ${dateText(start)}?\n\nThis can be set only once. Orders delivered before this date stay outside the ledger, so choose the day after the last payout you made by hand.`, { confirmLabel: "Set start date" }))) return;
    try { await api.put("/api/admin/finance/settings", { ledgerStartDate: start }); showMessage("Ledger start date set"); load(); }
    catch (e) { showMessage(errText(e, "Could not set the date (a super admin is needed).")); }
  };
  const sync = async () => {
    setBusy(true);
    try {
      const r = await api.post("/api/admin/finance/sync");
      const nr = r.data.ordersWithoutRate?.length || 0;
      showMessage(`Booked ${r.data.ordersPosted} orders and ${r.data.refundsPosted} refunds.` + (nr ? ` ${nr} orders were left out because no commission rate applies to them.` : ""));
      load();
    } catch (e) { showMessage(errText(e, "Sync failed")); }
    setBusy(false);
  };

  if (error) return <p style={{ color: "#dc2626" }}>{error}</p>;
  if (!o) return <p>Loading...</p>;
  return (
    <>
      {!o.ledgerStartDate && (
        <div style={{ ...card, background: "#fffbeb", borderColor: "#fcd34d", marginBottom: "1rem", maxWidth: 720 }}>
          <div style={{ fontWeight: 700, marginBottom: 4 }}>Switch the ledger on</div>
          <p style={{ margin: "0 0 0.75rem", fontSize: "0.85rem" }}>Choose the date from which delivered orders are booked. Anything delivered before it is treated as already settled.</p>
          <input type="date" value={start} max={today()} onChange={(e) => setStart(e.target.value)} style={inp} />{" "}
          <button style={btn(true)} onClick={saveStart}>Set start date</button>
        </div>
      )}
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: "1.25rem" }}>
        <Stat label="Payable now" value={rupees(o.payableNow)} hint="Return window closed, not yet in a run" />
        <Stat label="Waiting on return window" value={rupees(o.pendingReturnWindow)} />
        <Stat label="Reserves held" value={rupees(o.reservesHeld)} hint="Held back against returns" />
        <Stat label="Paid out so far" value={rupees(o.totalPaidOut)} />
        <Stat label="Open runs" value={o.openRuns} hint={o.unreconciledPayouts + " payments not matched to the bank"} />
      </div>
      <div style={{ marginBottom: "1.25rem" }}>
        <button style={btn(true)} disabled={busy || !o.ledgerStartDate} onClick={sync}>{busy ? "Booking..." : "Book new orders and refunds now"}</button>
        <span style={{ color: "#6b7280", fontSize: "0.8rem", marginLeft: 10 }}>
          {o.ledgerStartDate ? `Ledger started ${dateText(o.ledgerStartDate)}. It also updates itself every hour.` : "Not started yet."}
        </span>
      </div>
      <h2 style={{ fontSize: "1.05rem", fontWeight: 700, margin: "0 0 0.5rem" }}>Health checks</h2>
      <div style={{ ...card, padding: 0, maxWidth: 820 }}>
        {checks.map((c) => (
          <div key={c.code} style={{ display: "flex", gap: 12, padding: "0.75rem 1rem", borderTop: "1px solid #f1f5f9" }}>
            <span style={{ fontWeight: 700, color: c.ok ? "#15803d" : "#dc2626" }}>{c.ok ? "OK" : "Check"}</span>
            <div><div style={{ fontSize: "0.9rem" }}>{c.title}{!c.ok && c.count ? ` (${c.count})` : ""}</div>
              {!c.ok && c.detail && <div style={{ fontSize: "0.8rem", color: "#6b7280" }}>{c.detail}</div>}</div>
          </div>
        ))}
      </div>
    </>
  );
}

function Payables() {
  const [rows, setRows] = useState([]);
  const [error, setError] = useState("");
  const load = useCallback(() => {
    api.get("/api/admin/finance/payables").then((r) => { setRows(Array.isArray(r.data) ? r.data : []); setError(""); })
      .catch((e) => setError(errText(e, "Could not load balances.")));
  }, []);
  useEffect(() => { load(); }, [load]);

  const setReserve = async (r) => {
    const v = await promptText(`Reserve for ${r.sellerName}: the percentage of each payout held back against returns (0 to 20).`, String(r.reservePercent), { confirmLabel: "Save" });
    if (v === null) return;
    try { await api.put(`/api/admin/finance/sellers/${r.sellerId}/reserve`, { percent: v }); load(); }
    catch (e) { showMessage(errText(e, "Could not save the reserve")); }
  };
  const release = async (r) => {
    const amount = await promptText(`Release how much of the ${rupees(r.reserveHeld)} held for ${r.sellerName}?`, String(r.reserveHeld), { confirmLabel: "Next" });
    if (amount === null) return;
    const reason = await promptText("Why is it being released?", "", { confirmLabel: "Release" });
    if (!reason) return;
    try { await api.post(`/api/admin/finance/sellers/${r.sellerId}/reserve/release`, { amount, reason }); load(); }
    catch (e) { showMessage(errText(e, "Could not release")); }
  };

  return (
    <>
      {error && <p style={{ color: "#dc2626" }}>{error}</p>}
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Seller</th><th style={th}>Payable now</th><th style={th}>Waiting</th><th style={th}>Reserve</th><th style={th}></th></tr></thead>
          <tbody>
            {rows.length === 0 && <tr><td style={td} colSpan={5}>No balances yet. Book orders on the Overview tab.</td></tr>}
            {rows.map((r) => (
              <tr key={r.sellerId}>
                <td style={td}>{r.sellerName}</td>
                <td style={{ ...td, fontWeight: 600, color: r.available < 0 ? "#dc2626" : undefined }}>{rupees(r.available)}{r.available < 0 && <div style={{ fontSize: "0.75rem", fontWeight: 400 }}>Owes us; recovered from the next payout</div>}</td>
                <td style={td}>{rupees(r.pending)}</td>
                <td style={td}>{r.reservePercent}% · {rupees(r.reserveHeld)} held</td>
                <td style={td}>
                  <button style={btn(false)} onClick={() => setReserve(r)}>Set reserve</button>{" "}
                  {r.reserveHeld > 0 && <button style={btn(false)} onClick={() => release(r)}>Release</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function Runs() {
  const [runs, setRuns] = useState([]);
  const [open, setOpen] = useState(null);
  const [cutoff, setCutoff] = useState(today());
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try { const r = await api.get("/api/admin/finance/runs"); setRuns(Array.isArray(r.data) ? r.data : []); setError(""); }
    catch (e) { setError(errText(e, "Could not load runs.")); }
  }, []);
  const loadOne = useCallback(async (id) => {
    try { const r = await api.get(`/api/admin/finance/runs/${id}`); setOpen(r.data); }
    catch (e) { showMessage(errText(e, "Could not open the run")); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const act = async (fn, okMsg) => {
    try { await fn(); if (okMsg) showMessage(okMsg); await load(); if (open) loadOne(open.id); }
    catch (e) { showMessage(errText(e, "That did not go through")); }
  };
  const prepare = async () => {
    if (!(await confirmAction(`Prepare a payout run for everything payable up to ${dateText(cutoff)}?\n\nNothing is paid yet. A second person has to approve it.`, { confirmLabel: "Prepare run" }))) return;
    try { const r = await api.post("/api/admin/finance/runs", { cutoff }); await load(); loadOne(r.data.id); }
    catch (e) { showMessage(errText(e, "Could not prepare the run")); }
  };
  const approve = async () => {
    if (!(await confirmAction(`Approve run #${open.id} for ${rupees(open.totalPayable)} to ${open.payableSellers} sellers?\n\nOnly approve after checking the lines below. The run cannot be cancelled afterwards.`, { confirmLabel: "Approve" }))) return;
    act(() => api.post(`/api/admin/finance/runs/${open.id}/approve`), "Run approved. Download the bank file and make the transfers.");
  };
  const cancel = async () => {
    if (!(await confirmAction(`Cancel draft run #${open.id}? Its lines go back to the payable pool.`, { confirmLabel: "Cancel run", danger: true }))) return;
    act(() => api.post(`/api/admin/finance/runs/${open.id}/cancel`));
  };
  const markPaid = async (i) => {
    const utr = await promptText(`Bank reference (UTR) for the ${rupees(i.payable)} sent to ${i.sellerName}:`, "", { confirmLabel: "Record payment" });
    if (!utr) return;
    act(() => api.post(`/api/admin/finance/runs/${open.id}/items/${i.id}/paid`, { utr, paidOn: today() }), "Payment recorded");
  };
  const bankFile = async () => {
    try {
      const r = await api.get(`/api/admin/finance/runs/${open.id}/bank-file`, { responseType: "blob" });
      const url = URL.createObjectURL(r.data);
      const a = document.createElement("a"); a.href = url; a.download = `payout-run-${open.id}.csv`; a.click(); URL.revokeObjectURL(url);
    } catch (e) { showMessage("The bank file could not be created."); }
  };

  return (
    <>
      {error && <p style={{ color: "#dc2626" }}>{error}</p>}
      <div style={{ marginBottom: "1rem", display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <label style={{ fontSize: "0.85rem" }}>Pay everything payable up to</label>
        <input type="date" value={cutoff} max={today()} onChange={(e) => setCutoff(e.target.value)} style={inp} />
        <button style={btn(true)} onClick={prepare}>Prepare payout run</button>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: open ? "minmax(280px, 1fr) 2fr" : "1fr", gap: 16, alignItems: "start" }}>
        <div style={{ ...card, padding: 0, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Run</th><th style={th}>Up to</th><th style={th}>Amount</th><th style={th}>Status</th></tr></thead>
            <tbody>
              {runs.length === 0 && <tr><td style={td} colSpan={4}>No runs yet.</td></tr>}
              {runs.map((r) => (
                <tr key={r.id} onClick={() => loadOne(r.id)} style={{ cursor: "pointer", background: open?.id === r.id ? "#eff6ff" : undefined }}>
                  <td style={td}>#{r.id}</td><td style={td}>{dateText(r.cutoffDate)}</td>
                  <td style={td}>{rupees(r.totalPayable)}<div style={{ fontSize: "0.75rem", color: "#6b7280" }}>{r.payableSellers} sellers{r.heldSellers ? `, ${r.heldSellers} held` : ""}</div></td>
                  <td style={td}><Pill s={r.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {open && (
          <div style={card}>
            <div style={{ display: "flex", justifyContent: "space-between", flexWrap: "wrap", gap: 8 }}>
              <div><div style={{ fontWeight: 700, fontSize: "1.05rem" }}>Run #{open.id} <Pill s={open.status} /></div>
                <div style={{ fontSize: "0.8rem", color: "#6b7280" }}>Prepared by {open.createdBy}{open.approvedBy ? `, approved by ${open.approvedBy} on ${dateText(open.approvedAt)}` : ""}</div></div>
              <div style={{ display: "flex", gap: 6 }}>
                {open.status === "DRAFT" && <><button style={btn(true)} onClick={approve}>Approve</button><button style={btn(false, false)} onClick={cancel}>Cancel run</button></>}
                {(open.status === "APPROVED" || open.status === "PAID") && <button style={btn(false)} onClick={bankFile}>Download bank file</button>}
              </div>
            </div>
            {open.status === "DRAFT" && <p style={{ fontSize: "0.8rem", color: "#92400e", background: "#fffbeb", padding: "0.5rem 0.75rem", borderRadius: 6 }}>A different admin from the one who prepared this run has to approve it.</p>}
            <table style={{ width: "100%", borderCollapse: "collapse", marginTop: 8 }}>
              <thead><tr><th style={th}>Seller</th><th style={th}>Balance</th><th style={th}>Reserve</th><th style={th}>To pay</th><th style={th}></th></tr></thead>
              <tbody>
                {open.items.map((i) => (
                  <tr key={i.id}>
                    <td style={td}>{i.sellerName}<div style={{ fontSize: "0.75rem", color: "#6b7280" }}>{i.lines} lines</div></td>
                    <td style={td}>{rupees(i.gross)}</td><td style={td}>{i.reserve ? rupees(i.reserve) : "—"}</td>
                    <td style={{ ...td, fontWeight: 600 }}>{i.status === "HELD" ? "—" : rupees(i.payable)}</td>
                    <td style={td}>
                      {i.status === "HELD" && <><Pill s="HELD" /><div style={{ fontSize: "0.75rem", color: "#6b7280" }}>{i.heldReason}</div></>}
                      {i.status === "PAYABLE" && open.status === "APPROVED" && <button style={btn(true)} onClick={() => markPaid(i)}>Mark paid</button>}
                      {i.status === "PAYABLE" && open.status === "DRAFT" && <Pill s="PAYABLE" />}
                      {i.status === "PAID" && <span style={{ fontSize: "0.8rem" }}>Paid {dateText(i.paidOn)}<div style={{ color: "#6b7280" }}>{i.utr}</div></span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}

function Ledger() {
  const [data, setData] = useState({ lines: [], page: 0, totalPages: 0 });
  const [sellerId, setSellerId] = useState("");
  const [type, setType] = useState("");
  const [page, setPage] = useState(0);
  const [form, setForm] = useState({ sellerId: "", type: "ADJUSTMENT", amount: "", reason: "" });
  const [error, setError] = useState("");

  const load = useCallback(() => {
    api.get("/api/admin/finance/ledger", { params: { sellerId: sellerId || undefined, type: type || undefined, page } })
      .then((r) => { setData(r.data); setError(""); }).catch((e) => setError(errText(e, "Could not load the ledger.")));
  }, [sellerId, type, page]);
  useEffect(() => { load(); }, [load]);

  const submit = async (e) => {
    e.preventDefault();
    const word = form.type === "PENALTY" ? "deduct" : Number(form.amount) < 0 ? "deduct" : "credit";
    if (!(await confirmAction(`${word === "deduct" ? "Deduct" : "Credit"} ${rupees(Math.abs(form.amount))} ${word === "deduct" ? "from" : "to"} seller #${form.sellerId}?\n\nReason shown to the seller: ${form.reason}\n\nThis cannot be edited afterwards; it can only be reversed with another entry.`, { confirmLabel: "Post entry" }))) return;
    try { await api.post("/api/admin/finance/adjustments", form); setForm({ ...form, amount: "", reason: "" }); showMessage("Entry posted"); load(); }
    catch (err) { showMessage(errText(err, "Could not post the entry (a super admin is needed).")); }
  };

  return (
    <>
      <form onSubmit={submit} style={{ ...card, marginBottom: "1rem", display: "flex", gap: 8, flexWrap: "wrap", alignItems: "flex-end" }}>
        <div><div style={{ fontSize: "0.75rem" }}>Seller id</div><input style={{ ...inp, width: 90 }} required value={form.sellerId} onChange={(e) => setForm({ ...form, sellerId: e.target.value })} /></div>
        <div><div style={{ fontSize: "0.75rem" }}>Kind</div>
          <select style={inp} value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}><option value="ADJUSTMENT">Correction (+ or −)</option><option value="PENALTY">Penalty</option></select></div>
        <div><div style={{ fontSize: "0.75rem" }}>Amount ₹</div><input style={{ ...inp, width: 110 }} required type="number" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} /></div>
        <div style={{ flex: 1, minWidth: 200 }}><div style={{ fontSize: "0.75rem" }}>Reason (the seller sees it)</div><input style={{ ...inp, width: "100%" }} required minLength={5} value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} /></div>
        <button style={btn(true)} type="submit">Post entry</button>
      </form>
      <div style={{ display: "flex", gap: 8, marginBottom: 8, flexWrap: "wrap" }}>
        <input style={{ ...inp, width: 110 }} placeholder="Seller id" value={sellerId} onChange={(e) => { setPage(0); setSellerId(e.target.value); }} />
        <select style={inp} value={type} onChange={(e) => { setPage(0); setType(e.target.value); }}>
          <option value="">All kinds</option>
          {["SALE", "COMMISSION", "HANDLING_FEE", "PAYMENT_FEE", "GST_ON_FEES", "TCS", "ADJUSTMENT", "PENALTY", "RESERVE_RELEASE"].map((t) => <option key={t} value={t}>{t.replace(/_/g, " ").toLowerCase()}</option>)}
        </select>
      </div>
      {error && <p style={{ color: "#dc2626" }}>{error}</p>}
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>#</th><th style={th}>Seller</th><th style={th}>Kind</th><th style={th}>Amount</th><th style={th}>Payable from</th><th style={th}>Note</th><th style={th}>Run</th></tr></thead>
          <tbody>
            {data.lines.length === 0 && <tr><td style={td} colSpan={7}>Nothing in the ledger yet.</td></tr>}
            {data.lines.map((l) => (
              <tr key={l.id}>
                <td style={td}>{l.id}</td><td style={td}>{l.sellerId}</td><td style={td}>{l.type.replace(/_/g, " ").toLowerCase()}</td>
                <td style={{ ...td, fontWeight: 600, color: l.amount < 0 ? "#b91c1c" : "#15803d" }}>{rupees(l.amount)}</td>
                <td style={td}>{dateText(l.availableOn)}</td><td style={td}>{l.description}</td><td style={td}>{l.runId ? `#${l.runId}` : "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div style={{ marginTop: 8, display: "flex", gap: 8, alignItems: "center" }}>
        <button style={btn(false)} disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
        <span style={{ fontSize: "0.85rem" }}>Page {data.page + 1} of {Math.max(data.totalPages, 1)}</span>
        <button style={btn(false)} disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
      </div>
    </>
  );
}

function Reconcile() {
  const [text, setText] = useState("");
  const [rows, setRows] = useState(null);
  const run = async () => {
    try { const r = await api.post("/api/admin/finance/reconcile", { statement: text }); setRows(r.data); }
    catch (e) { showMessage(errText(e, "Could not check the statement")); }
  };
  const colour = { MATCHED: "#15803d", NOT_RECORDED: "#dc2626", AMOUNT_DIFFERS: "#dc2626", DUPLICATE_REFERENCE: "#b45309" };
  return (
    <div style={{ maxWidth: 780 }}>
      <p style={{ fontSize: "0.9rem", color: "#374151" }}>Paste the payout lines from your bank statement, one per line: the reference (UTR), a comma, then the amount. Payments that match are marked reconciled; anything else is shown for a person to look at.</p>
      <textarea value={text} onChange={(e) => setText(e.target.value)} rows={8} placeholder={"UTR123456789,4520.50\nUTR987654321,1200.00"} style={{ ...inp, width: "100%", fontFamily: "monospace" }} />
      <div style={{ margin: "8px 0" }}><button style={btn(true)} onClick={run} disabled={!text.trim()}>Check against our records</button></div>
      {rows && (
        <div style={{ ...card, padding: 0 }}>
          {rows.map((r, i) => (
            <div key={i} style={{ padding: "0.6rem 1rem", borderTop: i ? "1px solid #f1f5f9" : "none", display: "flex", gap: 12 }}>
              <span style={{ fontWeight: 700, color: colour[r.result], minWidth: 150 }}>{r.result.replace(/_/g, " ").toLowerCase()}</span>
              <span>{r.reference} · {rupees(r.amount)}<div style={{ fontSize: "0.8rem", color: "#6b7280" }}>{r.detail}</div></span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
