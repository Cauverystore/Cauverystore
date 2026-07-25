import React from "react";
import { Link, useSearchParams } from "react-router-dom";

const OrderSuccess = () => {
  const [params] = useSearchParams();
  const orderId = params.get("id");
  return (
    <div style={{ maxWidth: "600px", margin: "3rem auto", textAlign: "center", padding: "2rem" }}>
      <div style={{ fontSize: "4rem", marginBottom: "1rem" }}>&#10003;</div>
      <h1 style={{ color: "#16a34a", fontSize: "1.8rem", marginBottom: "0.5rem" }}>Order Confirmed!</h1>
      <p style={{ color: "#475569", marginBottom: "0.5rem" }}>Thank you for your purchase.</p>
      {orderId && <p style={{ fontWeight: 600 }}>Order ID: {orderId}</p>}
      <div style={{ marginTop: "2rem", display: "flex", gap: "1rem", justifyContent: "center" }}>
        <Link to="/orders" style={{ padding: "0.6rem 1.5rem", background: "#16a34a", color: "#fff", borderRadius: 6, textDecoration: "none" }}>View Orders</Link>
        <Link to="/" style={{ padding: "0.6rem 1.5rem", border: "1px solid #16a34a", color: "#16a34a", borderRadius: 6, textDecoration: "none" }}>Continue Shopping</Link>
      </div>
    </div>
  );
};
export default OrderSuccess;
