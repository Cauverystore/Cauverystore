import React, { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api/axios";
import CartItemImage from "../components/CartItemImage";
import "../styles/orders.css";

const STATUSES = ["ALL", "PLACED", "SHIPPED", "DELIVERED", "CANCELLED"];
const STATUS_LABELS = { ALL: "All Orders", PLACED: "Placed", SHIPPED: "Shipped", DELIVERED: "Delivered", CANCELLED: "Cancelled" };
const STATUS_COLORS = { PLACED: "#f59e0b", SHIPPED: "#3b82f6", DELIVERED: "#16a34a", CANCELLED: "#dc2626" };
const STATUS_STEPS = ["PLACED", "SHIPPED", "DELIVERED"];

const STATUS_ORDER = { PLACED: 0, SHIPPED: 1, DELIVERED: 2, CANCELLED: -1 };

function formatDate(dateStr) {
  if (!dateStr) return "";
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    return d.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
  } catch { return dateStr; }
}

function formatTime(dateStr) {
  if (!dateStr) return "";
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return "";
    return d.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" });
  } catch { return ""; }
}

function StatusTimeline({ status }) {
  const currentIdx = STATUS_ORDER[status];
  if (currentIdx < 0) return null;

  return (
    <div className="order-timeline">
      {STATUS_STEPS.map((step, i) => {
        const completed = i <= currentIdx;
        return (
          <div key={step} className={`order-timeline-step ${completed ? "completed" : ""} ${i === currentIdx ? "active" : ""}`}>
            <div className="order-timeline-dot" />
            {i < STATUS_STEPS.length - 1 && <div className={`order-timeline-bar ${i < currentIdx ? "filled" : ""}`} />}
            <span className="order-timeline-label">{step.charAt(0) + step.slice(1).toLowerCase()}</span>
          </div>
        );
      })}
    </div>
  );
}

const Orders = () => {
  const navigate = useNavigate();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [sortBy, setSortBy] = useState("newest");
  const [cancellingId, setCancellingId] = useState(null);
  const [error, setError] = useState("");

  const fetchOrders = async () => {
    setLoading(true);
    try {
      const params = {};
      if (statusFilter !== "ALL") params.status = statusFilter;
      const res = await api.get("/api/orders", { params });
      setOrders(res.data || []);
    } catch { setOrders([]); }
    setLoading(false);
  };

  useEffect(() => { fetchOrders(); }, [statusFilter]);

  const CANCELLABLE_STATUSES = ["PLACED", "CONFIRMED", "PACKED", "PENDING"];
  const canCancelOrder = (order) => CANCELLABLE_STATUSES.includes((order.status || "").toUpperCase());

  const handleCancel = async (orderId) => {
    if (!window.confirm("Are you sure you want to cancel this order?")) return;
    setCancellingId(orderId);
    setError("");
    try {
      await api.put(`/api/orders/${orderId}/cancel`);
      await fetchOrders();
    } catch (err) {
      setError(err.response?.data?.error || "Failed to cancel order. Please try again.");
    }
    setCancellingId(null);
  };

  const sorted = useMemo(() => {
    let list = [...orders];
    if (sortBy === "newest") {
      list.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
    } else if (sortBy === "oldest") {
      list.sort((a, b) => new Date(a.createdAt || 0) - new Date(b.createdAt || 0));
    } else if (sortBy === "price-desc") {
      list.sort((a, b) => (b.totalAmount || 0) - (a.totalAmount || 0));
    } else if (sortBy === "price-asc") {
      list.sort((a, b) => (a.totalAmount || 0) - (b.totalAmount || 0));
    }
    return list;
  }, [orders, sortBy]);

  const firstItem = (order) => {
    if (!order.items || order.items.length === 0) return null;
    return order.items[0];
  };

  if (loading) {
    return (
      <div className="orders-page">
        <div className="orders-loading">Loading your orders...</div>
      </div>
    );
  }

  return (
    <div className="orders-page">
      <div className="orders-header">
        <h1 className="orders-title">My Orders</h1>
        <span className="orders-count">{orders.length} order{orders.length !== 1 ? "s" : ""}</span>
      </div>

      {error && (
        <div style={{ marginBottom: "1rem", padding: "0.75rem 1rem", background: "#fef2f2", borderRadius: 8, color: "#dc2626", fontSize: "0.9rem", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <span>{error}</span>
          <button onClick={() => setError("")} style={{ background: "none", border: "none", color: "#dc2626", cursor: "pointer", fontWeight: 700 }}>&times;</button>
        </div>
      )}

      <div className="orders-toolbar">
        <div className="orders-filter-group">
          <label className="orders-filter-label">Status</label>
          <select className="orders-select" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>{STATUS_LABELS[s]}</option>
            ))}
          </select>
        </div>
        <div className="orders-filter-group">
          <label className="orders-filter-label">Sort by</label>
          <select className="orders-select" value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
            <option value="newest">Newest First</option>
            <option value="oldest">Oldest First</option>
            <option value="price-desc">Price: High to Low</option>
            <option value="price-asc">Price: Low to High</option>
          </select>
        </div>
      </div>

      {sorted.length === 0 ? (
        <div className="orders-empty">
          <div className="orders-empty-icon">&#128230;</div>
          <h2 className="orders-empty-title">You have no orders yet</h2>
          <p className="orders-empty-text">Start shopping now! Browse our wide selection of products and find something you love.</p>
          <button className="orders-empty-btn" onClick={() => navigate("/products")}>Start Shopping</button>
        </div>
      ) : (
        <div className="orders-list">
          {sorted.map((order) => {
            const oid = order.id || order._id;
            const items = order.items || [];
            const first = firstItem(order);

            return (
              <div key={oid} className="order-card">
                <div className="order-card-header">
                  <div className="order-card-id-group">
                    <span className="order-card-id">Order #{oid?.toString().slice(-8)}</span>
                    <span className="order-card-date">
                      {formatDate(order.createdAt)}
                      {order.createdAt && <span className="order-card-time">{formatTime(order.createdAt)}</span>}
                    </span>
                  </div>
                  <span className={`order-card-badge status-${(order.status || "").toLowerCase()}`}>
                    {order.status || "PLACED"}
                  </span>
                </div>

                <div className="order-card-body">
                  {items.length > 0 ? (
                    <div className="order-card-items">
                      {items.slice(0, 3).map((item) => (
                        <div key={item.id || item.productId} className="order-card-item">
                          <div className="order-card-item-img">
                            <CartItemImage src={item.productImage || ""} name={item.productName} width={64} height={64} />
                          </div>
                          <div className="order-card-item-info">
                            <div className="order-card-item-name">{item.productName || "Product"}</div>
                            <div className="order-card-item-meta">
                              Qty: {item.quantity || 1} x &#8377;{(item.price || 0).toFixed(2)}
                            </div>
                          </div>
                          <div className="order-card-item-total">
                            &#8377;{((item.price || 0) * (item.quantity || 1)).toFixed(2)}
                          </div>
                        </div>
                      ))}
                      {items.length > 3 && (
                        <div className="order-card-more">+{items.length - 3} more item{items.length - 3 > 1 ? "s" : ""}</div>
                      )}
                    </div>
                  ) : (
                    <div className="order-card-no-items">No items</div>
                  )}
                </div>

                <div className="order-card-footer">
                  <div className="order-card-total">
                    <span className="order-card-total-label">Total: </span>
                    <span className="order-card-total-value">&#8377;{(order.totalAmount || 0).toFixed(2)}</span>
                  </div>

                  {(order.status === "PLACED" || order.status === "SHIPPED" || order.status === "DELIVERED") && (
                    <StatusTimeline status={order.status} />
                  )}

                  <div style={{ display: "flex", gap: "0.5rem" }}>
                    {canCancelOrder(order) && (
                      <button
                        className="order-card-btn"
                        style={{ background: "#fef2f2", color: "#dc2626", borderColor: "#fecaca" }}
                        disabled={cancellingId === oid}
                        onClick={() => handleCancel(oid)}
                      >
                        {cancellingId === oid ? "Cancelling..." : "Cancel Order"}
                      </button>
                    )}
                    <button className="order-card-btn" onClick={() => navigate(`/orders/${oid}`)}>
                      View Details
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      <style>{`
        .orders-loading { text-align: center; padding: 3rem; color: #64748b; }
        .orders-header { display: flex; align-items: baseline; gap: 0.75rem; margin-bottom: 1.25rem; }
        .orders-title { font-size: 1.5rem; font-weight: 700; color: #0f172a; margin: 0; }
        .orders-count { font-size: 0.85rem; color: #64748b; }
        .orders-toolbar { display: flex; gap: 1rem; margin-bottom: 1.5rem; flex-wrap: wrap; }
        .orders-filter-group { display: flex; align-items: center; gap: 0.5rem; }
        .orders-filter-label { font-size: 0.82rem; font-weight: 600; color: #475569; text-transform: uppercase; letter-spacing: 0.3px; }
        .orders-select {
          padding: 0.45rem 0.75rem; border: 1px solid #d1d5db; border-radius: 6px;
          font-size: 0.85rem; background: #fff; color: #1e293b; cursor: pointer;
          outline: none; min-width: 140px;
        }
        .orders-select:focus { border-color: #16a34a; box-shadow: 0 0 0 2px rgba(22,163,74,0.15); }

        .orders-empty { text-align: center; padding: 4rem 1rem; }
        .orders-empty-icon { font-size: 4rem; margin-bottom: 1rem; opacity: 0.4; }
        .orders-empty-title { font-size: 1.3rem; font-weight: 700; color: #0f172a; margin-bottom: 0.5rem; }
        .orders-empty-text { color: #64748b; max-width: 400px; margin: 0 auto 1.5rem; line-height: 1.5; }
        .orders-empty-btn {
          padding: 0.75rem 2rem; background: #16a34a; color: #fff; border: none;
          border-radius: 8px; font-size: 1rem; font-weight: 600; cursor: pointer;
          transition: background 0.2s;
        }
        .orders-empty-btn:hover { background: #15803d; }

        .orders-list { display: flex; flex-direction: column; gap: 1rem; }

        .order-card {
          background: #fff; border: 1px solid #e2e8f0; border-radius: 12px;
          overflow: hidden; transition: box-shadow 0.2s;
        }
        .order-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.06); }

        .order-card-header {
          display: flex; align-items: center; justify-content: space-between;
          padding: 0.9rem 1.25rem; background: #f8fafc;
          border-bottom: 1px solid #e2e8f0;
        }
        .order-card-id-group { display: flex; align-items: baseline; gap: 0.75rem; }
        .order-card-id { font-weight: 700; font-size: 0.9rem; color: #0f172a; }
        .order-card-date { font-size: 0.8rem; color: #64748b; display: flex; align-items: center; gap: 0.3rem; }
        .order-card-time { color: #94a3b8; font-size: 0.75rem; }

        .order-card-badge {
          display: inline-block; padding: 3px 10px; border-radius: 999px;
          font-size: 0.75rem; font-weight: 700; text-transform: uppercase;
          letter-spacing: 0.3px;
        }
        .status-placed { background: #fef3c7; color: #92400e; }
        .status-shipped { background: #dbeafe; color: #1e40af; }
        .status-delivered { background: #dcfce7; color: #166534; }
        .status-cancelled { background: #fef2f2; color: #991b1b; }
        .status-pending { background: #fef3c7; color: #92400e; }
        .status-processing { background: #fef3c7; color: #92400e; }
        .status-refunded { background: #f3e8ff; color: #6b21a8; }

        .order-card-body { padding: 0.75rem 1.25rem; }
        .order-card-items { display: flex; flex-direction: column; gap: 0.5rem; }
        .order-card-item { display: flex; align-items: center; gap: 0.75rem; }
        .order-card-item-img { width: 64px; height: 64px; flex-shrink: 0; border-radius: 8px; overflow: hidden; background: #f1f5f9; border: 1px solid #e2e8f0; }
        .order-card-item-img img { width: 100%; height: 100%; object-fit: contain; }
        .order-card-item-info { flex: 1; min-width: 0; }
        .order-card-item-name { font-size: 0.88rem; font-weight: 500; color: #0f172a; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .order-card-item-meta { font-size: 0.78rem; color: #64748b; margin-top: 0.15rem; }
        .order-card-item-total { font-size: 0.85rem; font-weight: 600; color: #0f172a; white-space: nowrap; }
        .order-card-more { font-size: 0.8rem; color: #16a34a; font-weight: 500; padding-left: calc(64px + 0.75rem); }

        .order-card-footer {
          display: flex; align-items: center; justify-content: space-between;
          padding: 0.75rem 1.25rem; border-top: 1px solid #e2e8f0;
          flex-wrap: wrap; gap: 0.75rem;
        }
        .order-card-total { font-size: 0.9rem; }
        .order-card-total-label { color: #64748b; }
        .order-card-total-value { font-weight: 700; color: #0f172a; font-size: 1rem; }

        .order-card-btn {
          padding: 0.45rem 1.1rem; background: #16a34a; color: #fff; border: none;
          border-radius: 6px; font-size: 0.82rem; font-weight: 600; cursor: pointer;
          transition: background 0.2s; white-space: nowrap;
        }
        .order-card-btn:hover { background: #15803d; }

        .order-timeline { display: flex; align-items: center; gap: 0; flex: 1; max-width: 300px; }
        .order-timeline-step {
          display: flex; align-items: center; flex: 1; position: relative;
        }
        .order-timeline-dot {
          width: 10px; height: 10px; border-radius: 50%;
          background: #d1d5db; flex-shrink: 0; z-index: 1;
        }
        .order-timeline-step.completed .order-timeline-dot { background: #16a34a; }
        .order-timeline-step.active .order-timeline-dot {
          background: #16a34a; box-shadow: 0 0 0 3px rgba(22,163,74,0.2);
        }
        .order-timeline-bar {
          flex: 1; height: 2px; background: #e2e8f0; margin: 0 2px;
        }
        .order-timeline-bar.filled { background: #16a34a; }
        .order-timeline-label {
          display: none; position: absolute; top: 14px; left: 50%; transform: translateX(-50%);
          font-size: 0.65rem; color: #64748b; white-space: nowrap;
        }
        .order-timeline-step.active .order-timeline-label { display: block; font-weight: 600; color: #16a34a; }

        .order-card-no-items { font-size: 0.85rem; color: #94a3b8; padding: 0.5rem 0; }

        @media (max-width: 640px) {
          .order-card-header { flex-direction: column; align-items: flex-start; gap: 0.5rem; }
          .order-card-footer { flex-direction: column; align-items: stretch; }
          .order-card-btn { width: 100%; text-align: center; }
          .order-timeline { max-width: 100%; }
        }
      `}</style>
    </div>
  );
};

export default Orders;
