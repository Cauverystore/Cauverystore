import React, { useEffect, useState } from "react";
import api from "../api/axios";

const rupees = (n) => "₹" + Number(n || 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const cell = { padding: "8px 10px", textAlign: "right", whiteSpace: "nowrap" };
const left = { ...cell, textAlign: "left" };

const lastMonth = () => {
  const d = new Date();
  d.setDate(1);
  d.setMonth(d.getMonth() - 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
};

/** A seller's settlement statement: every deduction between the sale and the payout. */
const SettlementStatement = () => {
  const [month, setMonth] = useState(lastMonth());
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!month) return;
    setLoading(true);
    setError("");
    api.get("/api/seller/settlement", { params: { month } })
      .then((r) => setData(r.data))
      .catch((e) => { setData(null); setError(e.response?.data?.error || "The statement could not be loaded."); })
      .finally(() => setLoading(false));
  }, [month]);

  const box = { background: "#fff", border: "1px solid var(--sn-border, #e5e7eb)", borderRadius: 10, padding: "0.75rem 1rem", minWidth: 150 };
  const tile = (label, value, strong) => (
    <div style={box}>
      <div style={{ fontSize: "0.75rem", color: "var(--sn-text-secondary)" }}>{label}</div>
      <div style={{ fontSize: strong ? "1.25rem" : "1.05rem", fontWeight: 700 }}>{rupees(value)}</div>
    </div>
  );

  return (
    <section style={{ marginBottom: "2rem" }}>
      <div style={{ display: "flex", alignItems: "center", gap: "1rem", flexWrap: "wrap", marginBottom: "0.75rem" }}>
        <h2 style={{ fontSize: "1.15rem", fontWeight: 700, margin: 0 }}>Settlement statement</h2>
        <label style={{ fontSize: "0.85rem" }}>
          Sales month{" "}
          <input type="month" value={month} onChange={(e) => setMonth(e.target.value)} style={{ padding: "0.25rem" }} />
        </label>
      </div>

      {error && <p style={{ color: "#dc2626" }}>{error}</p>}
      {loading && <p style={{ color: "var(--sn-text-secondary)" }}>Loading statement...</p>}

      {data && !loading && (
        <>
          <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap", marginBottom: "1rem" }}>
            {tile("Sales", data.totalOrderValue)}
            {tile("Fees and GST on fees", (data.totalCommission || 0) + (data.totalHandlingFees || 0) + (data.totalGstOnFees || 0) + (data.totalPaymentFees || 0))}
            {tile("TCS (credited to your GSTIN)", data.totalTcs)}
            {tile("Payable now", data.payableNow)}
            {tile("Paid", data.totalPaid)}
            {tile("Balance due", data.balanceDue, true)}
          </div>

          {(data.orders || []).length === 0 ? (
            <p style={{ color: "var(--sn-text-secondary)" }}>No billable orders in this month.</p>
          ) : (
            <div style={{ overflowX: "auto" }}>
              <table className="sn-data-table" style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85rem" }}>
                <thead><tr style={{ background: "#f8f8f8" }}>
                  <th style={left}>Order</th>
                  <th style={cell}>Sale</th>
                  <th style={cell}>Commission</th>
                  <th style={cell}>Handling</th>
                  <th style={cell}>GST on fees</th>
                  <th style={cell}>Payment fees</th>
                  <th style={cell}>TCS</th>
                  <th style={cell}>Payable</th>
                  <th style={left}>Payable from</th>
                </tr></thead>
                <tbody>
                  {data.orders.map((o) => (
                    <tr key={o.orderId} style={{ borderBottom: "1px solid var(--sn-border)" }}>
                      <td style={left}>#{o.orderId}</td>
                      <td style={cell}>{rupees(o.orderValue)}</td>
                      <td style={cell}>{rupees(o.commission)} <span style={{ color: "#6b7280" }}>({o.commissionPercent}%)</span></td>
                      <td style={cell}>{rupees(o.handlingFee)}</td>
                      <td style={cell}>{rupees(o.gstOnFees)}</td>
                      <td style={cell}>{rupees((o.gatewayFee || 0) + (o.transferFee || 0))}</td>
                      <td style={cell}>{rupees(o.tcs)}</td>
                      <td style={{ ...cell, fontWeight: 600 }}>{rupees(o.payable)}</td>
                      <td style={left}>{o.payableFrom ? `${o.payableFrom}${o.due ? "" : " (not yet)"}` : "Not delivered yet"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p style={{ fontSize: "0.78rem", color: "var(--sn-text-secondary)", marginTop: "0.5rem" }}>{data.note}</p>
        </>
      )}
    </section>
  );
};

export default SettlementStatement;
