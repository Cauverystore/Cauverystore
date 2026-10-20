import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import "../styles/site-extras.css";

// A store-styled stand-in for window.confirm:
//   const confirm = useConfirm();
//   if (!(await confirm({ message: "Delete this address?", confirmLabel: "Delete", danger: true }))) return;
const ConfirmContext = createContext(null);
let activeConfirm = null;

export const ConfirmProvider = ({ children }) => {
  const [request, setRequest] = useState(null);
  const confirmBtnRef = useRef(null);
  const cancelBtnRef = useRef(null);
  const returnFocusRef = useRef(null);

  const confirm = useCallback((options) => new Promise((resolve) => {
    returnFocusRef.current = document.activeElement;
    setRequest({ title: "Are you sure?", confirmLabel: "Yes", cancelLabel: "No, go back", ...options, resolve });
  }), []);

  const close = useCallback((answer) => {
    setRequest((current) => {
      current?.resolve(answer);
      return null;
    });
    returnFocusRef.current?.focus?.();
  }, []);

  useEffect(() => {
    activeConfirm = confirm;
    return () => { if (activeConfirm === confirm) activeConfirm = null; };
  }, [confirm]);

  useEffect(() => {
    if (!request) return;
    // Start on the safe choice when the action cannot be undone.
    (request.danger ? cancelBtnRef : confirmBtnRef).current?.focus();
    const onKeyDown = (e) => { if (e.key === "Escape") close(false); };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [request, close]);

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {request && (
        <div className="cs-confirm-backdrop" onClick={(e) => { if (e.target === e.currentTarget) close(false); }}>
          <div className="cs-confirm-box" role="alertdialog" aria-modal="true" aria-labelledby="cs-confirm-title" aria-describedby="cs-confirm-message">
            <h2 id="cs-confirm-title">{request.title}</h2>
            <p id="cs-confirm-message">{request.message}</p>
            <div className="cs-confirm-actions">
              <button type="button" ref={cancelBtnRef} className="cs-btn cs-btn-secondary" onClick={() => close(false)}>
                {request.cancelLabel}
              </button>
              <button type="button" ref={confirmBtnRef} className={`cs-btn ${request.danger ? "cs-btn-danger" : "cs-btn-primary"}`} onClick={() => close(true)}>
                {request.confirmLabel}
              </button>
            </div>
          </div>
        </div>
      )}
    </ConfirmContext.Provider>
  );
};

export const useConfirm = () => {
  const confirm = useContext(ConfirmContext);
  // Outside the provider, fall back to the browser box rather than skipping the question.
  return confirm || ((options) => Promise.resolve(window.confirm(options.message)));
};

// Drop-in for window.confirm(message) in code that cannot use the hook:
//   if (!(await confirmAction("Delete this banner?"))) return;
// Picks a red button and a matching label for actions that remove or block something.
export const confirmAction = (message, options = {}) => {
  const verb = (String(message).match(/^\s*(?:are you sure you want to\s+)?(\w+)/i) || [])[1] || "";
  const risky = /^(delete|remove|unsubscribe|suspend|reject)$/i.test(verb) || /permanently|cannot be undone/i.test(message);
  const known = /^(delete|remove|unsubscribe|suspend|charge|retry|revoke|lift)$/i.test(verb);
  const request = {
    title: "Please confirm",
    message,
    confirmLabel: known ? verb.charAt(0).toUpperCase() + verb.slice(1).toLowerCase() : "Yes",
    danger: risky,
    ...options
  };
  return activeConfirm ? activeConfirm(request) : Promise.resolve(window.confirm(message));
};
