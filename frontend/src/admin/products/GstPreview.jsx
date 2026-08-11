import React, { useState, useEffect } from "react";
import api from "../../api/axios";

/**
 * Shows what the GST master will actually charge for the code being entered.
 *
 * The seller's own cessRate entry is a declaration, not a legal source - the compensation
 * cess comes from the same published row that decides the GST rate, so a product can never
 * invent a cess the CBIC has not put on it. This panel resolves that row live, so the number
 * on the screen is the number on the invoice, including the cess line.
 *
 * Nothing to show means the code is not resolvable yet: either it is missing, or the code
 * has rate lines the seller has not chosen between (the GstRateChoice box then appears).
 */
const GstPreview = ({ hsnCode, unitPrice, prePackaged }) => {
  const [preview, setPreview] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    if (!hsnCode) { setPreview(null); return; }
    setLoading(true);
    api.get("/api/hsn/rate-preview", {
      params: {
        hsnCode,
        ...(unitPrice ? { unitPrice } : {}),
        ...(prePackaged === undefined ? {} : { prePackaged }),
      },
    })
      .then((r) => { if (!cancelled) setPreview(r.data || null); })
      .catch(() => { if (!cancelled) setPreview(null); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [hsnCode, unitPrice, prePackaged]);

  if (loading) return null;
  if (!preview) return null;

  const total = preview.gstRate;
  const half = total / 2;
  const cess = preview.cessRate || 0;

  return (
    <div style={{
      border: "1px solid #dbeafe", background: "#eff6ff", borderRadius: 8,
      padding: "0.75rem 1rem", margin: "0.75rem 0",
    }}>
      <div style={{ fontWeight: 600, fontSize: "0.85rem", marginBottom: 6 }}>
        GST preview — what this code is charged
      </div>

      <table style={{ borderCollapse: "collapse", fontSize: "0.82rem", width: "100%" }}>
        <tbody>
          <tr>
            <td style={{ color: "#6b7280", padding: "2px 0" }}>GST rate</td>
            <td style={{ textAlign: "right", fontWeight: 600 }}>
              {total}%{cess > 0 && <span style={{ color: "#1d4ed8" }}> + cess {cess}%</span>}
            </td>
          </tr>
          <tr>
            <td style={{ color: "#6b7280", padding: "2px 0" }}>Within a state</td>
            <td style={{ textAlign: "right" }}>CGST {half}% + SGST {half}%</td>
          </tr>
          <tr>
            <td style={{ color: "#6b7280", padding: "2px 0" }}>Across states</td>
            <td style={{ textAlign: "right" }}>IGST {total}%</td>
          </tr>
          {cess > 0 && (
            <tr>
              <td style={{ color: "#6b7280", padding: "2px 0" }}>
                Cess ({preview.hsnCode})
              </td>
              <td style={{ textAlign: "right" }}>{cess}% on the taxable value</td>
            </tr>
          )}
        </tbody>
      </table>

      <div style={{ color: "#6b7280", fontSize: "0.72rem", marginTop: 6 }}>
        From the CBIC notification for HSN {preview.hsnCode}: “{preview.description}”.
        {cess > 0 && " The cess is the published rate — your own cess entry is a declaration, not the source."}
      </div>
    </div>
  );
};

export default GstPreview;
