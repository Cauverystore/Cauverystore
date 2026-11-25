import React, { useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import api from "../api/axios";
import { useAuth } from "../context/AuthContext";
import "../styles/feedback-tab.css";

const TYPES = ["FEEDBACK", "COMPLAINT", "SUGGESTION"];
const MAX_SIDE = 1280; // photos are shrunk to this size before sending
const STAFF_PAGES = /^\/(admin|seller|super-admin|executive)(\/|$)/;

/** Shrinks a chosen photo to a JPEG of at most 1280px a side, so uploads stay small. */
const shrinkPhoto = (file) =>
  new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      const scale = Math.min(1, MAX_SIDE / Math.max(img.width, img.height));
      const canvas = document.createElement("canvas");
      canvas.width = Math.round(img.width * scale);
      canvas.height = Math.round(img.height * scale);
      const ctx = canvas.getContext("2d");
      ctx.fillStyle = "#fff";
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      URL.revokeObjectURL(url);
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("shrink"))), "image/jpeg", 0.82);
    };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error("load")); };
    img.src = url;
  });

/**
 * "Feedback" tab on the right edge of every shop page. Opens a small form: type, message,
 * optional photo, optional name and email. Saved to Admin -> Feedback.
 */
const FeedbackTab = () => {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const { user, isAuthenticated } = useAuth();
  const [open, setOpen] = useState(false);
  const [type, setType] = useState("FEEDBACK");
  const [message, setMessage] = useState("");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [photo, setPhoto] = useState(null); // { blob, preview }
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);
  const panelRef = useRef(null);
  const tabRef = useRef(null);
  const fileRef = useRef(null);

  // Fill in name and email for signed-in customers the first time the form opens.
  useEffect(() => {
    if (open && isAuthenticated && user) {
      setName((n) => n || user.fullName || user.name || "");
      setEmail((e) => e || user.email || "");
    }
  }, [open, isAuthenticated, user]);

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => { if (e.key === "Escape") setOpen(false); };
    document.addEventListener("keydown", onKey);
    const timer = setTimeout(() => panelRef.current?.querySelector("textarea")?.focus(), 50);
    return () => { document.removeEventListener("keydown", onKey); clearTimeout(timer); };
  }, [open]);

  useEffect(() => () => { if (photo) URL.revokeObjectURL(photo.preview); }, [photo]);

  // Other page widgets (the TellX button) sit at the very top layer; hide them while this is open.
  useEffect(() => {
    document.body.classList.toggle("fb-open", open);
    return () => document.body.classList.remove("fb-open");
  }, [open]);

  if (STAFF_PAGES.test(pathname)) return null;

  const close = () => {
    setOpen(false);
    if (done) {
      setDone(false);
      setMessage("");
      setPhoto(null);
      setType("FEEDBACK");
    }
    setTimeout(() => tabRef.current?.focus(), 0);
  };

  const pickPhoto = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    if (!/^image\//.test(file.type)) { setError(t("feedbackTab.photoNotImage")); return; }
    try {
      const blob = await shrinkPhoto(file);
      if (blob.size > 1_000_000) { setError(t("feedbackTab.photoTooBig")); return; }
      setError("");
      setPhoto({ blob, preview: URL.createObjectURL(blob) });
    } catch {
      setError(t("feedbackTab.photoNotImage"));
    }
  };

  const submit = async (e) => {
    e.preventDefault();
    if (!message.trim()) { setError(t("feedbackTab.writeMessage")); return; }
    setBusy(true);
    setError("");
    const form = new FormData();
    form.append("type", type);
    form.append("message", message.trim());
    form.append("name", name.trim());
    form.append("email", email.trim());
    form.append("page", pathname);
    if (photo) form.append("photo", photo.blob, "photo.jpg");
    try {
      await api.post("/api/feedback", form, { headers: { "Content-Type": "multipart/form-data" } });
      setDone(true);
    } catch (err) {
      setError(err?.response?.data?.error || t("feedbackTab.sendFailed"));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      {!open && (
        <button ref={tabRef} type="button" className="fb-tab" onClick={() => setOpen(true)} aria-label={t("feedbackTab.open")}>
          {t("feedbackTab.tabLabel")}
        </button>
      )}

      {open && (
        <div className="fb-backdrop" onClick={close}>
          <div className="fb-panel" role="dialog" aria-modal="true" aria-labelledby="fb-title" ref={panelRef} onClick={(e) => e.stopPropagation()}>
            <div className="fb-head">
              <h2 id="fb-title">{t("feedbackTab.title")}</h2>
              <button type="button" className="fb-close" onClick={close} aria-label={t("feedbackTab.close")}>×</button>
            </div>

            {done ? (
              <div className="fb-done">
                <div className="fb-done-tick" aria-hidden="true">✓</div>
                <p>{t("feedbackTab.thanks")}</p>
                {!email.trim() && <p className="fb-hint">{t("feedbackTab.thanksNoEmail")}</p>}
                <button type="button" className="fb-send" onClick={close}>{t("feedbackTab.close")}</button>
              </div>
            ) : (
              <form onSubmit={submit} noValidate>
                <p className="fb-intro">{t("feedbackTab.intro")}</p>
                <div className="fb-types" role="radiogroup" aria-label={t("feedbackTab.typeLabel")}>
                  {TYPES.map((k) => (
                    <button
                      key={k}
                      type="button"
                      role="radio"
                      aria-checked={type === k}
                      className={`fb-type${type === k ? " fb-type-on" : ""}`}
                      onClick={() => setType(k)}
                    >
                      {t(`feedbackTab.type_${k}`)}
                    </button>
                  ))}
                </div>

                <label className="fb-label" htmlFor="fb-message">{t("feedbackTab.messageLabel")} <span aria-hidden="true">*</span></label>
                <textarea
                  id="fb-message"
                  className="fb-input"
                  rows={4}
                  maxLength={2000}
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder={t("feedbackTab.messagePlaceholder")}
                />

                <div className="fb-photo">
                  {photo ? (
                    <div className="fb-photo-preview">
                      <img src={photo.preview} alt={t("feedbackTab.photoAlt")} />
                      <button type="button" className="fb-link" onClick={() => setPhoto(null)}>{t("feedbackTab.removePhoto")}</button>
                    </div>
                  ) : (
                    <button type="button" className="fb-attach" onClick={() => fileRef.current?.click()}>
                      {t("feedbackTab.addPhoto")}
                    </button>
                  )}
                  <input ref={fileRef} type="file" accept="image/*" hidden onChange={pickPhoto} />
                </div>

                <div className="fb-row">
                  <div>
                    <label className="fb-label" htmlFor="fb-name">{t("feedbackTab.nameLabel")}</label>
                    <input id="fb-name" className="fb-input" maxLength={100} value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" />
                  </div>
                  <div>
                    <label className="fb-label" htmlFor="fb-email">{t("feedbackTab.emailLabel")}</label>
                    <input id="fb-email" className="fb-input" type="email" maxLength={200} value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
                  </div>
                </div>
                <p className="fb-hint">{t("feedbackTab.emailHint")}</p>

                {error && <p className="fb-error" role="alert">{error}</p>}
                <button type="submit" className="fb-send" disabled={busy}>
                  {busy ? t("feedbackTab.sending") : t("feedbackTab.send")}
                </button>
              </form>
            )}
          </div>
        </div>
      )}
    </>
  );
};

export default FeedbackTab;
