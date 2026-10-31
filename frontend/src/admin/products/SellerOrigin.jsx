import React, { useEffect, useState } from "react";
import api from "../../api/axios";

/*
  Who sells the product, and so the state it ships from. Checkout compares this state of
  origin with the delivery state (the destination) to choose IGST or CGST+SGST, and blocks
  any product whose seller has no GSTIN. The state is read from the seller's GSTIN (its first
  two digits), so it always matches what goes on the tax invoice.
  Sellers adding their own products never see this: their products are theirs automatically.
*/
const SellerOrigin = ({ value, onChange, error }) => {
  const [sellers, setSellers] = useState(null);

  useEffect(() => {
    api.get("/api/admin/products/sellers")
      .then((r) => setSellers(Array.isArray(r.data) ? r.data : []))
      .catch(() => setSellers([]));
  }, []);

  const chosen = sellers?.find((s) => String(s.id) === String(value ?? ""));
  const label = { display: "block", fontSize: "0.8rem", fontWeight: 600, color: "#374151", marginBottom: "3px" };
  const box = { background: "#F6FBF8", border: "1px solid #CFE8D6", borderRadius: 6, padding: "8px 12px", fontSize: "0.85rem" };

  return (
    <div style={{ marginBottom: "0.75rem" }}>
      <label style={label}>Seller *</label>
      <select
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}
        style={{ width: "100%", padding: "8px 12px", border: "1px solid #CFE8D6", borderRadius: 6, fontSize: "0.9rem", background: "#fff", boxSizing: "border-box" }}
      >
        <option value="">{sellers === null ? "Loading sellers..." : "Select the seller"}</option>
        {(sellers || []).map((s) => (
          <option key={s.id} value={s.id}>
            {s.name}{s.state ? ` (${s.state})` : " (no GSTIN)"}
          </option>
        ))}
      </select>
      {error && <div style={{ color: "#dc2626", fontSize: "0.7rem", marginTop: 2 }}>{error}</div>}
      {chosen && (chosen.canBeTaxed ? (
        <div style={{ ...box, marginTop: 6, display: "grid", gridTemplateColumns: "auto 1fr", gap: "2px 12px" }}>
          <span style={{ color: "#64748B" }}>GSTIN</span><span style={{ fontFamily: "monospace" }}>{chosen.gstin}</span>
          <span style={{ color: "#64748B" }}>State of origin</span>
          <span>{chosen.state || `State code ${chosen.stateCode}`}{chosen.city ? ` — ${chosen.city}` : ""}</span>
        </div>
      ) : (
        <div style={{ ...box, marginTop: 6, background: "#fef2f2", borderColor: "#fecaca", color: "#b91c1c" }}>
          This seller has no GSTIN on record, so the state of origin is unknown and customers
          cannot order this product. Add the seller's GSTIN first, or choose another seller.
        </div>
      ))}
      <div style={{ fontSize: "0.7rem", color: "#64748B", marginTop: 2 }}>
        The state of origin comes from the seller's GSTIN. The destination is the customer's
        delivery address at checkout; the two decide IGST or CGST+SGST.
      </div>
    </div>
  );
};

export default SellerOrigin;
