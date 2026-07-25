import React, { useState } from "react";
import homeService from "../services/homeService";
import "../styles/contactUs.css";

const ContactUs = () => {
  const [form, setForm] = useState({ name: "", email: "", subject: "", message: "" });
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    try { await homeService.submitContact(form); setSubmitted(true); } catch (err) { alert("Failed to send message"); }
  };

  return (
    <div className="contact-page">
      <h1>Contact Us</h1>
      <p className="contact-subtitle">Have a question or feedback? We'd love to hear from you.</p>

      {submitted ? <div style={{ textAlign: "center", padding: "2rem", color: "#16a34a", fontWeight: 500 }}>Thank you for your message! We'll get back to you soon.</div> : (
        <form className="contact-form" onSubmit={handleSubmit}>
          <div className="form-group"><label>Name</label><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></div>
          <div className="form-group"><label>Email</label><input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required /></div>
          <div className="form-group"><label>Subject</label><input value={form.subject} onChange={(e) => setForm({ ...form, subject: e.target.value })} required /></div>
          <div className="form-group"><label>Message</label><textarea value={form.message} onChange={(e) => setForm({ ...form, message: e.target.value })} required /></div>
          <button type="submit">Send Message</button>
        </form>
      )}

      <div className="contact-details">
        <h3>Our Contact Details</h3>
        <p>Email: support@cauverystore.in</p>
        <p>Phone: {process.env.REACT_APP_CONTACT_PHONE || "+91-XXXXXXXXXX"}</p>
        <p>Address: Coimbatore, Tamil Nadu, India</p>
      </div>
    </div>
  );
};
export default ContactUs;
