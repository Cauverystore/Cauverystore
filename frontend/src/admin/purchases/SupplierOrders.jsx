import React, { useCallback, useEffect, useState } from "react";
import api from "../../api/axios";

/**
 * Supplier Orders: what must be bought from suppliers for customer orders. The store prepares each
 * line. A person checks it, pays on the supplier's site and records the supplier's order number.
 */
const TABS = [
  { key: "todo", label: "To order" },
  { key: "problem", label: "Needs sorting out" },
  { key: "done", label: "Done" },
];

const C = { teal: "#00788C", dark: "#003A44", line: "#C4EFE6", grey: "#64748B", red: "#b91c1c", amber: "#b45309" };
const box = { background: "#fff", border: `1px solid ${C.line}`, borderRadius: 8, padding: "1rem 1.25rem", marginBottom: "1rem" };
const input = { padding: "7px 9px", border: `1px solid ${C.line}`, borderRadius: 6, fontSize: "0.875rem", minWidth: 0 };
const btn = (primary) => ({
  padding: "7px 14px", borderRadius: 6, fontSize: "0.85rem", fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : `1px solid ${C.line}`, background: primary ? C.teal : "#fff", color: primary ? "#fff" : C.grey,
});
const money = (v) => (v === null || v === undefined ? "not known" : `₹${Number(v).toLocaleString("en-IN")}`);
const safeLink = (u) => (typeof u === "string" && u.startsWith("https://") ? u : null);

function Row({ row, onDone, onHandled }) {
  const [ref, setRef] = useState("");
  const [amount, setAmount] = useState("");
  const [copied, setCopied] = useState(false);
  const link = safeLink(row.supplierUrl);
  const total = row.expectedCost ? row.expectedCost * row.quantity : null;

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(row.shipTo || "");
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  return (
    <div style={box}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
        <div style={{ minWidth: 0, overflowWrap: "anywhere" }}>
          <div style={{ fontWeight: 700, color: C.dark }}>{row.productName}</div>
          <div style={{ fontSize: "0.8rem", color: C.grey }}>
            Order #{row.orderId} · Item code {row.sku || "not known"}{row.payment ? ` · ${row.payment}` : ""}
          </div>
        </div>
        <div style={{ fontWeight: 700, color: C.dark }}>Quantity {row.quantity}</div>
      </div>

      {row.supplierOutOfStock && (
        <div style={{ color: C.red, fontSize: "0.85rem", marginTop: 8 }}>
          The supplier showed this as out of stock at the last check. Confirm before paying.
        </div>
      )}

      <div style={{ fontSize: "0.85rem", color: C.dark, marginTop: 8 }}>
        Expected supplier cost {money(row.expectedCost)} each{total ? ` (${money(total)} total)` : ""} · Customer paid {money(row.salePrice)} each
      </div>

      {row.status === "TO_ORDER" && (
        <>
          <div style={{ marginTop: 10, fontSize: "0.8rem", fontWeight: 600, color: C.dark }}>Deliver to</div>
          <pre style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere", fontFamily: "inherit", fontSize: "0.85rem", margin: "4px 0 8px", color: C.dark }}>
            {row.shipTo || "No address found. Check the order."}
          </pre>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 10 }}>
            <button style={btn(false)} disabled={!row.shipTo} onClick={copy}>{copied ? "Copied" : "Copy address"}</button>
            {link && <a href={link} target="_blank" rel="noopener noreferrer" style={{ ...btn(false), textDecoration: "none" }}>Open on supplier site</a>}
          </div>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <input aria-label="Supplier order number" placeholder="Supplier order number" style={input} value={ref} onChange={(e) => setRef(e.target.value)} />
            <input aria-label="Amount paid" placeholder="Amount paid (₹)" inputMode="decimal" style={input} value={amount} onChange={(e) => setAmount(e.target.value)} />
            <button style={btn(true)} disabled={!ref.trim() || !amount.trim()} onClick={() => onDone(row.id, ref, amount)}>I have ordered and paid</button>
          </div>
        </>
      )}

      {row.status === "PROBLEM" && (
        <div style={{ marginTop: 10 }}>
          <div style={{ color: C.amber, fontSize: "0.85rem", marginBottom: 8 }}>
            You bought this as supplier order {row.supplierOrderRef}, then the customer's order was cancelled. Ask the supplier to cancel or arrange a return.
          </div>
          <button style={btn(false)} onClick={() => onHandled(row.id)}>Mark as sorted out</button>
        </div>
      )}

      {row.status === "ORDERED" && (
        <div style={{ color: C.teal, fontSize: "0.85rem", marginTop: 8 }}>
          Ordered: {row.supplierOrderRef} · Paid {money(row.amountPaid)}
        </div>
      )}
      {row.status === "CANCELLED" && <div style={{ color: C.grey, fontSize: "0.85rem", marginTop: 8 }}>Cancelled</div>}
    </div>
  );
}

export default function SupplierOrders() {
  const [tab, setTab] = useState("todo");
  const [rows, setRows] = useState([]);
  const [counts, setCounts] = useState({});
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const r = await api.get("/api/admin/supplier-orders", { params: { view: tab } });
      setRows(r.data.rows || []);
      setCounts(r.data.counts || {});
    } catch {
      setMsg({ ok: false, text: "Could not load supplier orders." });
    }
    setLoading(false);
  }, [tab]);

  useEffect(() => { load(); }, [load]);

  const act = async (call, text) => {
    setMsg(null);
    try {
      await call();
      setMsg({ ok: true, text });
      load();
    } catch (e) {
      setMsg({ ok: false, text: e.response?.data?.error || "That did not work. Please try again." });
    }
  };

  const onDone = (id, supplierOrderRef, amountPaid) => act(
    () => api.post(`/api/admin/supplier-orders/${id}/ordered`, { supplierOrderRef, amountPaid }),
    "Saved as ordered.",
  );
  const onHandled = (id) => act(() => api.post(`/api/admin/supplier-orders/${id}/handled`), "Marked as sorted out.");

  return (
    <div style={{ maxWidth: 900 }}>
      <h1 style={{ fontSize: "1.4rem", fontWeight: 700, color: C.dark, margin: "0 0 0.25rem" }}>Supplier Orders</h1>
      <p style={{ color: C.grey, fontSize: "0.875rem", marginTop: 0 }}>
        Items customers bought that you need to order from the supplier. No money is paid from this page.
      </p>
      {msg && <div role="status" style={{ ...box, color: msg.ok ? C.teal : C.red }}>{msg.text}</div>}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: "1rem" }}>
        {TABS.map((t) => (
          <button key={t.key} style={btn(tab === t.key)} onClick={() => setTab(t.key)}>
            {t.label}{counts[t.key] ? ` (${counts[t.key]})` : ""}
          </button>
        ))}
      </div>
      {loading && <div style={{ ...box, color: C.grey }}>Loading...</div>}
      {!loading && rows.length === 0 && <div style={{ ...box, color: C.grey }}>Nothing here right now.</div>}
      {!loading && rows.map((row) => <Row key={row.id} row={row} onDone={onDone} onHandled={onHandled} />)}
    </div>
  );
}
