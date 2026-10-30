import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../api/axios";
import HsnPicker from "./HsnPicker";
import GstRateChoice from "./GstRateChoice";
import GstPreview from "./GstPreview";
import ProductImagePicker, { uploadProductImages } from "./ProductImagePicker";

const FALLBACK_CATEGORIES = ["Electronics","Fashion","Home & Kitchen","Grocery","Beauty","Appliances","Books","Sports & Fitness","Toys & Games"].map((name, i) => ({ id: null, name }));
const COUNTRIES = ["India","USA","China","Japan","South Korea","Germany","Vietnam","Taiwan","Other"];
const WARRANTY = ["6 Months","1 Year","2 Years","3 Years","No Warranty"];
const RETURN_POLICY = ["7 Days","10 Days","15 Days","No Returns"];
const SHIPPING_CLASS = ["Standard","Express","Heavy","Fragile"];
const BADGES = ["None","New Arrival","Best Seller","Limited Offer","Discount"];
const UNITS_OF_MEASURE = ["NOS","KG","G","ML","L","M","SQ.M","CBM","PAIR","PCS","BOX","SET","ROLL","PACK","BOTTLE","CAN","JAR","TUBE","STRIP","UNIT"];

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
    prePackagedAndLabelled: true, gstRateSelectionId: undefined,
    countryOfOrigin: "India", shortDescription: "", technicalSpecs: "",
    warranty: "1 Year", returnPolicy: "7 Days",
    billDescription: "", cessRate: "", uom: "NOS",
    metaTitle: "", metaDescription: "", keywords: "", slug: "",
    weight: "", dimensions: "", shippingClass: "Standard", deliveryDays: "", badges: "None"
  });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [msg, setMsg] = useState(null);
  const [categories, setCategories] = useState(FALLBACK_CATEGORIES);
  const [images, setImages] = useState([]);

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
        prePackagedAndLabelled: form.prePackagedAndLabelled,
        gstRateSelectionId: form.gstRateSelectionId ?? null,
        countryOfOrigin: form.countryOfOrigin, shortDescription: form.shortDescription,
        technicalSpecs: form.technicalSpecs, warranty: form.warranty,
        returnPolicy: form.returnPolicy, metaTitle: form.metaTitle,
        metaDescription: form.metaDescription, metaKeywords: form.keywords,
        billDescription: form.billDescription.trim() || null,
        cessRate: form.cessRate !== "" ? parseFloat(form.cessRate) : null,
        uom: form.uom || "NOS",
        slug: form.slug || slugify(form.name),
        weight: form.weight ? parseFloat(form.weight) : null,
        dimensions: form.dimensions, shippingClass: form.shippingClass,
        deliveryDays: form.deliveryDays ? parseInt(form.deliveryDays) : null,
        badges: form.badges !== "None" ? [form.badges] : [],
      };
      const endpoint = isSeller ? "/api/seller/products" : "/api/admin/products/add";
      const res = await api.post(endpoint, data);
      const productId = res.data?.id;
      let failed = [];
      if (productId && images.length > 0) {
        setMsg({ type: "info", text: "Uploading images..." });
        failed = await uploadProductImages(productId, images);
      }
      if (failed.length > 0) {
        // Stay on the page so the message can be read; the product itself was saved.
        setMsg({ type: "error", text: `Product saved, but ${failed.length} image(s) could not be uploaded (${failed.join(", ")}). Add them from the product's Images page.` });
        setSubmitting(false);
        return;
      }
      // A seller's listing is reviewed by an admin before shoppers can see it.
      setMsg({ type: "success", text: isSeller
        ? `Product submitted${images.length > 0 ? " with images" : ""}. It will appear in the store once an admin approves it.`
        : `Product created${images.length > 0 ? " with images" : ""}.` });
      setTimeout(() => navigate(redirectPath), isSeller ? 2500 : 1000);
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
              {categories.map(c => <option key={c.id ?? c.name} value={c.id ?? ""}>{c.name}{c.requiredLicence ? ` (needs ${c.requiredLicence === "FSSAI" ? "FSSAI" : c.requiredLicence.replace("_", " ").toLowerCase()} licence)` : ""}</option>)}
            </select>
          </div>
        </div>
        {fld("Description", "description", "textarea")}
      </div>

      {/* Pricing & Inventory */}
      <div style={S.section}>
        <h3 style={S.sectionTitle}>Pricing & Inventory</h3>
        <div style={S.grid3}>
          {fld("Price incl. GST (\u20B9) *", "price", "number")}
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
          <HsnPicker
            value={form.hsnCode}
            onChange={(code) => set("hsnCode", code)}
            categoryId={form.category || null}
            productName={form.name}
            unitPrice={form.price ? Number(form.price) : null}
            prePackaged={form.prePackagedAndLabelled}
          />
        </div>
        {/*
          Staples are taxed on packaging, not just on what they are - rice is 5% pre-packaged
          and labelled but nil loose, and the same split runs through meat, dairy and cereals.
          The HSN code is identical either way, so only this answer can settle the rate.
        */}
        <label style={{ display: "flex", gap: "0.6rem", alignItems: "flex-start", margin: "0.5rem 0 0" }}>
          <input
            type="checkbox"
            checked={form.prePackagedAndLabelled}
            onChange={(e) => setForm({ ...form, prePackagedAndLabelled: e.target.checked })}
            style={{ marginTop: 3 }}
          />
          <span>
            <span style={{ fontWeight: 500, fontSize: "0.9rem" }}>Sold pre-packaged and labelled</span>
            <span style={{ display: "block", color: "#6b7280", fontSize: "0.78rem" }}>
              Decides the GST on staples such as rice, flour, dairy and packed meat, which are
              taxed at 5% packaged and nil loose. Leave ticked for anything shipped in a sealed,
              labelled pack.
            </span>
          </span>
        </label>
        <GstRateChoice
          hsnCode={form.hsnCode}
          unitPrice={form.price ? parseFloat(form.price) : undefined}
          prePackaged={form.prePackagedAndLabelled}
          value={form.gstRateSelectionId}
          onChange={(rateId) => set("gstRateSelectionId", rateId)}
        />
        <GstPreview
          hsnCode={form.hsnCode}
          unitPrice={form.price ? parseFloat(form.price) : undefined}
          prePackaged={form.prePackagedAndLabelled}
        />

        {/* Bill & Print — what a tax invoice shows for this line. Defaults to the product name,
            so most sellers never touch it; it exists for the ones who need the invoice to read
            differently from the listing (e.g. "Basmati Rice 5kg" on the shelf, "rice (premium
            basmati)" on the bill). UOM prints next to the quantity; cess is a declaration and
            is only ever charged at the CBIC's published rate for the code. */}
        <h4 style={{ fontSize: "0.85rem", fontWeight: 700, color: "#0B3D2E", margin: "1rem 0 0.5rem" }}>
          Bill &amp; Print
        </h4>
        {fld("Bill / invoice description", "billDescription", "textarea", null,
          "Printed on invoices and tax bills. Leave blank to use the product name.")}
        <div style={S.grid2}>
          {fld("Unit of measure", "uom", null, UNITS_OF_MEASURE, "Printed beside the quantity, e.g. 5 KG.")}
          {fld("Cess rate %", "cessRate", "number", null,
            "Declaration only — cess is charged at the CBIC rate for this code, never at this number.")}
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

      <ProductImagePicker images={images} onChange={setImages} sectionStyle={S.section} titleStyle={S.sectionTitle} />

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
