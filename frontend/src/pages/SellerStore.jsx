import React, { useState, useEffect } from "react";
import api from "../api/axios";

const SellerStore = () => {
  const [store, setStore] = useState(null);
  const [form, setForm] = useState({});
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [savingProfile, setSavingProfile] = useState(false);
  const [savingPolicies, setSavingPolicies] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    Promise.all([
      api.get("/api/seller/store"),
      api.get("/api/seller/notifications")
    ]).then(([storeRes, notifRes]) => {
      const data = storeRes.data;
      setStore(data);
      setForm({
        storeName: data.storeName || "",
        storeSlug: data.storeSlug || "",
        description: data.description || "",
        address: data.address || "",
        contactPhone: data.contactPhone || "",
        contactEmail: data.contactEmail || "",
        returnPolicy: data.returnPolicy || "",
        shippingPolicy: data.shippingPolicy || "",
      });
      setNotifications(Array.isArray(notifRes.data) ? notifRes.data : []);
    }).catch(() => setError("Failed to load data"))
    .finally(() => setLoading(false));
  }, []);

  const handleProfileSave = async (e) => {
    e.preventDefault();
    setSavingProfile(true);
    try {
      const r = await api.put("/api/seller/store", {
        storeName: form.storeName,
        storeSlug: form.storeSlug,
        description: form.description,
        address: form.address,
        contactPhone: form.contactPhone,
        contactEmail: form.contactEmail,
      });
      setStore(r.data);
      setError("");
    } catch {
      setError("Failed to save profile");
    } finally {
      setSavingProfile(false);
    }
  };

  const handlePoliciesSave = async (e) => {
    e.preventDefault();
    setSavingPolicies(true);
    try {
      const r = await api.put("/api/seller/store", {
        returnPolicy: form.returnPolicy,
        shippingPolicy: form.shippingPolicy,
      });
      setStore(r.data);
      setError("");
    } catch {
      setError("Failed to save policies");
    } finally {
      setSavingPolicies(false);
    }
  };

  const inputStyle = { width: "100%", padding: "0.5rem", border: "1px solid #d1d5db", borderRadius: "4px", fontSize: "0.875rem", boxSizing: "border-box" };
  const labelStyle = { display: "block", fontSize: "0.875rem", fontWeight: 600, color: "#374151", marginBottom: "0.25rem" };
  const sectionStyle = { background: "#fff", padding: "1.5rem", borderRadius: "8px", boxShadow: "0 1px 3px rgba(0,0,0,0.1)", marginBottom: "1.5rem" };
  const btnStyle = { padding: "0.5rem 1.5rem", background: "#3b82f6", color: "#fff", border: "none", borderRadius: "4px", cursor: "pointer", fontSize: "0.875rem", fontWeight: 600 };

  if (loading) return <div style={{ textAlign: "center", padding: "2rem", color: "#6b7280" }}>Loading settings...</div>;

  return (
    <div style={{ padding: "2rem", display: "flex", gap: "2rem" }}>
      <div style={{ flex: 1, maxWidth: "720px" }}>
        <h1 style={{ fontSize: "1.5rem", fontWeight: 700, marginBottom: "1.5rem" }}>Settings</h1>

        {error && <div style={{ background: "#fee2e2", color: "#dc2626", padding: "0.75rem 1rem", borderRadius: "4px", marginBottom: "1rem", fontSize: "0.875rem" }}>{error}</div>}

        <div style={sectionStyle}>
          <h2 style={{ fontSize: "1.125rem", fontWeight: 700, marginBottom: "1rem" }}>Store Profile</h2>
          <form onSubmit={handleProfileSave}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
              <div>
                <label style={labelStyle}>Store Name</label>
                <input style={inputStyle} value={form.storeName || ""} onChange={e => setForm({...form, storeName: e.target.value})} />
              </div>
              <div>
                <label style={labelStyle}>Store Slug</label>
                <input style={inputStyle} value={form.storeSlug || ""} onChange={e => setForm({...form, storeSlug: e.target.value})} />
              </div>
            </div>
            <div style={{ marginTop: "1rem" }}>
              <label style={labelStyle}>Description</label>
              <textarea style={{ ...inputStyle, minHeight: "80px", resize: "vertical" }} value={form.description || ""} onChange={e => setForm({...form, description: e.target.value})} />
            </div>
            <div style={{ marginTop: "1rem" }}>
              <label style={labelStyle}>Address</label>
              <input style={inputStyle} value={form.address || ""} onChange={e => setForm({...form, address: e.target.value})} />
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem", marginTop: "1rem" }}>
              <div>
                <label style={labelStyle}>Contact Phone</label>
                <input style={inputStyle} value={form.contactPhone || ""} onChange={e => setForm({...form, contactPhone: e.target.value})} />
              </div>
              <div>
                <label style={labelStyle}>Contact Email</label>
                <input style={inputStyle} value={form.contactEmail || ""} onChange={e => setForm({...form, contactEmail: e.target.value})} />
              </div>
            </div>
            <button type="submit" style={{ ...btnStyle, marginTop: "1rem", opacity: savingProfile ? 0.7 : 1 }} disabled={savingProfile}>
              {savingProfile ? "Saving..." : "Save Profile"}
            </button>
          </form>
        </div>

        <div style={sectionStyle}>
          <h2 style={{ fontSize: "1.125rem", fontWeight: 700, marginBottom: "1rem" }}>Return & Shipping Policy</h2>
          <form onSubmit={handlePoliciesSave}>
            <div style={{ marginBottom: "1rem" }}>
              <label style={labelStyle}>Return Policy</label>
              <textarea style={{ ...inputStyle, minHeight: "100px", resize: "vertical" }} value={form.returnPolicy || ""} onChange={e => setForm({...form, returnPolicy: e.target.value})} />
            </div>
            <div style={{ marginBottom: "1rem" }}>
              <label style={labelStyle}>Shipping Policy</label>
              <textarea style={{ ...inputStyle, minHeight: "100px", resize: "vertical" }} value={form.shippingPolicy || ""} onChange={e => setForm({...form, shippingPolicy: e.target.value})} />
            </div>
            <button type="submit" style={{ ...btnStyle, opacity: savingPolicies ? 0.7 : 1 }} disabled={savingPolicies}>
              {savingPolicies ? "Saving..." : "Save Policies"}
            </button>
          </form>
        </div>

        <div style={sectionStyle}>
          <h2 style={{ fontSize: "1.125rem", fontWeight: 700, marginBottom: "1rem" }}>Account Info</h2>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
            <div>
              <span style={labelStyle}>Full Name</span>
              <div style={{ padding: "0.5rem 0", color: "#6b7280", fontSize: "0.875rem" }}>{store?.fullName || "N/A"}</div>
            </div>
            <div>
              <span style={labelStyle}>Email</span>
              <div style={{ padding: "0.5rem 0", color: "#6b7280", fontSize: "0.875rem" }}>{store?.email || "N/A"}</div>
            </div>
            <div>
              <span style={labelStyle}>Phone</span>
              <div style={{ padding: "0.5rem 0", color: "#6b7280", fontSize: "0.875rem" }}>{store?.phone || "N/A"}</div>
            </div>
            <div>
              <span style={labelStyle}>Store Status</span>
              <div style={{ padding: "0.5rem 0", color: store?.status === "ACTIVE" ? "#22c55e" : "#ef4444", fontSize: "0.875rem", fontWeight: 600 }}>{store?.status || "N/A"}</div>
            </div>
          </div>
        </div>
      </div>

      <div style={{ width: "300px", flexShrink: 0 }}>
        <div style={sectionStyle}>
          <h2 style={{ fontSize: "1.125rem", fontWeight: 700, marginBottom: "1rem" }}>Notifications</h2>
          {notifications.length === 0 ? (
            <div style={{ textAlign: "center", padding: "2rem 0", color: "#9ca3af", fontSize: "0.875rem" }}>No notifications</div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
              {notifications.map((n, idx) => (
                <div key={idx} style={{ padding: "0.75rem", background: "#f9fafb", borderRadius: "6px", borderLeft: "4px solid #3b82f6", fontSize: "0.875rem", color: "#374151" }}>
                  {typeof n === "string" ? n : n.message || JSON.stringify(n)}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default SellerStore;
