import React, { useState } from "react";

const Pagination = ({ page, totalPages, onPage }) => {
  const [goTo, setGoTo] = useState("");
  if (totalPages <= 1) return null;
  const handleGoTo = () => {
    const p = parseInt(goTo, 10);
    if (p >= 1 && p <= totalPages) onPage(p);
    setGoTo("");
  };
  return (
    <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", justifyContent: "center", marginTop: "1.5rem" }}>
      <button disabled={page === 1} onClick={() => onPage(1)} style={btnStyle}>First</button>
      <button disabled={page === 1} onClick={() => onPage(page - 1)} style={btnStyle}>Prev</button>
      <span style={{ fontSize: "0.9rem" }}>Page {page} of {totalPages}</span>
      <input value={goTo} onChange={(e) => setGoTo(e.target.value)} onKeyDown={(e) => e.key === "Enter" && handleGoTo()} placeholder="Go" aria-label={`Go to page (1 to ${totalPages})`} style={{ width: "50px", padding: "0.3rem", fontSize: "0.85rem", border: "1px solid #ccc", borderRadius: "4px", textAlign: "center" }} />
      <button disabled={page === totalPages} onClick={() => onPage(page + 1)} style={btnStyle}>Next</button>
      <button disabled={page === totalPages} onClick={() => onPage(totalPages)} style={btnStyle}>Last</button>
    </div>
  );
};

const btnStyle = { padding: "0.35rem 0.75rem", border: "1px solid #e2e8f0", background: "#fff", borderRadius: "4px", cursor: "pointer", fontSize: "0.85rem" };
export default Pagination;
