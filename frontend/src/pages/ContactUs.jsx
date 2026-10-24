import React, { useState } from "react";
import { Helmet } from "react-helmet-async";
import { canonicalFor } from "../i18n/canonical";
import { Mail, Phone, MapPin, Send, Loader } from "lucide-react";
import homeService from "../services/homeService";
import "../styles/contactUs.css";
import { useTranslation } from "react-i18next";

const ContactUs = () => {
  const { t } = useTranslation();

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
      setError(t("contactUs.failedToSendMessagePlease"));
    } finally {
      setSubmitting(false);
    }
  };

  if (submitted) {
    return (
      <div className="contact-page">
        <div className="contact-success">
          <div className="contact-success-icon">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#0E5C5C" strokeWidth="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
          </div>
          <h2>{t("contactUs.thankYou")}</h2>
          <p>{t("contactUs.yourMessageHasBeenSent")}</p>
          <button className="contact-btn" onClick={() => { setSubmitted(false); setForm({ name: "", email: "", subject: "", message: "" }); }}>{t("contactUs.sendAnotherMessage")}</button>
        </div>
      </div>
    );
  }

  return (
    <div className="contact-page">
      <Helmet>
        <title>{t("contactUs.contactUsCauveryStore")}</title>
        <meta name="description" content={t("contactUs.contactCauveryStoreForHelp")} />
        <link rel="canonical" href={canonicalFor("/contact")} />
        <meta property="og:title" content={t("contactUs.contactCauveryStore")} />
        <meta property="og:description" content={t("contactUs.questionsAboutAnOrderDelivery")} />
        <meta property="og:type" content="website" />
        <meta property="og:url" content={canonicalFor("/contact")} />
        <meta property="og:image" content="https://cauverystore.in/logo512.png" />
      </Helmet>
      <div className="contact-header">
        <h1>{t("footer.contact")}</h1>
        <p>{t("contactUs.haveAQuestionOrFeedback")}</p>
      </div>

      <div className="contact-layout">
        <form className="contact-form" onSubmit={handleSubmit}>
          {error && <div className="contact-error">{error}</div>}
          <div className="form-group">
            <label htmlFor="contact-name">{t("addressBook.name")}</label>
            <input id="contact-name" value={form.name} onChange={handleChange("name")} required placeholder={t("contactUs.yourFullName")} />
          </div>
          <div className="form-group">
            <label htmlFor="contact-email">{t("contactUs.email")}</label>
            <input id="contact-email" type="email" value={form.email} onChange={handleChange("email")} required placeholder={t("contactUs.yourEmailCom")} />
          </div>
          <div className="form-group">
            <label htmlFor="contact-subject">{t("contactUs.subject")}</label>
            <input id="contact-subject" value={form.subject} onChange={handleChange("subject")} required placeholder={t("contactUs.whatIsThisAbout")} />
          </div>
          <div className="form-group">
            <label htmlFor="contact-message">{t("contactUs.message")}</label>
            <textarea id="contact-message" value={form.message} onChange={handleChange("message")} required placeholder={t("contactUs.tellUsMore")} rows={5} />
          </div>
          <button type="submit" className="contact-btn" disabled={submitting}>
            {submitting ? <><Loader size={18} className="contact-spinner" />{" "}{t("contactUs.sending")}</> : <><Send size={18} />{" "}{t("contactUs.sendMessage")}</>}
          </button>
        </form>

        <aside className="contact-sidebar">
          <div className="contact-details">
            <h3>{t("contactUs.getInTouch")}</h3>
            <div className="contact-detail-item">
              <Mail size={18} />
              <div>
                <strong>{t("contactUs.email")}</strong>
                <a href="mailto:support@cauverystore.in">{t("contactUs.supportCauverystoreIn")}</a>
              </div>
            </div>
            {/* Shown only when a real number is configured - a made-up fallback number
                misleads customers and fails a payment partner's contact-details check. */}
            {process.env.REACT_APP_CONTACT_PHONE && (
            <div className="contact-detail-item">
              <Phone size={18} />
              <div>
                <strong>{t("addressBook.phone")}</strong>
                <span>{process.env.REACT_APP_CONTACT_PHONE}</span>
              </div>
            </div>
            )}
            <div className="contact-detail-item">
              <MapPin size={18} />
              <div>
                <strong>{t("addressBook.address")}</strong>
                <span>{t("contactUs.coimbatoreTamilNaduIndia")}</span>
              </div>
            </div>
          </div>
          <div className="contact-hours">
            <h4>{t("contactUs.businessHours")}</h4>
            <p>{t("contactUs.monSat900Am")}</p>
            <p>{t("contactUs.sundayClosed")}</p>
          </div>
        </aside>
      </div>
    </div>
  );
};
export default ContactUs;
