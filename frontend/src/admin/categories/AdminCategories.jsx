import React, { useState, useEffect } from "react";
import api from "../../api/axios";

const S = {
  section: { background: "#fff", borderRadius: "8px", border: "1px solid #CFE8D6", padding: "1.25rem", marginBottom: "1rem" },
  title: { fontSize: "0.95rem", fontWeight: 700, color: "#0B3D2E", marginBottom: "1rem", paddingBottom: "0.5rem", borderBottom: "2px solid #EAF7EE" },
  input: { width: "100%", padding: "8px 10px", border: "1px solid #CFE8D6", borderRadius: "6px", fontSize: "0.9rem", outline: "none", boxSizing: "border-box", background: "#fff" },
  textarea: { width: "100%", padding: "8px 10px", border: "1px solid #CFE8D6", borderRadius: "6px", fontSize: "0.9rem", minHeight: "72px", resize: "vertical", outline: "none", boxSizing: "border-box", fontFamily: "inherit" },
  btnP: { padding: "8px 16px", background: "#2E9B57", color: "#fff", border: "none", borderRadius: "6px", cursor: "pointer", fontWeight: 600, whiteSpace: "nowrap" },
  btnD: { padding: "6px 14px", background: "#fef2f2", color: "#dc2626", border: "1px solid #fecaca", borderRadius: "4px", cursor: "pointer", fontSize: "0.8rem" },
  btnS: { padding: "6px 14px", background: "#EAF7EE", color: "#146C43", border: "1px solid #CFE8D6", borderRadius: "4px", cursor: "pointer", fontSize: "0.8rem" },
  modalOverlay: { position: "fixed", inset: 0, background: "rgba(0,0,0,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, padding: "1rem" },
  modalBox: { background: "#fff", borderRadius: "10px", padding: "1.5rem", maxWidth: "420px", width: "100%", boxShadow: "0 4px 24px rgba(0,0,0,0.15)" },
  msg: (type) => ({ padding: "10px 14px", borderRadius: "6px", marginBottom: "0.75rem", background: type === "success" ? "#EAF7EE" : "#fef2f2", color: type === "success" ? "#146C43" : "#dc2626", border: `1px solid ${type === "success" ? "#CFE8D6" : "#fecaca"}`, fontSize: "0.85rem" }),
};

// Licence a seller must hold to list in a category (codes match SellerLicenceService).
const LICENCES = [
  ["", "No licence needed"],
  ["FSSAI", "FSSAI Licence (food)"],
  ["DRUG", "Drug Licence"],
  ["BIS", "BIS Certificate"],
  ["LEGAL_METROLOGY", "Legal Metrology"],
  ["IEC", "Import Export Code"],
  ["TRADE", "Trade Licence"],
  ["OTHER", "Other licence"],
];
const licenceName = (code) => (LICENCES.find(([c]) => c === code) || [, code])[1];

const AdminCategories = () => {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [newCat, setNewCat] = useState({ name: "", description: "", requiredLicence: "" });
  const [editing, setEditing] = useState(null);
  const [editForm, setEditForm] = useState({ name: "", description: "", requiredLicence: "" });
  const [msg, setMsg] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [errors, setErrors] = useState({});
  const [standard, setStandard] = useState(null); // recommended list, shown before adding
  const [addingStandard, setAddingStandard] = useState(false);

  const fetchCategories = async () => {
    try {
      const res = await api.get("/api/categories");
      setCategories(res.data || []);
      window.dispatchEvent(new CustomEvent("category-changed"));
    } catch (err) { console.error(err); }
  };

  useEffect(() => {
    const load = async () => {
      await fetchCategories();
      setLoading(false);
    };
    load();
  }, []);

  const validate = (name, desc) => {
    const e = {};
    if (!name || !name.trim()) e.name = "Name is required";
    if (desc && desc.trim().length > 0 && desc.trim().length < 10) e.description = "Min 10 characters";
    if (desc && desc.length > 500) e.description = "Max 500 characters";
    return e;
  };

  const handleAdd = async () => {
    const v = validate(newCat.name, newCat.description);
    setErrors(v);
    if (Object.keys(v).length > 0) return;
    try {
      await api.post("/api/categories/admin/add", { name: newCat.name.trim(), description: newCat.description.trim() || null, requiredLicence: newCat.requiredLicence || null });
      setNewCat({ name: "", description: "", requiredLicence: "" });
      await fetchCategories();
      setMsg({ type: "success", text: "Category added!" });
    } catch (err) { setMsg({ type: "error", text: err.response?.data?.error || "Failed to add category" }); }
  };

  const startEdit = (cat) => {
    setEditing(cat.id);
    setEditForm({ name: cat.name || "", description: cat.description || "", requiredLicence: cat.requiredLicence || "" });
    setErrors({});
  };

  const cancelEdit = () => { setEditing(null); setEditForm({ name: "", description: "", requiredLicence: "" }); setErrors({}); };

  const saveEdit = async (id) => {
    const v = validate(editForm.name, editForm.description);
    setErrors(v);
    if (Object.keys(v).length > 0) return;
    try {
      await api.put("/api/categories/admin/update/" + id, { name: editForm.name.trim(), description: editForm.description.trim() || null, requiredLicence: editForm.requiredLicence || null });
      await fetchCategories();
      setEditing(null);
      setMsg({ type: "success", text: "Category updated!" });
    } catch (err) { setMsg({ type: "error", text: err.response?.data?.error || "Update failed" }); }
  };

  const openStandard = async () => {
    try {
      const res = await api.get("/api/categories/admin/standard");
      setStandard(res.data || []);
    } catch (err) { setMsg({ type: "error", text: err.response?.data?.error || "Could not load the recommended categories" }); }
  };

  const addStandard = async () => {
    setAddingStandard(true);
    try {
      const res = await api.post("/api/categories/admin/add-standard");
      await fetchCategories();
      const n = res.data?.count || 0;
      setMsg({ type: "success", text: n > 0 ? `Added ${n} categor${n === 1 ? "y" : "ies"}.` : "All recommended categories are already in the store." });
      setStandard(null);
    } catch (err) { setMsg({ type: "error", text: err.response?.data?.error || "Could not add the categories" }); }
    setAddingStandard(false);
  };

  const confirmDelete = (cat) => setDeleteTarget(cat);

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      const res = await api.delete("/api/categories/admin/delete/" + deleteTarget.id);
      if (res.data?.error) throw new Error(res.data.error);
      await fetchCategories();
      setMsg({ type: "success", text: "Category deleted!" });
    } catch (err) {
      setMsg({ type: "error", text: err.response?.data?.error || err.message || "Delete failed" });
    }
    setDeleteTarget(null);
  };

  const errMsg = (key) => errors[key] ? <div style={{ color: "#dc2626", fontSize: "0.7rem", marginTop: "2px" }}>{errors[key]}</div> : null;

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;

  return (
    <div>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 600, marginBottom: "1.5rem" }}>Category Management</h1>

      {msg && (
        <div style={S.msg(msg.type)}>
          {msg.type === "success" ? "\u2713 " : "\u2717 "}{msg.text}
          <button onClick={() => setMsg(null)} style={{ float: "right", border: "none", background: "none", cursor: "pointer", fontWeight: 700, color: "inherit", fontSize: "1rem", lineHeight: 1, padding: 0 }}>&times;</button>
        </div>
      )}

      {/* Add Category */}
      <div style={S.section}>
        <h3 style={S.title}>Add Category</h3>
        <div style={{ display: "flex", gap: "8px", marginBottom: "0.5rem" }}>
          <div style={{ flex: 1 }}>
            <input style={S.input} placeholder="Category name *" value={newCat.name} onChange={(e) => setNewCat({ ...newCat, name: e.target.value })} />
            {errMsg("name")}
          </div>
          <select aria-label="Licence sellers need" style={{ ...S.input, width: "auto", alignSelf: "flex-start" }} value={newCat.requiredLicence} onChange={(e) => setNewCat({ ...newCat, requiredLicence: e.target.value })}>
            {LICENCES.map(([c, l]) => <option key={c} value={c}>{l}</option>)}
          </select>
          <button onClick={handleAdd} style={{ ...S.btnP, alignSelf: "flex-start" }}>Add</button>
        </div>
        <div>
          <textarea style={S.textarea} placeholder="Description (optional, 10-500 chars)" value={newCat.description} onChange={(e) => setNewCat({ ...newCat, description: e.target.value })} maxLength={500} />
          <div style={{ fontSize: "0.7rem", color: "#94a3b8", textAlign: "right" }}>{(newCat.description || "").length}/500</div>
          {errMsg("description")}
        </div>
      </div>

      {/* Recommended categories */}
      <div style={S.section}>
        <h3 style={S.title}>Recommended Categories</h3>
        <p style={{ color: "#64748B", fontSize: "0.85rem", margin: "0 0 0.75rem" }}>
          A ready-made list for an Indian marketplace. Goods that need a licence or a quality certificate (FSSAI, drug licence, BIS / ISI, hallmarking, Legal Metrology) get their own category, so only sellers holding it can list there. Existing categories are not changed.
        </p>
        <button onClick={openStandard} style={S.btnP}>Review and add recommended categories</button>
      </div>

      {/* Category List */}
      <div style={S.section}>
        <h3 style={S.title}>All Categories ({categories.length})</h3>
        {categories.length === 0 ? (
          <p style={{ color: "#64748B", textAlign: "center", padding: "2rem" }}>No categories yet.</p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85rem" }}>
              <thead>
                <tr style={{ background: "#f8f8f8" }}>
                  <th style={{ padding: "10px", textAlign: "left", minWidth: "150px" }}>Name</th>
                  <th style={{ padding: "10px", textAlign: "left", minWidth: "200px" }}>Description</th>
                  <th style={{ padding: "10px", textAlign: "left", minWidth: "160px" }}>Sellers need</th>
                  <th style={{ padding: "10px", textAlign: "right", minWidth: "140px" }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {categories.map((c) => (
                  <tr key={c.id} style={{ borderBottom: "1px solid var(--sn-border)" }}>
                    {editing === c.id ? (
                      <>
                        <td style={{ padding: "10px", verticalAlign: "top" }}>
                          <input style={S.input} value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} />
                          {errMsg("name")}
                        </td>
                        <td style={{ padding: "10px", verticalAlign: "top" }}>
                          <textarea style={S.textarea} value={editForm.description} onChange={(e) => setEditForm({ ...editForm, description: e.target.value })} maxLength={500} />
                          <div style={{ fontSize: "0.7rem", color: "#94a3b8", textAlign: "right" }}>{(editForm.description || "").length}/500</div>
                          {errMsg("description")}
                        </td>
                        <td style={{ padding: "10px", verticalAlign: "top" }}>
                          <select aria-label="Licence sellers need" style={S.input} value={editForm.requiredLicence} onChange={(e) => setEditForm({ ...editForm, requiredLicence: e.target.value })}>
                            {LICENCES.map(([code, l]) => <option key={code} value={code}>{l}</option>)}
                          </select>
                        </td>
                        <td style={{ padding: "10px", textAlign: "right", whiteSpace: "nowrap" }}>
                          <button onClick={() => saveEdit(c.id)} style={{ ...S.btnS, marginRight: "6px" }}>Save</button>
                          <button onClick={cancelEdit} style={{ padding: "6px 14px", border: "1px solid #CFE8D6", borderRadius: "4px", background: "#fff", cursor: "pointer", fontSize: "0.8rem" }}>Cancel</button>
                        </td>
                      </>
                    ) : (
                      <>
                        <td style={{ padding: "10px", fontWeight: 500 }}>{c.name}</td>
                        <td style={{ padding: "10px", color: "#64748B" }}>{c.description || <span style={{ fontStyle: "italic", color: "#cbd5e1" }}>No description</span>}</td>
                        <td style={{ padding: "10px", color: c.requiredLicence ? "#b45309" : "#94a3b8", fontSize: "0.85rem" }}>{c.requiredLicence ? licenceName(c.requiredLicence) : "Anyone"}</td>
                        <td style={{ padding: "10px", textAlign: "right", whiteSpace: "nowrap" }}>
                          <button onClick={() => startEdit(c)} style={{ ...S.btnS, marginRight: "6px" }}>Edit</button>
                          <button onClick={() => confirmDelete(c)} style={S.btnD}>Delete</button>
                        </td>
                      </>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Recommended categories review */}
      {standard && (
        <div style={S.modalOverlay} onClick={() => setStandard(null)}>
          <div style={{ ...S.modalBox, maxWidth: "760px", maxHeight: "85vh", display: "flex", flexDirection: "column" }} onClick={(e) => e.stopPropagation()}>
            <h3 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "0.5rem" }}>Recommended categories</h3>
            <p style={{ color: "#64748B", fontSize: "0.85rem", margin: "0 0 0.75rem" }}>
              {standard.filter((s) => !s.exists).length} will be added. Ones you already have are greyed out and left as they are. You can edit or delete any of them afterwards.
            </p>
            <div style={{ overflowY: "auto", flex: 1, border: "1px solid #CFE8D6", borderRadius: "6px" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.8rem" }}>
                <thead>
                  <tr style={{ background: "#f8f8f8", position: "sticky", top: 0 }}>
                    <th style={{ padding: "8px", textAlign: "left" }}>Category</th>
                    <th style={{ padding: "8px", textAlign: "left" }}>Sellers need</th>
                  </tr>
                </thead>
                <tbody>
                  {standard.map((s) => (
                    <tr key={s.name} style={{ borderTop: "1px solid #EAF7EE", opacity: s.exists ? 0.45 : 1 }}>
                      <td style={{ padding: "8px" }}>
                        <div style={{ fontWeight: 600 }}>{s.name}{s.exists ? " (already added)" : ""}</div>
                        <div style={{ color: "#64748B" }}>{s.description}</div>
                      </td>
                      <td style={{ padding: "8px", whiteSpace: "nowrap", color: s.requiredLicence ? "#b45309" : "#94a3b8" }}>
                        {s.requiredLicence ? licenceName(s.requiredLicence) : "Anyone"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div style={{ display: "flex", gap: "10px", justifyContent: "flex-end", marginTop: "1rem" }}>
              <button onClick={() => setStandard(null)} style={{ padding: "8px 20px", border: "1px solid #CFE8D6", borderRadius: "6px", background: "#fff", cursor: "pointer", fontSize: "0.85rem", fontWeight: 500 }}>Cancel</button>
              <button onClick={addStandard} disabled={addingStandard || standard.every((s) => s.exists)} style={{ ...S.btnP, opacity: addingStandard ? 0.6 : 1 }}>
                {addingStandard ? "Adding..." : `Add ${standard.filter((s) => !s.exists).length} categories`}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {deleteTarget && (
        <div style={S.modalOverlay} onClick={() => setDeleteTarget(null)}>
          <div style={S.modalBox} onClick={(e) => e.stopPropagation()}>
            <h3 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "0.75rem" }}>Delete Category</h3>
            <p style={{ color: "#64748B", marginBottom: "1.5rem", fontSize: "0.9rem", lineHeight: 1.5 }}>
              Are you sure you want to delete <strong>"{deleteTarget.name}"</strong>?
            </p>
            <div style={{ display: "flex", gap: "10px", justifyContent: "flex-end" }}>
              <button onClick={() => setDeleteTarget(null)} style={{ padding: "8px 20px", border: "1px solid #CFE8D6", borderRadius: "6px", background: "#fff", cursor: "pointer", fontSize: "0.85rem", fontWeight: 500 }}>Cancel</button>
              <button onClick={handleDelete} style={{ padding: "8px 20px", background: "#dc2626", color: "#fff", border: "none", borderRadius: "6px", cursor: "pointer", fontSize: "0.85rem", fontWeight: 600 }}>Delete</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminCategories;
