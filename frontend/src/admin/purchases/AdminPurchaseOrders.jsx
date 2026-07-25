import React, { useState, useEffect } from "react";
import api from "../../api/axios";

const statuses = ["PENDING", "CONFIRMED", "SHIPPED", "RECEIVED", "CANCELLED"];

const statusColors = {
  PENDING: { bg: "#FEF3C7", color: "#D97706" },
  CONFIRMED: { bg: "#DBEAFE", color: "#2563EB" },
  SHIPPED: { bg: "#E0E7FF", color: "#4F46E5" },
  RECEIVED: { bg: "#E6F7EC", color: "#2E9B57" },
  CANCELLED: { bg: "#FEE2E2", color: "#dc2626" },
};

const styles = {
  container: { padding: "24px" },
  header: { fontSize: "28px", fontWeight: "700", color: "#0B3D2E", marginBottom: "24px" },
  formCard: { background: "#fff", border: "1px solid #CFE8D6", borderRadius: "8px", padding: "20px", marginBottom: "24px" },
  formTitle: { fontSize: "18px", fontWeight: "600", color: "#0B3D2E", marginBottom: "16px" },
  formRow: { display: "flex", gap: "16px", marginBottom: "12px", flexWrap: "wrap", alignItems: "center" },
  input: { flex: "1 1 200px", padding: "10px 12px", border: "1px solid #CFE8D6", borderRadius: "6px", fontSize: "14px", outline: "none" },
  select: { flex: "1 1 200px", padding: "10px 12px", border: "1px solid #CFE8D6", borderRadius: "6px", fontSize: "14px", outline: "none", background: "#fff" },
  textarea: { flex: "1 1 100%", padding: "10px 12px", border: "1px solid #CFE8D6", borderRadius: "6px", fontSize: "14px", outline: "none", resize: "vertical", minHeight: "60px" },
  btnPrimary: { background: "#2E9B57", color: "#fff", border: "none", padding: "10px 24px", borderRadius: "6px", fontSize: "14px", fontWeight: "600", cursor: "pointer" },
  btnSecondary: { background: "#CFE8D6", color: "#0B3D2E", border: "none", padding: "10px 24px", borderRadius: "6px", fontSize: "14px", fontWeight: "600", cursor: "pointer", marginLeft: "8px" },
  btnAddItem: { background: "#0B3D2E", color: "#fff", border: "none", padding: "8px 16px", borderRadius: "6px", fontSize: "13px", fontWeight: "600", cursor: "pointer" },
  btnRemoveItem: { background: "#dc2626", color: "#fff", border: "none", padding: "4px 10px", borderRadius: "4px", fontSize: "12px", fontWeight: "600", cursor: "pointer" },
  table: { width: "100%", borderCollapse: "collapse", background: "#fff", border: "1px solid #CFE8D6", borderRadius: "8px", overflow: "hidden" },
  th: { textAlign: "left", padding: "12px 16px", background: "#0B3D2E", color: "#fff", fontSize: "13px", fontWeight: "600", textTransform: "uppercase", letterSpacing: "0.5px" },
  td: { padding: "12px 16px", borderBottom: "1px solid #CFE8D6", fontSize: "14px", color: "#333" },
  statusBadge: (status) => {
    const c = statusColors[status] || { bg: "#E5E7EB", color: "#374151" };
    return { background: c.bg, color: c.color, padding: "4px 10px", borderRadius: "12px", fontSize: "12px", fontWeight: "600", cursor: "pointer", display: "inline-block" };
  },
  loading: { textAlign: "center", padding: "40px", color: "#666" },
  successMsg: { background: "#E6F7EC", color: "#2E9B57", padding: "12px 16px", borderRadius: "6px", marginBottom: "16px", fontSize: "14px" },
  errorMsg: { background: "#FEE2E2", color: "#dc2626", padding: "12px 16px", borderRadius: "6px", marginBottom: "16px", fontSize: "14px" },
  itemRow: { display: "flex", gap: "8px", marginBottom: "8px", flexWrap: "wrap", alignItems: "center" },
};

export default function AdminPurchaseOrders() {
  const [orders, setOrders] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [products, setProducts] = useState([]);
  const [form, setForm] = useState({
    poNumber: "", supplierId: "", warehouseId: "", status: "PENDING",
    orderDate: "", expectedDelivery: "", notes: "", items: [{ productId: "", quantity: "", unitPrice: "" }],
  });
  const [editId, setEditId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchAll();
  }, []);

  const fetchAll = async () => {
    try {
      setLoading(true);
      const [ordersRes, suppliersRes, warehousesRes, productsRes] = await Promise.all([
        api.get("/api/admin/purchase-orders"),
        api.get("/api/admin/suppliers"),
        api.get("/api/admin/warehouses"),
        api.get("/api/admin/products/all"),
      ]);
      setOrders(ordersRes.data.purchaseOrders || ordersRes.data || []);
      setSuppliers(suppliersRes.data.suppliers || suppliersRes.data || []);
      setWarehouses(warehousesRes.data.warehouses || warehousesRes.data || []);
      setProducts(productsRes.data.products || productsRes.data || []);
      setError(null);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to fetch data");
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleItemChange = (index, field, value) => {
    const items = [...form.items];
    items[index] = { ...items[index], [field]: value };
    setForm({ ...form, items });
  };

  const addItem = () => {
    setForm({ ...form, items: [...form.items, { productId: "", quantity: "", unitPrice: "" }] });
  };

  const removeItem = (index) => {
    const items = form.items.filter((_, i) => i !== index);
    setForm({ ...form, items });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      setError(null);
      setMessage(null);
      const payload = {
        ...form,
        items: form.items.map((item) => ({
          productId: item.productId,
          quantity: Number(item.quantity),
          unitPrice: Number(item.unitPrice),
        })),
      };
      if (editId) {
        await api.put(`/api/admin/purchase-orders/${editId}`, payload);
        setMessage("Purchase order updated successfully");
      } else {
        await api.post("/api/admin/purchase-orders", payload);
        setMessage("Purchase order created successfully");
      }
      setForm({
        poNumber: "", supplierId: "", warehouseId: "", status: "PENDING",
        orderDate: "", expectedDelivery: "", notes: "", items: [{ productId: "", quantity: "", unitPrice: "" }],
      });
      setEditId(null);
      fetchAll();
    } catch (err) {
      setError(err.response?.data?.message || "Operation failed");
    }
  };

  const handleEdit = (po) => {
    setEditId(po._id || po.id);
    setForm({
      poNumber: po.poNumber || "",
      supplierId: po.supplierId?._id || po.supplierId?.id || po.supplierId || "",
      warehouseId: po.warehouseId?._id || po.warehouseId?.id || po.warehouseId || "",
      status: po.status || "PENDING",
      orderDate: po.orderDate ? po.orderDate.slice(0, 10) : "",
      expectedDelivery: po.expectedDelivery ? po.expectedDelivery.slice(0, 10) : "",
      notes: po.notes || "",
      items: po.items && po.items.length > 0
        ? po.items.map((item) => ({
            productId: item.productId?._id || item.productId?.id || item.productId || "",
            quantity: item.quantity || "",
            unitPrice: item.unitPrice || "",
          }))
        : [{ productId: "", quantity: "", unitPrice: "" }],
    });
  };

  const handleCancelEdit = () => {
    setEditId(null);
    setForm({
      poNumber: "", supplierId: "", warehouseId: "", status: "PENDING",
      orderDate: "", expectedDelivery: "", notes: "", items: [{ productId: "", quantity: "", unitPrice: "" }],
    });
  };

  const cycleStatus = async (po) => {
    const id = po._id || po.id;
    const currentIdx = statuses.indexOf(po.status);
    const nextStatus = statuses[(currentIdx + 1) % statuses.length];
    try {
      setError(null);
      await api.put(`/api/admin/purchase-orders/${id}`, { ...po, status: nextStatus });
      setMessage(`Status changed to ${nextStatus}`);
      fetchAll();
    } catch (err) {
      setError(err.response?.data?.message || "Failed to update status");
    }
  };

  const orderList = Array.isArray(orders) ? orders : [];
  const supplierList = Array.isArray(suppliers) ? suppliers : [];
  const warehouseList = Array.isArray(warehouses) ? warehouses : [];
  const productList = Array.isArray(products) ? products : [];

  const getTotal = (items) => {
    if (!items || !Array.isArray(items)) return 0;
    return items.reduce((sum, item) => sum + (Number(item.quantity) || 0) * (Number(item.unitPrice) || 0), 0);
  };

  if (loading) return <div style={styles.loading}>Loading purchase orders...</div>;

  return (
    <div style={styles.container}>
      <h1 style={styles.header}>Purchase Order Management</h1>

      {message && <div style={styles.successMsg}>{message}</div>}
      {error && <div style={styles.errorMsg}>{error}</div>}

      <div style={styles.formCard}>
        <h2 style={styles.formTitle}>{editId ? "Edit Purchase Order" : "Add Purchase Order"}</h2>
        <form onSubmit={handleSubmit}>
          <div style={styles.formRow}>
            <input style={styles.input} name="poNumber" placeholder="PO Number" value={form.poNumber} onChange={handleChange} />
            <select style={styles.select} name="supplierId" value={form.supplierId} onChange={handleChange}>
              <option value="">Select Supplier</option>
              {supplierList.map((s) => (
                <option key={s._id || s.id} value={s._id || s.id}>{s.name}</option>
              ))}
            </select>
            <select style={styles.select} name="warehouseId" value={form.warehouseId} onChange={handleChange}>
              <option value="">Select Warehouse</option>
              {warehouseList.map((w) => (
                <option key={w._id || w.id} value={w._id || w.id}>{w.name}</option>
              ))}
            </select>
          </div>
          <div style={styles.formRow}>
            <select style={styles.select} name="status" value={form.status} onChange={handleChange}>
              {statuses.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
            <input style={styles.input} name="orderDate" type="date" value={form.orderDate} onChange={handleChange} />
            <input style={styles.input} name="expectedDelivery" type="date" value={form.expectedDelivery} onChange={handleChange} />
          </div>
          <div style={styles.formRow}>
            <textarea style={styles.textarea} name="notes" placeholder="Notes" value={form.notes} onChange={handleChange} />
          </div>

          <div style={{ marginBottom: "12px" }}>
            <strong style={{ color: "#0B3D2E", fontSize: "15px" }}>Items</strong>
            <button type="button" style={{ ...styles.btnAddItem, marginLeft: "12px" }} onClick={addItem}>+ Add Item</button>
          </div>
          {form.items.map((item, idx) => (
            <div key={idx} style={styles.itemRow}>
              <select style={{ ...styles.select, flex: "1 1 250px" }} value={item.productId} onChange={(e) => handleItemChange(idx, "productId", e.target.value)}>
                <option value="">Select Product</option>
                {productList.map((p) => (
                  <option key={p._id || p.id} value={p._id || p.id}>{p.name}</option>
                ))}
              </select>
              <input style={{ ...styles.input, flex: "0 1 100px" }} type="number" placeholder="Qty" value={item.quantity} onChange={(e) => handleItemChange(idx, "quantity", e.target.value)} />
              <input style={{ ...styles.input, flex: "0 1 120px" }} type="number" step="0.01" placeholder="Unit Price" value={item.unitPrice} onChange={(e) => handleItemChange(idx, "unitPrice", e.target.value)} />
              {form.items.length > 1 && (
                <button type="button" style={styles.btnRemoveItem} onClick={() => removeItem(idx)}>X</button>
              )}
            </div>
          ))}

          <div style={{ marginTop: "12px" }}>
            <button type="submit" style={styles.btnPrimary}>{editId ? "Update" : "Create"}</button>
            {editId && <button type="button" style={styles.btnSecondary} onClick={handleCancelEdit}>Cancel</button>}
          </div>
        </form>
      </div>

      <table style={styles.table}>
        <thead>
          <tr>
            <th style={styles.th}>PO #</th>
            <th style={styles.th}>Supplier</th>
            <th style={styles.th}>Warehouse</th>
            <th style={styles.th}>Status</th>
            <th style={styles.th}>Order Date</th>
            <th style={styles.th}>Expected Delivery</th>
            <th style={styles.th}>Total Amount</th>
            <th style={styles.th}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {orderList.length === 0 ? (
            <tr><td style={styles.td} colSpan={8}>No purchase orders found.</td></tr>
          ) : (
            orderList.map((po) => {
              const itemsArr = po.items && Array.isArray(po.items) ? po.items : [];
              const total = itemsArr.reduce((sum, item) => sum + (Number(item.quantity) || 0) * (Number(item.unitPrice) || 0), 0);
              return (
                <tr key={po._id || po.id}>
                  <td style={styles.td}>{po.poNumber || "-"}</td>
                  <td style={styles.td}>{po.supplierId?.name || (typeof po.supplierId === "string" ? po.supplierId : "-")}</td>
                  <td style={styles.td}>{po.warehouseId?.name || (typeof po.warehouseId === "string" ? po.warehouseId : "-")}</td>
                  <td style={styles.td}>
                    <span style={styles.statusBadge(po.status)} onClick={() => cycleStatus(po)} title="Click to change status">
                      {po.status || "PENDING"}
                    </span>
                  </td>
                  <td style={styles.td}>{po.orderDate ? po.orderDate.slice(0, 10) : "-"}</td>
                  <td style={styles.td}>{po.expectedDelivery ? po.expectedDelivery.slice(0, 10) : "-"}</td>
                  <td style={styles.td}>${total.toFixed(2)}</td>
                  <td style={styles.td}>
                    <button style={{ background: "#2E9B57", color: "#fff", border: "none", padding: "6px 14px", borderRadius: "6px", fontSize: "13px", fontWeight: "600", cursor: "pointer" }} onClick={() => handleEdit(po)}>Edit</button>
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
}
