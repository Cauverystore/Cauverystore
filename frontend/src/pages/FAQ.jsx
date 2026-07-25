import React, { useState, useEffect } from "react";
import api from "../api/axios";

const FAQ = () => {
  const [faqs, setFaqs] = useState([]);
  const [openId, setOpenId] = useState(null);
  useEffect(() => { api.get("/api/faqs").then(r => setFaqs(r.data)).catch(() => {}); }, []);
  return (
    <div style={{ padding:"2rem", maxWidth:"800px", margin:"0 auto" }}>
      <h1 style={{ fontSize:"1.8rem", fontWeight:700, marginBottom:"0.5rem" }}>Frequently Asked Questions</h1>
      <p style={{ color:"#6b7280", marginBottom:"2rem" }}>Find answers to common questions about shopping at Cauvery Store.</p>
      {faqs.length === 0 && <p style={{ color:"#94a3b8", textAlign:"center", padding:"3rem" }}>No FAQs available at the moment.</p>}
      {faqs.map(f => (
        <div key={f.id} style={{ border:"1px solid #e5e7eb", borderRadius:8, marginBottom:"0.75rem", overflow:"hidden" }}>
          <button onClick={() => setOpenId(openId === f.id ? null : f.id)} style={{ width:"100%", padding:"1rem", background:"#f9fafb", border:"none", cursor:"pointer", display:"flex", justifyContent:"space-between", alignItems:"center", fontSize:"0.95rem", fontWeight:600, textAlign:"left", color:"#111827" }}>
            {f.question} <span style={{ transform: openId === f.id ? "rotate(180deg)" : "rotate(0)", transition:"transform 0.2s", fontSize:"0.8rem" }}>▼</span>
          </button>
          {openId === f.id && <div style={{ padding:"1rem", borderTop:"1px solid #e5e7eb", fontSize:"0.9rem", color:"#374151", lineHeight:1.6 }}>{f.answer}</div>}
        </div>
      ))}
    </div>
  );
};
export default FAQ;