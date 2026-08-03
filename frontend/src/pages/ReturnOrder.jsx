import React, { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import api from "../api/axios";

const ReturnOrder = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [reason, setReason] = useState("");
  const [details, setDetails] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!reason.trim()) return;
    setSubmitting(true);
    setError("");
    try {
      const fullReason = details.trim() ? `${reason}: ${details.trim()}` : reason;
      await api.post(`/api/orders/${id}/return`, { reason: fullReason });
      setDone(true);
    } catch (err) {
      setError(err.response?.data?.error || "Failed to submit return request. Try again.");
    }
    setSubmitting(false);
  };

  if (done) {
    return (
      <div style={{ maxWidth: 500, margin: "3rem auto", textAlign: "center", padding: "1.5rem" }}>
        <h2>Return Request Submitted</h2>
        <p>Your return request for order #{id} has been submitted. We'll notify you once it's processed.</p>
        <button onClick={() => navigate("/orders")} style={{ padding: "0.5rem 1.25rem", background: "var(--color-primary)", color: "#fff", border: "none", borderRadius: "var(--radius-sm)", cursor: "pointer", fontWeight: 600, marginTop: "1rem" }}>Back to Orders</button>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 500, margin: "3rem auto", padding: "1.5rem" }}>
      <h2>Return Order #{id}</h2>
      {error && <div style={{ marginBottom: "1rem", padding: "0.75rem", background: "#fef2f2", borderRadius: "var(--radius-sm)", color: "#dc2626" }}>{error}</div>}
      <form onSubmit={handleSubmit}>
        <label style={{ display: "block", marginBottom: "0.5rem", fontWeight: 600 }}>Reason for Return</label>
        <select value={reason} onChange={e => setReason(e.target.value)} required style={{ width: "100%", padding: "0.5rem", borderRadius: "var(--radius-sm)", border: "1px solid var(--color-border)", marginBottom: "1rem" }}>
          <option value="">Select a reason</option>
          <option value="Damaged product">Damaged product</option>
          <option value="Wrong item">Wrong item received</option>
          <option value="Size issue">Size / fit issue</option>
          <option value="Defective">Product defective</option>
          <option value="Changed mind">Changed mind</option>
          <option value="Other">Other</option>
        </select>
        <textarea value={details} onChange={e => setDetails(e.target.value)} placeholder="Describe the issue (optional)" rows={4} style={{ width: "100%", padding: "0.5rem", borderRadius: "var(--radius-sm)", border: "1px solid var(--color-border)", marginBottom: "1rem", resize: "vertical" }} />
        <button type="submit" disabled={submitting} style={{ padding: "0.6rem 1.5rem", background: "var(--color-primary)", color: "#fff", border: "none", borderRadius: "var(--radius-sm)", cursor: "pointer", fontWeight: 600, opacity: submitting ? 0.6 : 1 }}>
          {submitting ? "Submitting..." : "Submit Return Request"}
        </button>
      </form>
    </div>
  );
};

export default ReturnOrder;
