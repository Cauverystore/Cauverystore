import React, { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { getMyOrders } from "../services/orderService";
import api from "../api/axios";
import "../styles/orders.css";

const Orders = () => {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState("");
  const [selected, setSelected] = useState([]);
  const navigate = useNavigate();

  useEffect(() => {
    const fetch = async () => {
      try {
        const res = await getMyOrders({ params: { status: statusFilter } });
        setOrders(res.data.content || res.data || []);
      } catch (err) {             void err; }
      setLoading(false);
    };
    fetch();
  }, [statusFilter]);

  const toggleSelect = (id) => setSelected(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]);

  const handleBulkCancel = async () => {
    try {
      await api.post("/api/orders/bulk-cancel", { ids: selected });
      setSelected([]);
      const res = await getMyOrders();
      setOrders(res.data.content || res.data || []);
    } catch (err) { alert("Failed to cancel orders"); }
  };

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;

  return (
    <div className="orders-page">
      <h1>My Orders</h1>
      <div className="orders-filters">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">All Orders</option>
          <option value="PENDING">Pending</option><option value="PROCESSING">Processing</option><option value="SHIPPED">Shipped</option><option value="DELIVERED">Delivered</option><option value="CANCELLED">Cancelled</option>
        </select>
        {selected.length > 0 && <div className="bulk-actions"><button onClick={handleBulkCancel}>Cancel Selected ({selected.length})</button></div>}
      </div>

      {orders.length === 0 ? <div className="empty-orders">No orders found</div> : orders.map((o) => (
        <div key={o.id || o._id} className="order-card" onClick={() => navigate(`/orders/${o.id || o._id}`)}>
          <input type="checkbox" checked={selected.includes(o.id || o._id)} onChange={(e) => { e.stopPropagation(); toggleSelect(o.id || o._id); }} style={{ marginRight: "0.5rem" }} />
          <div className="order-info">
            <div className="order-id">Order #{o.orderId || (o.id || o._id)?.toString().slice(-8)}</div>
            <div className="order-date">{new Date(o.createdAt).toLocaleDateString()}</div>
          </div>
          <div className="order-amount">&#8377;{(o.totalAmount || o.total || 0).toFixed(2)}</div>
          <span className={`order-status status-${(o.status || "").toLowerCase()}`}>{o.status}</span>
        </div>
      ))}
    </div>
  );
};
export default Orders;
