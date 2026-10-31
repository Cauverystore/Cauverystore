import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams, useLocation, Link } from "react-router-dom";
import api from "../api/axios";
import HsnPicker from "../admin/products/HsnPicker";
import GstRateChoice from "../admin/products/GstRateChoice";
import {
  ComplianceField, ScoreBadge, checkFields, detailsBody, detailsFromServer, errorText,
} from "../admin/compliance/ComplianceEditor";
import { CATEGORY_SECTIONS, SECTIONS, STATUS_LABEL, isIndia } from "../admin/compliance/complianceFields";

/*
  Seller product wizard: seven steps from a new listing to one ready for review.

  1 Basic information   2 Pricing and GST   3 Inventory   4 Legal Metrology
  5 Manufacturer, origin and customer care   6 Category details and dates   7 Review and submit

  Step 1 creates the product as a draft (pending approval, so shoppers never see it half-made).
  Every later step saves as it goes. Completion is the compliance engine's own score, so the
  wizard and the admin team always see the same number.
*/

const section = (id) => SECTIONS.find((s) => s.id === id).fields;

const STEP_FIELDS = [
  ["name", "description", "category", "brand", "sku"],
  ["mrp", "price", "hsnCode", "gstRate"],
  ["stock", "minOrderQty", "maxOrderQty"],
  ["netQuantity", "grossQuantity", "quantityUnit"],
  ["manufacturerName", "manufacturerAddress", "packerName", "packerAddress", "importerName", "importerAddress",
    "countryOfOrigin", "stateOfOrigin", "customerCarePhone", "customerCareEmail"],
  ["mfgDate", "batchNumber", "expiryDate", "bestBeforeDate", "warranty",
    ...new Set(Object.values(CATEGORY_SECTIONS).flatMap((fs) => fs.map((f) => f.key)))],
  ["sellerId", "sellerGstin", "sellerBusinessName", "sellerBusinessAddress", "sellerLicence"],
];
const STEP_TITLES = [
  "Basic information", "Pricing and GST", "Inventory", "Legal Metrology",
  "Manufacturer, origin and care", "Category details and dates", "Review and submit",
];

const S = {
  card: { background: "#fff", border: "1px solid #CFE8D6", borderRadius: 8, padding: "1.1rem", marginBottom: "1rem" },
  grid: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "0.75rem" },
  label: { display: "block", fontSize: "0.8rem", fontWeight: 600, color: "#374151", marginBottom: 3 },
  input: { width: "100%", padding: "8px 10px", border: "1px solid #CFE8D6", borderRadius: 6, fontSize: "0.88rem", boxSizing: "border-box", background: "#fff" },
  btn: { padding: "8px 16px", border: "none", borderRadius: 6, cursor: "pointer", fontSize: "0.88rem", fontWeight: 600 },
};
const primary = { ...S.btn, background: "#146C43", color: "#fff" };
const secondary = { ...S.btn, background: "#fff", color: "#146C43", border: "1px solid #CFE8D6" };

const EMPTY_PRODUCT = {
  name: "", brand: "", category: "", sku: "", description: "",
  mrp: "", price: "", hsnCode: "", prePackagedAndLabelled: true, gstRateSelectionId: undefined,
  stock: "", countryOfOrigin: "India", manufacturer: "", warranty: "", returnPolicy: "",
};

const SellerProductWizard = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  // Creating the draft moves to the /:id route, which mounts afresh; it carries the next step.
  const [step, setStep] = useState(location.state?.step ?? 0);
  const [categories, setCategories] = useState([]);
  const [current, setCurrent] = useState(null);    // the product as last loaded
  const [form, setForm] = useState(EMPTY_PRODUCT);
  const [details, setDetails] = useState(detailsFromServer(null));
  const [compliance, setCompliance] = useState(null);
  const [status, setStatus] = useState("DRAFT");
  const [statusNote, setStatusNote] = useState("");
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState(null);

  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));
  const setD = (k, v) => setDetails((d) => ({ ...d, [k]: v }));

  useEffect(() => {
    api.get("/api/categories").then((r) => setCategories(Array.isArray(r.data) ? r.data.filter((c) => c && c.id) : [])).catch(() => {});
  }, []);

  const load = useCallback(async () => {
    if (!id) return;
    try {
      const [p, c] = await Promise.all([
        api.get(`/api/seller/products/${id}`),
        api.get(`/api/compliance/products/${id}`),
      ]);
      const prod = p.data?.product || p.data;
      setCurrent(prod);
      setForm({
        name: prod.name || "", brand: prod.brand || "", category: prod.category?.id ?? "", sku: prod.sku || "",
        description: prod.description || "", mrp: prod.mrp ?? "", price: prod.price ?? "", hsnCode: prod.hsnCode || "",
        prePackagedAndLabelled: prod.prePackagedAndLabelled !== false, gstRateSelectionId: prod.gstRateSelectionId ?? undefined,
        stock: prod.stock ?? "", countryOfOrigin: prod.countryOfOrigin || "India", manufacturer: prod.manufacturer || "",
        warranty: prod.warranty || "", returnPolicy: prod.returnPolicy || "",
      });
      setDetails(detailsFromServer(c.data.details));
      setCompliance(c.data.compliance);
      setStatus(c.data.status);
      setStatusNote(c.data.details?.statusNote || "");
    } catch (e) {
      setMsg({ type: "error", text: errorText(e, "Could not load this product.") });
    }
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const missing = useMemo(() => new Set(compliance?.missingFields || []), [compliance]);
  const stepDone = (i) => !!compliance && STEP_FIELDS[i].every((k) => !missing.has(k));
  const categoryType = useMemo(() => {
    const c = categories.find((x) => String(x.id) === String(form.category));
    return c?.complianceType || "GENERAL";
  }, [categories, form.category]);

  // ---------------------------------------------------------------- saving

  /** The seller product update, keeping on-sale and status as they are. */
  const putProduct = (fields) => api.put(`/api/seller/products/${id}`, {
    ...fields, active: current?.active ?? true, productStatus: current?.productStatus || "draft",
  });
  const putBasics = (fields) => api.put(`/api/compliance/products/${id}/basics`, fields);
  const putDetails = () => api.put(`/api/compliance/products/${id}`, detailsBody(details));

  const validate = () => {
    const e = {};
    if (step === 0) {
      if (!form.name.trim()) e.name = "Required";
      if (!form.category) e.category = "Choose a category";
    }
    if (step === 1) {
      if (!form.price || Number(form.price) <= 0) e.price = "More than zero";
      if (form.mrp !== "" && Number(form.mrp) <= 0) e.mrp = "More than zero";
      if (form.mrp && Number(form.price) > Number(form.mrp)) e.price = "Above the MRP";
    }
    if (step === 2) {
      if (form.stock === "" || Number(form.stock) < 0) e.stock = "Zero or more";
      if (details.minOrderQty && details.maxOrderQty && Number(details.maxOrderQty) < Number(details.minOrderQty)) e.maxOrderQty = "Below the minimum";
    }
    if (step === 3 && details.netQuantity && details.grossQuantity && Number(details.grossQuantity) < Number(details.netQuantity)) {
      e.grossQuantity = "Less than the net quantity";
    }
    Object.assign(e, checkFields(stepFields(step), details));
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const save = async () => {
    if (!validate()) { setMsg({ type: "error", text: "Fix the highlighted fields first." }); return false; }
    setBusy(true); setMsg(null);
    try {
      if (step === 0) {
        const basic = { name: form.name.trim(), brand: form.brand, category: { id: form.category }, sku: form.sku, description: form.description };
        if (!id) {
          // A draft: not on sale, and no HSN asked for yet.
          const r = await api.post("/api/seller/products", { ...basic, price: 0, stock: 0, productStatus: "draft" });
          setBusy(false);
          navigate(`/seller/products/wizard/${r.data.id}`, { replace: true, state: { step: 1 } });
          return false;   // the new route opens at step 2 itself
        }
        await putProduct(basic);
      } else if (step === 1) {
        await putProduct({ price: Number(form.price), hsnCode: form.hsnCode || null,
          prePackagedAndLabelled: form.prePackagedAndLabelled, gstRateSelectionId: form.gstRateSelectionId ?? null });
        await putBasics({ mrp: form.mrp === "" ? null : form.mrp });
      } else if (step === 2) {
        await putProduct({ stock: Number(form.stock) });
        await putDetails();
      } else if (step === 3) {
        await putDetails();
      } else if (step === 4) {
        await putBasics({ countryOfOrigin: form.countryOfOrigin, manufacturer: form.manufacturer });
        await putDetails();
      } else if (step === 5) {
        await putBasics({ warranty: form.warranty, returnPolicy: form.returnPolicy });
        await putDetails();
      }
      await load();
      setBusy(false);
      return true;
    } catch (e) {
      setMsg({ type: "error", text: errorText(e, "Could not save this step.") });
      setBusy(false);
      return false;
    }
  };

  const next = async () => { if (await save()) setStep((s) => Math.min(6, s + 1)); };

  const act = async (action) => {
    setBusy(true); setMsg(null);
    try {
      const r = await api.post(`/api/compliance/products/${id}/workflow/${action}`,
        action === "reject" ? { note: "Taken back to draft by the seller." } : {});
      await load();
      setMsg({ type: r.data.status === "DRAFT" ? "error" : "success",
        text: r.data.status === "DRAFT" ? (r.data.statusNote || "Not ready yet.") : `Now: ${STATUS_LABEL[r.data.status] || r.data.status}.` });
    } catch (e) {
      setMsg({ type: "error", text: errorText(e, "That step could not be completed.") });
    }
    setBusy(false);
  };

  // ---------------------------------------------------------------- fields per step

  function stepFields(i) {
    const imported = form.countryOfOrigin && !isIndia(form.countryOfOrigin);
    if (i === 2) return section("inventory");
    if (i === 3) return section("legalMetrology");
    if (i === 4) {
      return [
        ...section("parties").filter((f) => !(f.importedOnly && !imported) && !(f.hideIf && f.hideIf(details))),
        ...(isIndia(form.countryOfOrigin) ? section("origin") : []),
        ...section("customerCare"),
      ];
    }
    if (i === 5) return [...(CATEGORY_SECTIONS[categoryType] || []), ...section("dates")];
    return [];
  }

  const productInput = (key, label, type = "text") => (
    <div>
      <label style={S.label}>{label}{missing.has(key) ? " *" : ""}</label>
      {type === "textarea"
        ? <textarea style={{ ...S.input, minHeight: 72 }} value={form[key]} onChange={(e) => set(key, e.target.value)} />
        : <input style={S.input} type={type} value={form[key]} min={type === "number" ? 0 : undefined} step={type === "number" ? "any" : undefined}
            onChange={(e) => set(key, e.target.value)} />}
      {errors[key] && <div style={{ color: "#dc2626", fontSize: "0.7rem" }}>{errors[key]}</div>}
    </div>
  );
  const detailInputs = (fields) => fields.map((f) => (
    <ComplianceField key={f.key} f={f} value={details[f.key]} onChange={(v) => setD(f.key, v)} missing={missing.has(f.key)} error={errors[f.key]} />
  ));

  const score = compliance?.score ?? 0;
  const editable = ["DRAFT", "SELLER_REVIEW", "PUBLISHED"].includes(status) || !id;

  return (
    <div style={{ maxWidth: 1000 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 8 }}>
        <h1 style={{ fontSize: "1.4rem", fontWeight: 700, margin: 0 }}>{id ? form.name || "Product" : "New product"}</h1>
        <Link to="/seller/products" style={{ fontSize: "0.85rem" }}>Back to products</Link>
      </div>

      {/* Completion */}
      <div style={{ ...S.card, marginTop: "0.75rem" }}>
        <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.85rem", marginBottom: 6 }}>
          <span><strong>{id ? `${score}% complete` : "0% complete"}</strong>{compliance && <> · <ScoreBadge score={score} band={compliance.band} /></>}</span>
          <span>{id ? (STATUS_LABEL[status] || status) : "Not saved yet"}</span>
        </div>
        <div style={{ height: 8, background: "#EAF7EE", borderRadius: 999, overflow: "hidden" }} role="progressbar" aria-valuenow={score} aria-valuemin={0} aria-valuemax={100}>
          <div style={{ width: `${id ? score : 0}%`, height: "100%", background: score >= 100 ? "#146C43" : score >= 90 ? "#d97706" : "#dc2626" }} />
        </div>
        <ol style={{ display: "flex", flexWrap: "wrap", gap: 6, listStyle: "none", padding: 0, margin: "0.75rem 0 0" }}>
          {STEP_TITLES.map((t, i) => (
            <li key={t}>
              <button disabled={!id && i > 0} onClick={() => setStep(i)}
                style={{ ...S.btn, padding: "5px 10px", fontSize: "0.78rem", fontWeight: i === step ? 700 : 500,
                  background: i === step ? "#146C43" : stepDone(i) ? "#EAF7EE" : "#fff",
                  color: i === step ? "#fff" : "#0B3D2E", border: "1px solid #CFE8D6", cursor: !id && i > 0 ? "not-allowed" : "pointer" }}>
                {stepDone(i) && i !== step ? "✓ " : `${i + 1}. `}{t}
              </button>
            </li>
          ))}
        </ol>
      </div>

      {statusNote && status === "DRAFT" && <div style={{ ...S.card, background: "#fff7ed", color: "#b45309" }}>{statusNote}</div>}
      {!editable && <div style={{ ...S.card, background: "#f8fafc" }}>This listing is with the Cauvery Store team ({STATUS_LABEL[status]}). Changing it now sends it back to draft.</div>}
      {msg && <div style={{ ...S.card, background: msg.type === "error" ? "#fef2f2" : "#EAF7EE", color: msg.type === "error" ? "#b91c1c" : "#146C43" }}>{msg.text}</div>}

      <div style={S.card}>
        <h2 style={{ fontSize: "1.05rem", margin: "0 0 0.75rem" }}>{step + 1}. {STEP_TITLES[step]}</h2>

        {step === 0 && (
          <div style={S.grid}>
            {productInput("name", "Product name")}
            {productInput("brand", "Brand")}
            <div>
              <label style={S.label}>Category</label>
              <select style={S.input} value={form.category} onChange={(e) => set("category", e.target.value)}>
                <option value="">Choose a category</option>
                {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
              {errors.category && <div style={{ color: "#dc2626", fontSize: "0.7rem" }}>{errors.category}</div>}
              {categoryType !== "GENERAL" && <div style={{ fontSize: "0.72rem", color: "#64748B", marginTop: 2 }}>This category asks for {categoryType.toLowerCase()} details in step 6.</div>}
            </div>
            {productInput("sku", "SKU")}
            <div style={{ gridColumn: "1 / -1" }}>{productInput("description", "Description", "textarea")}</div>
          </div>
        )}

        {step === 1 && (
          <>
            <div style={S.grid}>
              {productInput("mrp", "MRP (₹, printed on the pack)", "number")}
              {productInput("price", "Selling price incl. GST (₹)", "number")}
            </div>
            <div style={{ marginTop: 10 }}>
              <HsnPicker value={form.hsnCode} onChange={(code) => set("hsnCode", code)} categoryId={form.category || null}
                productName={form.name} unitPrice={form.price ? Number(form.price) : null} prePackaged={form.prePackagedAndLabelled} />
            </div>
            <label style={{ display: "flex", gap: 8, margin: "0.5rem 0", fontSize: "0.88rem" }}>
              <input type="checkbox" checked={form.prePackagedAndLabelled}
                onChange={(e) => setForm((f) => ({ ...f, prePackagedAndLabelled: e.target.checked, gstRateSelectionId: undefined }))} />
              Sold pre-packaged and labelled (decides the GST on staples such as rice, flour and dairy)
            </label>
            <GstRateChoice hsnCode={form.hsnCode} unitPrice={form.price ? Number(form.price) : undefined}
              prePackaged={form.prePackagedAndLabelled} value={form.gstRateSelectionId} onChange={(v) => set("gstRateSelectionId", v)} />
          </>
        )}

        {step === 2 && <div style={S.grid}>{productInput("stock", "Stock", "number")}{detailInputs(stepFields(2))}</div>}
        {step === 3 && <div style={S.grid}>{detailInputs(stepFields(3))}</div>}

        {step === 4 && (
          <div style={S.grid}>
            {productInput("countryOfOrigin", "Country of origin")}
            {productInput("manufacturer", "Manufacturer / brand owner (short name)")}
            {detailInputs(stepFields(4))}
          </div>
        )}

        {step === 5 && (
          <div style={S.grid}>
            {productInput("warranty", "Warranty")}
            {productInput("returnPolicy", "Return and refund policy")}
            {detailInputs(stepFields(5))}
          </div>
        )}

        {step === 6 && (
          <div>
            {compliance && compliance.issues.length === 0 && <p style={{ color: "#146C43" }}>Everything is filled in. Submit it for review.</p>}
            {compliance && compliance.issues.length > 0 && (
              <>
                <p style={{ fontSize: "0.88rem" }}>Still missing (the step to fix it is in brackets):</p>
                <ul style={{ fontSize: "0.85rem" }}>
                  {compliance.issues.slice().sort((a, b) => b.critical - a.critical).map((i) => {
                    const s = STEP_FIELDS.findIndex((fs) => fs.includes(i.field));
                    return (
                      <li key={i.field} style={{ color: i.critical ? "#b91c1c" : "#b45309" }}>
                        {i.message}{" "}
                        {s >= 0 && s < 6 && <button style={{ ...S.btn, padding: "0 6px", background: "none", color: "#146C43", textDecoration: "underline" }} onClick={() => setStep(s)}>(step {s + 1})</button>}
                        {s === 6 && <Link to="/seller/store" style={{ fontSize: "0.8rem" }}> (store profile)</Link>}
                      </li>
                    );
                  })}
                </ul>
              </>
            )}
            <p style={{ fontSize: "0.78rem", color: "#64748B" }}>
              100 can be published. 90 to 99 can be published with a warning. Below 90, or with anything in red, it cannot be published.
            </p>
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              {status === "DRAFT" && <button style={primary} disabled={busy} onClick={() => act("submit")}>Submit for compliance check</button>}
              {status === "SELLER_REVIEW" && <button style={primary} disabled={busy} onClick={() => act("confirm")}>Confirm and send to Cauvery Store</button>}
              {status === "SELLER_REVIEW" && <button style={secondary} disabled={busy} onClick={() => act("reject")}>Back to draft</button>}
              {status === "ADMIN_REVIEW" && <span style={{ fontSize: "0.88rem" }}>Waiting for the Cauvery Store team to review it.</span>}
              {status === "APPROVED" && <span style={{ fontSize: "0.88rem" }}>Approved; it goes on sale when the team publishes it.</span>}
              {status === "PUBLISHED" && <span style={{ fontSize: "0.88rem", color: "#146C43" }}>On the store.</span>}
            </div>
          </div>
        )}

        {step < 6 && (
          <div style={{ display: "flex", gap: 8, marginTop: "1rem" }}>
            {step > 0 && <button style={secondary} disabled={busy} onClick={() => setStep(step - 1)}>Back</button>}
            <button style={primary} disabled={busy} onClick={next}>{busy ? "Saving..." : step === 0 && !id ? "Create draft and continue" : "Save and continue"}</button>
            {id && <button style={secondary} disabled={busy} onClick={save}>Save</button>}
          </div>
        )}
      </div>
    </div>
  );
};

export default SellerProductWizard;
