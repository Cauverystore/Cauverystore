import React from "react";

const AdminOrderListItem = ({ order, onStatusChange }) => (
  <tr>
    <td style={{ padding: "0.5rem 0.75rem", borderBottom: "1px solid #e2e8f0" }}>{order.orderId || (order.id || order._id)?.toString().slice(-8)}</td>
    <td style={{ padding: "0.5rem 0.75rem", borderBottom: "1px solid #e2e8f0" }}>{order.user?.name || order.shippingAddress?.name || "N/A"}</td>
    <td style={{ padding: "0.5rem 0.75rem", borderBottom: "1px solid #e2e8f0" }}>&#8377;{(order.totalAmount || 0).toFixed(2)}</td>
    <td style={{ padding: "0.5rem 0.75rem", borderBottom: "1px solid #e2e8f0" }}>
      <span style={{ padding: "0.2rem 0.5rem", borderRadius: 999, fontSize: "0.75rem", fontWeight: 500, background: order.status === "DELIVERED" ? "#dcfce7" : "#fef3c7", color: order.status === "DELIVERED" ? "#166534" : "#92400e" }}>{order.status}</span>
    </td>
    <td style={{ padding: "0.5rem 0.75rem", borderBottom: "1px solid #e2e8f0" }}>{new Date(order.createdAt).toLocaleDateString()}</td>
    <td style={{ padding: "0.5rem 0.75rem", borderBottom: "1px solid #e2e8f0" }}>
      <select value={order.status} onChange={(e) => onStatusChange(order.id || order._id, e.target.value)} style={{ padding: "0.2rem", fontSize: "0.8rem" }}>
        <option value="PENDING">Pending</option><option value="PROCESSING">Processing</option><option value="SHIPPED">Shipped</option><option value="DELIVERED">Delivered</option><option value="CANCELLED">Cancelled</option>
      </select>
    </td>
  </tr>
);
export default AdminOrderListItem;
