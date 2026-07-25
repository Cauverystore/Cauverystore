import React, { useState } from "react";
import api from "../../api/axios";

const tabs = [
  { key: "price", label: "Bulk Price Update" },
  { key: "stock", label: "Bulk Stock Update" },
  { key: "status", label: "Bulk Status" },
  { key: "delete", label: "Bulk Delete" },
];

const styles = {
  container: { padding: "24px" },
  header: { fontSize: "28px", fontWeight: "700", color: "#0B3D2E", marginBottom: "24px" },
  tabRow: { display: "flex", gap: "4px", marginBottom: "24px", borderBottom: "2px solid #CFE8D6", paddingBottom: "0" },
  tab: (active) => ({
    background: active ? "#0B3D2E" : "transparent",
    color: active ? "#fff" : "#0B3D2E",
    border: "none",
    padding: "12px 24px",
    fontSize: "14px",
    fontWeight: "600",
    cursor: "pointer",
    borderRadius: "8px 8px 0 0",
  }),
  card: { background: "#fff", border: "1px solid #CFE8D6", borderRadius: "8px", padding: "24px" },
  label: { display: "block", fontSize: "14px", fontWeight: "600", color: "#0B3D2E", marginBottom: "8px" },
  textarea: { width: "100%", padding: "12px", border: "1px solid #CFE8D6", borderRadius: "6px", fontSize: "14px", resize: "vertical", minHeight: "100px", marginBottom: "16px", outline: "none" },
  input: { width: "100%", padding: "10px 12px", border: "1px solid #CFE8D6", borderRadius: "6px", fontSize: "14px", marginBottom: "16px", outline: "none" },
  btnPrimary: { background: "#2E9B57", color: "#fff", border: "none", padding: "12px 32px", borderRadius: "6px", fontSize: "15px", fontWeight: "600", cursor: "pointer" },
  btnDanger: { background: "#dc2626", color: "#fff", border: "none", padding: "12px 32px", borderRadius: "6px", fontSize: "15px", fontWeight: "600", cursor: "pointer" },
  successMsg: { background: "#E6F7EC", color: "#2E9B57", padding: "12px 16px", borderRadius: "6px", marginBottom: "16px", fontSize: "14px" },
  errorMsg: { background: "#FEE2E2", color: "#dc2626", padding: "12px 16px", borderRadius: "6px", marginBottom: "16px", fontSize: "14px" },
  toggleRow: { display: "flex", alignItems: "center", gap: "12px", marginBottom: "16px" },
  toggleBtn: (active) => ({
    background: active ? "#2E9B57" : "#CFE8D6",
    color: active ? "#fff" : "#666",
    border: "none",
    padding: "8px 20px",
    borderRadius: "6px",
    fontSize: "14px",
    fontWeight: "600",
    cursor: "pointer",
  }),
};

export default function BulkOperations() {
  const [activeTab, setActiveTab] = useState("price");
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const [priceForm, setPriceForm] = useState({ ids: "", price: "", offerPrice: "" });
  const [stockForm, setStockForm] = useState({ ids: "", stock: "" });
  const [statusForm, setStatusForm] = useState({ ids: "", active: true });
  const [deleteForm, setDeleteForm] = useState({ ids: "" });

  const parseIds = (text) => {
    return text
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
  };

  const handleBulkPrice = async () => {
    const ids = parseIds(priceForm.ids);
    if (ids.length === 0) { setError("Enter at least one product ID"); return; }
    if (!priceForm.price) { setError("Enter a price"); return; }
    try {
      setLoading(true);
      setError(null);
      setMessage(null);
      await api.put("/api/admin/products/bulk/price", {
        ids,
        price: Number(priceForm.price),
        offerPrice: priceForm.offerPrice ? Number(priceForm.offerPrice) : undefined,
      });
      setMessage("Bulk price update completed successfully");
      setPriceForm({ ids: "", price: "", offerPrice: "" });
    } catch (err) {
      setError(err.response?.data?.message || "Bulk price update failed");
    } finally {
      setLoading(false);
    }
  };

  const handleBulkStock = async () => {
    const ids = parseIds(stockForm.ids);
    if (ids.length === 0) { setError("Enter at least one product ID"); return; }
    if (!stockForm.stock) { setError("Enter stock quantity"); return; }
    try {
      setLoading(true);
      setError(null);
      setMessage(null);
      await api.put("/api/admin/products/bulk/stock", {
        ids,
        stock: Number(stockForm.stock),
      });
      setMessage("Bulk stock update completed successfully");
      setStockForm({ ids: "", stock: "" });
    } catch (err) {
      setError(err.response?.data?.message || "Bulk stock update failed");
    } finally {
      setLoading(false);
    }
  };

  const handleBulkStatus = async () => {
    const ids = parseIds(statusForm.ids);
    if (ids.length === 0) { setError("Enter at least one product ID"); return; }
    try {
      setLoading(true);
      setError(null);
      setMessage(null);
      await api.put("/api/admin/products/bulk/status", {
        ids,
        active: statusForm.active,
      });
      setMessage(`Bulk status update completed (${statusForm.active ? "Active" : "Inactive"})`);
      setStatusForm({ ids: "", active: true });
    } catch (err) {
      setError(err.response?.data?.message || "Bulk status update failed");
    } finally {
      setLoading(false);
    }
  };

  const handleBulkDelete = async () => {
    const ids = parseIds(deleteForm.ids);
    if (ids.length === 0) { setError("Enter at least one product ID"); return; }
    if (!window.confirm(`Are you sure you want to delete ${ids.length} product(s)? This action cannot be undone.`)) return;
    try {
      setLoading(true);
      setError(null);
      setMessage(null);
      await api.post("/api/admin/products/bulk/delete", { ids });
      setMessage(`${ids.length} product(s) deleted successfully`);
      setDeleteForm({ ids: "" });
    } catch (err) {
      setError(err.response?.data?.message || "Bulk delete failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.container}>
      <h1 style={styles.header}>Bulk Operations</h1>

      <div style={styles.tabRow}>
        {tabs.map((tab) => (
          <button key={tab.key} style={styles.tab(activeTab === tab.key)} onClick={() => { setActiveTab(tab.key); setMessage(null); setError(null); }}>
            {tab.label}
          </button>
        ))}
      </div>

      {message && <div style={styles.successMsg}>{message}</div>}
      {error && <div style={styles.errorMsg}>{error}</div>}

      <div style={styles.card}>
        {activeTab === "price" && (
          <div>
            <label style={styles.label}>Product IDs (comma-separated)</label>
            <textarea
              style={styles.textarea}
              placeholder="e.g. 64a1b2c3d4e5f6a7b8c9d0e1, 64a1b2c3d4e5f6a7b8c9d0e2"
              value={priceForm.ids}
              onChange={(e) => setPriceForm({ ...priceForm, ids: e.target.value })}
            />
            <label style={styles.label}>Price</label>
            <input style={styles.input} type="number" step="0.01" placeholder="New Price" value={priceForm.price} onChange={(e) => setPriceForm({ ...priceForm, price: e.target.value })} />
            <label style={styles.label}>Offer Price (optional)</label>
            <input style={styles.input} type="number" step="0.01" placeholder="Offer Price" value={priceForm.offerPrice} onChange={(e) => setPriceForm({ ...priceForm, offerPrice: e.target.value })} />
            <button style={styles.btnPrimary} onClick={handleBulkPrice} disabled={loading}>{loading ? "Updating..." : "Update Prices"}</button>
          </div>
        )}

        {activeTab === "stock" && (
          <div>
            <label style={styles.label}>Product IDs (comma-separated)</label>
            <textarea
              style={styles.textarea}
              placeholder="e.g. 64a1b2c3d4e5f6a7b8c9d0e1, 64a1b2c3d4e5f6a7b8c9d0e2"
              value={stockForm.ids}
              onChange={(e) => setStockForm({ ...stockForm, ids: e.target.value })}
            />
            <label style={styles.label}>Stock Quantity</label>
            <input style={styles.input} type="number" placeholder="New Stock Quantity" value={stockForm.stock} onChange={(e) => setStockForm({ ...stockForm, stock: e.target.value })} />
            <button style={styles.btnPrimary} onClick={handleBulkStock} disabled={loading}>{loading ? "Updating..." : "Update Stock"}</button>
          </div>
        )}

        {activeTab === "status" && (
          <div>
            <label style={styles.label}>Product IDs (comma-separated)</label>
            <textarea
              style={styles.textarea}
              placeholder="e.g. 64a1b2c3d4e5f6a7b8c9d0e1, 64a1b2c3d4e5f6a7b8c9d0e2"
              value={statusForm.ids}
              onChange={(e) => setStatusForm({ ...statusForm, ids: e.target.value })}
            />
            <div style={styles.toggleRow}>
              <span style={{ fontSize: "14px", fontWeight: "600", color: "#0B3D2E" }}>Status:</span>
              <button style={styles.toggleBtn(statusForm.active === true)} onClick={() => setStatusForm({ ...statusForm, active: true })}>Active</button>
              <button style={styles.toggleBtn(statusForm.active === false)} onClick={() => setStatusForm({ ...statusForm, active: false })}>Inactive</button>
            </div>
            <button style={styles.btnPrimary} onClick={handleBulkStatus} disabled={loading}>{loading ? "Updating..." : "Update Status"}</button>
          </div>
        )}

        {activeTab === "delete" && (
          <div>
            <label style={styles.label}>Product IDs (comma-separated)</label>
            <textarea
              style={styles.textarea}
              placeholder="e.g. 64a1b2c3d4e5f6a7b8c9d0e1, 64a1b2c3d4e5f6a7b8c9d0e2"
              value={deleteForm.ids}
              onChange={(e) => setDeleteForm({ ...deleteForm, ids: e.target.value })}
            />
            <button style={styles.btnDanger} onClick={handleBulkDelete} disabled={loading}>{loading ? "Deleting..." : "Delete Products"}</button>
          </div>
        )}
      </div>
    </div>
  );
}
