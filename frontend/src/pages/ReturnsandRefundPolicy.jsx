import React, { useState, useMemo } from "react";
import { Helmet } from "react-helmet-async";
import { Package, CreditCard, Clock, Truck, Search, HelpCircle, ArrowRight, ExternalLink, ShieldCheck, FileText, ChevronDown, Mail } from "lucide-react";
import StaticLayout from "../components/StaticLayout";

const green = "#16a34a";
const dark = "#0f172a";
const muted = "#475569";
const border = "1px solid #e2e8f0";

const sidebarLinks = [
  { id: "policy", label: "Returns Policy", icon: Package },
  { id: "refund-process", label: "Refund Process", icon: CreditCard },
  { id: "faqs", label: "FAQs", icon: HelpCircle },
  { id: "shipping", label: "Shipping & Charges", icon: Truck },
];

const timelineSteps = [
  { title: "Request", desc: "Submit return via account or email", icon: FileText },
  { title: "Approval", desc: "CauveryStore reviews & approves", icon: ShieldCheck },
  { title: "Razorpay Processing", desc: "Refund initiated via Razorpay", icon: CreditCard },
  { title: "Bank Credit", desc: "5-7 business days to your account", icon: Clock },
];

const returnWindowCards = [
  { label: "Fashion & Apparel", value: "7-15 days", color: "#2563eb" },
  { label: "Electronics", value: "7 days", color: "#7c3aed" },
  { label: "Home Essentials", value: "10 days", color: "#d97706" },
  { label: "Groceries & Perishables", value: "Non-returnable*", color: "#dc2626" },
];

const refundQuestions = [
  { question: "How do I request a refund?", answer: "Log in to your account > Orders > Request Return, or email support@cauverystore.in. Once approved, we initiate the refund through Razorpay.", tag: "refund" },
  { question: "How long does it take to receive my refund?", answer: "Normal refunds take 5-7 business days. Instant refunds may be credited within minutes if supported by your bank. Payments older than 6 months are not eligible.", tag: "timeline" },
  { question: "Where will my refund be credited?", answer: "Always credited back to the original payment method (card, UPI, wallet, netbanking). Razorpay does not allow alternate accounts.", tag: "refund" },
  { question: "Can I cancel a refund once started?", answer: "No. Once initiated, a refund cannot be canceled or reversed.", tag: "refund" },
  { question: "Are transaction fees refunded?", answer: "No. Transaction fees and GST are non-refundable. Only the product amount is refunded.", tag: "refund" },
  { question: "How do I track my refund?", answer: "You will receive a Refund ID and bank reference number. Track using Razorpay's Refund Tracker with your Payment ID, Refund ID, or Order ID.", tag: "timeline" },
  { question: "What if my refund is delayed?", answer: "If it hasn't arrived after 10 business days, contact support@cauverystore.in with your Refund ID. Razorpay's Money Back Promise may apply.", tag: "timeline" },
];

const returnQuestions = [
  { question: "What is the return window for different products?", answer: "Fashion & Apparel: 7-15 days. Electronics: 7 days. Home Essentials: 10 days. Groceries and perishables are non-returnable unless damaged.", tag: "returns" },
  { question: "Which items are non-returnable?", answer: "Innerwear, swimwear, perishables, clearance items, and customized products are non-returnable.", tag: "returns" },
  { question: "In what condition should I return the product?", answer: "Items must be unused, unwashed, in original packaging with tags. Electronics must include all accessories.", tag: "returns" },
  { question: "How do I initiate a return?", answer: "Log in > Orders > Request Return, or email support@cauverystore.in. You will receive instructions and a return label.", tag: "returns" },
  { question: "Who pays for return shipping?", answer: "Defective/damaged: Free return shipping. Change-of-mind: Customer bears shipping or Rs.50-Rs.100 deduction.", tag: "shipping" },
  { question: "How long does return processing take?", answer: "Inspected within 48 hours of receipt. Refunds via Razorpay take 5-10 business days.", tag: "timeline" },
  { question: "Can I exchange instead of refund?", answer: "Yes. Request an exchange for eligible items or opt for store credit.", tag: "returns" },
  { question: "What if my return is rejected?", answer: "If conditions are not met (used, damaged, missing tags), it is sent back at your cost.", tag: "returns" },
];

const allQuestions = [...refundQuestions, ...returnQuestions];

const AccordionGroup = ({ items, openIndex, onToggle }) => (
  <div className="static-accordion" style={{ marginTop: 0 }}>
    {items.map((item, idx) => (
      <div className="static-accordion-item" key={idx}>
        <button className="static-accordion-trigger" onClick={() => onToggle(idx)}>
          <span>{item.question}</span>
          <ChevronDown size={16} className={`static-accordion-arrow${openIndex === idx ? " open" : ""}`} style={{ color: green }} />
        </button>
        <div className={`static-accordion-body${openIndex === idx ? " open" : ""}`}>
          <p>{item.answer}</p>
        </div>
      </div>
    ))}
  </div>
);

const ReturnsandRefundPolicy = () => {
  const [activeSection, setActiveSection] = useState("policy");
  const [faq1Open, setFaq1Open] = useState(null);
  const [faq2Open, setFaq2Open] = useState(null);
  const [searchText, setSearchText] = useState("");

  const filtered = useMemo(() => {
    if (!searchText.trim()) return allQuestions;
    const lower = searchText.toLowerCase();
    return allQuestions.filter((f) => f.question.toLowerCase().includes(lower));
  }, [searchText]);

  return (
    <div className="static-page" style={{ paddingBottom: "80px" }}>
      <div className="static-hero" style={{ borderRadius: 0, minHeight: "auto", padding: "2.5rem 1.5rem" }}>
        <div className="static-hero-content">
          <h1>Returns & Refunds</h1>
          <p>
            Refunds are processed via <strong style={{ color: "#4ade80" }}>Razorpay</strong> and always credited back
            to your original payment method. Returns are hassle-free — eligible items can be returned within the specified window.
          </p>
          <div className="static-hero-actions">
            <a href="/orders" className="static-btn static-btn-primary">
              <ArrowRight size={16} /> Request Return
            </a>
            <a href="https://razorpay.com/refund-tracker/" target="_blank" rel="noopener noreferrer" className="static-btn static-btn-secondary">
              <ExternalLink size={16} /> Track Refund
            </a>
          </div>
        </div>
      </div>

      <Helmet>
        <title>Returns & Refunds | Cauvery Store</title>
        <meta name="description" content="Learn about Cauvery Store's return policy, refund process via Razorpay, and browse our FAQ section for quick answers." />
      </Helmet>

      <div className="static-content" style={{ maxWidth: "1100px" }}>
        <div className="rrp-layout" style={{ display: "flex", gap: "32px", alignItems: "flex-start" }}>
          <nav className="rrp-nav" style={{ width: "240px", flexShrink: 0, position: "sticky", top: "88px", alignSelf: "flex-start" }}>
            <div style={{ background: "#fff", borderRadius: "12px", border, padding: "8px", marginBottom: "16px" }}>
              {sidebarLinks.map((link) => (
                <button key={link.id} onClick={() => setActiveSection(link.id)} style={{
                  display: "flex", alignItems: "center", gap: "10px", width: "100%", padding: "10px 12px",
                  border: "none", background: activeSection === link.id ? "#f0fdf4" : "transparent",
                  borderRadius: "8px", cursor: "pointer", fontSize: "0.85rem",
                  fontWeight: activeSection === link.id ? 600 : 400,
                  color: activeSection === link.id ? green : dark, textAlign: "left", transition: "all 0.15s",
                }}>
                  <link.icon size={18} />
                  {link.label}
                </button>
              ))}
            </div>
            <div style={{ background: "#fefce8", borderRadius: "12px", border: "1px solid #fde68a", padding: "14px", fontSize: "0.8rem", color: "#92400e", lineHeight: 1.6 }}>
              <strong style={{ display: "block", marginBottom: "4px" }}>Need Help?</strong>
              Email <strong>support@cauverystore.in</strong> and we will get back to you within 24 hours.
            </div>
          </nav>

          <main style={{ flex: 1, minWidth: 0, background: "#fff", borderRadius: "12px", border, padding: "28px" }}>
            {activeSection === "policy" && (
              <div>
                <h2 style={{ fontSize: "1.2rem", fontWeight: 700, color: dark, margin: "0 0 20px" }}>Returns Policy</h2>
                <div className="static-section" style={{ marginBottom: "1.25rem" }}>
                  <h3>1. General Terms</h3>
                  <ul>
                    <li>All refunds are processed through <strong>Razorpay</strong>, our payment gateway partner.</li>
                    <li>Refunds are always credited back to the <strong>original payment method</strong> used (card, UPI, wallet, netbanking). Alternate accounts are not allowed.</li>
                    <li>Refunds <strong>cannot be canceled or reversed</strong> once initiated.</li>
                  </ul>
                </div>
                <div className="static-section" style={{ marginBottom: "1.25rem" }}>
                  <h3>2. Return Window</h3>
                  <div className="static-card-grid" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: "12px", marginTop: "4px" }}>
                    {returnWindowCards.map((card) => (
                      <div className="static-card" key={card.label} style={{ padding: "12px" }}>
                        <p style={{ fontSize: "0.75rem", color: muted, marginBottom: "4px" }}>{card.label}</p>
                        <p style={{ fontSize: "0.95rem", fontWeight: 700, color: card.color, margin: 0 }}>{card.value}</p>
                      </div>
                    ))}
                    <p style={{ fontSize: "0.78rem", color: muted, gridColumn: "1 / -1", margin: 0 }}>* Unless damaged or expired on arrival.</p>
                  </div>
                </div>
                <div className="static-section" style={{ marginBottom: "1.25rem" }}>
                  <h3>3. Eligibility</h3>
                  <div style={{ display: "flex", gap: "16px", flexWrap: "wrap" }}>
                    <div className="static-info-box" style={{ flex: 1, minWidth: "200px" }}>
                      <strong>Accepted:</strong> Wrong item, defective/damaged product, size mismatch.
                    </div>
                    <div style={{ flex: 1, minWidth: "200px", background: "#fef2f2", border: "1px solid #fecaca", borderRadius: "8px", padding: "1rem 1.25rem", fontSize: "0.9rem", color: "#991b1b", lineHeight: 1.6 }}>
                      <strong>Not Accepted:</strong> Innerwear, swimwear, perishables, clearance items, customized products.
                    </div>
                  </div>
                </div>
                <div className="static-section" style={{ marginBottom: "1.25rem" }}>
                  <h3>4. Refund Process</h3>
                  <ul>
                    <li>Refund requests must be raised within the return window.</li>
                    <li>Once approved, refunds are initiated via Razorpay.</li>
                    <li><strong>Normal Refunds:</strong> 5-7 business days (bank dependent).</li>
                    <li><strong>Instant Refunds:</strong> Credited within minutes if supported by your bank.</li>
                    <li>Payments older than <strong>6 months</strong> are not eligible for refunds.</li>
                  </ul>
                </div>
                <div className="static-section" style={{ marginBottom: "1.25rem" }}>
                  <h3>5. Charges</h3>
                  <ul>
                    <li>Razorpay does not charge for refunds, but transaction fees and GST from the original payment are <strong>non-refundable</strong>.</li>
                    <li><strong>Defective/damaged items:</strong> CauveryStore covers return shipping.</li>
                    <li><strong>Change-of-mind returns:</strong> Customer bears shipping cost or a nominal deduction (Rs.50-Rs.100).</li>
                  </ul>
                </div>
                <div className="static-section" style={{ marginBottom: "1.25rem" }}>
                  <h3>6. Communication & Tracking</h3>
                  <ul>
                    <li>Customers receive a <strong>Refund ID</strong> and bank reference number once Razorpay processes the refund.</li>
                    <li>Refund status can be tracked using Razorpay's Refund Tracker tool with Payment ID, Refund ID, or Order ID.</li>
                  </ul>
                </div>
                <div className="static-section">
                  <h3>7. Buyer Protection</h3>
                  <div className="static-warning-box">
                    If a dispute arises and CauveryStore does not respond, customers may raise a claim under <strong>Razorpay's Money Back Promise Program</strong>. Razorpay will review eligibility and may reimburse the full transaction value if approved.
                  </div>
                </div>
              </div>
            )}

            {activeSection === "refund-process" && (
              <div>
                <h2 style={{ fontSize: "1.2rem", fontWeight: 700, color: dark, margin: "0 0 20px" }}>Refund Process</h2>
                <div className="static-timeline">
                  {timelineSteps.map((step, idx) => (
                    <div className="static-timeline-step" key={idx}>
                      <div className="static-timeline-dot" />
                      <h4>{step.title}</h4>
                      <p>{step.desc}</p>
                    </div>
                  ))}
                </div>
                <div className="static-info-box">
                  <p style={{ margin: "0 0 8px", fontWeight: 600, color: dark }}>Refund Tracking Details</p>
                  <p>After Razorpay processes your refund, you will receive:</p>
                  <ul>
                    <li><strong>Refund ID</strong> — unique refund identifier</li>
                    <li><strong>UTR/ARN/RRN</strong> — bank reference number</li>
                  </ul>
                  <p style={{ margin: 0 }}>
                    Track your refund: <a href="https://razorpay.com/refund-tracker/" target="_blank" rel="noopener noreferrer" style={{ color: green, fontWeight: 600 }}>Razorpay Refund Tracker <ExternalLink size={12} style={{ display: "inline" }} /></a>
                  </p>
                </div>
              </div>
            )}

            {activeSection === "faqs" && (
              <div>
                <h2 style={{ fontSize: "1.2rem", fontWeight: 700, color: dark, margin: "0 0 16px" }}>Frequently Asked Questions</h2>
                <div className="static-search">
                  <Search size={18} className="static-search-icon" />
                  <input type="text" placeholder="Search FAQs..." value={searchText} onChange={(e) => setSearchText(e.target.value)} />
                </div>
                {searchText ? (
                  filtered.length === 0 ? (
                    <p style={{ textAlign: "center", color: muted, padding: "2rem" }}>No FAQs match your search.</p>
                  ) : (
                    <AccordionGroup items={filtered} openIndex={faq1Open} onToggle={(index) => setFaq1Open(faq1Open === index ? null : index)} />
                  )
                ) : (
                  <>
                    <h3 style={{ fontSize: "0.95rem", fontWeight: 600, color: dark, margin: "0 0 4px", display: "flex", alignItems: "center", gap: "8px" }}>
                      <CreditCard size={18} color={green} /> Refund FAQs
                    </h3>
                    <AccordionGroup items={refundQuestions} openIndex={faq1Open} onToggle={(index) => setFaq1Open(faq1Open === index ? null : index)} />
                    <h3 style={{ fontSize: "0.95rem", fontWeight: 600, color: dark, margin: "24px 0 4px", display: "flex", alignItems: "center", gap: "8px" }}>
                      <Package size={18} color={green} /> Returns FAQs
                    </h3>
                    <AccordionGroup items={returnQuestions} openIndex={faq2Open} onToggle={(index) => setFaq2Open(faq2Open === index ? null : index)} />
                  </>
                )}
              </div>
            )}

            {activeSection === "shipping" && (
              <div>
                <h2 style={{ fontSize: "1.2rem", fontWeight: 700, color: dark, margin: "0 0 20px" }}>Shipping & Charges</h2>
                <div className="static-section" style={{ marginBottom: "1.25rem" }}>
                  <h3>Charges</h3>
                  <ul>
                    <li>Razorpay does not charge for refunds, but transaction fees and GST from the original payment are <strong>non-refundable</strong>.</li>
                    <li><strong>Defective/damaged items:</strong> CauveryStore covers return shipping.</li>
                    <li><strong>Change-of-mind returns:</strong> Customer bears shipping cost or a nominal deduction (Rs.50-Rs.100).</li>
                  </ul>
                </div>
                <div className="static-info-box">
                  <strong>Tip:</strong> Always check the product condition on delivery. For defective items, contact us within 48 hours for free return shipping.
                </div>
              </div>
            )}
          </main>
        </div>

        <div className="static-info-box" style={{ textAlign: "center", marginTop: "2rem" }}>
          <p style={{ fontSize: "1rem", fontWeight: 600, color: dark, margin: "0 0 8px" }}>We are here to help</p>
          <p style={{ margin: "0 0 12px" }}>
            Contact <strong>support@cauverystore.in</strong> if you need assistance with a return or refund.
          </p>
          <a href="https://razorpay.com/refund-tracker/" target="_blank" rel="noopener noreferrer" style={{ color: green, fontSize: "0.85rem", fontWeight: 600, textDecoration: "underline" }}>
            Razorpay Refund Tracker <ExternalLink size={12} style={{ display: "inline" }} />
          </a>
        </div>
      </div>

      <a href="mailto:support@cauverystore.in" style={{
        position: "fixed", bottom: "24px", right: "24px", zIndex: 50,
        display: "flex", alignItems: "center", gap: "8px",
        padding: "12px 20px", background: green, color: "#fff", borderRadius: "12px",
        textDecoration: "none", fontWeight: 600, fontSize: "0.88rem",
        boxShadow: "0 4px 16px rgba(22,163,74,0.35)",
      }}>
        <Mail size={20} />
        Need Help?
      </a>

      <style>{`
        @media (max-width: 768px) {
          .rrp-layout { flex-direction: column !important; }
          .rrp-nav { width: 100% !important; position: static !important; }
        }
      `}</style>
    </div>
  );
};

export default ReturnsandRefundPolicy;
