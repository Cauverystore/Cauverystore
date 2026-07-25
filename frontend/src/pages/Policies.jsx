import React, { useState } from "react";
import { Helmet } from "react-helmet-async";
import StaticLayout from "../components/StaticLayout";

const tabs = [
  { id: "privacy", label: "Privacy Policy" },
  { id: "terms", label: "Terms of Service" },
  { id: "shipping", label: "Shipping Policy" },
];

const policies = {
  privacy: {
    title: "Privacy Policy",
    updated: "January 2025",
    sections: [
      {
        heading: "Information We Collect",
        content: "We collect information you provide when creating an account, placing an order, or contacting support. This includes your name, email address, phone number, shipping address, and payment information. We also automatically collect certain technical information such as IP address, browser type, and device information to improve our services."
      },
      {
        heading: "How We Use Your Information",
        items: [
          "Process and fulfill your orders, including sending order confirmations and updates",
          "Provide customer support and respond to your inquiries",
          "Send personalised product recommendations and promotional offers (with your consent)",
          "Improve our website, products, and services based on usage patterns",
          "Prevent fraud and ensure the security of our platform"
        ]
      },
      {
        heading: "Information Sharing",
        content: "We do not sell your personal information. We may share your data with trusted third parties who assist us in operating our website, processing payments (Razorpay), delivering orders, and conducting business operations — all under strict confidentiality agreements."
      },
      {
        heading: "Data Security",
        content: "We implement industry-standard security measures including SSL encryption, secure servers, and regular security audits. Your payment information is processed directly by Razorpay and is never stored on our servers."
      },
      {
        heading: "Your Rights",
        items: [
          "Access, update, or delete your personal information at any time",
          "Withdraw consent for marketing communications",
          "Request a copy of the data we hold about you",
          "File a complaint with the relevant data protection authority"
        ]
      },
      {
        heading: "Cookies",
        content: "We use cookies to enhance your browsing experience, remember your preferences, and analyse site traffic. You can control cookie settings through your browser preferences."
      },
      {
        heading: "Contact Us",
        content: "For privacy-related queries, please email us at support@cauverystore.in. We will respond within 48 hours."
      }
    ]
  },
  terms: {
    title: "Terms of Service",
    updated: "January 2025",
    sections: [
      {
        heading: "Acceptance of Terms",
        content: "By accessing or using Cauvery Store, you agree to be bound by these Terms of Service. If you do not agree with any part of the terms, you may not use our services."
      },
      {
        heading: "Eligibility",
        content: "You must be at least 18 years of age to use our services. By using Cauvery Store, you represent that you meet this requirement. If you are under 18, you may use the platform only under the supervision of a parent or guardian."
      },
      {
        heading: "Account Registration",
        items: [
          "You are responsible for maintaining the confidentiality of your account credentials",
          "You must provide accurate, current, and complete information during registration",
          "You are responsible for all activities that occur under your account",
          "Notify us immediately of any unauthorised use of your account"
        ]
      },
      {
        heading: "Orders and Payments",
        items: [
          "All orders are subject to availability and acceptance",
          "Prices are listed in Indian Rupees (INR) and include applicable taxes unless stated otherwise",
          "We reserve the right to cancel any order due to pricing errors, stock unavailability, or suspected fraud",
          "Payment must be completed before order processing begins"
        ]
      },
      {
        heading: "Returns and Refunds",
        content: "Our return and refund policy is outlined separately on our Returns & Refunds page. Please refer to it for detailed information about eligibility, timelines, and processes."
      },
      {
        heading: "Intellectual Property",
        content: "All content on Cauvery Store — including text, graphics, logos, images, and software — is the property of Cauvery Store or its licensors and is protected by Indian copyright and intellectual property laws."
      },
      {
        heading: "Limitation of Liability",
        content: "Cauvery Store shall not be liable for any indirect, incidental, special, or consequential damages arising from your use of the platform. Our total liability is limited to the amount paid by you for the product or service in question."
      },
      {
        heading: "Governing Law",
        content: "These terms are governed by the laws of India. Any disputes arising from these terms shall be subject to the exclusive jurisdiction of the courts in Coimbatore, Tamil Nadu."
      }
    ]
  },
  shipping: {
    title: "Shipping Policy",
    updated: "January 2025",
    sections: [
      {
        heading: "Shipping Coverage",
        content: "We currently ship to all pin codes across India. We do not offer international shipping at this time."
      },
      {
        heading: "Delivery Timeframes",
        content: "Estimated delivery times vary by location:",
        items: [
          "Metro Cities: 2-4 business days",
          "Urban Areas: 3-6 business days",
          "Rural & Remote Areas: 5-10 business days",
          "North-East India: 7-12 business days"
        ]
      },
      {
        heading: "Shipping Charges",
        items: [
          "Free shipping on orders above Rs. 499",
          "A flat Rs. 40 shipping fee applies to orders below Rs. 499",
          "Heavy or oversized items may have additional shipping charges, which will be clearly displayed at checkout"
        ]
      },
      {
        heading: "Order Processing",
        content: "Orders are processed within 24 hours of placement (excluding Sundays and public holidays). Orders placed after 2 PM IST may be processed the next business day."
      },
      {
        heading: "Order Tracking",
        content: "Once your order is shipped, you will receive a tracking number via email and SMS. You can track your order in real-time through the My Orders section of your account."
      },
      {
        heading: "Delivery Issues",
        content: "If you do not receive your order within the estimated timeframe, please contact our support team. We will investigate and resolve the issue promptly."
      },
      {
        heading: "Failed Delivery",
        content: "If delivery fails due to an incorrect address or repeated unavailability, the package will be returned to us. You will be charged a return shipping fee, and a refund (minus shipping charges) will be processed."
      }
    ]
  }
};

const Policies = () => {
  const [activeTab, setActiveTab] = useState("privacy");
  const policy = policies[activeTab];

  const tabsConfig = tabs.map((t) => ({ id: t.id, label: t.label }));

  return (
    <StaticLayout
      hero={{
        title: "Policies",
        subtitle: "Understand how we handle your data, your rights, and our commitments to you.",
      }}
      tabs={tabsConfig}
      activeTab={activeTab}
      onTabChange={setActiveTab}
    >
      <Helmet>
        <title>{policy.title} | Cauvery Store</title>
        <meta name="description" content={`Read Cauvery Store's ${policy.title.toLowerCase()}. Learn about our commitments to your privacy, terms of use, and shipping arrangements.`} />
      </Helmet>

      <p style={{ fontSize: "0.85rem", color: "#94a3b8", marginBottom: "1.5rem" }}>
        Last updated: {policy.updated}
      </p>

      {policy.sections.map((section, i) => (
        <div className="static-section" key={i}>
          <h3>{section.heading}</h3>
          {section.content && <p>{section.content}</p>}
          {section.items && (
            <ul>
              {section.items.map((item, j) => (
                <li key={j}>{item}</li>
              ))}
            </ul>
          )}
        </div>
      ))}

      <div className="static-section" style={{ borderTop: "1px solid #e2e8f0", paddingTop: "1.5rem" }}>
        <p style={{ fontSize: "0.85rem", color: "#64748b" }}>
          If you have any questions about our policies, please contact us at{" "}
          <a href="mailto:support@cauverystore.in">support@cauverystore.in</a>.
        </p>
      </div>
    </StaticLayout>
  );
};

export default Policies;
