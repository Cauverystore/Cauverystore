import React, { useState } from "react";
import { Mail, Phone, MapPin, Send, Loader } from "lucide-react";
import homeService from "../services/homeService";
import "../styles/contactUs.css";

const ContactUs = () => {
  const [form, setForm] = useState({ name: "", email: "", subject: "", message: "" });
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState("");

  const handleChange = (field) => (e) => {
    setForm({ ...form, [field]: e.target.value });
    if (error) setError("");
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      await homeService.submitContact(form);
      setSubmitted(true);
    } catch {
      setError("Failed to send message. Please try again or email us directly at support@cauverystore.in.");
    } finally {
      setSubmitting(false);
    }
  };

  if (submitted) {
    return (
      <div className="contact-page">
        <div className="contact-success">
          <div className="contact-success-icon">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#16a34a" strokeWidth="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
          </div>
          <h2>Thank You!</h2>
          <p>Your message has been sent successfully. We will get back to you within 24 hours.</p>
          <button className="contact-btn" onClick={() => { setSubmitted(false); setForm({ name: "", email: "", subject: "", message: "" }); }}>
            Send Another Message
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="contact-page">
      <div className="contact-header">
        <h1>Contact Us</h1>
        <p>Have a question or feedback? We would love to hear from you.</p>
      </div>

      <div className="contact-layout">
        <form className="contact-form" onSubmit={handleSubmit}>
          {error && <div className="contact-error">{error}</div>}
          <div className="form-group">
            <label htmlFor="contact-name">Name</label>
            <input id="contact-name" value={form.name} onChange={handleChange("name")} required placeholder="Your full name" />
          </div>
          <div className="form-group">
            <label htmlFor="contact-email">Email</label>
            <input id="contact-email" type="email" value={form.email} onChange={handleChange("email")} required placeholder="your@email.com" />
          </div>
          <div className="form-group">
            <label htmlFor="contact-subject">Subject</label>
            <input id="contact-subject" value={form.subject} onChange={handleChange("subject")} required placeholder="What is this about?" />
          </div>
          <div className="form-group">
            <label htmlFor="contact-message">Message</label>
            <textarea id="contact-message" value={form.message} onChange={handleChange("message")} required placeholder="Tell us more..." rows={5} />
          </div>
          <button type="submit" className="contact-btn" disabled={submitting}>
            {submitting ? <><Loader size={18} className="contact-spinner" /> Sending...</> : <><Send size={18} /> Send Message</>}
          </button>
        </form>

        <aside className="contact-sidebar">
          <div className="contact-details">
            <h3>Get in Touch</h3>
            <div className="contact-detail-item">
              <Mail size={18} />
              <div>
                <strong>Email</strong>
                <a href="mailto:support@cauverystore.in">support@cauverystore.in</a>
              </div>
            </div>
            <div className="contact-detail-item">
              <Phone size={18} />
              <div>
                <strong>Phone</strong>
                <span>{process.env.REACT_APP_CONTACT_PHONE || "+91 98765 43210"}</span>
              </div>
            </div>
            <div className="contact-detail-item">
              <MapPin size={18} />
              <div>
                <strong>Address</strong>
                <span>Coimbatore, Tamil Nadu, India</span>
              </div>
            </div>
          </div>
          <div className="contact-hours">
            <h4>Business Hours</h4>
            <p>Mon - Sat: 9:00 AM - 6:00 PM</p>
            <p>Sunday: Closed</p>
          </div>
        </aside>
      </div>
    </div>
  );
};
export default ContactUs;
