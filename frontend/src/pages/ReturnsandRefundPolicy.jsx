import React, { useState } from "react";
import { Helmet } from "react-helmet-async";
import { Link } from "react-router-dom";
import { Package, CreditCard, Clock, Truck, HelpCircle, ArrowRight, ShieldCheck, ChevronDown, Mail, XCircle, AlertTriangle, Ban } from "lucide-react";
import "../styles/staticLayout.css";

const green = "#16a34a";
const dark = "#0f172a";
const muted = "#475569";
const border = "1px solid #e2e8f0";

const LAST_UPDATED = "10 October 2026";

const sidebarLinks = [
  { id: "summary", label: "At a Glance", icon: ShieldCheck },
  { id: "returns", label: "Returns", icon: Package },
  { id: "non-returnable", label: "Non-Returnable Items", icon: Ban },
  { id: "how-to-return", label: "How to Return", icon: Truck },
  { id: "damaged", label: "Damaged or Wrong Items", icon: AlertTriangle },
  { id: "cancellations", label: "Cancellations", icon: XCircle },
  { id: "refunds", label: "Refunds", icon: CreditCard },
  { id: "faqs", label: "FAQs", icon: HelpCircle },
  { id: "contact", label: "Contact Us", icon: Mail },
];

// The figures here mirror what the order system enforces (ReturnEligibilityService and
// OrderService on the backend) - change them together, or this page promises something
// the Return button will refuse.
const summaryCards = [
  { label: "Return window", value: "7 days", note: "from delivery, unless the product page says otherwise", color: "#2563eb" },
  { label: "Faulty or wrong item", value: "Free pickup", note: "the return costs you nothing", color: green },
  { label: "Refund goes to", value: "Original payment method", note: "the card, UPI or account you paid with", color: "#7c3aed" },
  { label: "Refund time", value: "5-7 business days", note: "after we approve it; often faster", color: "#d97706" },
];

const returnSteps = [
  { title: "1. Request the return", desc: "Go to My Orders, open the order and choose Return. Pick the reason and add a short note. You will see whether the order can be returned, and until what date, before you submit." },
  { title: "2. We review it", desc: "We check the request and email you the decision. If it is approved, the email tells you how the item will be sent back." },
  { title: "3. The item travels back", desc: "Pack the item securely with everything it came with. We email you again when it has been collected." },
  { title: "4. The item is checked", desc: "When the item reaches us it is checked against the conditions on this page. This usually takes a day or two, and we email you the outcome either way." },
  { title: "5. Your refund is sent", desc: "Once the item passes the check, the refund is sent to your original payment method." },
];

const faqs = [
  { question: "How many days do I have to return an item?", answer: "7 days from the date of delivery, unless the product page shows a different return period set by the seller. If an order has several items with different return periods, the shortest one applies to the whole order." },
  { question: "Which items cannot be returned?", answer: "Innerwear, swimwear, perishable goods (such as fresh, dairy, meat and frozen items), clearance items, customised or made-to-order items, and any product marked \"No Returns\" on its product page. You can still report these if they arrive damaged, defective or wrong." },
  { question: "Who pays for return shipping?", answer: "If the item is damaged, defective, wrong, not as described or has missing parts, the return is free. If you are returning it for any other reason, such as a change of mind, you pay the cost of sending it back." },
  { question: "Where will my refund be credited?", answer: "To the same payment method you used for the order: the same card, UPI ID or bank account. We cannot send a refund to a different account." },
  { question: "How long does a refund take?", answer: "Once we send it, some refunds arrive within minutes. Otherwise allow 5-7 business days for your bank to credit it. If it has not arrived after 7 business days, email support@cauverystore.in with your order number." },
  { question: "Can I cancel an order?", answer: "Yes, any time before it is shipped, from My Orders. If you have already paid, the full amount is refunded automatically. Once an order has shipped it can no longer be cancelled, but you can return it after delivery if it is eligible." },
  { question: "What happens if my return is not accepted?", answer: "We email you the reason. No refund is issued for a return that does not pass the check. If you think the decision is wrong, reply to that email or write to support@cauverystore.in and we will look at it again." },
  { question: "Can I cancel a refund once it has started?", answer: "No. Once a refund has been sent to your bank it cannot be stopped or reversed." },
];

const Section = ({ id, title, icon: Icon, children }) => (
  <section id={id} className="static-section" style={{ scrollMarginTop: "96px", marginBottom: "2rem" }}>
    <h2 style={{ fontSize: "1.2rem", fontWeight: 700, color: dark, margin: "0 0 14px", display: "flex", alignItems: "center", gap: "8px" }}>
      <Icon size={20} color={green} /> {title}
    </h2>
    {children}
  </section>
);

const ReturnsandRefundPolicy = () => {
  const [openFaq, setOpenFaq] = useState(null);

  const jumpTo = (id) => {
    const el = document.getElementById(id);
    if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  return (
    <div className="static-page" style={{ paddingBottom: "80px" }}>
      <div className="static-hero" style={{ borderRadius: 0, minHeight: "auto", padding: "2.5rem 1.5rem" }}>
        <div className="static-hero-content">
          <h1>Returns, Cancellations & Refunds</h1>
          <p>
            What you can send back, how to do it, and when you get your money back, in plain words.
          </p>
          <div className="static-hero-actions">
            <Link to="/orders" className="static-btn static-btn-primary">
              <ArrowRight size={16} /> Return or Cancel an Order
            </Link>
            <a href="mailto:support@cauverystore.in" className="static-btn static-btn-secondary">
              <Mail size={16} /> Email Support
            </a>
          </div>
        </div>
      </div>

      <Helmet>
        <title>Returns, Cancellations & Refunds Policy | Cauvery Store</title>
        <meta name="description" content="Cauvery Store's return, cancellation and refund policy: 7-day returns, free pickup for faulty or wrong items, and refunds to your original payment method." />
        <link rel="canonical" href="https://cauverystore.in/refund-policy" />
      </Helmet>

      <div className="static-content" style={{ maxWidth: "1100px" }}>
        <div className="rrp-layout" style={{ display: "flex", gap: "32px", alignItems: "flex-start" }}>
          <nav className="rrp-nav" aria-label="Policy sections" style={{ width: "240px", flexShrink: 0, position: "sticky", top: "88px", alignSelf: "flex-start" }}>
            <div className="rrp-nav-list" style={{ background: "#fff", borderRadius: "12px", border, padding: "8px", marginBottom: "16px" }}>
              {sidebarLinks.map((link) => (
                <button key={link.id} className="rrp-nav-link" onClick={() => jumpTo(link.id)} style={{
                  display: "flex", alignItems: "center", gap: "10px", width: "100%", padding: "10px 12px",
                  border: "none", background: "transparent", borderRadius: "8px", cursor: "pointer",
                  fontSize: "0.85rem", color: dark, textAlign: "left",
                }}>
                  <link.icon size={18} />
                  {link.label}
                </button>
              ))}
            </div>
            <div className="rrp-nav-help" style={{ background: "#fefce8", borderRadius: "12px", border: "1px solid #fde68a", padding: "14px", fontSize: "0.8rem", color: "#92400e", lineHeight: 1.6 }}>
              <strong style={{ display: "block", marginBottom: "4px" }}>Need Help?</strong>
              Email <strong>support@cauverystore.in</strong>. Our team is available Mon-Sat, 9 AM - 6 PM.
            </div>
          </nav>

          <main style={{ flex: 1, minWidth: 0, background: "#fff", borderRadius: "12px", border, padding: "28px" }}>
            <p style={{ fontSize: "0.8rem", color: muted, margin: "0 0 20px" }}>Last updated: {LAST_UPDATED}</p>

            <Section id="summary" title="At a Glance" icon={ShieldCheck}>
              <div className="static-card-grid" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: "12px" }}>
                {summaryCards.map((card) => (
                  <div className="static-card" key={card.label} style={{ padding: "14px" }}>
                    <p style={{ fontSize: "0.75rem", color: muted, marginBottom: "4px" }}>{card.label}</p>
                    <p style={{ fontSize: "1rem", fontWeight: 700, color: card.color, margin: "0 0 4px" }}>{card.value}</p>
                    <p style={{ fontSize: "0.78rem", color: muted, margin: 0 }}>{card.note}</p>
                  </div>
                ))}
              </div>
              <p style={{ marginTop: "14px" }}>
                Cauvery Store is a marketplace: every product is sold by a shop or trader based in Tamil Nadu.
                This policy applies to every order placed on cauverystore.in. Where a seller offers a longer
                or shorter return period for a product, it is shown on that product's page and that period applies.
              </p>
            </Section>

            <Section id="returns" title="Returns" icon={Package}>
              <h3>Return period</h3>
              <ul>
                <li>You can ask to return an item within <strong>7 days of delivery</strong>.</li>
                <li>A seller may set a different return period for a product. If so, it is shown on the product page and applies instead of the 7 days.</li>
                <li>If one order contains items with different return periods, the <strong>shortest</strong> period applies to the whole order.</li>
                <li>The return must be requested before the period ends. The exact last date is shown when you open the order and choose Return.</li>
              </ul>

              <h3>Condition of the item</h3>
              <p>To be accepted, a returned item must be:</p>
              <ul>
                <li>Unused, unwashed and undamaged (other than the fault you are reporting).</li>
                <li>In its original packaging, with all tags, labels, manuals, accessories and free gifts that came with it.</li>
                <li>The same item that was delivered to you.</li>
              </ul>

              <h3>Who pays for the return</h3>
              <div style={{ display: "flex", gap: "16px", flexWrap: "wrap" }}>
                <div className="static-info-box" style={{ flex: 1, minWidth: "220px" }}>
                  <strong>Free return:</strong> the item is damaged, defective, the wrong item, not as described, or has missing parts.
                </div>
                <div className="static-warning-box" style={{ flex: 1, minWidth: "220px" }}>
                  <strong>You pay return shipping:</strong> any other reason, such as a change of mind or ordering the wrong size.
                </div>
              </div>
            </Section>

            <Section id="non-returnable" title="Non-Returnable Items" icon={Ban}>
              <p>For hygiene and safety reasons, these cannot be returned once delivered:</p>
              <ul>
                <li>Innerwear, lingerie and swimwear.</li>
                <li>Perishable goods, such as fresh, dairy, meat and frozen items.</li>
                <li>Clearance items.</li>
                <li>Customised, personalised or made-to-order items.</li>
                <li>Any product marked <strong>"No Returns"</strong> on its product page.</li>
              </ul>
              <div className="static-info-box">
                If one of these arrives damaged, defective, expired or is not what you ordered, email{" "}
                <a href="mailto:support@cauverystore.in">support@cauverystore.in</a> with your order number and
                photos. See <strong>Damaged or Wrong Items</strong> below.
              </div>
            </Section>

            <Section id="how-to-return" title="How to Return an Item" icon={Truck}>
              <div className="static-timeline">
                {returnSteps.map((step) => (
                  <div className="static-timeline-step" key={step.title}>
                    <div className="static-timeline-dot" />
                    <h4>{step.title}</h4>
                    <p>{step.desc}</p>
                  </div>
                ))}
              </div>
              <p>
                We email you at every stage, so you always know where your return stands. You can also
                check it any time under <Link to="/orders">My Orders</Link>.
              </p>
              <h3>If a return is not accepted</h3>
              <ul>
                <li>A request may be declined if it is made after the return period, or the item is non-returnable.</li>
                <li>A returned item may fail the check if it is used, damaged, incomplete, or not the item we delivered. In that case no refund is issued.</li>
                <li>We always email you the reason. If you disagree, reply to that email and we will review the decision.</li>
              </ul>
            </Section>

            <Section id="damaged" title="Damaged, Defective or Wrong Items" icon={AlertTriangle}>
              <ul>
                <li>Please check your parcel when it arrives.</li>
                <li>If something is damaged, defective, missing or not what you ordered, raise a return from <Link to="/orders">My Orders</Link> and choose the matching reason, or email <a href="mailto:support@cauverystore.in">support@cauverystore.in</a>.</li>
                <li>Include your order number and clear photos of the item and its packaging. This helps us settle it quickly.</li>
                <li>Tell us as early as you can, and in any case within the return period.</li>
                <li>The return is free, and you receive a full refund for the item once it is confirmed.</li>
              </ul>
            </Section>

            <Section id="cancellations" title="Cancellations" icon={XCircle}>
              <h3>Cancelling your order</h3>
              <ul>
                <li>You can cancel an order from <Link to="/orders">My Orders</Link> at any time <strong>before it is shipped</strong>. There is no cancellation fee.</li>
                <li>If you had already paid, the <strong>full amount</strong> is refunded automatically to your original payment method.</li>
                <li>Once an order has shipped it cannot be cancelled. You can return it after delivery if it is eligible.</li>
              </ul>
              <h3>If we cancel your order</h3>
              <p>
                We or the seller may cancel an order because of a pricing error, the item being out of stock,
                or suspected fraud. If this happens we email you, and any amount you paid is refunded in full.
              </p>
            </Section>

            <Section id="refunds" title="Refunds" icon={CreditCard}>
              <h3>How refunds are paid</h3>
              <ul>
                <li>Refunds are processed through <strong>Razorpay</strong>, our payment partner.</li>
                <li>Refunds always go back to the <strong>original payment method</strong>: the same card, UPI ID or bank account. They cannot be sent to a different account.</li>
                <li>A refund that has been sent cannot be stopped or reversed.</li>
              </ul>
              <h3>When refunds are sent</h3>
              <ul>
                <li><strong>Cancelled orders:</strong> as soon as the order is cancelled.</li>
                <li><strong>Returned items:</strong> once the item has reached us and passed the check.</li>
              </ul>
              <h3>How long it takes to reach you</h3>
              <ul>
                <li>Some refunds are credited <strong>within minutes</strong>, where your bank supports it.</li>
                <li>Otherwise, allow <strong>5-7 business days</strong> for your bank to credit the amount.</li>
                <li>We email you when the refund is sent.</li>
              </ul>
              <div className="static-info-box">
                <strong><Clock size={14} style={{ display: "inline", verticalAlign: "-2px" }} /> Refund not arrived?</strong>{" "}
                If it has not reached you after 7 business days, email{" "}
                <a href="mailto:support@cauverystore.in">support@cauverystore.in</a> with your order number and we will trace it with the bank.
              </div>
            </Section>

            <Section id="faqs" title="Frequently Asked Questions" icon={HelpCircle}>
              <div className="static-accordion" style={{ marginTop: 0 }}>
                {faqs.map((item, idx) => (
                  <div className="static-accordion-item" key={item.question}>
                    <button className="static-accordion-trigger" aria-expanded={openFaq === idx} onClick={() => setOpenFaq(openFaq === idx ? null : idx)}>
                      <span>{item.question}</span>
                      <ChevronDown size={16} className={`static-accordion-arrow${openFaq === idx ? " open" : ""}`} style={{ color: green }} />
                    </button>
                    <div className={`static-accordion-body${openFaq === idx ? " open" : ""}`}>
                      <p>{item.answer}</p>
                    </div>
                  </div>
                ))}
              </div>
            </Section>

            <Section id="contact" title="Contact Us" icon={Mail}>
              <p>
                For any question about a return, cancellation or refund, email{" "}
                <a href="mailto:support@cauverystore.in">support@cauverystore.in</a> with your order number.
                Our support team is available Monday to Saturday, 9 AM to 6 PM.
              </p>
              <p style={{ marginBottom: 0 }}>
                This policy does not limit any rights you have under Indian consumer law. See also our{" "}
                <Link to="/terms-and-conditions">Terms &amp; Conditions</Link> and <Link to="/shipping-policy">Shipping Policy</Link>.
              </p>
            </Section>
          </main>
        </div>
      </div>

      <style>{`
        .rrp-nav-link:hover { background: #f0fdf4 !important; color: ${green} !important; }
        @media (max-width: 768px) {
          .rrp-layout { flex-direction: column !important; }
          .rrp-nav { width: 100% !important; position: static !important; }
          /* Nine stacked links would push the policy a full screen down on a phone. */
          .rrp-nav-list { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 0 !important; }
          .rrp-nav-link { width: auto !important; padding: 6px 10px !important; font-size: 0.78rem !important; background: #f1f5f9 !important; }
          .rrp-nav-link svg, .rrp-nav-help { display: none; }
        }
      `}</style>
    </div>
  );
};

export default ReturnsandRefundPolicy;
