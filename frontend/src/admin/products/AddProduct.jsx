import React, { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../api/axios";
import { compressImages } from "../../utils/compressImage";

const FALLBACK_CATEGORIES = ["Electronics","Fashion","Home & Kitchen","Grocery","Beauty","Appliances","Books","Sports & Fitness","Toys & Games"].map((name, i) => ({ id: null, name }));
const COUNTRIES = ["India","USA","China","Japan","South Korea","Germany","Vietnam","Taiwan","Other"];
const WARRANTY = ["6 Months","1 Year","2 Years","3 Years","No Warranty"];
const RETURN_POLICY = ["7 Days","10 Days","15 Days","No Returns"];
const SHIPPING_CLASS = ["Standard","Express","Heavy","Fragile"];
const BADGES = ["None","New Arrival","Best Seller","Limited Offer","Discount"];

function slugify(text) {
  return text.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").substring(0, 80);
}

const AddProduct = () => {
  const navigate = useNavigate();
  const isSeller = window.location.pathname.startsWith("/seller");
  const redirectPath = isSeller ? "/seller/products" : "/admin/products";

  const [form, setForm] = useState({
    name: "", brand: "", category: "", price: "", stock: "", sku: "",
    description: "", manufacturer: "", modelNumber: "", barcode: "", hsnCode: "",
    countryOfOrigin: "India", shortDescription: "", technicalSpecs: "",
    warranty: "1 Year", returnPolicy: "7 Days",
    metaTitle: "", metaDescription: "", keywords: "", slug: "",
    weight: "", dimensions: "", shippingClass: "Standard", deliveryDays: "", badges: "None"
  });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [msg, setMsg] = useState(null);
  const [categories, setCategories] = useState(FALLBACK_CATEGORIES);
  const [images, setImages] = useState([]);
  const [imageErrors, setImageErrors] = useState([]);
  const fileRef = useRef();

  const set = (k, v) => {
    const updated = { ...form, [k]: v };
    if (k === "name" && !form.slug) updated.slug = slugify(v);
    setForm(updated);
  };

  const fetchCategories = async () => {
    try {
      const res = await api.get("/api/categories");
      const data = Array.isArray(res.data) ? res.data : [];
      if (data.length > 0) {
        const normalized = data.map((c) => (typeof c === "string" ? { id: null, name: c } : { id: c.id, name: c.name }));
        setCategories(normalized);
        setForm((f) => (f.category ? f : { ...f, category: normalized[0]?.id ?? "" }));
      }
    } catch {}
  };

  useEffect(() => {
    fetchCategories();
    const handler = () => fetchCategories();
    window.addEventListener("category-changed", handler);
    return () => window.removeEventListener("category-changed", handler);
  }, []);

  const ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];
  const MAX_FILE_SIZE = 2 * 1024 * 1024;
  const MAX_IMAGES = 5;

  const handleFiles = (files) => {
    const fileArr = Array.from(files);
    const newErrors = [];
    const valid = fileArr.filter((f) => {
      if (!ACCEPTED_TYPES.includes(f.type)) { newErrors.push(`${f.name}: Invalid format. Use JPG, PNG, or WebP.`); return false; }
      if (f.size > MAX_FILE_SIZE) { newErrors.push(`${f.name}: Exceeds 2MB limit.`); return false; }
      return true;
    });
    const combined = [...images, ...valid].slice(0, MAX_IMAGES);
    if (valid.length + images.length > MAX_IMAGES) {
      newErrors.push(`Maximum ${MAX_IMAGES} images allowed.`);
    }
    setImages(combined);
    setImageErrors(newErrors);
  };

  const removeImage = (idx) => {
    setImages((prev) => prev.filter((_, i) => i !== idx));
  };

  const validate = () => {
    const e = {};
    if (!form.name.trim()) e.name = "Required";
    if (!form.price || parseFloat(form.price) <= 0) e.price = "Must be > 0";
    if (!form.stock || parseInt(form.stock) < 0) e.stock = "Must be >= 0";
    if (form.shortDescription && form.shortDescription.length > 150) e.shortDescription = "Max 150 chars";
    if (form.metaTitle && form.metaTitle.length > 60) e.metaTitle = "Max 60 chars";
    if (form.metaDescription && form.metaDescription.length > 160) e.metaDescription = "Max 160 chars";
    if (form.barcode && !/^\d{12,13}$/.test(form.barcode)) e.barcode = "12-13 digits required";
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setSubmitting(true);
    setMsg(null);
    try {
      const data = {
        name: form.name, brand: form.brand,
        category: form.category ? { id: form.category } : null,
        price: parseFloat(form.price),
        stock: parseInt(form.stock), sku: form.sku, description: form.description,
        manufacturer: form.manufacturer, modelNumber: form.modelNumber,
        barcode: form.barcode, hsnCode: form.hsnCode,
        countryOfOrigin: form.countryOfOrigin, shortDescription: form.shortDescription,
        technicalSpecs: form.technicalSpecs, warranty: form.warranty,
        returnPolicy: form.returnPolicy, metaTitle: form.metaTitle,
        metaDescription: form.metaDescription, metaKeywords: form.keywords,
        slug: form.slug || slugify(form.name),
        weight: form.weight ? parseFloat(form.weight) : null,
        dimensions: form.dimensions, shippingClass: form.shippingClass,
        deliveryDays: form.deliveryDays ? parseInt(form.deliveryDays) : null,
        badges: form.badges !== "None" ? [form.badges] : [],
      };
      const endpoint = isSeller ? "/api/seller/products" : "/api/admin/products/add";
      const res = await api.post(endpoint, data);
      const productId = res.data?.id;
      if (productId && images.length > 0) {
        setMsg({ type: "info", text: "Compressing images..." });
        const compressed = await compressImages(images);
        for (const file of compressed) {
          const fd = new FormData();
          fd.append("file", file);
          try {
            await api.post(`/api/admin/products/${productId}/images`, fd);
          } catch (imgErr) {
            setImageErrors((prev) => [...prev, `Failed to upload ${file.name}`]);
          }
        }
      }
      setMsg({ type: "success", text: `Product created${images.length > 0 ? " with images" : ""}!` });
      setTimeout(() => navigate(redirectPath), 1000);
    } catch (err) {
      setMsg({ type: "error", text: err.response?.data?.error || "Failed to create product." });
    }
    setSubmitting(false);
  };

  const S = {
    field: { marginBottom: "0.75rem" },
    label: { display: "block", fontSize: "0.8rem", fontWeight: 600, color: "#374151", marginBottom: "3px" },
    input: {
      width: "100%", padding: "8px 12px", border: "1px solid #CFE8D6", borderRadius: "6px",
      fontSize: "0.9rem", outline: "none", boxSizing: "border-box", background: "#fff"
    },
    select: {
      width: "100%", padding: "8px 12px", border: "1px solid #CFE8D6", borderRadius: "6px",
      fontSize: "0.9rem", background: "#fff", outline: "none", boxSizing: "border-box"
    },
    textarea: {
      width: "100%", padding: "8px 12px", border: "1px solid #CFE8D6", borderRadius: "6px",
      fontSize: "0.9rem", minHeight: "80px", resize: "vertical", outline: "none", boxSizing: "border-box"
    },
    section: { background: "#fff", borderRadius: "8px", border: "1px solid #CFE8D6", padding: "1.25rem", marginBottom: "1rem" },
    sectionTitle: { fontSize: "0.95rem", fontWeight: 700, color: "#0B3D2E", marginBottom: "1rem", paddingBottom: "0.5rem", borderBottom: "2px solid #EAF7EE" },
    counter: { fontSize: "0.7rem", color: "#94a3b8" },
    hint: { fontSize: "0.7rem", color: "#64748B", marginTop: "2px" },
    error: { color: "#dc2626", fontSize: "0.7rem", marginTop: "2px" },
    grid2: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0.75rem" },
    grid3: { display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "0.75rem" },
  };

  const fld = (label, key, type = "text", opts = null, hint = null) => (
    <div style={S.field}>
      <label style={S.label}>{label}</label>
      {opts ? (
        <select style={S.select} value={form[key]} onChange={e => set(key, e.target.value)}>
          {opts.map(o => <option key={o} value={o}>{o}</option>)}
        </select>
      ) : type === "textarea" ? (
        <>
          <textarea style={S.textarea} value={form[key]} onChange={e => set(key, e.target.value)}
            maxLength={key === "shortDescription" ? 150 : key === "metaTitle" ? 60 : key === "metaDescription" ? 160 : undefined}
          />
          {(key === "shortDescription" || key === "metaTitle" || key === "metaDescription") && (
            <div style={S.counter}>{(form[key] || "").length}/{key === "shortDescription" ? 150 : key === "metaTitle" ? 60 : 160}</div>
          )}
        </>
      ) : (
        <input style={S.input} type={type} value={form[key]} onChange={e => set(key, e.target.value)}
          min={type === "number" ? 0 : undefined}
          step={type === "number" && key === "weight" ? "0.01" : undefined}
        />
      )}
      {errors[key] && <div style={S.error}>{errors[key]}</div>}
      {hint && <div style={S.hint}>{hint}</div>}
    </div>
  );

  return (
    <div style={{ maxWidth: "860px", width: "100%", margin: "0 auto", padding: "1.5rem", boxSizing: "border-box" }}>
      <h1 style={{ fontSize: "1.4rem", fontWeight: 700, color: "#0B3D2E", marginBottom: "0.25rem" }}>
        Add Product
      </h1>
      <p style={{ color: "#64748B", marginBottom: "1rem", fontSize: "0.85rem" }}>
        {isSeller ? "Add a new product to your store" : "Create a new product listing"}
      </p>

      {msg && (
        <div style={{
          padding: "10px 14px", borderRadius: "6px", marginBottom: "1rem",
          background: msg.type === "success" ? "#EAF7EE" : "#fef2f2",
          color: msg.type === "success" ? "#146C43" : "#dc2626",
          border: `1px solid ${msg.type === "success" ? "#CFE8D6" : "#fecaca"}`,
          fontSize: "0.85rem"
        }}>
          {msg.type === "success" ? "\u2713 " : "\u2717 "}{msg.text}
        </div>
      )}

      {/* Basic Info */}
      <div style={S.section}>
        <h3 style={S.sectionTitle}>Basic Info</h3>
        {fld("Product Name *", "name", "text", null, "Required")}
        <div style={S.grid2}>
          {fld("Brand", "brand")}
          <div style={S.field}>
            <label style={S.label}>Category</label>
            <select style={S.select} value={form.category} onChange={e => set("category", e.target.value)}>
              <option value="">Select category</option>
              {categories.map(c => <option key={c.id ?? c.name} value={c.id ?? ""}>{c.name}</option>)}
            </select>
          </div>
        </div>
        {fld("Description", "description", "textarea")}
      </div>

      {/* Pricing & Inventory */}
      <div style={S.section}>
        <h3 style={S.sectionTitle}>Pricing & Inventory</h3>
        <div style={S.grid3}>
          {fld("Price (\u20B9) *", "price", "number")}
          {fld("Stock *", "stock", "number")}
          {fld("SKU", "sku")}
        </div>
      </div>

      {/* Product Details */}
      <div style={S.section}>
        <h3 style={S.sectionTitle}>Product Details</h3>
        <div style={S.grid2}>
          {fld("Manufacturer", "manufacturer")}
          {fld("Model Number", "modelNumber")}
        </div>
        <div style={S.grid2}>
          {fld("Barcode/UPC", "barcode", "text", null, "12-13 digits")}
          {fld("HSN Code", "hsnCode")}
        </div>
        <div style={S.grid2}>
          {fld("Country of Origin", "countryOfOrigin", null, COUNTRIES)}
          {fld("Warranty", "warranty", null, WARRANTY)}
        </div>
        <div style={S.grid2}>
          {fld("Return Policy", "returnPolicy", null, RETURN_POLICY)}
          {fld("Badges", "badges", null, BADGES)}
        </div>
        {fld("Short Description", "shortDescription", "textarea", null, "Max 150 characters")}
        {fld("Technical Specifications", "technicalSpecs", "textarea", null, "Format: RAM: 8GB; Storage: 256GB; Camera: 48MP")}
      </div>

      {/* SEO & Metadata */}
      <div style={S.section}>
        <h3 style={S.sectionTitle}>SEO & Metadata</h3>
        {fld("Meta Title", "metaTitle", "text", null, "Max 60 characters")}
        {fld("Meta Description", "metaDescription", "textarea", null, "Max 160 characters")}
        <div style={S.grid2}>
          {fld("Keywords", "keywords", "text", null, "Comma-separated")}
          {fld("Slug", "slug", "text", null, "Auto-generated from name")}
        </div>
      </div>

      {/* Logistics */}
      <div style={S.section}>
        <h3 style={S.sectionTitle}>Logistics</h3>
        <div style={S.grid3}>
          {fld("Weight (kg)", "weight", "number", null, "e.g. 0.25")}
          {fld("Dimensions (LxWxH)", "dimensions", "text", null, "e.g. 16x8x1")}
          {fld("Delivery Days", "deliveryDays", "number")}
        </div>
        {fld("Shipping Class", "shippingClass", null, SHIPPING_CLASS)}
      </div>

      {/* Product Images */}
      <div style={S.section}>
        <h3 style={S.sectionTitle}>Product Images</h3>
        <div style={{ display: "flex", gap: "10px", alignItems: "center", flexWrap: "wrap", marginBottom: "0.75rem" }}>
          <input
            type="file" ref={fileRef} multiple accept=".jpg,.jpeg,.png,.webp"
            onChange={(e) => handleFiles(e.target.files)}
            style={{ fontSize: "0.85rem" }}
          />
          <span style={{ fontSize: "0.75rem", color: "#64748B" }}>
            Max {MAX_IMAGES} images, JPG/PNG/WebP, 2MB each
          </span>
        </div>
        {imageErrors.length > 0 && (
          <div style={{ marginBottom: "0.5rem" }}>
            {imageErrors.map((err, i) => (
              <div key={i} style={{ fontSize: "0.75rem", color: "#dc2626" }}>{err}</div>
            ))}
          </div>
        )}
        {images.length > 0 ? (
          <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
            {images.map((f, i) => (
              <div key={i} style={{ position: "relative", width: "80px", height: "80px", borderRadius: "6px", overflow: "hidden", border: "1px solid #CFE8D6" }}>
                <img src={URL.createObjectURL(f)} alt="" style={{ width: "100%", height: "100%", objectFit: "cover" }} />
                <button type="button" onClick={() => removeImage(i)}
                  style={{ position: "absolute", top: "2px", right: "2px", width: "20px", height: "20px", borderRadius: "50%", border: "none", background: "rgba(220,38,38,0.8)", color: "#fff", cursor: "pointer", fontSize: "12px", lineHeight: "20px", padding: 0 }}>
                  &times;
                </button>
              </div>
            ))}
            {images.length < MAX_IMAGES && (
              <div onClick={() => fileRef.current?.click()}
                style={{ width: "80px", height: "80px", borderRadius: "6px", border: "2px dashed #CFE8D6", display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer", color: "#94a3b8", fontSize: "1.5rem" }}>
                +
              </div>
            )}
          </div>
        ) : (
          <div onClick={() => fileRef.current?.click()}
            style={{ padding: "1.5rem", textAlign: "center", border: "2px dashed #CFE8D6", borderRadius: "6px", cursor: "pointer", color: "#94a3b8", fontSize: "0.85rem" }}>
            Click to upload product images
          </div>
        )}
      </div>

      {/* Actions */}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px", marginTop: "0.5rem" }}>
        <button onClick={() => navigate(redirectPath)}
          style={{
            padding: "10px 20px", border: "1px solid #CFE8D6", borderRadius: "6px",
            background: "#fff", color: "#64748B", cursor: "pointer", fontWeight: 500, fontSize: "0.9rem"
          }}>
          Cancel
        </button>
        <button onClick={handleSubmit} disabled={submitting}
          style={{
            padding: "10px 32px",
            background: submitting ? "#94a3b8" : "#2E9B57",
            color: "#fff", border: "none", borderRadius: "6px",
            cursor: submitting ? "not-allowed" : "pointer", fontWeight: 600, fontSize: "0.9rem"
          }}>
          {submitting ? "Creating..." : "Create Product"}
        </button>
      </div>
    </div>
  );
};

export default AddProduct;
