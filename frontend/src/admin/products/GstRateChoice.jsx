import React, { useState, useEffect } from "react";
import api from "../../api/axios";

/**
 * Asks which published rate line describes the goods, when the code alone cannot say.
 *
 * HSN 0901 is 5% for roasted coffee and nil for green beans. Both are official, both are
 * correct, and no rate table can choose between them - the missing fact is what the product
 * actually is. So the question goes to the only person who knows.
 *
 * It is asked in the notification's own words rather than as percentages, because "roasted or
 * not roasted?" is answerable by anyone looking at their own stock and "5% or nil?" is not.
 *
 * "I'm not sure" is a real answer, not a gap to be filled. Forcing a guess produces a
 * confident wrong classification, which is the failure this whole system exists to prevent -
 * so the product simply stays a draft until someone can answer.
 */
const GstRateChoice = ({ hsnCode, unitPrice, prePackaged, value, onChange, productStatus }) => {
  const [options, setOptions] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    if (!hsnCode) { setOptions([]); return; }
    setLoading(true);
    api.get("/api/hsn/rate-options", {
      params: {
        hsnCode,
        ...(unitPrice ? { unitPrice } : {}),
        ...(prePackaged === undefined ? {} : { prePackaged }),
      },
    })
      .then((r) => { if (!cancelled) setOptions(r.data || []); })
      .catch(() => { if (!cancelled) setOptions([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [hsnCode, unitPrice, prePackaged]);

  // Nothing to ask: either one rate applies, or price and packaging already settle it.
  if (loading || options.length === 0) return null;

  const unanswered = !value;
  const willPublish = (productStatus || "published") === "published";

  return (
    <div style={{
      border: `1px solid ${unanswered ? "#fde68a" : "#d1fae5"}`,
      background: unanswered ? "#fffbeb" : "#f0fdf4",
      borderRadius: 8, padding: "0.9rem 1rem", margin: "0.75rem 0",
    }}>
      <div style={{ fontWeight: 600, fontSize: "0.9rem", marginBottom: 4 }}>
        HSN {hsnCode} is taxed at more than one rate — which describes this product?
      </div>
      <div style={{ color: "#6b7280", fontSize: "0.78rem", marginBottom: "0.7rem" }}>
        These are the government's own words for each rate. Pick the one that matches what you
        actually sell.
      </div>

      {options.map((o) => (
        <label key={o.rateId} style={rowStyle(value === o.rateId)}>
          <input
            type="radio"
            name="gstRateChoice"
            checked={value === o.rateId}
            onChange={() => onChange(o.rateId)}
            style={{ marginTop: 3 }}
          />
          <span>
            <span style={{ fontWeight: 600 }}>{o.gstRate}% GST</span>
            <span style={{ display: "block", color: "#374151", fontSize: "0.82rem" }}>
              {o.description}
            </span>
          </span>
        </label>
      ))}

      <label style={rowStyle(value === null && !unanswered)}>
        <input
          type="radio"
          name="gstRateChoice"
          checked={value === null}
          onChange={() => onChange(null)}
          style={{ marginTop: 3 }}
        />
        <span>
          <span style={{ fontWeight: 600 }}>I'm not sure</span>
          <span style={{ display: "block", color: "#374151", fontSize: "0.82rem" }}>
            Saves as a draft for someone to check. Better than guessing — a wrong rate is
            charged to every customer and only shows up at an audit.
          </span>
        </span>
      </label>

      {unanswered && willPublish && (
        <div style={{ marginTop: "0.6rem", fontSize: "0.78rem", color: "#92400e" }}>
          Until this is answered the product can be saved but not put on sale, because every
          invoice has to state a rate that is correct for what was sold.
        </div>
      )}
    </div>
  );
};

const rowStyle = (selected) => ({
  display: "flex", gap: "0.6rem", alignItems: "flex-start",
  padding: "0.5rem 0.6rem", marginBottom: 4, borderRadius: 6, cursor: "pointer",
  fontSize: "0.85rem",
  background: selected ? "#ffffff" : "transparent",
  border: `1px solid ${selected ? "#c7d2fe" : "transparent"}`,
});

export default GstRateChoice;
