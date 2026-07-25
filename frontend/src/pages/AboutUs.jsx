import React from "react";
import { Helmet } from "react-helmet-async";
import { Shield, Truck, IndianRupee, HeadphonesIcon, BadgeCheck, Store, HeartHandshake, Leaf, Award } from "lucide-react";
import StaticLayout from "../components/StaticLayout";

const features = [
  { icon: Store, title: "Extensive Product Range", text: "From traditional Indian wear to daily essentials, discover thousands of products curated just for you." },
  { icon: BadgeCheck, title: "Value You Can Trust", text: "Every product is verified for quality and authenticity, ensuring you get the best value for your money." },
  { icon: Shield, title: "Secure Shopping Experience", text: "Shop with confidence with bank-grade encryption, multiple payment options, and 100% buyer protection via Razorpay." },
  { icon: Truck, title: "Fast & Reliable Delivery", text: "Lightning-fast shipping across India with real-time tracking and free delivery on eligible orders." },
  { icon: HeartHandshake, title: "Easy Returns & Support", text: "Hassle-free returns within 7-15 days and a dedicated support team ready to help you around the clock." },
  { icon: IndianRupee, title: "Empowering Indian Businesses", text: "We partner with local artisans, small businesses, and trusted brands across India to bring you the best." },
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
        subtitle: "Your trusted online marketplace — built with care, driven by values, delivered with pride.",
      }}
    >
      <Helmet>
        <title>About Us | Cauvery Store</title>
        <meta name="description" content="Learn more about Cauvery Store — your trusted Indian online marketplace. Discover our mission, vision, and commitment to quality." />
      </Helmet>

      <div className="static-section">
        <h2>Welcome to Cauvery Store</h2>
        <p>
          Cauvery Store was founded with a simple vision: to make quality products accessible to every Indian home
          while empowering local businesses and artisans. Named after the vibrant Cauvery river — a lifeline of South India —
          our marketplace flows with the same energy, connecting buyers with the best sellers across the country.
        </p>
        <p>
          Whether you are looking for the latest electronics, traditional apparel, daily groceries, or unique handcrafted
          goods, Cauvery Store brings you a seamless shopping experience backed by trust, transparency, and technology.
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
