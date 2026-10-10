import React, { useEffect, useRef } from "react";
import { Link, useSearchParams } from "react-router-dom";
import api from "../api/axios";
import { trackPurchase } from "../utils/analytics";

const OrderSuccess = () => {
  const [params] = useSearchParams();
  const orderId = params.get("id");
  const tracked = useRef(false);

  useEffect(() => {
    if (!orderId || tracked.current) return;
    tracked.current = true;
    api.get(`/api/orders/${orderId}`)
      .then((res) => trackPurchase(res.data || {}))
      .catch(() => trackPurchase({ id: orderId }));
  }, [orderId]);

  return (
    <div style={{ maxWidth: "600px", margin: "3rem auto", textAlign: "center", padding: "2rem" }}>
      <div style={{ fontSize: "4rem", marginBottom: "1rem" }}>&#10003;</div>
      <h1 style={{ color: "#16a34a", fontSize: "1.8rem", marginBottom: "0.5rem" }}>Thank you. Your order is placed.</h1>
      <p style={{ color: "#475569", marginBottom: "0.5rem" }}>Your order has gone to a seller in Tamil Nadu.</p>
      {orderId && <p style={{ fontWeight: 600 }}>Order number: {orderId}</p>}
      <div style={{ margin: "1.5rem auto 0", maxWidth: 420, textAlign: "left", background: "#f0fdf4", border: "1px solid #bbf7d0", borderRadius: 8, padding: "1rem 1.25rem", color: "#334155", fontSize: "0.9rem", lineHeight: 1.6 }}>
        <strong style={{ display: "block", marginBottom: "0.35rem", color: "#0f172a" }}>What happens next</strong>
        <ol style={{ margin: 0, paddingLeft: "1.1rem" }}>
          <li>The seller confirms and packs your order.</li>
          <li>It is shipped, and you can follow each step under My Orders.</li>
          <li>You can cancel free of charge any time before it ships.</li>
        </ol>
      </div>
      <div style={{ marginTop: "1.5rem", display: "flex", gap: "1rem", justifyContent: "center", flexWrap: "wrap" }}>
        <Link to={orderId ? `/orders/${orderId}` : "/orders"} style={{ padding: "0.6rem 1.5rem", background: "#16a34a", color: "#fff", borderRadius: 6, textDecoration: "none" }}>Track this order</Link>
        <Link to="/products" style={{ padding: "0.6rem 1.5rem", border: "1px solid #16a34a", color: "#16a34a", borderRadius: 6, textDecoration: "none" }}>Keep shopping</Link>
      </div>
      <p style={{ marginTop: "1.5rem", fontSize: "0.85rem", color: "#64748b" }}>
        Questions about this order? Email <a href="mailto:support@cauverystore.in" style={{ color: "#16a34a" }}>support@cauverystore.in</a> with your order number.
      </p>
    </div>
  );
};
export default OrderSuccess;
