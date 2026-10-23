import React, { useState, useEffect } from "react";
import userService from "../services/userService";
import "../styles/account.css";
import { useTranslation } from "react-i18next";

const initialForm = { fullName: "", phone: "", line1: "", line2: "", street: "", city: "", state: "", pincode: "", country: "India", isDefault: false };

const AddressBook = () => {
  const { t } = useTranslation();

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
      setError(t("addressBook.failedToLoadAddresses"));
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
      setError(err.response?.data?.error || t("addressBook.failedToSaveAddress"));
    }
    setSaving(false);
  };

  const handleDelete = async (id) => {
    try {
      await userService.deleteAddress(id);
      await fetchAddresses();
    } catch (err) {
      setError(t("addressBook.failedToDeleteAddress"));
    }
  };

  const openEdit = (addr) => {
    setEditId(addr.id || addr._id);
    setForm({ fullName: addr.fullName, phone: addr.phone, line1: addr.line1, line2: addr.line2, street: addr.street, city: addr.city, state: addr.state, pincode: addr.pincode, country: addr.country || "India", isDefault: addr.isDefault || false });
    setShowForm(true);
  };

  if (loading) {
    return (
      <div className="account-page">
        <div className="account-content">
          <p style={{ textAlign: "center", padding: "3rem" }}>{t("addressBook.loadingAddresses")}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="account-page">
      <div className="account-content">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1.5rem" }}>
          <h1 className="account-page-title" style={{ marginBottom: 0 }}>{t("addressBook.addresses")}</h1>
          <button className="admin-btn admin-btn-primary" onClick={() => { setShowForm(!showForm); setEditId(null); setForm({ ...initialForm }); }}>
            {showForm ? t("addressBook.cancel") : t("addressBook.addAddress")}
          </button>
        </div>

        {error && <div style={{ marginBottom: "1rem", padding: "0.75rem", background: "#fef2f2", borderRadius: 8, color: "#dc2626" }}>{error}</div>}

        {showForm && (
          <form onSubmit={handleSubmit} style={{ marginBottom: "1.5rem" }}>
            <div className="profile-card">
              <h3 className="profile-section-title">{editId ? t("addressBook.editAddress") : t("addressBook.addAddress")}</h3>
              <div className="profile-form">
                <div className="admin-form-group">
                  <label className="admin-form-label" htmlFor="addr-name">{t("addressBook.name")}</label>
                  <input id="addr-name" className="admin-form-input" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label" htmlFor="addr-phone">{t("addressBook.phone")}</label>
                  <input id="addr-phone" className="admin-form-input" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} required />
                </div>
                <div className="admin-form-group full-width">
                  <label className="admin-form-label" htmlFor="addr-line1">{t("addressBook.addressLine1")}</label>
                  <input id="addr-line1" className="admin-form-input" value={form.line1} onChange={(e) => setForm({ ...form, line1: e.target.value })} required />
                </div>
                <div className="admin-form-group full-width">
                  <label className="admin-form-label" htmlFor="addr-line2">{t("addressBook.addressLine2")}</label>
                  <input id="addr-line2" className="admin-form-input" value={form.line2} onChange={(e) => setForm({ ...form, line2: e.target.value })} placeholder={t("addressBook.areaLandmarkOptional")} />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label" htmlFor="addr-city">{t("addressBook.city")}</label>
                  <input id="addr-city" className="admin-form-input" value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} required />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label" htmlFor="addr-state">{t("addressBook.state")}</label>
                  <input id="addr-state" className="admin-form-input" value={form.state} onChange={(e) => setForm({ ...form, state: e.target.value })} required />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label" htmlFor="addr-pincode">{t("addressBook.pincode")}</label>
                  <input id="addr-pincode" className="admin-form-input" value={form.pincode} onChange={(e) => setForm({ ...form, pincode: e.target.value })} required />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label" htmlFor="addr-country">{t("addressBook.country")}</label>
                  <input id="addr-country" className="admin-form-input" value={form.country} onChange={(e) => setForm({ ...form, country: e.target.value })} />
                </div>
              </div>
              <div className="admin-form-actions">
                <button type="submit" className="admin-btn admin-btn-primary" disabled={saving}>{saving ? t("addressBook.saving") : (editId ? t("addressBook.update") : t("addressBook.save"))}{" "}{t("addressBook.address")}</button>
                <button type="button" className="admin-btn admin-btn-secondary" onClick={() => { setShowForm(false); setEditId(null); setForm({ ...initialForm }); }}>{t("addressBook.cancel")}</button>
              </div>
            </div>
          </form>
        )}

        {addresses.length === 0 && !showForm ? (
          <div className="wishlist-empty">
            <div className="wishlist-empty-icon">📍</div>
            <h3 className="wishlist-empty-title">{t("addressBook.noAddressesYet")}</h3>
            <p className="wishlist-empty-text">{t("addressBook.addADeliveryAddressTo")}</p>
          </div>
        ) : (
          <div className="address-grid">
            {addresses.map((addr) => (
              <div key={addr.id || addr._id} className={`address-card ${addr.isDefault ? "default" : ""}`}>
                {addr.isDefault && <span className="address-card-badge">{t("addressBook.default")}</span>}
                <div className="address-card-name">{addr.fullName}</div>
                <div className="address-card-detail">{addr.line1 || addr.street}{addr.line2 ? `, ${addr.line2}` : ""}, {addr.city}, {addr.state} - {addr.pincode}</div>
                <div className="address-card-phone">{addr.phone}</div>
                <div className="address-card-actions">
                  <button className="address-card-action" onClick={() => openEdit(addr)}>{t("addressBook.edit")}</button>
                  <button className="address-card-action delete" onClick={() => handleDelete(addr.id || addr._id)}>{t("addressBook.delete")}</button>
                </div>
              </div>
            ))}
            {!showForm && (
              <button type="button" className="address-add-card" onClick={() => { setShowForm(true); setEditId(null); setForm({ ...initialForm }); }}>
                <div className="address-add-icon">+</div>
                <div className="address-add-text">{t("addressBook.addNewAddress")}</div>
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
export default AddressBook;
