import React from "react";
import { Helmet } from "react-helmet-async";
import { Link, useNavigate } from "react-router-dom";
import StaticLayout from "../components/StaticLayout";

// Each policy has its own address. Payment partners (Razorpay) and search engines check a
// policy by its URL, and a tab chosen by a "#terms" suffix all looks like one page to them.
const tabs = [
  { id: "privacy", label: "Privacy Policy", path: "/privacy-policy" },
  { id: "terms", label: "Terms & Conditions", path: "/terms-and-conditions" },
  { id: "shipping", label: "Shipping Policy", path: "/shipping-policy" },
];

const UPDATED = "10 October 2026";

const policies = {
  privacy: {
    title: "Privacy Policy",
    updated: UPDATED,
    sections: [
      {
        heading: "Information We Collect",
        content: "We collect information you provide when creating an account, placing an order, or contacting support. This includes your name, email address, phone number and delivery address. We also automatically collect certain technical information such as IP address, browser type, and device information to improve our services."
      },
      {
        heading: "Payment Information",
        items: [
          "Online payments are handled by Razorpay, our payment partner. You enter your card, UPI or bank details on Razorpay's secure payment window, not on our pages",
          "We do not receive or store your full card number, CVV, UPI PIN or net banking password",
          "Razorpay tells us whether the payment succeeded, the amount, the payment method used and a payment reference number. We keep these with your order so we can confirm it, issue invoices and process refunds",
          "Razorpay handles your payment details under its own privacy policy, published at razorpay.com/privacy"
        ]
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
        content: "We do not sell your personal information. We share only what is needed with the seller who fulfils your order (your name, delivery address and phone number), with Razorpay to process payments and refunds, and with delivery partners to deliver your order. We may also disclose information where the law requires it."
      },
      {
        heading: "Data Security",
        content: "We implement industry-standard security measures including SSL encryption, secure servers, and regular security audits. Your card and bank details are processed directly by Razorpay and are never stored on our servers."
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
    title: "Terms & Conditions",
    updated: UPDATED,
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
        heading: "A Marketplace of Independent Sellers",
        content: "Cauvery Store is an online marketplace. Products are listed and sold by independent shops and traders based in Tamil Nadu, and the contract of sale for a product is between you and that seller. Cauvery Store runs the website, takes payment on the seller's behalf, and supports you with delivery, returns and refunds as set out in these terms and our policies."
      },
      {
        heading: "Prices and Orders",
        items: [
          "All prices are in Indian Rupees (INR). The price, GST and delivery charge are shown at checkout before you pay",
          "All orders are subject to availability and acceptance",
          "We or the seller may cancel an order because of a pricing error, the item being out of stock, or suspected fraud. If you have already paid, the full amount is refunded"
        ]
      },
      {
        heading: "Payments",
        items: [
          "Online payments are processed by Razorpay Software Private Limited (Razorpay), a payment gateway. By paying online you also agree to Razorpay's terms of use, published at razorpay.com/terms",
          "You can pay using the methods shown at checkout, which may include UPI, credit and debit cards, net banking and wallets",
          "You must use a payment method that belongs to you or that you are authorised to use, and the details you give must be accurate",
          "Your card, UPI and bank details are entered on Razorpay's secure payment window. Cauvery Store does not receive or store them",
          "An order paid online is confirmed only when Razorpay tells us the payment has succeeded. You will see a confirmation on screen and under My Orders",
          "Your bank or card issuer may apply its own charges or limits. These are between you and them"
        ]
      },
      {
        heading: "Failed and Duplicate Payments",
        items: [
          "If a payment fails, no order is placed and you can try again or choose another method",
          "If money leaves your account but the order is not confirmed, the amount is usually returned to the same account automatically by your bank within 5-7 business days",
          "If you are charged twice for the same order, email support@cauverystore.in with your order number and the payment reference. We will refund the extra amount to the original payment method",
          "We are not responsible for delays caused by your bank or the payment network once a refund has been sent"
        ]
      },
      {
        heading: "Cancellations, Returns and Refunds",
        content: "You can cancel an order free of charge before it ships, and return most items within 7 days of delivery. Refunds are sent through Razorpay to the original payment method, normally within 5-7 business days. The full rules are in our Returns, Cancellations & Refunds policy, which forms part of these terms.",
        link: { to: "/refund-policy", label: "Read the Returns, Cancellations & Refunds policy" }
      },
      {
        heading: "Delivery",
        content: "Delivery areas, charges and timeframes are set out in our Shipping Policy, which forms part of these terms.",
        link: { to: "/shipping-policy", label: "Read the Shipping Policy" }
      },
      {
        heading: "Prohibited Use",
        items: [
          "Do not use the site for any unlawful or fraudulent purpose, or use a payment method without the owner's permission",
          "Do not attempt to interfere with the site, its security or other customers' accounts",
          "We may suspend an account or cancel orders where we reasonably suspect fraud or misuse"
        ]
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
        heading: "Complaints and Contact",
        content: "If something has gone wrong with an order or a payment, email support@cauverystore.in with your order number. Our support team is available Monday to Saturday, 9 AM to 6 PM. Nothing in these terms limits the rights you have under Indian consumer law.",
        link: { to: "/contact", label: "Contact us" }
      },
      {
        heading: "Governing Law",
        content: "These terms are governed by the laws of India. Any disputes arising from these terms shall be subject to the exclusive jurisdiction of the courts in Coimbatore, Tamil Nadu."
      }
    ]
  },
  // Several couriers are used, so nothing here names one or relies on one courier's terms.
  // Delivery times below are estimates built from published courier norms (India Post Speed
  // Post service standards and the usual private-courier ranges), not a contract. Check them
  // against the courier agreement actually in use before changing a number. Charges mirror
  // the cart: free from Rs. 500, otherwise Rs. 40.
  shipping: {
    title: "Shipping Policy",
    updated: UPDATED,
    sections: [
      {
        heading: "Where We Deliver",
        items: [
          "Cauvery Store is a marketplace. Each order is packed by the seller, a shop or trader based in Tamil Nadu, and sent through one of several courier and postal partners",
          "The courier for your order is chosen according to your pincode and the size of the parcel. You cannot choose the courier, and different parcels in one order may come by different couriers",
          "We deliver to addresses in India only. We do not deliver outside India",
          "Whether a product can be delivered to your pincode depends on the seller and on which of our couriers serve your area. If an order cannot be delivered to your address, we tell you and refund anything you have paid"
        ]
      },
      {
        heading: "Delivery Charges",
        items: [
          "Free delivery on orders of Rs. 500 or more",
          "A flat Rs. 40 delivery charge applies to orders below Rs. 500",
          "The delivery charge is shown in your cart and at checkout before you pay. There are no other delivery fees"
        ]
      },
      {
        heading: "How Long Delivery Takes",
        content: "Delivery time has two parts: the time the seller takes to pack and hand over your order, and the time the courier takes to bring it to you.",
        items: [
          "Dispatch: sellers normally hand your order to the courier within 1-2 business days of the order being confirmed",
          "Within Tamil Nadu and Puducherry: usually 2-4 business days after dispatch",
          "Kerala, Karnataka, Andhra Pradesh and Telangana: usually 3-5 business days after dispatch",
          "Rest of India: usually 4-7 business days after dispatch",
          "Remote areas, the North-East, Jammu & Kashmir, Ladakh and the islands: usually 7-10 business days after dispatch"
        ]
      },
      {
        heading: "About These Estimates",
        items: [
          "These are estimates, not guaranteed dates. Actual times differ from one courier to another. Where a product page or checkout shows a different estimate for your order, that estimate applies",
          "Business days are Monday to Saturday and do not include Sundays or public holidays",
          "Delivery can take longer during festivals and sales, in bad weather, during strikes or transport disruptions, or where the courier cannot reach your area",
          "If your order is running late, we do not cancel it automatically. See \"Late or Missing Orders\" below for what you can do"
        ]
      },
      {
        heading: "Orders From More Than One Seller",
        content: "If your order has products from different sellers, they may arrive in separate parcels on different days. You pay the delivery charge once for the order, not once per parcel."
      },
      {
        heading: "Tracking Your Order",
        items: [
          "You can follow every stage of your order, from confirmed to packed, shipped and delivered, under My Orders",
          "Once your order ships, the name of the courier and the tracking number are shown on the order page, so you can also track it on that courier’s own website",
          "We email you when your order is confirmed and when its status changes",
          "Courier tracking can take up to 24 hours to show movement after an order is marked as shipped"
        ]
      },
      {
        heading: "Changing or Cancelling Before Dispatch",
        items: [
          "You can cancel an order free of charge from My Orders at any time before it ships",
          "To correct a delivery address or phone number, email support@cauverystore.in with your order number as soon as possible. We can only make changes before the order is handed to the courier"
        ]
      },
      {
        heading: "When Your Order Arrives",
        items: [
          "Please make sure someone is available at the delivery address to receive the parcel and that your phone is reachable. The courier may call before delivery",
          "Check the parcel before accepting it. If the outer packaging is open, torn or looks tampered with, you may refuse it and tell us",
          "If the item inside is damaged, defective or not what you ordered, raise a return from My Orders within the return period. The return is free",
          "The risk in the goods passes to you once the parcel has been delivered to your address"
        ],
        link: { to: "/refund-policy", label: "Read the Returns, Cancellations & Refunds policy" }
      },
      {
        heading: "If Delivery Cannot Be Completed",
        items: [
          "The courier tries to deliver more than once. The number of attempts depends on the courier, and is usually two or three",
          "If delivery fails because the address is wrong or incomplete, nobody is available, or the parcel is refused, it is returned to the seller and the order is cancelled",
          "For an order you have paid for, we refund the price of the items to your original payment method once the parcel is back with the seller. The delivery charge, if you paid one, is not refunded in this case",
          "If delivery fails for a reason that is not yours, such as the courier being unable to serve your area, you receive a full refund including any delivery charge"
        ]
      },
      {
        heading: "Late or Missing Orders",
        items: [
          "If your order has not arrived 3 business days after the latest estimated date, email support@cauverystore.in with your order number. We will trace it with the seller and the courier",
          "If a parcel is confirmed lost in transit, you can choose a replacement, where the seller has stock, or a full refund",
          "If your order is marked as delivered but you have not received it, tell us within 7 days of the delivery date so that we can investigate with the courier"
        ]
      },
      {
        heading: "Contact",
        content: "For any delivery question, email support@cauverystore.in with your order number. Our support team is available Monday to Saturday, 9 AM to 6 PM.",
        link: { to: "/contact", label: "Contact us" }
      }
    ]
  }
};

const Policies = ({ tab }) => {
  const navigate = useNavigate();
  // Old links used /policies#terms; keep them opening the right policy.
  const hashTab = window.location.hash.replace("#", "");
  const wanted = tab || hashTab;
  const activeTab = tabs.some((t) => t.id === wanted) ? wanted : "privacy";
  const setActiveTab = (id) => navigate(tabs.find((t) => t.id === id).path);
  const policy = policies[activeTab];
  const canonicalUrl = "https://cauverystore.in" + tabs.find((t) => t.id === activeTab).path;

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
        {/* One string, not text plus an expression: Helmet drops a title with several children. */}
        <title>{`${policy.title} | Cauvery Store`}</title>
        <meta name="description" content={`Read Cauvery Store's ${policy.title.toLowerCase()}. Learn about our commitments to your privacy, terms of use, and shipping arrangements.`} />
        <link rel="canonical" href={canonicalUrl} />
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
          {section.link && <p><Link to={section.link.to}>{section.link.label}</Link></p>}
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
