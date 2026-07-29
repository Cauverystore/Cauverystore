import React, { useState } from "react";
import api from "../../api/axios";

const AdminSettings = () => {
  const [settings, setSettings] = useState({ siteName: "Cauvery Store", siteDescription: "", contactEmail: "", contactPhone: "", razorpayKey: "", shippingCharge: "" });
  const [saved, setSaved] = useState(false);

  const handleChange = (e) => setSettings({ ...settings, [e.target.name]: e.target.value });

  const handleSave = async () => {
    try { await api.put("/api/admin/settings", settings); setSaved(true); setTimeout(() => setSaved(false), 3000); } catch (err) { alert("Failed to save settings"); }
  };

  return (
    <div style={{ maxWidth: "600px", width: "100%", margin: "0 auto", padding: "0 1rem", boxSizing: "border-box" }}>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 600, marginBottom: "1.5rem" }}>Settings</h1>
      <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
        <div className="form-group"><label>Site Name</label><input name="siteName" value={settings.siteName} onChange={handleChange} style={inputStyle} /></div>
        <div className="form-group"><label>Site Description</label><textarea name="siteDescription" value={settings.siteDescription} onChange={handleChange} style={{ ...inputStyle, minHeight: 80 }} /></div>
        <div className="form-group"><label>Contact Email</label><input name="contactEmail" value={settings.contactEmail} onChange={handleChange} style={inputStyle} /></div>
        <div className="form-group"><label>Contact Phone</label><input name="contactPhone" value={settings.contactPhone} onChange={handleChange} style={inputStyle} /></div>
        <div className="form-group"><label>Razorpay Key</label><input name="razorpayKey" value={settings.razorpayKey} onChange={handleChange} style={inputStyle} /></div>
        <div className="form-group"><label>Shipping Charge (&#8377;)</label><input name="shippingCharge" type="number" value={settings.shippingCharge} onChange={handleChange} style={inputStyle} /></div>
        <button onClick={handleSave} style={{ padding: "0.6rem 1.5rem", background: "#16a34a", color: "#fff", border: "none", borderRadius: 6, cursor: "pointer", alignSelf: "flex-start" }}>Save Settings</button>
        {saved && <span style={{ color: "#16a34a", fontSize: "0.9rem" }}>Settings saved!</span>}
      </div>
    </div>
  );
};
const inputStyle = { padding: "0.6rem 0.75rem", border: "1px solid #e2e8f0", borderRadius: 6, fontSize: "0.9rem", width: "100%" };
export default AdminSettings;
