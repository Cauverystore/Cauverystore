import React, { useState, useEffect, useMemo } from "react";
import { variantLabel } from "../utils/variant";
import { useParams, useNavigate, Link } from "react-router-dom";
import api from "../api/axios";
import CartItemImage from "../components/CartItemImage";
import CopyButton from "../components/CopyButton";
import { useConfirm } from "../components/ConfirmDialog";
import "../styles/orderDetail.css";
import { useTranslation } from "react-i18next";
import i18n from "../i18n";

const STATUS_STEPS = [
  {
    key: "PLACED",

    get label() {
      return i18n.t("orders.status.placed");
    }
  },
  {
    key: "CONFIRMED",

    get label() {
      return i18n.t("orderDetail.confirmed");
    }
  },
  {
    key: "PACKED",

    get label() {
      return i18n.t("orderDetail.packed");
    }
  },
  {
    key: "SHIPPED",

    get label() {
      return i18n.t("orders.status.shipped");
    }
  },
  {
    key: "DELIVERED",

    get label() {
      return i18n.t("orders.status.delivered");
    }
  }
];

const CANCELLED_STATUSES = ["CANCELLED", "CANCELED", "RETURNED"];

const OrderDetail = () => {
  const { t } = useTranslation();

  const { id } = useParams();
  const navigate = useNavigate();
  const confirm = useConfirm();

  const [items, setItems] = useState([]);
  const [timeline, setTimeline] = useState([]);
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [cancelling, setCancelling] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [hasInvoice, setHasInvoice] = useState(false);
  const [pdfDl, setPdfDl] = useState(false);

  useEffect(() => {
    const fetchOrderData = async () => {
      setError("");
      try {
        const [orderRes, itemsRes, timelineRes] = await Promise.allSettled([
          api.get(`/api/orders/${id}`),
          api.get(`/api/orders/${id}/items`),
          api.get(`/api/orders/${id}/timeline`)
        ]);

        if (orderRes.status === "fulfilled") {
          setOrder(orderRes.value.data);
        } else {
          setError(t("orderDetail.failedToLoadOrderDetails"));
        }

        if (itemsRes.status === "fulfilled") {
          const data = itemsRes.value.data;
          setItems(Array.isArray(data) ? data : data?.items || data?.orderItems || []);
        } else {
          void itemsRes;
        }

        if (timelineRes.status === "fulfilled") {
          const data = timelineRes.value.data;
          setTimeline(Array.isArray(data) ? data : data?.timeline || data?.events || []);
        } else {
          void timelineRes;
        }
      } catch (err) {
        void err;
        setError(t("orderDetail.failedToLoadOrderDetails"));
      }
      try {
        const invRes = await api.get(`/api/gst/my-order-invoice/${id}`);
        if (invRes.data?.invoice) setHasInvoice(true);
      } catch (_) { void _; }
      setLoading(false);
    };

    fetchOrderData();
  }, [id]);

  const status = order?.status || items[0]?.orderStatus || items[0]?.status || "PLACED";
  const isCancelled = CANCELLED_STATUSES.some((s) =>
    status.toUpperCase() === s.toUpperCase()
  );

  const currentStep = STATUS_STEPS.findIndex(
    (step) => step.key === status.toUpperCase()
  );

  const canCancel =
    !isCancelled &&
    ["PLACED", "CONFIRMED", "PACKED", "PENDING"].includes(status.toUpperCase());

  const handleCancel = async () => {
    if (!(await confirm({ title: t("orderDetail.cancelThisOrder"), message: t("orderDetail.theOrderWillBeCancelled"), confirmLabel: i18n.t("msgs.cancelOrder"), cancelLabel: i18n.t("msgs.keepOrder"), danger: true }))) return;
    setCancelling(true);
    try {
      await api.put(`/api/orders/${id}/cancel`);
      const orderRes = await api.get(`/api/orders/${id}`);
      setOrder(orderRes.data);
    } catch (err) {
      void err;
      setError(t("orderDetail.failedToCancelOrderPlease"));
    }
    setCancelling(false);
  };

  const handleViewInvoice = () => {
    navigate(`/orders/${id}/invoice`);
  };

  const handleDownloadPdf = async () => {
    setPdfDl(true);
    try {
      const res = await api.get(`/api/gst/my-order-invoice/${id}/pdf`, { responseType: "blob" });
      const blob = new Blob([res.data], { type: "application/pdf" });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `invoice-order-${id}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch { setError(t("orderDetail.failedToDownloadInvoicePdf")); }
    setPdfDl(false);
  };

  const safeId = (item, idx) => item.id || item._id || idx;
  const safeProduct = (item) => item.product || {};
  const lineTotal = (item) => {
    const price = item.price || safeProduct(item).price || 0;
    const qty = item.quantity || 1;
    return price * qty;
  };

  const itemsTotal = items.reduce((sum, item) => sum + lineTotal(item), 0) || order?.subTotal || 0;
  const deliveryCharge = order?.deliveryCharge || 0;
  const giftWrapCharge = order?.giftWrapCharge || 0;
  // Older orders had GST added on top of the prices; current ones have it inside.
  const taxOnTop = order && !order.pricesIncludeTax ? (order.tax || 0) : 0;
  // What was actually charged comes from the server. Adding up the item prices alone left out
  // delivery, gift wrap and any coupon, so this page could disagree with the payment.
  const orderTotal = order?.totalAmount ?? order?.total ?? itemsTotal;
  const discount = Math.max(itemsTotal + taxOnTop + deliveryCharge + giftWrapCharge - orderTotal, 0);
  const orderId = order?.orderId || id?.slice(-8) || id;

  const orderItemsList = useMemo(() => items.map((item, idx) => {
    const product = safeProduct(item);
    const imageSrc = product.image || product.images?.[0] || item.image || null;
    const baseName = item.product
      ? (product.name || item.productName || item.name || "Product")
      : i18n.t("msgs.productUnavailable");
    const name = variantLabel(item) ? `${baseName} (${variantLabel(item)})` : baseName;
    const price = item.price || product.price || 0;
    const qty = item.quantity || 1;
    const key = safeId(item, idx);

    return (
      <div
        key={key}
        style={{
          display: "flex", gap: "1rem", alignItems: "center",
          padding: "0.75rem 0", borderBottom: idx < items.length - 1 ? "1px solid var(--color-border-light)" : "none"
        }}
      >
        <div style={{ width: 72, height: 72, flexShrink: 0, borderRadius: "var(--radius-sm)", overflow: "hidden", background: "var(--color-bg-secondary)" }}>
          <CartItemImage src={imageSrc} name={name} width={72} height={72} />
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontWeight: 500, fontSize: "0.95rem" }}>{name}</div>
          {item.variantName && (
            <div style={{ fontSize: "0.8rem", color: "var(--color-text-secondary)" }}>
              {item.variantName}
            </div>
          )}
          <div style={{ fontSize: "0.85rem", color: "var(--color-text-secondary)", marginTop: "0.25rem" }}>{t("checkout.qty")}{" "}{qty}x ₹{price.toFixed(2)}
          </div>
        </div>
        <div style={{ fontWeight: 600, fontSize: "0.95rem" }}>
          &#8377;{lineTotal(item).toFixed(2)}
        </div>
      </div>
    );
  }), [items]);

  if (loading) {
    return (<div style={{ textAlign: "center", padding: "3rem", color: "var(--color-text-secondary)" }}>{t("orderDetail.loadingOrderDetails")}</div>);
  }

  if (!loading && items.length === 0 && !order) {
    return (
      <div style={{ textAlign: "center", padding: "3rem" }}>
        <p style={{ fontSize: "1.1rem", color: "var(--color-text-secondary)", marginBottom: "1rem" }}>{t("orderDetail.orderNotFound")}</p>
        <button
          onClick={() => navigate("/orders")}
          style={{
            padding: "0.5rem 1.25rem", background: "var(--color-primary)", color: "#fff",
            border: "none", borderRadius: "var(--radius-sm)", cursor: "pointer", fontWeight: 600
          }}
        >{t("orderDetail.backToOrders")}</button>
      </div>
    );
  }

  return (
    <div className="order-detail-page" style={{ maxWidth: 800, margin: "0 auto", padding: "1.5rem" }}>
      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", gap: "1rem", marginBottom: "1.5rem" }}>
        <button
          onClick={() => navigate("/orders")}
          style={{
            padding: "0.4rem 0.75rem", border: "1px solid var(--color-border)",
            borderRadius: "var(--radius-sm)", background: "#fff", cursor: "pointer",
            fontSize: "0.9rem", color: "var(--color-text-secondary)"
          }}
        >{t("checkout.back")}</button>
        <h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: 0 }}>{t("orderDetail.orderNo", { id: orderId })}
        </h1>
        <CopyButton text={orderId} label={t("orderDetail.copy")} ariaLabel={i18n.t("msgs.copyOrderNumber")} />
        {isCancelled && (
          <span style={{
            background: "#dc2626", color: "#fff", padding: "3px 10px",
            borderRadius: "var(--radius-full)", fontSize: "0.75rem", fontWeight: 600
          }}>{t("orders.status.cancelled")}</span>
        )}
      </div>

      {error && (
        <div style={{
          background: "#fef2f2", color: "var(--color-error)", padding: "0.75rem 1rem",
          borderRadius: "var(--radius-sm)", marginBottom: "1rem", fontSize: "0.9rem",
          display: "flex", justifyContent: "space-between", alignItems: "center"
        }}>
          <span>{error}</span>
          <button onClick={() => setError("")} style={{
            background: "none", border: "none", color: "var(--color-error)",
            cursor: "pointer", fontSize: "1rem", fontWeight: 700
          }}>x</button>
        </div>
      )}

      {/* Tracking stepper */}
      <div className="tracking-stepper" style={{
        display: "flex", justifyContent: "space-between", alignItems: "flex-start",
        marginBottom: "1.5rem", padding: "1rem 0.5rem",
        border: "1px solid var(--color-border)", borderRadius: "var(--radius-md)",
        background: "var(--color-surface)", position: "relative"
      }}>
        {STATUS_STEPS.map((step, i) => {
          const completed = i <= currentStep && currentStep >= 0;
          const active = i === currentStep;
          const stepCancelled = isCancelled && i > currentStep;

          return (
            <div
              key={step.key}
              className={`step${completed ? " completed" : ""}${active ? " active" : ""}`}
              style={{
                display: "flex", flexDirection: "column", alignItems: "center",
                flex: 1, position: "relative"
              }}
            >
              <div style={{
                width: 36, height: 36, borderRadius: "50%", display: "flex",
                alignItems: "center", justifyContent: "center", fontWeight: 700,
                fontSize: "0.85rem", marginBottom: "0.5rem",
                background: completed
                  ? "var(--color-primary)"
                  : active
                    ? "var(--color-primary)"
                    : stepCancelled
                      ? "#dc2626"
                      : "var(--color-gray-200)",
                color: completed || active ? "#fff" : "var(--color-text-secondary)",
                ...(active && { boxShadow: "0 0 0 4px rgba(22,163,74,0.2)" })
              }}>
                {stepCancelled ? "x" : i + 1}
              </div>
              <div style={{
                fontSize: "0.8rem", fontWeight: active ? 600 : 400,
                color: completed
                  ? "var(--color-primary)"
                  : stepCancelled
                    ? "#dc2626"
                    : "var(--color-text-secondary)",
                textAlign: "center"
              }}>
                {step.label}
              </div>
              {i < STATUS_STEPS.length - 1 && (
                <div style={{
                  position: "absolute", top: 18, left: "60%", width: "80%", height: 3,
                  background: completed ? "var(--color-primary)" : "var(--color-gray-200)",
                  zIndex: 0
                }} />
              )}
            </div>
          );
        })}
      </div>

      {/* Order items */}
      <div className="order-items-section" style={{
        padding: "1.25rem", border: "1px solid var(--color-border)",
        borderRadius: "var(--radius-md)", background: "var(--color-surface)",
        marginBottom: "1.25rem"
      }}>
        <h3 style={{ fontWeight: 600, marginBottom: "1rem", fontSize: "1.05rem" }}>{t("orderDetail.orderItems")}</h3>
        {orderItemsList}
      </div>

      {/* Order summary */}
      <div style={{
        padding: "1.25rem", border: "1px solid var(--color-border)",
        borderRadius: "var(--radius-md)", background: "var(--color-surface)", marginBottom: "1.25rem"
      }}>
        <h3 style={{ fontWeight: 600, marginBottom: "0.75rem", fontSize: "1.05rem" }}>{t("checkout.orderSummary")}</h3>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.4rem", fontSize: "0.9rem" }}>
          <span style={{ color: "var(--color-text-secondary)" }}>{t("orderDetail.items")}</span>
          <span>&#8377;{itemsTotal.toFixed(2)}</span>
        </div>
        {discount >= 0.01 && (
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.4rem", fontSize: "0.9rem" }}>
            <span style={{ color: "var(--color-text-secondary)" }}>{t("cart.discount")}</span>
            <span style={{ color: "#16a34a" }}>-&#8377;{discount.toFixed(2)}</span>
          </div>
        )}
        {taxOnTop > 0 && (
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.4rem", fontSize: "0.9rem" }}>
            <span style={{ color: "var(--color-text-secondary)" }}>GST</span>
            <span>&#8377;{taxOnTop.toFixed(2)}</span>
          </div>
        )}
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.4rem", fontSize: "0.9rem" }}>
          <span style={{ color: "var(--color-text-secondary)" }}>{t("cart.delivery")}</span>
          <span>{deliveryCharge > 0 ? <>&#8377;{deliveryCharge.toFixed(2)}</> : "FREE"}</span>
        </div>
        {giftWrapCharge > 0 && (
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.4rem", fontSize: "0.9rem" }}>
            <span style={{ color: "var(--color-text-secondary)" }}>{t("checkout.giftWrap")}</span>
            <span>&#8377;{giftWrapCharge.toFixed(2)}</span>
          </div>
        )}
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.4rem", fontSize: "0.9rem" }}>
          <span style={{ color: "var(--color-text-secondary)" }}>{t("cart.totalAmount")}</span>
          <strong>&#8377;{orderTotal.toFixed(2)}</strong>
        </div>
        {order?.pricesIncludeTax && (
          <p style={{ fontSize: "0.8rem", color: "var(--color-text-secondary)", margin: "0 0 0.4rem" }}>{t("orderDetail.pricesIncludeGst")}{order.tax ? <> (&#8377;{order.tax.toFixed(2)})</> : null}
          </p>
        )}
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.4rem", fontSize: "0.9rem" }}>
          <span style={{ color: "var(--color-text-secondary)" }}>{t("checkout.paymentMethod")}</span>
          <span>{order?.paymentMethod || t("orderDetail.online")}</span>
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.4rem", fontSize: "0.9rem" }}>
          <span style={{ color: "var(--color-text-secondary)" }}>{t("orderDetail.paymentStatus")}</span>
          <span style={{
            fontWeight: 600,
            color: order?.paymentStatus === "PAID" || order?.paymentStatus === "COMPLETED" ? "#0E5C5C" : "#f59e0b"
          }}>
            {order?.paymentStatus || "PENDING"}
          </span>
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.4rem", fontSize: "0.9rem" }}>
          <span style={{ color: "var(--color-text-secondary)" }}>{t("orderDetail.status")}</span>
          <span style={{
            fontWeight: 600,
            color: isCancelled ? "#dc2626" : currentStep >= STATUS_STEPS.length - 1 ? "#0E5C5C" : "#f59e0b"
          }}>
            {status}
          </span>
        </div>
        {(order?.trackingNumber || order?.courier) && (
          <div style={{ marginTop: "0.5rem", padding: "0.5rem", background: "var(--color-bg-secondary)", borderRadius: "var(--radius-sm)" }}>
            {order?.courier && (
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.85rem", marginBottom: "0.25rem" }}>
                <span style={{ color: "var(--color-text-secondary)" }}>{t("orderDetail.courier")}</span>
                <span style={{ fontWeight: 500 }}>{order.courier}</span>
              </div>
            )}
            {order?.trackingNumber && (
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.85rem" }}>
                <span style={{ color: "var(--color-text-secondary)" }}>{t("orderDetail.tracking")}</span>
                <span style={{ fontWeight: 500 }}>{order.trackingNumber}</span>
              </div>
            )}
            {order?.trackingUrl && (
              <div style={{ textAlign: "right", fontSize: "0.85rem", marginTop: "0.25rem" }}>
                <a href={order.trackingUrl} target="_blank" rel="noopener noreferrer" style={{ color: "var(--color-primary)", fontWeight: 500 }}>{t("orderDetail.trackParcel")}</a>
              </div>
            )}
          </div>
        )}
        {order?.shippingAddress && (
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.9rem" }}>
            <span style={{ color: "var(--color-text-secondary)" }}>{t("orderDetail.shippingTo")}</span>
            <span style={{ textAlign: "right", maxWidth: "60%" }}>
              {[
                order.shippingAddress.name,
                order.shippingAddress.address,
                order.shippingAddress.city,
                order.shippingAddress.state,
                order.shippingAddress.pincode
              ].filter(Boolean).join(", ")}
            </span>
          </div>
        )}
        {order?.createdAt && (
          <div style={{ display: "flex", justifyContent: "space-between", marginTop: "0.4rem", fontSize: "0.9rem" }}>
            <span style={{ color: "var(--color-text-secondary)" }}>{t("orderDetail.orderedOn")}</span>
            <span>{new Date(order.createdAt).toLocaleDateString("en-IN", {
              day: "numeric", month: "short", year: "numeric",
              hour: "2-digit", minute: "2-digit"
            })}</span>
          </div>
        )}
      </div>

      {/* Timeline */}
      {timeline.length > 0 && (
        <div className="timeline-section" style={{
          padding: "1.25rem", border: "1px solid var(--color-border)",
          borderRadius: "var(--radius-md)", background: "var(--color-surface)", marginBottom: "1.25rem"
        }}>
          <h3 style={{ fontWeight: 600, marginBottom: "1rem", fontSize: "1.05rem" }}>{t("orderDetail.orderTimeline")}</h3>
          <div className="timeline" style={{ position: "relative", paddingLeft: "1.5rem" }}>
            <div style={{
              position: "absolute", left: 6, top: 0, bottom: 0, width: 2,
              background: "var(--color-gray-200)"
            }} />
            {timeline.map((event, idx) => (
              <div
                key={idx}
                className="timeline-item"
                style={{
                  position: "relative", marginBottom: idx < timeline.length - 1 ? "1rem" : 0,
                  paddingBottom: idx < timeline.length - 1 ? "1rem" : 0
                }}
              >
                <div style={{
                  position: "absolute", left: "-1.5rem", top: 4,
                  width: 14, height: 14, borderRadius: "50%",
                  background: "var(--color-primary)", border: "2px solid #fff",
                  boxShadow: "var(--shadow-sm)", zIndex: 1
                }} />
                <div style={{ fontSize: "0.8rem", color: "var(--color-text-secondary)", marginBottom: "0.15rem" }}>
                  {new Date(event.date || event.timestamp || event.createdAt).toLocaleString("en-IN", {
                    day: "numeric", month: "short", year: "numeric",
                    hour: "2-digit", minute: "2-digit"
                  })}
                </div>
                <div style={{ fontWeight: 500 }}>
                  {event.status || event.description || event.event || event.title}
                </div>
                {event.comment && (
                  <div style={{ fontSize: "0.85rem", color: "var(--color-text-secondary)", marginTop: "0.15rem" }}>
                    {event.comment}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Action buttons */}
      <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap" }}>
        <button
          onClick={handleViewInvoice}
          className="invoice-btn"
          style={{
            padding: "0.6rem 1.25rem", background: "var(--color-primary)", color: "#fff",
            border: "none", borderRadius: "var(--radius-sm)", fontSize: "0.9rem",
            fontWeight: 600, cursor: "pointer"
          }}
        >
          {hasInvoice ? t("orderDetail.viewGstInvoice") : t("orderDetail.checkInvoice")}
        </button>
        {hasInvoice && (
          <button
            onClick={handleDownloadPdf}
            disabled={pdfDl}
            style={{
              padding: "0.6rem 1.25rem", background: "transparent",
              color: "var(--color-primary)", border: "1px solid var(--color-primary)",
              borderRadius: "var(--radius-sm)", fontSize: "0.9rem", fontWeight: 600,
              cursor: pdfDl ? "not-allowed" : "pointer", opacity: pdfDl ? 0.7 : 1
            }}
          >
            {pdfDl ? t("orderDetail.downloading") : t("orderDetail.downloadInvoicePdf")}
          </button>
        )}
        {canCancel && (
          <button
            onClick={handleCancel}
            disabled={cancelling}
            style={{
              padding: "0.6rem 1.25rem", background: "transparent",
              color: "var(--color-error)", border: "1px solid var(--color-error)",
              borderRadius: "var(--radius-sm)", fontSize: "0.9rem", fontWeight: 600,
              cursor: cancelling ? "not-allowed" : "pointer", opacity: cancelling ? 0.7 : 1
            }}
          >
            {cancelling ? t("orderDetail.cancelling") : t("orderDetail.cancelOrder")}
          </button>
        )}
        {status === "DELIVERED" && (
          <button
            onClick={() => navigate(`/orders/${id}/return`)}
            style={{
              padding: "0.6rem 1.25rem", background: "transparent",
              color: "#f59e0b", border: "1px solid #f59e0b",
              borderRadius: "var(--radius-sm)", fontSize: "0.9rem", fontWeight: 600,
              cursor: "pointer"
            }}
          >{t("orderDetail.returnReplace")}</button>
        )}
        <button
          onClick={() => navigate("/orders")}
          style={{
            padding: "0.6rem 1.25rem", background: "transparent",
            color: "var(--color-text-secondary)", border: "1px solid var(--color-border)",
            borderRadius: "var(--radius-sm)", fontSize: "0.9rem", fontWeight: 500,
            cursor: "pointer", marginLeft: "auto"
          }}
        >{t("orderDetail.backToOrders")}</button>
      </div>
    </div>
  );
};

export default OrderDetail;
