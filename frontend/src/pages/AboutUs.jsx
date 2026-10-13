import React from "react";
import { Link } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { Shield, Truck, IndianRupee, BadgeCheck, Store, HeartHandshake, ArrowRight } from "lucide-react";
import "../styles/aboutUs.css";

const facts = [
  { value: "Coimbatore", label: "Where we are based, in Tamil Nadu" },
  { value: "₹500", label: "Orders from this amount ship free" },
  { value: "7 days", label: "To return most items after delivery" },
  { value: "Mon–Sat", label: "Support, 9 AM to 6 PM" },
];

const features = [
  { icon: Store, title: "Approved listings", text: "Every seller is checked before they can list, and each product is approved before it appears on the store." },
  { icon: BadgeCheck, title: "Clear prices", text: "The price, GST and delivery charge are shown before you pay. Delivery is free on orders of ₹500 or more." },
  { icon: Shield, title: "Secure payments", text: "Pay by UPI, card, net banking or wallet through Razorpay. We never see or store your card details." },
  { icon: Truck, title: "Delivery times shown upfront", text: "Delivery times are shown at checkout before you pay. You can follow each order's status from your Orders page." },
  { icon: HeartHandshake, title: "7-day returns and support", text: "Return most items within 7 days of delivery, free if the item is faulty or wrong. Our support team is available Monday to Saturday, 9 AM to 6 PM." },
  { icon: IndianRupee, title: "Bulk and corporate orders", text: "Buying in quantity? Email support@cauverystore.in for a negotiated rate." },
];

const values = [
  { title: "Honesty", text: "Prices, delivery charges and return terms are shown before you pay." },
  { title: "Customers first", text: "Our support team answers Monday to Saturday, 9 AM to 6 PM." },
  { title: "Reliability", text: "Delivery estimates are realistic, so you know when to expect your order." },
  { title: "Quality", text: "Listings are reviewed before they go live, and you can return what does not match its description." },
];

const faqs = [
  { q: "Where is Cauvery Store based?", a: "We are based in Coimbatore, Tamil Nadu, and deliver across India." },
  { q: "How do you ensure product quality?", a: "Every seller is checked before they can list, and each listing is approved before shoppers see it. If something is still not right, most items can be returned within 7 days of delivery." },
  { q: "Do you offer bulk or corporate orders?", a: "Yes. Bulk and corporate orders are priced at a negotiated rate. Email support@cauverystore.in with what you need and the quantity, and we will send you a quote." },
  { q: "Can I sell on Cauvery Store?", a: "Yes. Sellers based in India can apply. Create an account, then complete seller registration from the Sell on Cauvery Store link. Your listings go live once we have approved them." },
  { q: "What payment methods do you accept?", a: "UPI, credit and debit cards, net banking and wallets, all through Razorpay. EMI is available on eligible cards. We do not offer cash on delivery at present." },
];

const AboutUs = () => {
  return (
    <div className="about-page">
      <Helmet>
        <title>About Us | Cauvery Store</title>
        <meta name="description" content="Cauvery Store is an online store based in Coimbatore, India. Learn who we are, how ordering, delivery and returns work, and how to reach us." />
        <link rel="canonical" href="https://cauverystore.in/about" />
        <meta property="og:title" content="About Cauvery Store" />
        <meta property="og:description" content="An online store based in Coimbatore, delivering across India." />
        <meta property="og:type" content="website" />
        <meta property="og:url" content="https://cauverystore.in/about" />
        <meta property="og:image" content="https://cauverystore.in/logo512.png" />
      </Helmet>

      <header className="about-hero">
        <svg className="about-river" viewBox="0 0 600 500" fill="none" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
          <path d="M620 20C470 60 520 170 380 210S250 330 120 360 20 470 -20 500" stroke="currentColor" strokeWidth="1.25" />
          <path d="M620 60C490 95 540 200 400 240S270 360 140 390 40 490 0 520" stroke="currentColor" strokeWidth="1" opacity="0.6" />
          <path d="M620 100C510 130 560 230 420 270S290 390 160 420 60 510 20 540" stroke="currentColor" strokeWidth="0.75" opacity="0.35" />
        </svg>
        <div className="about-wrap">
          <h1>About Cauvery Store</h1>
          <p className="about-display">
            An online store based in Coimbatore, delivering <em>across India</em>.
          </p>
          <dl className="about-facts">
            {facts.map((f) => (
              <div key={f.value}>
                <dt>{f.value}</dt>
                <dd>{f.label}</dd>
              </div>
            ))}
          </dl>
        </div>
      </header>

      <section className="about-section">
        <div className="about-wrap about-split">
          <div>
            <span className="about-label">Our story</span>
            <h2>Welcome to Cauvery Store</h2>
          </div>
          <div className="about-prose">
            <p className="about-lead">
              Cauvery Store is an online store based in Coimbatore. We built it to keep online shopping simple, with
              clear prices, secure payment and delivery to your door.
            </p>
            <p>
              We take our name from the Cauvery, the river that runs through South India.
            </p>
            <p>
              We are starting with t-shirts, and more categories will follow.
            </p>
          </div>
        </div>
      </section>

      <section className="about-wrap">
        <div className="about-panel">
          <span className="about-label">How it works</span>
          <h2>From order <em>to your door</em></h2>
          <div className="about-panel-cols">
            <p>
              Choose what you like and pay online through Razorpay. Orders are normally dispatched within 1 to 2
              business days, and delivery takes 2 to 7 business days for most of India. Delivery is free on orders
              of ₹500 or more.
            </p>
            <p>
              Cauvery Store is a marketplace, so products are listed by sellers we have approved, and each product
              page names its seller. If something is not right, most items can be returned within 7 days of
              delivery.
            </p>
          </div>
        </div>
      </section>

      <section className="about-section">
        <div className="about-wrap about-pair">
          <article>
            <span className="about-num">01</span>
            <h2>Our mission</h2>
            <p>
              To make online shopping straightforward for everyone in India, with prices shown upfront, secure
              payment, and delivery and returns you can rely on.
            </p>
          </article>
          <article>
            <span className="about-num">02</span>
            <h2>Our vision</h2>
            <p>
              To be the online store people come back to, because orders arrive as described and any problem is
              sorted out quickly.
            </p>
          </article>
        </div>
      </section>

      <section className="about-section" style={{ paddingTop: 0 }}>
        <div className="about-wrap">
          <div className="about-section-head">
            <div>
              <span className="about-label">What you get</span>
              <h2>Why shop with us?</h2>
            </div>
          </div>
          <ul className="about-features">
            {features.map((f) => {
              const Icon = f.icon;
              return (
                <li key={f.title}>
                  <span className="about-feature-icon"><Icon size={20} strokeWidth={1.6} aria-hidden="true" /></span>
                  <div>
                    <h3>{f.title}</h3>
                    <p>{f.text}</p>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      </section>

      <section className="about-section" style={{ paddingTop: 0 }}>
        <div className="about-wrap">
          <div className="about-section-head">
            <div>
              <span className="about-label">What we stand for</span>
              <h2>Our values</h2>
            </div>
          </div>
          <ol className="about-values">
            {values.map((v, i) => (
              <li key={v.title}>
                <span className="about-num">{String(i + 1).padStart(2, "0")}</span>
                <h3>{v.title}</h3>
                <p>{v.text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="about-section" style={{ paddingTop: 0 }}>
        <div className="about-wrap about-split">
          <div>
            <span className="about-label">Good to know</span>
            <h2>Frequently asked questions</h2>
          </div>
          <div className="about-faq">
            {faqs.map((item, i) => (
              <AccordionItem key={item.q} id={`about-faq-${i}`} question={item.q} answer={item.a} />
            ))}
          </div>
        </div>
      </section>

      <section className="about-closing">
        <div className="about-wrap">
          <p className="about-closing-line">
            Thank you for shopping with Cauvery Store.
          </p>
          <div className="about-actions">
            <Link className="about-cta" to="/products">
              Browse products <ArrowRight size={18} strokeWidth={1.8} aria-hidden="true" />
            </Link>
            <Link className="about-link" to="/seller/register">Sell on Cauvery Store</Link>
            <Link className="about-link" to="/contact">Contact us</Link>
          </div>
        </div>
      </section>
    </div>
  );
};

const AccordionItem = ({ id, question, answer }) => {
  const [open, setOpen] = React.useState(false);
  return (
    <div className={`about-faq-item${open ? " open" : ""}`}>
      <button
        className="about-faq-trigger"
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen(!open)}
      >
        <span>{question}</span>
        <span className="about-faq-mark" aria-hidden="true" />
      </button>
      <div className="about-faq-body" id={id}>
        <div>
          <p>{answer}</p>
        </div>
      </div>
    </div>
  );
};

export default AboutUs;
