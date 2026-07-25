import React, { useState, useEffect } from "react";
import api from "../../api/axios";

const styles = {
  container: { padding: "24px" },
  header: { fontSize: "28px", fontWeight: "700", color: "#0B3D2E", marginBottom: "24px" },
  formCard: { background: "#fff", border: "1px solid #CFE8D6", borderRadius: "8px", padding: "20px", marginBottom: "24px" },
  formTitle: { fontSize: "18px", fontWeight: "600", color: "#0B3D2E", marginBottom: "16px" },
  formRow: { display: "flex", gap: "16px", marginBottom: "12px", flexWrap: "wrap" },
  input: { flex: "1 1 200px", padding: "10px 12px", border: "1px solid #CFE8D6", borderRadius: "6px", fontSize: "14px", outline: "none" },
  textarea: { flex: "1 1 200px", padding: "10px 12px", border: "1px solid #CFE8D6", borderRadius: "6px", fontSize: "14px", outline: "none", resize: "vertical", minHeight: "60px" },
  btnPrimary: { background: "#2E9B57", color: "#fff", border: "none", padding: "10px 24px", borderRadius: "6px", fontSize: "14px", fontWeight: "600", cursor: "pointer" },
  btnSecondary: { background: "#CFE8D6", color: "#0B3D2E", border: "none", padding: "10px 24px", borderRadius: "6px", fontSize: "14px", fontWeight: "600", cursor: "pointer", marginLeft: "8px" },
  btnDanger: { background: "#dc2626", color: "#fff", border: "none", padding: "6px 14px", borderRadius: "6px", fontSize: "13px", fontWeight: "600", cursor: "pointer" },
  btnEdit: { background: "#2E9B57", color: "#fff", border: "none", padding: "6px 14px", borderRadius: "6px", fontSize: "13px", fontWeight: "600", cursor: "pointer", marginRight: "8px" },
  table: { width: "100%", borderCollapse: "collapse", background: "#fff", border: "1px solid #CFE8D6", borderRadius: "8px", overflow: "hidden" },
  th: { textAlign: "left", padding: "12px 16px", background: "#0B3D2E", color: "#fff", fontSize: "13px", fontWeight: "600", textTransform: "uppercase", letterSpacing: "0.5px" },
  td: { padding: "12px 16px", borderBottom: "1px solid #CFE8D6", fontSize: "14px", color: "#333" },
  loading: { textAlign: "center", padding: "40px", color: "#666" },
  error: { textAlign: "center", padding: "40px", color: "#dc2626" },
  successMsg: { background: "#E6F7EC", color: "#2E9B57", padding: "12px 16px", borderRadius: "6px", marginBottom: "16px", fontSize: "14px" },
  errorMsg: { background: "#FEE2E2", color: "#dc2626", padding: "12px 16px", borderRadius: "6px", marginBottom: "16px", fontSize: "14px" },
  inlineForm: { background: "#f9fdfb", padding: "12px", borderRadius: "6px", border: "1px solid #CFE8D6" },
};

const initialForm = { name: "", address: "", city: "", state: "", pincode: "", contactPerson: "", contactPhone: "", contactEmail: "", capacity: "" };

export default function AdminWarehouses() {
  const [warehouses, setWarehouses] = useState([]);
  const [form, setForm] = useState(initialForm);
  const [editId, setEditId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => { fetchWarehouses(); }, []);

  const fetchWarehouses = async () => {
    try {
      setLoading(true);
      const { data } = await api.get("/api/admin/warehouses");
      setWarehouses(data.warehouses || data || []);
      setError(null);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to fetch warehouses");
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.name.trim()) {
      setError("Name is required");
      return;
    }
    try {
      setError(null);
      setMessage(null);
      const payload = { ...form, capacity: form.capacity ? Number(form.capacity) : undefined };
      if (editId) {
        await api.put(`/api/admin/warehouses/${editId}`, payload);
        setMessage("Warehouse updated successfully");
      } else {
        await api.post("/api/admin/warehouses", payload);
        setMessage("Warehouse created successfully");
      }
      setForm(initialForm);
      setEditId(null);
      fetchWarehouses();
    } catch (err) {
      setError(err.response?.data?.message || "Operation failed");
    }
  };

  const handleEdit = (wh) => {
    setEditId(wh._id || wh.id);
    setForm({
      name: wh.name || "",
      address: wh.address || "",
      city: wh.city || "",
      state: wh.state || "",
      pincode: wh.pincode || "",
      contactPerson: wh.contactPerson || "",
      contactPhone: wh.contactPhone || "",
      contactEmail: wh.contactEmail || "",
      capacity: wh.capacity || "",
    });
  };

  const handleCancelEdit = () => {
    setEditId(null);
    setForm(initialForm);
  };

  const handleDelete = async (id, name) => {
    if (!window.confirm(`Are you sure you want to delete warehouse "${name}"?`)) return;
    try {
      setError(null);
      setMessage(null);
      await api.delete(`/api/admin/warehouses/${id}`);
      setMessage("Warehouse deleted successfully");
      fetchWarehouses();
    } catch (err) {
      setError(err.response?.data?.message || "Delete failed");
    }
  };

  const warehouseList = Array.isArray(warehouses) ? warehouses : [];

  if (loading) return <div style={styles.loading}>Loading warehouses...</div>;

  return (
    <div style={styles.container}>
      <h1 style={styles.header}>Warehouse Management</h1>

      {message && <div style={styles.successMsg}>{message}</div>}
      {error && <div style={styles.errorMsg}>{error}</div>}

      <div style={styles.formCard}>
        <h2 style={styles.formTitle}>{editId ? "Edit Warehouse" : "Add Warehouse"}</h2>
        <form onSubmit={handleSubmit}>
          <div style={styles.formRow}>
            <input style={styles.input} name="name" placeholder="Name *" value={form.name} onChange={handleChange} required />
            <input style={styles.input} name="address" placeholder="Address" value={form.address} onChange={handleChange} />
            <input style={styles.input} name="city" placeholder="City" value={form.city} onChange={handleChange} />
          </div>
          <div style={styles.formRow}>
            <input style={styles.input} name="state" placeholder="State" value={form.state} onChange={handleChange} />
            <input style={styles.input} name="pincode" placeholder="Pincode" value={form.pincode} onChange={handleChange} />
            <input style={styles.input} name="contactPerson" placeholder="Contact Person" value={form.contactPerson} onChange={handleChange} />
          </div>
          <div style={styles.formRow}>
            <input style={styles.input} name="contactPhone" placeholder="Contact Phone" value={form.contactPhone} onChange={handleChange} />
            <input style={styles.input} name="contactEmail" placeholder="Contact Email" value={form.contactEmail} onChange={handleChange} />
            <input style={styles.input} name="capacity" type="number" placeholder="Capacity" value={form.capacity} onChange={handleChange} />
          </div>
          <div>
            <button type="submit" style={styles.btnPrimary}>{editId ? "Update" : "Create"}</button>
            {editId && <button type="button" style={styles.btnSecondary} onClick={handleCancelEdit}>Cancel</button>}
          </div>
        </form>
      </div>

      <table style={styles.table}>
        <thead>
          <tr>
            <th style={styles.th}>Name</th>
            <th style={styles.th}>City</th>
            <th style={styles.th}>Contact</th>
            <th style={styles.th}>Phone</th>
            <th style={styles.th}>Capacity</th>
            <th style={styles.th}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {warehouseList.length === 0 ? (
            <tr><td style={styles.td} colSpan={6}>No warehouses found.</td></tr>
          ) : (
            warehouseList.map((wh) => {
              const isEditing = (wh._id || wh.id) === editId;
              return (
                <tr key={wh._id || wh.id}>
                  {isEditing ? (
                    <td colSpan={6} style={styles.td}>
                      <div style={styles.inlineForm}>
                        <form onSubmit={handleSubmit}>
                          <div style={styles.formRow}>
                            <input style={styles.input} name="name" value={form.name} onChange={handleChange} required />
                            <input style={styles.input} name="address" value={form.address} onChange={handleChange} />
                            <input style={styles.input} name="city" value={form.city} onChange={handleChange} />
                          </div>
                          <div style={styles.formRow}>
                            <input style={styles.input} name="state" value={form.state} onChange={handleChange} />
                            <input style={styles.input} name="pincode" value={form.pincode} onChange={handleChange} />
                            <input style={styles.input} name="contactPerson" value={form.contactPerson} onChange={handleChange} />
                          </div>
                          <div style={styles.formRow}>
                            <input style={styles.input} name="contactPhone" value={form.contactPhone} onChange={handleChange} />
                            <input style={styles.input} name="contactEmail" value={form.contactEmail} onChange={handleChange} />
                            <input style={styles.input} name="capacity" type="number" value={form.capacity} onChange={handleChange} />
                          </div>
                          <div>
                            <button type="submit" style={styles.btnPrimary}>Save</button>
                            <button type="button" style={styles.btnSecondary} onClick={handleCancelEdit}>Cancel</button>
                          </div>
                        </form>
                      </div>
                    </td>
                  ) : (
                    <>
                      <td style={styles.td}>{wh.name}</td>
                      <td style={styles.td}>{wh.city || "-"}</td>
                      <td style={styles.td}>{wh.contactPerson || "-"}</td>
                      <td style={styles.td}>{wh.contactPhone || "-"}</td>
                      <td style={styles.td}>{wh.capacity || "-"}</td>
                      <td style={styles.td}>
                        <button style={styles.btnEdit} onClick={() => handleEdit(wh)}>Edit</button>
                        <button style={styles.btnDanger} onClick={() => handleDelete(wh._id || wh.id, wh.name)}>Delete</button>
                      </td>
                    </>
                  )}
                </tr>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
}
