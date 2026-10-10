import React from "react";
import { Helmet } from "react-helmet-async";
import { Shield, Truck, IndianRupee, HeadphonesIcon, BadgeCheck, Store, HeartHandshake, Leaf, Award } from "lucide-react";
import StaticLayout from "../components/StaticLayout";

const features = [
  { icon: Store, title: "Real Tamil Nadu Sellers", text: "Every listing comes from a verified shop or trader based in Tamil Nadu, not an anonymous warehouse SKU." },
  { icon: BadgeCheck, title: "Clear Prices", text: "The price, GST and delivery charge are shown before you pay. Delivery is free on orders of ₹500 or more." },
  { icon: Shield, title: "Secure Payments", text: "Pay by UPI, card or net banking through Razorpay. We never see or store your card details." },
  { icon: Truck, title: "Delivery You Can Track", text: "Delivery times and coverage depend on the seller, shown clearly at checkout, with real-time tracking on every order." },
  { icon: HeartHandshake, title: "7-Day Returns & Support", text: "Return most items within 7 days of delivery, free if the item is faulty or wrong. Our support team is available Monday to Saturday, 9 AM to 6 PM." },
  { icon: IndianRupee, title: "Keeping Business Local", text: "Buying here keeps more of what you spend with Tamil Nadu's own shops and traders, instead of a national chain." },
];

const faqs = [
  { q: "Where is Cauvery Store based?", a: "We are headquartered in Coimbatore, Tamil Nadu, and serve customers across all major cities and towns in India." },
  { q: "How do you ensure product quality?", a: "Every seller and product goes through a verification process. We also have a robust buyer protection policy via Razorpay to safeguard your purchases." },
  { q: "Do you offer bulk or corporate orders?", a: "Yes, we offer special pricing and dedicated support for bulk and corporate orders. Please contact us at support@cauverystore.in for more details." },
  { q: "Can I sell on Cauvery Store?", a: "Absolutely! We welcome sellers of all sizes. Visit our Seller Dashboard section or contact us to start your journey with us." },
  { q: "What payment methods do you accept?", a: "We accept all major credit/debit cards, UPI (GPay, PhonePe, Paytm), net banking, and EMI options on select orders." },
];

const AboutUs = () => {
  return (
    <StaticLayout
      hero={{
        title: "About Cauvery Store",
        subtitle: "An online marketplace where every seller is a real shop or trader in Tamil Nadu.",
      }}
    >
      <Helmet>
        <title>About Us | Cauvery Store - Shop Direct from Tamil Nadu Sellers</title>
        <meta name="description" content="Cauvery Store connects you directly with real shops and traders across Tamil Nadu. Learn who we are and why we built a marketplace this way." />
        <link rel="canonical" href="https://cauverystore.in/about" />
        <meta property="og:title" content="About Cauvery Store" />
        <meta property="og:description" content="Cauvery Store connects you directly with real shops and traders across Tamil Nadu." />
        <meta property="og:type" content="website" />
        <meta property="og:url" content="https://cauverystore.in/about" />
        <meta property="og:image" content="https://cauverystore.in/logo512.png" />
      </Helmet>

      <div className="static-section">
        <h2>Welcome to Cauvery Store</h2>
        <p>
          Cauvery Store was founded with a simple vision: to make it easy to buy directly from the shops and traders
          of Tamil Nadu, instead of routing every purchase through an anonymous national warehouse. Named after the
          vibrant Cauvery river — a lifeline of South India — our marketplace flows with the same energy, connecting
          buyers with real local sellers they can actually see and know.
        </p>
        <p>
          Whether you are looking for electronics, fashion, home and kitchen essentials, books, or sports gear, every
          listing on Cauvery Store comes from a seller based in Tamil Nadu — not a faceless fulfillment center.
        </p>
      </div>

      <div className="static-section">
        <h2>Real Sellers, Not a Warehouse</h2>
        <p>
          Every shop on Cauvery Store is run by a real trader or small business — the kind of shop you could walk
          into in Coimbatore or any town across Tamil Nadu, now also reachable online. Each seller account goes
          through our verification process before they can list, and you can see a seller's store details before
          you buy, not just a generic "Sold by Marketplace" line.
        </p>
        <p>
          Buying this way keeps more of what you spend with local businesses, and gives you someone real to reach
          if a product needs an exchange, a question, or a warranty claim — rather than a support queue with no
          name attached.
        </p>
      </div>

      <div className="static-section">
        <h2>Our Mission</h2>
        <p>
          To democratize e-commerce in India by providing a fair, reliable, and user-friendly platform where every
          customer finds value and every seller finds opportunity. We are committed to making online shopping simple,
          secure, and accessible to all.
        </p>
      </div>

      <div className="static-section">
        <h2>Our Vision</h2>
        <p>
          To become the most trusted online marketplace in India — known not just for our products, but for our
          integrity, customer-centric approach, and unwavering commitment to quality. We envision a future where
          every Indian, regardless of location, has access to the best products at the best prices.
        </p>
      </div>

      <div className="static-section">
        <h2>Why Shop With Us?</h2>
        <div className="static-card-grid">
          {features.map((f) => {
            const Icon = f.icon;
            return (
              <div className="static-card" key={f.title}>
                <div className="static-card-icon"><Icon size={20} /></div>
                <h4>{f.title}</h4>
                <p>{f.text}</p>
              </div>
            );
          })}
        </div>
      </div>

      <div className="static-section">
        <h2>Our Values</h2>
        <div className="static-card-grid" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))" }}>
          <div className="static-card">
            <div className="static-card-icon"><Award size={20} /></div>
            <h4>Integrity</h4>
            <p>We do the right thing, even when no one is watching.</p>
          </div>
          <div className="static-card">
            <div className="static-card-icon"><HeartHandshake size={20} /></div>
            <h4>Customer First</h4>
            <p>Every decision starts with our customers' needs.</p>
          </div>
          <div className="static-card">
            <div className="static-card-icon"><Leaf size={20} /></div>
            <h4>Sustainability</h4>
            <p>We promote eco-friendly practices and responsible consumption.</p>
          </div>
          <div className="static-card">
            <div className="static-card-icon"><BadgeCheck size={20} /></div>
            <h4>Quality</h4>
            <p>We never compromise on the quality of products or service.</p>
          </div>
        </div>
      </div>

      <div className="static-section">
        <h2>Frequently Asked Questions</h2>
        <div className="static-accordion">
          {faqs.map((item, i) => (
            <AccordionItem key={i} question={item.q} answer={item.a} />
          ))}
        </div>
      </div>

      <div className="static-section" style={{ textAlign: "center", paddingTop: "1rem" }}>
        <p style={{ fontSize: "1.05rem", color: "#1e293b" }}>
          Thank you for choosing Cauvery Store. Together, we are building a better way to shop.
        </p>
      </div>
    </StaticLayout>
  );
};

const AccordionItem = ({ question, answer }) => {
  const [open, setOpen] = React.useState(false);
  return (
    <div className="static-accordion-item">
      <button className="static-accordion-trigger" onClick={() => setOpen(!open)}>
        <span>{question}</span>
        <svg className={`static-accordion-arrow${open ? " open" : ""}`} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="6 9 12 15 18 9"/></svg>
      </button>
      <div className={`static-accordion-body${open ? " open" : ""}`}>
        <p>{answer}</p>
      </div>
    </div>
  );
};

export default AboutUs;
