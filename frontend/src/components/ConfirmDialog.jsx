import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import "../styles/site-extras.css";

// A store-styled stand-in for window.confirm:
//   const confirm = useConfirm();
//   if (!(await confirm({ message: "Delete this address?", confirmLabel: "Delete", danger: true }))) return;
const ConfirmContext = createContext(null);

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
