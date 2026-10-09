import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import api from "../api/axios";

const FAQ = () => {
  const [faqs, setFaqs] = useState([]);
  const [openId, setOpenId] = useState(null);
  useEffect(() => { api.get("/api/faqs").then(r => setFaqs(r.data)).catch(() => {}); }, []);

  // FAQPage schema only when there's real content to describe - Google explicitly
  // penalizes/ignores FAQPage markup that doesn't match visible on-page Q&A content,
  // so this must stay in sync with what's actually rendered below rather than being a
  // static block that could drift from the real faqs list.
  const faqJsonLd = faqs.length > 0 ? {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    "mainEntity": faqs.map((f) => ({
      "@type": "Question",
      "name": f.question,
      "acceptedAnswer": { "@type": "Answer", "text": f.answer },
    })),
  } : null;

  return (
    <div style={{ padding:"2rem", maxWidth:"800px", margin:"0 auto" }}>
      <Helmet>
        <title>FAQ | Cauvery Store - Shop Direct from Tamil Nadu Sellers</title>
        <meta name="description" content="Answers to common questions about shopping at Cauvery Store - payments, delivery, returns, and becoming a seller." />
        <link rel="canonical" href="https://cauverystore.in/faq" />
        <meta property="og:title" content="FAQ | Cauvery Store" />
        <meta property="og:description" content="Answers to common questions about shopping at Cauvery Store - payments, delivery, returns, and becoming a seller." />
        <meta property="og:type" content="website" />
        <meta property="og:url" content="https://cauverystore.in/faq" />
        <meta property="og:image" content="https://cauverystore.in/logo512.png" />
        {faqJsonLd && <script type="application/ld+json">{JSON.stringify(faqJsonLd)}</script>}
      </Helmet>
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