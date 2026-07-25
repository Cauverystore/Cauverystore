import React, { useState, useEffect } from "react";
import userService from "../services/userService";
import "../styles/account.css";

const initialForm = { name: "", address: "", city: "", state: "", pincode: "", phone: "", isDefault: false };

const AddressBook = () => {
  const [addresses, setAddresses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ ...initialForm });
  const [editId, setEditId] = useState(null);
  const [saving, setSaving] = useState(false);

  const fetchAddresses = async () => {
    try {
      const res = await userService.getAddresses();
      setAddresses(res.data || []);
    } catch (err) {
      setError("Failed to load addresses");
    }
  };

  useEffect(() => {
    const init = async () => {
      await fetchAddresses();
      setLoading(false);
    };
    init();
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError("");
    try {
      if (editId) {
        await userService.updateAddress(editId, form);
      } else {
        await userService.addAddress(form);
      }
      setShowForm(false);
      setEditId(null);
      setForm({ ...initialForm });
      await fetchAddresses();
    } catch (err) {
      setError(err.response?.data?.error || "Failed to save address");
    }
    setSaving(false);
  };

  const handleDelete = async (id) => {
    try {
      await userService.deleteAddress(id);
      await fetchAddresses();
    } catch (err) {
      setError("Failed to delete address");
    }
  };

  const openEdit = (addr) => {
    setEditId(addr.id || addr._id);
    setForm({ name: addr.name, address: addr.address, city: addr.city, state: addr.state, pincode: addr.pincode, phone: addr.phone, isDefault: addr.isDefault || false });
    setShowForm(true);
  };

  if (loading) {
    return (
      <div className="account-page">
        <div className="account-content">
          <p style={{ textAlign: "center", padding: "3rem" }}>Loading addresses...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="account-page">
      <div className="account-content">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1.5rem" }}>
          <h1 className="account-page-title" style={{ marginBottom: 0 }}>Addresses</h1>
          <button className="admin-btn admin-btn-primary" onClick={() => { setShowForm(!showForm); setEditId(null); setForm({ ...initialForm }); }}>
            {showForm ? "Cancel" : "Add Address"}
          </button>
        </div>

        {error && <div style={{ marginBottom: "1rem", padding: "0.75rem", background: "#fef2f2", borderRadius: 8, color: "#dc2626" }}>{error}</div>}

        {showForm && (
          <form onSubmit={handleSubmit} style={{ marginBottom: "1.5rem" }}>
            <div className="profile-card">
              <h3 className="profile-section-title">{editId ? "Edit Address" : "Add Address"}</h3>
              <div className="profile-form">
                <div className="admin-form-group">
                  <label className="admin-form-label">Name</label>
                  <input className="admin-form-input" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Phone</label>
                  <input className="admin-form-input" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} required />
                </div>
                <div className="admin-form-group full-width">
                  <label className="admin-form-label">Address</label>
                  <input className="admin-form-input" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} required />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">City</label>
                  <input className="admin-form-input" value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} required />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">State</label>
                  <input className="admin-form-input" value={form.state} onChange={(e) => setForm({ ...form, state: e.target.value })} required />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Pincode</label>
                  <input className="admin-form-input" value={form.pincode} onChange={(e) => setForm({ ...form, pincode: e.target.value })} required />
                </div>
              </div>
              <div className="admin-form-actions">
                <button type="submit" className="admin-btn admin-btn-primary" disabled={saving}>{saving ? "Saving..." : (editId ? "Update" : "Save")} Address</button>
                <button type="button" className="admin-btn admin-btn-secondary" onClick={() => { setShowForm(false); setEditId(null); setForm({ ...initialForm }); }}>Cancel</button>
              </div>
            </div>
          </form>
        )}

        {addresses.length === 0 && !showForm ? (
          <div className="wishlist-empty">
            <div className="wishlist-empty-icon">📍</div>
            <h3 className="wishlist-empty-title">No addresses yet</h3>
            <p className="wishlist-empty-text">Add a delivery address to get started.</p>
          </div>
        ) : (
          <div className="address-grid">
            {addresses.map((addr) => (
              <div key={addr.id || addr._id} className={`address-card ${addr.isDefault ? "default" : ""}`}>
                {addr.isDefault && <span className="address-card-badge">Default</span>}
                <div className="address-card-name">{addr.name}</div>
                <div className="address-card-detail">{addr.address}, {addr.city}, {addr.state} - {addr.pincode}</div>
                <div className="address-card-phone">{addr.phone}</div>
                <div className="address-card-actions">
                  <button className="address-card-action" onClick={() => openEdit(addr)}>Edit</button>
                  <button className="address-card-action delete" onClick={() => handleDelete(addr.id || addr._id)}>Delete</button>
                </div>
              </div>
            ))}
            {!showForm && (
              <div className="address-add-card" onClick={() => { setShowForm(true); setEditId(null); setForm({ ...initialForm }); }}>
                <div className="address-add-icon">+</div>
                <div className="address-add-text">Add New Address</div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
export default AddressBook;
