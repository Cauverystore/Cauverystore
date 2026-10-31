import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import api from "../api/axios";

/*
  The declarations a customer should see before buying: what it is, its tax and price, how
  much is in the pack, where it comes from, who made it, who to call, and the warranty and
  return terms. Product page only - listing cards and tiles never show this block, and it
  never carries the seller's GSTIN or state.
  Rows with nothing to show are left out rather than printed blank.
*/
const ProductLabelInfo = ({ productId }) => {
  const { t: tr } = useTranslation();
  const [info, setInfo] = useState(null);

  useEffect(() => {
    setInfo(null);
    api.get(`/api/products/${productId}/label-info`).then((r) => setInfo(r.data)).catch(() => setInfo(null));
  }, [productId]);

  if (!info) return null;

  const money = (v) => (v == null ? null : `₹${Number(v).toLocaleString("en-IN", { maximumFractionDigits: 2 })}`);
  const care = info.customerCare || {};
  const careText = [care.name, care.phone, care.email, care.address].filter(Boolean).join(" · ");
  const returns = info.returnPolicy
    || (info.returnEligible === false ? tr("productInfo.notReturnable", "Not returnable") : info.returnWindow ? `${info.returnWindow} days` : null);

  const rows = [
    [tr("productInfo.name", "Product"), info.name],
    [tr("productInfo.brand", "Brand"), info.brand],
    [tr("productInfo.sku", "SKU"), info.sku],
    [tr("productInfo.hsn", "HSN code"), info.hsnCode],
    [tr("productInfo.gstRate", "GST rate"), info.gstRate != null ? `${info.gstRate}%` : null],
    [tr("productInfo.mrp", "MRP (incl. of all taxes)"), money(info.mrp)],
    [tr("productInfo.price", "Price"), money(info.price)],
    [tr("productInfo.netQuantity", "Net quantity"), info.netQuantity != null ? `${Number(info.netQuantity)} ${info.quantityUnit || ""}`.trim() : null],
    [tr("productInfo.countryOfOrigin", "Country of origin"), info.countryOfOrigin],
    [tr("productInfo.manufacturer", "Manufacturer"), [info.manufacturerName, info.manufacturerAddress].filter(Boolean).join(", ") || null],
    [tr("productInfo.customerCare", "Customer care"), careText || null],
    [tr("productInfo.warranty", "Warranty"), info.warranty],
  ].filter(([, v]) => v !== null && v !== undefined && String(v).trim() !== "");

  return (
    <section className="pd-label-info" aria-labelledby="pd-label-info-title">
      <h3 id="pd-label-info-title">{tr("productInfo.title", "Product information")}</h3>
      <dl>
        {rows.map(([k, v]) => (
          <React.Fragment key={k}><dt>{k}</dt><dd>{v}</dd></React.Fragment>
        ))}
        <dt>{tr("productInfo.returns", "Return and refund policy")}</dt>
        <dd>{returns ? `${returns}. ` : ""}<Link to="/refund-policy">{tr("productInfo.readPolicy", "Read the full policy")}</Link></dd>
      </dl>
      <style>{`
        .pd-label-info { margin-top: 1.25rem; padding: 0.9rem 1rem; border: 1px solid #e2e8f0; border-radius: 10px; }
        .pd-label-info h3 { font-size: 1rem; margin: 0 0 0.6rem; color: #0f172a; }
        .pd-label-info dl { display: grid; grid-template-columns: minmax(110px, 38%) 1fr; gap: 0.35rem 0.9rem; margin: 0; font-size: 0.86rem; }
        .pd-label-info dt { color: #64748b; }
        .pd-label-info dd { margin: 0; color: #1e293b; overflow-wrap: anywhere; }
        .pd-label-info a { color: #0E5C5C; }
      `}</style>
    </section>
  );
};

export default ProductLabelInfo;
