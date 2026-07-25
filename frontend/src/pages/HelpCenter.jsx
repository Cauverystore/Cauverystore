import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Search, Package, Truck, RotateCcw, User, CreditCard, Headphones, ChevronDown } from "lucide-react";
import StaticLayout from "../components/StaticLayout";
import api from "../api/axios";

const categories = [
  { id: "orders", label: "Orders", icon: Package },
  { id: "shipping", label: "Shipping & Delivery", icon: Truck },
  { id: "returns", label: "Returns & Refunds", icon: RotateCcw },
  { id: "account", label: "Account & Profile", icon: User },
  { id: "payments", label: "Payments", icon: CreditCard },
];

const fallbackFaqs = {
  orders: [
    { q: "How do I place an order?", a: "Simply browse our catalog, add items to your cart, and proceed to checkout. You can place an order as a guest or after logging in." },
    { q: "Can I cancel my order?", a: "Orders can be cancelled within 24 hours of placement, provided they have not been shipped yet. Visit your Orders page to cancel." },
    { q: "How do I track my order?", a: "You can track your order in real-time from the My Orders section in your account. We update the status at every stage." },
    { q: "What if I receive a damaged or wrong item?", a: "Please contact our support team within 48 hours of delivery with photos of the item. We will arrange a replacement or refund." },
  ],
  shipping: [
    { q: "What are the shipping charges?", a: "We offer free shipping on orders above a certain value. For smaller orders, a nominal shipping fee applies based on weight and location." },
    { q: "How long does delivery take?", a: "Standard delivery takes 3-7 business days across most Indian cities. Remote areas may take longer." },
    { q: "Do you ship internationally?", a: "Currently, we ship only within India. We plan to expand internationally soon." },
    { q: "Can I change my delivery address after ordering?", a: "You can update the address before the order is shipped. Once shipped, changes are not possible." },
  ],
  returns: [
    { q: "What is the return policy?", a: "Most items can be returned within 7-15 days of delivery. The exact window depends on the product category." },
    { q: "How do I initiate a return?", a: "Go to your Orders page, select the item, and click 'Return'. Follow the instructions to generate a return request." },
    { q: "When will I get my refund?", a: "Refunds are processed within 5-7 business days after the returned item is received and verified." },
    { q: "Are there any items that cannot be returned?", a: "Yes, perishable goods, personal care items, and software products are non-returnable unless defective." },
  ],
  account: [
    { q: "How do I create an account?", a: "Click on 'Login' and then 'Register'. Fill in your details to create your Cauvery Store account in seconds." },
    { q: "I forgot my password. What should I do?", a: "On the login page, click 'Forgot Password' and follow the instructions to reset it via email." },
    { q: "How do I update my profile?", a: "Log in and visit the Profile section. You can edit your name, email, phone number, and saved addresses." },
    { q: "Can I delete my account?", a: "Yes, please contact our support team at support@cauverystore.in to request account deletion." },
  ],
  payments: [
    { q: "What payment methods do you accept?", a: "We accept credit/debit cards, UPI (GPay, PhonePe, Paytm), net banking, and EMI on select orders." },
    { q: "Is my payment information secure?", a: "Absolutely. All payments are processed through Razorpay with bank-grade encryption. We do not store your payment details." },
    { q: "What should I do if my payment fails?", a: "First, check if the amount was deducted. If yes, it will be refunded automatically within 5-7 days. Contact support if it does not." },
    { q: "Do you offer cash on delivery?", a: "Yes, COD is available on eligible orders. A small convenience fee may apply." },
  ],
};

const HelpCenter = () => {
  const [activeTab, setActiveTab] = useState("orders");
  const [faqs, setFaqs] = useState(null);
  const [searchTerm, setSearchTerm] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get("/api/faqs").then((r) => {
      if (r.data && Array.isArray(r.data)) {
        const grouped = {};
        r.data.forEach((f) => {
          const cat = f.category || "general";
          if (!grouped[cat]) grouped[cat] = [];
          grouped[cat].push({ q: f.question, a: f.answer });
        });
        if (Object.keys(grouped).length > 0) setFaqs(grouped);
      }
    }).catch(() => {}).finally(() => setLoading(false));
  }, []);

  const data = faqs || fallbackFaqs;
  const currentFaqs = data[activeTab] || [];

  const filtered = searchTerm.trim()
    ? currentFaqs.filter(
        (item) =>
          item.q.toLowerCase().includes(searchTerm.toLowerCase()) ||
          item.a.toLowerCase().includes(searchTerm.toLowerCase())
      )
    : currentFaqs;

  const tabs = categories.map((c) => ({ id: c.id, label: c.label }));

  return (
    <StaticLayout
      hero={{
        title: "Help Center",
        subtitle: "Find answers to common questions or reach out to our support team.",
      }}
      tabs={tabs}
      activeTab={activeTab}
      onTabChange={setActiveTab}
    >
      <Helmet>
        <title>Help Center | Cauvery Store</title>
        <meta name="description" content="Get answers to frequently asked questions about orders, shipping, returns, account, and payments at Cauvery Store." />
      </Helmet>

      <div className="static-search">
        <Search size={18} className="static-search-icon" />
        <input
          type="text"
          placeholder="Search within this section..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
      </div>

      {loading ? (
        <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>Loading FAQs...</p>
      ) : filtered.length === 0 ? (
        <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>
          No FAQs found for "{searchTerm}". Try a different keyword.
        </p>
      ) : (
        <div className="static-accordion">
          {filtered.map((item, i) => (
            <AccordionItem key={i} question={item.q} answer={item.a} />
          ))}
        </div>
      )}

      <div className="static-section" style={{ textAlign: "center", marginTop: "2.5rem" }}>
        <h3 style={{ marginBottom: "0.75rem" }}>Still need help?</h3>
        <p style={{ marginBottom: "1rem" }}>Our support team is available Mon-Sat, 9 AM - 6 PM.</p>
        <a href="mailto:support@cauverystore.in" className="static-btn static-btn-outline">
          <Headphones size={16} /> Contact Support
        </a>
      </div>
    </StaticLayout>
  );
};

const AccordionItem = ({ question, answer }) => {
  const [open, setOpen] = useState(false);
  return (
    <div className="static-accordion-item">
      <button className="static-accordion-trigger" onClick={() => setOpen(!open)}>
        <span>{question}</span>
        <ChevronDown size={16} className={`static-accordion-arrow${open ? " open" : ""}`} />
      </button>
      <div className={`static-accordion-body${open ? " open" : ""}`}>
        <p>{answer}</p>
      </div>
    </div>
  );
};

export default HelpCenter;
