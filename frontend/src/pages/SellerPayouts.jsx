import React, { useState, useEffect } from "react";
import api from "../api/axios";

const METHOD_LABELS = { BANK_TRANSFER: "Bank transfer", UPI: "UPI", OTHER: "Other" };
const rupees = (n) => "₹" + Number(n || 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const dateText = (d) => (d ? new Date(d).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "—");
const cell = { padding: "12px", textAlign: "left", verticalAlign: "top" };

const SellerPayouts = () => {
  const [payouts, setPayouts] = useState([]);
  const [totalPaid, setTotalPaid] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [payoutDelayDays, setPayoutDelayDays] = useState(null);
  const [balance, setBalance] = useState(null);
  const [statement, setStatement] = useState([]);

  // Balance and statement come from the settlement ledger. If it is not switched on yet, or this
  // team member cannot see payouts, the cards simply do not appear.
  useEffect(() => {
    api.get("/api/seller/finance/balance").then((r) => setBalance(r.data)).catch(() => {});
    api.get("/api/seller/finance/statement").then((r) => setStatement(Array.isArray(r.data?.lines) ? r.data.lines : [])).catch(() => {});
  }, []);

  // The payout delay lives on the server so this page never states a number the policy has changed.
  // Team members without product permission cannot read it, so a failure just hides the number.
  useEffect(() => {
    api.get("/api/seller/commission-preview", { params: { price: 0 } })
      .then((r) => setPayoutDelayDays(r.data?.payoutDelayDays ?? null))
      .catch(() => {});
  }, []);

  useEffect(() => {
    api.get("/api/seller/payouts")
      .then((r) => {
        setPayouts(Array.isArray(r.data?.payouts) ? r.data.payouts : []);
        setTotalPaid(Number(r.data?.totalPaid || 0));
      })
      .catch((e) => setError(e.response?.data?.error || "Your payouts could not be loaded. Please try again later."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="page-loader"><div>Loading payouts...</div></div>;

  return (
    <div style={{ padding: "2rem" }}>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 700, marginBottom: "0.5rem" }}>Payouts</h1>
      <p style={{ color: "var(--sn-text-secondary)", margin: "0 0 1.5rem", fontSize: "0.9rem" }}>
        Payments Cauvery Store has sent to your bank account. Match each one to your bank statement using the reference.
      </p>

      {error ? (
        <p style={{ color: "#dc2626" }}>{error}</p>
      ) : (
        <>
          <div style={{ display: "inline-block", background: "#fff", border: "1px solid var(--sn-border, #e5e7eb)", borderRadius: 10, padding: "1rem 1.25rem", marginBottom: "1.5rem" }}>
            <div style={{ fontSize: "0.8rem", color: "var(--sn-text-secondary)" }}>Total paid to you</div>
            <div style={{ fontSize: "1.5rem", fontWeight: 700 }}>{rupees(totalPaid)}</div>
          </div>

          {balance && balance.tracked && (
            <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: "1.5rem" }}>
              {[
                ["Ready to be paid", balance.payableNow, "Return window closed. Included in the next payout run."],
                ["Waiting for return window", balance.pending, "Paid after the return window closes."],
                ...(balance.reserveHeld > 0 ? [["Held in reserve", balance.reserveHeld, `${balance.reservePercent}% of each payout is held against returns.`]] : []),
                ...(balance.owedToUs > 0 ? [["To be recovered", balance.owedToUs, "Returns and refunds beyond your balance come off your next payout."]] : []),
              ].map(([label, value, hint]) => (
                <div key={label} style={{ background: "#fff", border: "1px solid var(--sn-border, #e5e7eb)", borderRadius: 10, padding: "1rem 1.25rem", minWidth: 200, maxWidth: 260 }}>
                  <div style={{ fontSize: "0.8rem", color: "var(--sn-text-secondary)" }}>{label}</div>
                  <div style={{ fontSize: "1.4rem", fontWeight: 700 }}>{rupees(value)}</div>
                  <div style={{ fontSize: "0.75rem", color: "var(--sn-text-secondary)", marginTop: 2 }}>{hint}</div>
                </div>
              ))}
            </div>
          )}

          <div style={{ background: "#f8fafc", border: "1px solid var(--sn-border, #e5e7eb)", borderRadius: 10, padding: "1rem 1.25rem", marginBottom: "1.5rem", fontSize: "0.85rem", color: "#374151", maxWidth: 720 }}>
            <div style={{ fontWeight: 700, marginBottom: 6 }}>How you are paid</div>
            <ul style={{ margin: 0, paddingLeft: "1.1rem", lineHeight: 1.6 }}>
              <li>
                {payoutDelayDays
                  ? `We pay ${payoutDelayDays} days after an order is delivered, once the return window has closed.`
                  : "We pay after an order is delivered and its return window has closed."}
              </li>
              <li>From each order we deduct our commission (set by category), a flat handling fee per order, and the payment gateway fees.</li>
              <li>You can see the commission and what you will receive for any product while you add it.</li>
              <li>Payments go to the bank account you gave when you registered. To change it, write to support@cauverystore.in.</li>
            </ul>
          </div>

          {payouts.length === 0 ? (
            <div style={{ textAlign: "center", padding: "3rem", color: "var(--sn-text-secondary)" }}>
              <p>No payouts yet. Payments appear here once they have been sent.</p>
            </div>
          ) : (
            <div style={{ overflowX: "auto" }}>
              <table className="sn-data-table" style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr style={{ background: "#f8f8f8" }}>
                  <th style={cell}>Date sent</th>
                  <th style={cell}>Amount</th>
                  <th style={cell}>Reference</th>
                  <th style={cell}>Method</th>
                  <th style={cell}>Sales period</th>
                  <th style={cell}>Note</th>
                </tr></thead>
                <tbody>
                  {payouts.map((p) => (
                    <tr key={p.id} style={{ borderBottom: "1px solid var(--sn-border)", color: p.voided ? "#9ca3af" : "inherit" }}>
                      <td style={cell}>{dateText(p.paidOn)}</td>
                      <td style={{ ...cell, fontWeight: 600 }}>
                        <span style={{ textDecoration: p.voided ? "line-through" : "none" }}>{rupees(p.amount)}</span>
                        {p.voided && <div style={{ fontSize: "0.75rem", fontWeight: 500 }}>Cancelled entry: {p.voidReason}</div>}
                      </td>
                      <td style={cell}>{p.reference}</td>
                      <td style={cell}>{METHOD_LABELS[p.method] || p.method}</td>
                      <td style={cell}>{p.periodFrom || p.periodTo ? `${dateText(p.periodFrom)} to ${dateText(p.periodTo)}` : "—"}</td>
                      <td style={cell}>{p.note || "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {statement.length > 0 && (
            <div style={{ marginTop: "2rem" }}>
              <h2 style={{ fontSize: "1.1rem", fontWeight: 700, marginBottom: "0.25rem" }}>Statement</h2>
              <p style={{ color: "var(--sn-text-secondary)", fontSize: "0.85rem", margin: "0 0 0.75rem" }}>Every sale, fee, tax and refund that makes up your balance. Money in is green, money out is red.</p>
              <div style={{ overflowX: "auto" }}>
                <table className="sn-data-table" style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead><tr style={{ background: "#f8f8f8" }}>
                    <th style={cell}>Order</th><th style={cell}>What</th><th style={cell}>Amount</th><th style={cell}>Payable from</th>
                  </tr></thead>
                  <tbody>
                    {statement.map((l) => (
                      <tr key={l.id} style={{ borderBottom: "1px solid var(--sn-border)" }}>
                        <td style={cell}>{l.orderId ? `#${l.orderId}` : "—"}</td>
                        <td style={cell}>{l.description}</td>
                        <td style={{ ...cell, fontWeight: 600, color: l.amount < 0 ? "#b91c1c" : "#15803d" }}>{rupees(l.amount)}</td>
                        <td style={cell}>{dateText(l.availableOn)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default SellerPayouts;
