import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import "../styles/site-extras.css";

// Store-styled stand-ins for the browser's grey boxes:
//   window.confirm -> useConfirm() in components, or confirmAction(message) anywhere
//   window.alert   -> showMessage(message)
//   window.prompt  -> promptText(message, defaultValue)  (resolves to the text, or null on cancel)
const ConfirmContext = createContext(null);
let activeOpen = null;

export const ConfirmProvider = ({ children }) => {
  // Requests wait their turn, so a second box never swallows the first one's answer.
  const [queue, setQueue] = useState([]);
  const request = queue[0] || null;
  const [text, setText] = useState("");
  const confirmBtnRef = useRef(null);
  const cancelBtnRef = useRef(null);
  const inputRef = useRef(null);
  const returnFocusRef = useRef(null);

  const open = useCallback((options) => new Promise((resolve) => {
    setQueue((q) => {
      if (q.length === 0) returnFocusRef.current = document.activeElement;
      return [...q, { kind: "confirm", title: "Are you sure?", confirmLabel: "Yes", cancelLabel: "No, go back", ...options, resolve }];
    });
  }), []);

  const confirm = useCallback((options) => open({ ...options, kind: "confirm" }), [open]);

  const close = useCallback((ok) => {
    if (!request) return;
    const answer = request.kind === "prompt" ? (ok ? text : null)
      : request.kind === "message" ? undefined
      : ok;
    request.resolve(answer);
    setQueue((q) => {
      const rest = q.slice(1);
      if (rest.length === 0) returnFocusRef.current?.focus?.();
      return rest;
    });
  }, [request, text]);

  useEffect(() => {
    activeOpen = open;
    return () => { if (activeOpen === open) activeOpen = null; };
  }, [open]);

  useEffect(() => {
    if (!request) return;
    if (request.kind === "prompt") {
      setText(request.defaultValue || "");
      inputRef.current?.focus();
    } else {
      // Start on the safe choice when the action cannot be undone.
      (request.danger ? cancelBtnRef : confirmBtnRef).current?.focus();
    }
  }, [request]);

  useEffect(() => {
    if (!request) return;
    const onKeyDown = (e) => { if (e.key === "Escape") close(false); };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [request, close]);

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {request && (
        <div className="cs-confirm-backdrop" onClick={(e) => { if (e.target === e.currentTarget) close(false); }}>
          <form
            className="cs-confirm-box"
            role={request.kind === "message" ? "dialog" : "alertdialog"}
            aria-modal="true" aria-labelledby="cs-confirm-title" aria-describedby="cs-confirm-message"
            onSubmit={(e) => { e.preventDefault(); close(true); }}
          >
            <h2 id="cs-confirm-title">{request.title}</h2>
            <p id="cs-confirm-message">{request.message}</p>
            {request.kind === "prompt" && (
              <input
                ref={inputRef}
                className="cs-confirm-input"
                type="text"
                value={text}
                onChange={(e) => setText(e.target.value)}
                aria-labelledby="cs-confirm-message"
              />
            )}
            <div className="cs-confirm-actions">
              {request.kind !== "message" && (
                <button type="button" ref={cancelBtnRef} className="cs-btn cs-btn-secondary" onClick={() => close(false)}>
                  {request.cancelLabel}
                </button>
              )}
              <button type="submit" ref={confirmBtnRef} className={`cs-btn ${request.danger ? "cs-btn-danger" : "cs-btn-primary"}`}>
                {request.confirmLabel}
              </button>
            </div>
          </form>
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
    kind: "confirm",
    title: "Please confirm",
    message,
    confirmLabel: known ? verb.charAt(0).toUpperCase() + verb.slice(1).toLowerCase() : "Yes",
    cancelLabel: "No, go back",
    danger: risky,
    ...options
  };
  return activeOpen ? activeOpen(request) : Promise.resolve(window.confirm(message));
};

// Drop-in for window.alert(message). Code after it carries on at once, as it did with alert
// for anything that did not depend on the box being closed.
export const showMessage = (message, options = {}) => {
  const text = String(message ?? "");
  const failed = /^(failed|could not|couldn't|unable|error)\b/i.test(text);
  const done = /\bsuccessfully\b/i.test(text);
  const request = {
    kind: "message",
    title: failed ? "Something went wrong" : done ? "Done" : "Please note",
    // A bare "Failed" tells the person nothing they can act on.
    message: /^failed\.?$/i.test(text) ? "That didn't work. Please try again." : text,
    confirmLabel: "OK",
    ...options
  };
  return activeOpen ? activeOpen(request) : Promise.resolve(window.alert(text));
};

// Drop-in for window.prompt(message, defaultValue): resolves to the typed text, or null on cancel.
export const promptText = (message, defaultValue = "", options = {}) => {
  const request = {
    kind: "prompt",
    title: "Please confirm",
    message,
    defaultValue,
    confirmLabel: "OK",
    cancelLabel: "Cancel",
    ...options
  };
  return activeOpen ? activeOpen(request) : Promise.resolve(window.prompt(message, defaultValue));
};
