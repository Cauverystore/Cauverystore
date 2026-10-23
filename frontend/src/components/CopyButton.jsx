import React, { useEffect, useRef, useState } from "react";
import { Copy, Check } from "lucide-react";
import "../styles/site-extras.css";
import { useTranslation } from "react-i18next";

const copyText = async (text) => {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    // Older browsers and some in-app webviews have no clipboard API.
    const area = document.createElement("textarea");
    area.value = text;
    area.setAttribute("readonly", "");
    area.style.position = "fixed";
    area.style.opacity = "0";
    document.body.appendChild(area);
    area.select();
    let ok = false;
    try { ok = document.execCommand("copy"); } catch { ok = false; }
    document.body.removeChild(area);
    return ok;
  }
};

const CopyButton = ({ text, label = "Copy", ariaLabel }) => {
  const { t } = useTranslation();

  const [state, setState] = useState("idle");
  const timerRef = useRef(null);

  useEffect(() => () => clearTimeout(timerRef.current), []);

  const handleClick = async () => {
    const ok = await copyText(String(text));
    setState(ok ? "copied" : "failed");
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => setState("idle"), 2000);
  };

  return (
    <button type="button" className="cs-copy-btn" onClick={handleClick} aria-label={ariaLabel || label}>
      {state === "copied" ? <Check size={14} aria-hidden="true" /> : <Copy size={14} aria-hidden="true" />}
      <span aria-live="polite">{state === "copied" ? t("copyButton.copied") : state === "failed" ? t("copyButton.couldnTCopy") : label}</span>
    </button>
  );
};

export default CopyButton;
