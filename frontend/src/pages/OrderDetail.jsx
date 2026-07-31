import React, { useState, useEffect, useMemo } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import api from "../api/axios";
import CartItemImage from "../components/CartItemImage";
import "../styles/orderDetail.css";

const STATUS_STEPS = [
  { key: "PLACED", label: "Placed" },
  { key: "CONFIRMED", label: "Confirmed" },
  { key: "PACKED", label: "Packed" },
  { key: "SHIPPED", label: "Shipped" },
  { key: "DELIVERED", label: "Delivered" }
];

const CANCELLED_STATUSES = ["CANCELLED", "CANCELED", "RETURNED"];

const OrderDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();

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
        const [itemsRes, timelineRes] = await Promise.allSettled([
          api.get(`/api/orders/${id}/items`),
          api.get(`/api/orders/${id}/timeline`)
        ]);

        if (itemsRes.status === "fulfilled") {
          const data = itemsRes.value.data;
          const isArray = Array.isArray(data);
          if (isArray) {
            setItems(data);
          } else {
            setItems(data?.items || data?.orderItems || []);
            setOrder(data?.order || data);
          }
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
        setError("Failed to load order details.");
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
    (status.toUpperCase() === "PLACED" || status.toUpperCase() === "CONFIRMED" || status.toUpperCase() === "PENDING");

  const handleCancel = async () => {
    if (!window.confirm("Are you sure you want to cancel this order?")) return;
    setCancelling(true);
    try {
      await api.put(`/api/orders/${id}/cancel`);
      setOrder((prev) => ({ ...prev, status: "CANCELLED" }));
      const itemsRes = await api.get(`/api/orders/${id}/items`);
      const data = itemsRes.data;
      if (Array.isArray(data)) {
        setItems(data);
      } else {
        setItems(data?.items || data?.orderItems || []);
        setOrder((prev) => ({ ...prev, ...(data?.order || data) }));
      }
    } catch (err) {
      void err;
      setError("Failed to cancel order. Please try again.");
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
    } catch { setError("Failed to download invoice PDF."); }
    setPdfDl(false);
  };

  const safeId = (item, idx) => item.id || item._id || idx;
  const safeProduct = (item) => item.product || {};
  const lineTotal = (item) => {
    const price = item.price || safeProduct(item).price || 0;
    const qty = item.quantity || 1;
    return price * qty;
  };

  const orderTotal = items.reduce((sum, item) => sum + lineTotal(item), 0) || order?.totalAmount || order?.total || 0;
  const orderId = order?.orderId || id?.slice(-8) || id;

  const orderItemsList = useMemo(() => items.map((item, idx) => {
    const product = safeProduct(item);
    const imageSrc = product.image || product.images?.[0] || item.image || null;
    const name = item.product
      ? (product.name || item.productName || item.name || "Product")
      : "Product is no longer available";
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
          <div style={{ fontSize: "0.85rem", color: "var(--color-text-secondary)", marginTop: "0.25rem" }}>
            Qty: {qty} x &#8377;{price.toFixed(2)}
          </div>
        </div>
        <div style={{ fontWeight: 600, fontSize: "0.95rem" }}>
          &#8377;{lineTotal(item).toFixed(2)}
        </div>
      </div>
    );
  }), [items]);

  if (loading) {
    return (
      <div style={{ textAlign: "center", padding: "3rem", color: "var(--color-text-secondary)" }}>
        Loading order details...
      </div>
    );
  }

  if (!loading && items.length === 0 && !order) {
    return (
      <div style={{ textAlign: "center", padding: "3rem" }}>
        <p style={{ fontSize: "1.1rem", color: "var(--color-text-secondary)", marginBottom: "1rem" }}>
          Order not found
        </p>
        <button
          onClick={() => navigate("/orders")}
          style={{
            padding: "0.5rem 1.25rem", background: "var(--color-primary)", color: "#fff",
            border: "none", borderRadius: "var(--radius-sm)", cursor: "pointer", fontWeight: 600
          }}
        >
          Back to Orders
        </button>
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
        >
          &larr; Back
        </button>
        <h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: 0 }}>
          Order #{orderId}
        </h1>
        {isCancelled && (
          <span style={{
            background: "#dc2626", color: "#fff", padding: "3px 10px",
            borderRadius: "var(--radius-full)", fontSize: "0.75rem", fontWeight: 600
          }}>
            Cancelled
          </span>
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
        <h3 style={{ fontWeight: 600, marginBottom: "1rem", fontSize: "1.05rem" }}>Order Items</h3>
        {orderItemsList}
      </div>

      {/* Order summary */}
      <div style={{
        padding: "1.25rem", border: "1px solid var(--color-border)",
        borderRadius: "var(--radius-md)", background: "var(--color-surface)", marginBottom: "1.25rem"
      }}>
        <h3 style={{ fontWeight: 600, marginBottom: "0.75rem", fontSize: "1.05rem" }}>Order Summary</h3>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.4rem", fontSize: "0.9rem" }}>
          <span style={{ color: "var(--color-text-secondary)" }}>Total Amount</span>
          <strong>&#8377;{orderTotal.toFixed(2)}</strong>
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.4rem", fontSize: "0.9rem" }}>
          <span style={{ color: "var(--color-text-secondary)" }}>Payment Method</span>
          <span>{order?.paymentMethod || "COD"}</span>
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.4rem", fontSize: "0.9rem" }}>
          <span style={{ color: "var(--color-text-secondary)" }}>Payment Status</span>
          <span style={{
            fontWeight: 600,
            color: order?.paymentStatus === "PAID" || order?.paymentStatus === "COMPLETED" ? "#16a34a" : "#f59e0b"
          }}>
            {order?.paymentStatus || "PENDING"}
          </span>
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.4rem", fontSize: "0.9rem" }}>
          <span style={{ color: "var(--color-text-secondary)" }}>Status</span>
          <span style={{
            fontWeight: 600,
            color: isCancelled ? "#dc2626" : currentStep >= STATUS_STEPS.length - 1 ? "#16a34a" : "#f59e0b"
          }}>
            {status}
          </span>
        </div>
        {(order?.trackingNumber || order?.courier) && (
          <div style={{ marginTop: "0.5rem", padding: "0.5rem", background: "var(--color-bg-secondary)", borderRadius: "var(--radius-sm)" }}>
            {order?.courier && (
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.85rem", marginBottom: "0.25rem" }}>
                <span style={{ color: "var(--color-text-secondary)" }}>Courier</span>
                <span style={{ fontWeight: 500 }}>{order.courier}</span>
              </div>
            )}
            {order?.trackingNumber && (
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.85rem" }}>
                <span style={{ color: "var(--color-text-secondary)" }}>Tracking #</span>
                <span style={{ fontWeight: 500 }}>{order.trackingNumber}</span>
              </div>
            )}
          </div>
        )}
        {order?.shippingAddress && (
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.9rem" }}>
            <span style={{ color: "var(--color-text-secondary)" }}>Shipping To</span>
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
            <span style={{ color: "var(--color-text-secondary)" }}>Ordered On</span>
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
          <h3 style={{ fontWeight: 600, marginBottom: "1rem", fontSize: "1.05rem" }}>Order Timeline</h3>
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
          {hasInvoice ? "View GST Invoice" : "Check Invoice"}
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
            {pdfDl ? "Downloading..." : "Download Invoice PDF"}
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
            {cancelling ? "Cancelling..." : "Cancel Order"}
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
          >
            Return / Replace
          </button>
        )}
        <button
          onClick={() => navigate("/orders")}
          style={{
            padding: "0.6rem 1.25rem", background: "transparent",
            color: "var(--color-text-secondary)", border: "1px solid var(--color-border)",
            borderRadius: "var(--radius-sm)", fontSize: "0.9rem", fontWeight: 500,
            cursor: "pointer", marginLeft: "auto"
          }}
        >
          Back to Orders
        </button>
      </div>
    </div>
  );
};

export default OrderDetail;
