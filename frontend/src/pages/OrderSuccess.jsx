import React, { useEffect, useRef } from "react";
import { Link, useSearchParams } from "react-router-dom";
import api from "../api/axios";
import { trackPurchase } from "../utils/analytics";
import { useTranslation } from "react-i18next";

const OrderSuccess = () => {
  const { t } = useTranslation();

  const [params] = useSearchParams();
  const orderId = params.get("id");
  const tracked = useRef(false);

  useEffect(() => {
    if (!orderId || tracked.current) return;
    tracked.current = true;
    api.get(`/api/orders/${orderId}`)
      .then((res) => trackPurchase(res.data || {}))
      .catch(() => trackPurchase({ id: orderId }));
  }, [orderId]);

  return (
    <div style={{ maxWidth: "600px", margin: "3rem auto", textAlign: "center", padding: "2rem" }}>
      <div style={{ fontSize: "4rem", marginBottom: "1rem" }}>&#10003;</div>
      <h1 style={{ color: "#0E5C5C", fontSize: "1.8rem", marginBottom: "0.5rem" }}>{t("orderSuccess.thankYouYourOrderIs")}</h1>
      <p style={{ color: "#475569", marginBottom: "0.5rem" }}>{t("orderSuccess.yourOrderHasGoneTo")}</p>
      {orderId && <p style={{ fontWeight: 600 }}>{t("orderSuccess.orderNumber")}{" "}{orderId}</p>}
      <div style={{ margin: "1.5rem auto 0", maxWidth: 420, textAlign: "left", background: "#edf8f7", border: "1px solid #a8e3e0", borderRadius: 8, padding: "1rem 1.25rem", color: "#334155", fontSize: "0.9rem", lineHeight: 1.6 }}>
        <strong style={{ display: "block", marginBottom: "0.35rem", color: "#0f172a" }}>{t("orderSuccess.whatHappensNext")}</strong>
        <ol style={{ margin: 0, paddingLeft: "1.1rem" }}>
          <li>{t("orderSuccess.theSellerConfirmsAndPacks")}</li>
          <li>{t("orderSuccess.itIsShippedAndYou")}</li>
          <li>{t("orderSuccess.youCanCancelFreeOf")}</li>
        </ol>
      </div>
      <div style={{ marginTop: "1.5rem", display: "flex", gap: "1rem", justifyContent: "center", flexWrap: "wrap" }}>
        <Link to={orderId ? `/orders/${orderId}` : "/orders"} style={{ padding: "0.6rem 1.5rem", background: "#0E5C5C", color: "#fff", borderRadius: 6, textDecoration: "none" }}>{t("orderSuccess.trackThisOrder")}</Link>
        <Link to="/products" style={{ padding: "0.6rem 1.5rem", border: "1px solid #0E5C5C", color: "#0E5C5C", borderRadius: 6, textDecoration: "none" }}>{t("orderSuccess.keepShopping")}</Link>
      </div>
      <p style={{ marginTop: "1.5rem", fontSize: "0.85rem", color: "#64748b" }}>{t("orderSuccess.questionsAboutThisOrderEmail")}{" "}<a href="mailto:support@cauverystore.in" style={{ color: "#0E5C5C" }}>{t("contactUs.supportCauverystoreIn")}</a>{" "}{t("orderSuccess.withYourOrderNumber")}</p>
    </div>
  );
};
export default OrderSuccess;
