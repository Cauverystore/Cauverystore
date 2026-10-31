import React from "react";
import { Link, useParams } from "react-router-dom";
import ComplianceEditor from "./ComplianceEditor";

/* /admin/products/:id/compliance - one product's compliance details and approval steps. */
const ProductCompliancePage = () => {
  const { id } = useParams();
  const role = localStorage.getItem("role") || "";
  const isAdmin = role === "ADMIN" || role === "SUPER_ADMIN";
  return (
    <div style={{ maxWidth: 1100 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem", flexWrap: "wrap", gap: 8 }}>
        <h1 style={{ fontSize: "1.4rem", fontWeight: 700, margin: 0 }}>Product compliance</h1>
        <div style={{ display: "flex", gap: 12, fontSize: "0.85rem" }}>
          <Link to="/admin/product-compliance">Compliance dashboard</Link>
          <Link to="/admin/products">All products</Link>
        </div>
      </div>
      <ComplianceEditor productId={id} isAdmin={isAdmin} />
    </div>
  );
};

export default ProductCompliancePage;
