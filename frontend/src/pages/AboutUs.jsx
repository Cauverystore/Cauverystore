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
  { icon: Store, title: "Real, independent sellers", text: "Every listing comes from a verified shop or trader, not an anonymous warehouse SKU." },
  { icon: BadgeCheck, title: "Clear prices", text: "The price, GST and delivery charge are shown before you pay. Delivery is free on orders of ₹500 or more." },
  { icon: Shield, title: "Secure payments", text: "Pay by UPI, card, net banking or wallet through Razorpay. We never see or store your card details." },
  { icon: Truck, title: "Delivery times shown upfront", text: "Delivery times and coverage depend on the seller and are shown at checkout. You can follow each order's status from your Orders page." },
  { icon: HeartHandshake, title: "7-day returns and support", text: "Return most items within 7 days of delivery, free if the item is faulty or wrong. Our support team is available Monday to Saturday, 9 AM to 6 PM." },
  { icon: IndianRupee, title: "Backing small businesses", text: "Buying here keeps more of what you spend with independent shops and traders, instead of a national chain." },
];

const values = [
  { title: "Integrity", text: "We do the right thing, even when no one is watching." },
  { title: "Customer first", text: "Every decision starts with our customers' needs." },
  { title: "Sustainability", text: "We promote eco-friendly practices and responsible consumption." },
  { title: "Quality", text: "We never compromise on the quality of products or service." },
];

const faqs = [
  { q: "Where is Cauvery Store based?", a: "We are headquartered in Coimbatore, Tamil Nadu, and serve customers across all major cities and towns in India." },
  { q: "How do you ensure product quality?", a: "Every seller is checked before they can list, and each listing is approved before shoppers see it. If something is still not right, most items can be returned within 7 days of delivery." },
  { q: "Do you offer bulk or corporate orders?", a: "Yes. Bulk and corporate orders are priced at a negotiated rate. Email support@cauverystore.in with what you need and the quantity, and we will send you a quote." },
  { q: "Can I sell on Cauvery Store?", a: "Yes. Any real shop or trader in India can apply. Create an account, then complete seller registration from the Sell on Cauvery Store link. Your listings go live once we have approved them." },
  { q: "What payment methods do you accept?", a: "UPI, credit and debit cards, net banking and wallets, all through Razorpay. EMI is available on eligible cards. We do not offer cash on delivery at present." },
];

const AboutUs = () => {
  return (
    <div className="about-page">
      <Helmet>
        <title>About Us | Cauvery Store - Shop Direct from Independent Sellers</title>
        <meta name="description" content="Cauvery Store connects you directly with real, independent shops and traders. Learn who we are and why we built a marketplace this way." />
        <link rel="canonical" href="https://cauverystore.in/about" />
        <meta property="og:title" content="About Cauvery Store" />
        <meta property="og:description" content="Cauvery Store connects you directly with real, independent shops and traders." />
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
            An online marketplace where every seller is a <em>real shop or trader</em>.
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
              Cauvery Store was founded with a simple vision: to make it easy to buy directly from independent shops and
              traders, instead of routing every purchase through an anonymous national warehouse.
            </p>
            <p>
              Named after the vibrant Cauvery river — a lifeline of South India — our marketplace flows with the same
              energy, connecting buyers with real sellers they can actually see and know.
            </p>
            <p>
              We are starting with t-shirts, and more categories will follow as sellers join. Every listing on
              Cauvery Store comes from a real seller — not a faceless fulfillment center.
            </p>
          </div>
        </div>
      </section>

      <section className="about-wrap">
        <div className="about-panel">
          <span className="about-label">How it works</span>
          <h2>Real sellers, <em>not a warehouse</em></h2>
          <div className="about-panel-cols">
            <p>
              Every shop on Cauvery Store is run by a real trader or small business — the kind of shop you could walk
              into in Coimbatore or any town across India, now also reachable online. Each seller account goes
              through our verification process before they can list, and you can see a seller's store details before
              you buy, not just a generic "Sold by Marketplace" line.
            </p>
            <p>
              Buying this way keeps more of what you spend with small businesses, and gives you someone real to reach
              if a product needs an exchange, a question, or a warranty claim — rather than a support queue with no
              name attached.
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
              To democratize e-commerce in India by providing a fair, reliable, and user-friendly platform where every
              customer finds value and every seller finds opportunity. We are committed to making online shopping simple,
              secure, and accessible to all.
            </p>
          </article>
          <article>
            <span className="about-num">02</span>
            <h2>Our vision</h2>
            <p>
              To become the most trusted online marketplace in India — known not just for our products, but for our
              integrity, customer-centric approach, and unwavering commitment to quality. We envision a future where
              every Indian, regardless of location, has access to the best products at the best prices.
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
            Thank you for choosing Cauvery Store. Together, we are building a better way to shop.
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
