import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import api from "../../api/axios";

const EditProduct = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState(0);
  const [form, setForm] = useState({ name: "", description: "", category: "", brand: "", price: "", mrp: "", stock: "", sku: "" });
  const [loading, setLoading] = useState(true);
  const [categories, setCategories] = useState([]);

  useEffect(() => {
    const fetch = async () => {
      try {
        const [prodRes, catRes] = await Promise.all([
          api.get(`/api/admin/products/${id}`),
          api.get("/api/categories")
        ]);
        const prod = prodRes.data;
        const cat = prod.category;
        setForm({
          name: prod.name || "",
          description: prod.description || "",
          category: typeof cat === "object" && cat ? cat.name || "" : cat || "",
          brand: prod.brand || "",
          price: prod.price || "",
          mrp: prod.mrp || "",
          stock: prod.stock || "",
          sku: prod.sku || "",
        });
        setCategories(Array.isArray(catRes.data) ? catRes.data.map((c) => c.name) : []);
      } catch (err) { console.error(err); }
      setLoading(false);
    };
    fetch();
  }, [id]);

  useEffect(() => {
    const handler = () => {
      api.get("/api/categories").then(r => setCategories(Array.isArray(r.data) ? r.data.map((c) => c.name) : [])).catch(() => {});
    };
    window.addEventListener("category-changed", handler);
    return () => window.removeEventListener("category-changed", handler);
  }, []);

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async () => {
    try {
      await api.put(`/api/admin/products/${id}`, {
        name: form.name, description: form.description, category: form.category, brand: form.brand,
        price: parseFloat(form.price), mrp: parseFloat(form.mrp), stock: parseInt(form.stock), sku: form.sku,
      });
      navigate("/admin/products");
    } catch (err) { alert("Failed to update product"); }
  };

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;

  return (
    <div style={{ maxWidth: "700px", width: "100%", margin: "0 auto", padding: "0 1rem", boxSizing: "border-box" }}>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 600, marginBottom: "1.5rem" }}>Edit Product</h1>
      <div style={{ display: "flex", gap: "0.25rem", marginBottom: "1.5rem", borderBottom: "1px solid #e2e8f0", overflowX: "auto", flexWrap: "wrap" }}>
        {["Basic", "Pricing", "Inventory", "Images", "Variants", "Discounts"].map((tab, i) => (
          <button key={i} onClick={() => setActiveTab(i)}
            style={{ padding: "0.5rem 1rem", border: "none", background: activeTab === i ? "#16a34a" : "transparent", color: activeTab === i ? "#fff" : "#475569", borderRadius: "6px 6px 0 0", cursor: "pointer", fontWeight: 500, fontSize: "0.85rem", whiteSpace: "nowrap" }}>
            {tab}
          </button>
        ))}
      </div>

      {activeTab === 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
          <div className="form-group"><label>Name</label><input name="name" value={form.name || ""} onChange={handleChange} style={inputStyle} /></div>
          <div className="form-group"><label>Description</label><textarea name="description" value={form.description || ""} onChange={handleChange} style={{ ...inputStyle, minHeight: 100 }} /></div>
          <div className="form-group"><label>Category</label>
            <select name="category" value={form.category || ""} onChange={handleChange} style={inputStyle}>
              {categories.length === 0 && <option value="">No categories</option>}
              {categories.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          <div className="form-group"><label>Brand</label><input name="brand" value={form.brand || ""} onChange={handleChange} style={inputStyle} /></div>
        </div>
      )}
      {activeTab === 1 && (
        <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
          <div className="form-group"><label>Price</label><input name="price" type="number" value={form.price || ""} onChange={handleChange} style={inputStyle} /></div>
          <div className="form-group"><label>MRP</label><input name="mrp" type="number" value={form.mrp || ""} onChange={handleChange} style={inputStyle} /></div>
        </div>
      )}
      {activeTab === 2 && (
        <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
          <div className="form-group"><label>Stock</label><input name="stock" type="number" value={form.stock || ""} onChange={handleChange} style={inputStyle} /></div>
          <div className="form-group"><label>SKU</label><input name="sku" value={form.sku || ""} onChange={handleChange} style={inputStyle} /></div>
        </div>
      )}
      {activeTab === 3 && <p style={{ color: "#475569" }}>Manage images from the product images page.</p>}
      {activeTab === 4 && <p style={{ color: "#475569" }}>Manage variants from the product variants page.</p>}
      {activeTab === 5 && <p style={{ color: "#475569" }}>Manage discounts from the product discounts page.</p>}

      <div style={{ marginTop: "1.5rem", display: "flex", gap: "0.75rem" }}>
        {activeTab > 0 && <button onClick={() => setActiveTab(activeTab - 1)} style={{ padding: "0.5rem 1.25rem", border: "1px solid #e2e8f0", borderRadius: 6, background: "#fff", cursor: "pointer" }}>Previous</button>}
        {activeTab < 5 && <button onClick={() => setActiveTab(activeTab + 1)} style={{ padding: "0.5rem 1.25rem", background: "#16a34a", color: "#fff", border: "none", borderRadius: 6, cursor: "pointer" }}>Next</button>}
        <button onClick={handleSubmit} style={{ padding: "0.5rem 1.25rem", background: "#16a34a", color: "#fff", border: "none", borderRadius: 6, cursor: "pointer" }}>Update Product</button>
      </div>
    </div>
  );
};

const inputStyle = { padding: "0.6rem 0.75rem", border: "1px solid #e2e8f0", borderRadius: 6, fontSize: "0.9rem", width: "100%" };
export default EditProduct;
