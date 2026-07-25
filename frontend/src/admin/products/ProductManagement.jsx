import React, { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { Download } from "lucide-react";
import api from "../../api/axios";
import { imgUrl } from "../../utils/images";
import "../../styles/admin.css";

const CATEGORIES = ["Electronics","Fashion","Home & Kitchen","Grocery","Beauty","Appliances","Books","Sports & Fitness","Toys & Games"];
const COUNTRIES = ["India","USA","China","Japan","South Korea","Germany","Vietnam","Taiwan","Other"];
const WARRANTY = ["6 Months","1 Year","2 Years","3 Years","No Warranty"];
const RETURN_POLICY = ["7 Days","10 Days","15 Days","No Returns"];
const SHIPPING_CLASS = ["Standard","Express","Heavy","Fragile"];
const BADGES = ["None","New Arrival","Best Seller","Limited Offer","Discount"];

function slugify(t) { return t.toLowerCase().replace(/[^a-z0-9]+/g,"-").replace(/^-|-$/g,"").substring(0,80); }

const S = {
  field: { marginBottom: "0.65rem" },
  label: { display: "block", fontSize: "0.8rem", fontWeight: 600, color: "#374151", marginBottom: "3px" },
  input: { width: "100%", padding: "8px 10px", border: "1px solid #CFE8D6", borderRadius: "6px", fontSize: "0.9rem", outline: "none", boxSizing: "border-box", background: "#fff" },
  select: { width: "100%", padding: "8px 10px", border: "1px solid #CFE8D6", borderRadius: "6px", fontSize: "0.9rem", background: "#fff", outline: "none", boxSizing: "border-box" },
  textarea: { width: "100%", padding: "8px 10px", border: "1px solid #CFE8D6", borderRadius: "6px", fontSize: "0.9rem", minHeight: "72px", resize: "vertical", outline: "none", boxSizing: "border-box" },
  section: { background: "#fff", borderRadius: "8px", border: "1px solid #CFE8D6", padding: "1rem", marginBottom: "0.75rem" },
  sTitle: { fontSize: "0.9rem", fontWeight: 700, color: "#0B3D2E", marginBottom: "0.75rem", paddingBottom: "0.4rem", borderBottom: "2px solid #EAF7EE" },
};

const ProductManagement = () => {
  const navigate = useNavigate();
  const isSeller = window.location.pathname.startsWith("/seller");
  const role = localStorage.getItem("role") || "";
  const isSuperAdmin = role === "SUPER_ADMIN" || role === "SUPER_ADMIN";
  const isAdmin = isSuperAdmin || role === "ADMIN";
  const canManageCategories = isAdmin || role === "EXECUTIVE" || isSuperAdmin;

  const [tab, setTab] = useState("add");
  const [form, setForm] = useState({ name:"",brand:"",category:"Electronics",price:"",stock:"",sku:"",description:"",manufacturer:"",modelNumber:"",barcode:"",hsnCode:"",countryOfOrigin:"India",shortDescription:"",technicalSpecs:"",warranty:"1 Year",returnPolicy:"7 Days",metaTitle:"",metaDescription:"",keywords:"",slug:"",weight:"",dimensions:"",shippingClass:"Standard",deliveryDays:"",badges:"None" });
  const [submitting, setSubmitting] = useState(false);
  const [msg, setMsg] = useState(null);
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [newCat, setNewCat] = useState("");
  const [inventory, setInventory] = useState([]);
  const [file, setFile] = useState(null);
  const fileRef = useRef(null);
  const [editing, setEditing] = useState(null);
  const [editForm, setEditForm] = useState({});
  const [selected, setSelected] = useState(new Set());
  const [filters, setFilters] = useState({ code:"", name:"", priceMin:"", priceMax:"", stockMin:"", stockMax:"", status:"all" });

  const switchTab = (t) => { setTab(t); setMsg(null); };
  useEffect(() => { loadProducts(); loadCategories(); }, []);
  useEffect(() => { if(tab==="list"||tab==="inventory") loadProducts(); }, [tab]);
  const loadProducts = () => api.get(isSeller?"/api/seller/products":"/api/admin/products/all").then(r => { const data = Array.isArray(r.data)?r.data:r.data?.content||[]; setProducts(data); }).catch(e=>setMsg({type:"error",text:"Failed to load: "+(e.response?.status||e.message)}));
  const loadCategories = () => { api.get("/api/categories").then(r => {setCategories(Array.isArray(r.data)?r.data:[]); window.dispatchEvent(new CustomEvent("category-changed"));}).catch(()=>{}); };
  const refresh = () => { loadProducts(); setMsg({type:"success",text:"Refreshed"}); };

  const toggleSel = (id) => { const s = new Set(selected); s.has(id)?s.delete(id):s.add(id); setSelected(s); };
  const selectAll = () => { if(selected.size===filtered.length) setSelected(new Set()); else setSelected(new Set(filtered.map(p=>p.id))); };
  const bulkDelete = async () => { if(selected.size===0)return; if(!window.confirm(`Delete ${selected.size} product(s)?`))return;
    try{const ep=isSeller?"/api/seller/products/bulk-delete":"/api/admin/products/bulk-delete"; await api.post(ep,{ids:Array.from(selected)}); setSelected(new Set()); setTimeout(()=>loadProducts(),300); setMsg({type:"success",text:"Deleted"});}catch(e){setMsg({type:"error",text:"Bulk delete failed"});} };

  const setF = (k,v) => setFilters(f=>({...f,[k]:v}));
  const filtered = products.filter(p => {
    if(filters.code && !(p.productCode||"").toLowerCase().includes(filters.code.toLowerCase())) return false;
    if(filters.name && !(p.name||"").toLowerCase().includes(filters.name.toLowerCase())) return false;
    if(filters.priceMin && p.price < parseFloat(filters.priceMin)) return false;
    if(filters.priceMax && p.price > parseFloat(filters.priceMax)) return false;
    if(filters.stockMin && p.stock < parseInt(filters.stockMin)) return false;
    if(filters.stockMax && p.stock > parseInt(filters.stockMax)) return false;
    if(filters.status==="active" && !p.active) return false;
    if(filters.status==="suspended" && p.active) return false;
    return true;
  });

  const set = (k, v) => { const u = {...form, [k]:v}; if(k==="name"&&!form.slug) u.slug=slugify(v); setForm(u); };
  const fld = (label, key, type="text", opts=null, hint=null) => (
    <div style={S.field}><label style={S.label}>{label}</label>
      {opts ? <select style={S.select} value={form[key]} onChange={e=>set(key,e.target.value)}>{opts.map(o=><option key={o}>{o}</option>)}</select>
       : type==="textarea" ? <textarea style={S.textarea} value={form[key]} onChange={e=>set(key,e.target.value)} />
       : <input style={S.input} type={type} value={form[key]} onChange={e=>set(key,e.target.value)} min={type==="number"?0:undefined} step={type==="number"&&key==="weight"?"0.01":undefined} />}
      {hint && <div style={{fontSize:"0.7rem",color:"#64748B",marginTop:"2px"}}>{hint}</div>}
    </div>
  );

  const handleAdd = async () => {
    if (!form.name||!form.price||!form.stock) { setMsg({type:"error",text:"Name, Price, and Stock are required."}); return; }
    setSubmitting(true); setMsg(null);
    try {
      const ep = isSeller?"/api/seller/products":"/api/admin/products/add";
      const data = { name:form.name,brand:form.brand,price:parseFloat(form.price),stock:parseInt(form.stock),sku:form.sku,description:form.description,manufacturer:form.manufacturer,modelNumber:form.modelNumber,barcode:form.barcode,hsnCode:form.hsnCode,countryOfOrigin:form.countryOfOrigin,shortDescription:form.shortDescription,technicalSpecs:form.technicalSpecs,warranty:form.warranty,returnPolicy:form.returnPolicy,metaTitle:form.metaTitle,metaDescription:form.metaDescription,metaKeywords:form.keywords,slug:form.slug||slugify(form.name),weight:form.weight?parseFloat(form.weight):null,dimensions:form.dimensions,shippingClass:form.shippingClass,deliveryDays:form.deliveryDays?parseInt(form.deliveryDays):null,badges:form.badges!=="None"?[form.badges]:[] };
      await api.post(ep, data);
      setMsg({type:"success",text:"Product created!"}); loadProducts();
      setForm({ name:"",brand:"",category:"Electronics",price:"",stock:"",sku:"",description:"",manufacturer:"",modelNumber:"",barcode:"",hsnCode:"",countryOfOrigin:"India",shortDescription:"",technicalSpecs:"",warranty:"1 Year",returnPolicy:"7 Days",metaTitle:"",metaDescription:"",keywords:"",slug:"",weight:"",dimensions:"",shippingClass:"Standard",deliveryDays:"",badges:"None" });
    } catch (err) { setMsg({type:"error",text:err.response?.data?.error||"Failed"}); }
    setSubmitting(false);
  };

  const [newCatDesc, setNewCatDesc] = useState("");
  const [editCatId, setEditCatId] = useState(null);
  const [editCatForm, setEditCatForm] = useState({ name: "", description: "" });
  const [deleteCatTarget, setDeleteCatTarget] = useState(null);
  const [catErrors, setCatErrors] = useState({});

  const addCategory = async () => {
    if (!newCat.trim()) { setCatErrors({ name: "Name is required" }); return; }
    if (newCatDesc.trim() && newCatDesc.trim().length < 10) { setCatErrors({ description: "Min 10 chars" }); return; }
    if (newCatDesc.length > 500) { setCatErrors({ description: "Max 500 chars" }); return; }
    try {
      await api.post("/api/categories/admin/add", { name: newCat.trim(), description: newCatDesc.trim() || null });
      setNewCat(""); setNewCatDesc(""); setCatErrors({}); loadCategories();
      window.dispatchEvent(new CustomEvent("category-changed"));
      setMsg({ type: "success", text: "Category added!" });
    } catch (e) { setMsg({ type: "error", text: "Failed to add category" }); }
  };

  const startEditCat = (c) => { setEditCatId(c.id); setEditCatForm({ name: c.name || "", description: c.description || "" }); setCatErrors({}); };
  const cancelEditCat = () => { setEditCatId(null); setEditCatForm({ name: "", description: "" }); setCatErrors({}); };

  const saveEditCat = async () => {
    if (!editCatForm.name.trim()) { setCatErrors({ name: "Name is required" }); return; }
    if (editCatForm.description.trim() && editCatForm.description.trim().length < 10) { setCatErrors({ description: "Min 10 chars" }); return; }
    if (editCatForm.description.length > 500) { setCatErrors({ description: "Max 500 chars" }); return; }
    try {
      await api.put("/api/categories/admin/update/" + editCatId, { name: editCatForm.name.trim(), description: editCatForm.description.trim() || null });
      loadCategories();
      window.dispatchEvent(new CustomEvent("category-changed"));
      cancelEditCat();
      setMsg({ type: "success", text: "Category updated!" });
    } catch (e) { setMsg({ type: "error", text: e.response?.data?.error || "Update failed" }); }
  };

  const delCategory = async (id) => {
    try {
      const r = await api.delete("/api/categories/admin/delete/" + id);
      if (r.data?.error) throw new Error(r.data.error);
      loadCategories();
      window.dispatchEvent(new CustomEvent("category-changed"));
      setMsg({ type: "success", text: "Category deleted!" });
    } catch (e) {
      setMsg({ type: "error", text: e.response?.data?.error || e.message || "Delete failed" });
    }
    setDeleteCatTarget(null);
  };

  const handleBulk = async () => { if(!file)return; const fd=new FormData(); fd.append("file",file); setSubmitting(true);
    try{const ep=isSeller?"/api/seller/products/bulk-upload":"/api/admin/products/bulk-upload"; const r=await api.post(ep,fd); setMsg({type:"success",text:r.data.message||"Uploaded"}); setFile(null); if(fileRef.current) fileRef.current.value=""; await loadProducts(); }catch(e){const err=e.response?.data?.error||e.response?.status||e.message; setMsg({type:"error",text:"Upload failed: "+(err||"Unknown error")});} setSubmitting(false); };

  const exportExcel = () => { window.open("http://localhost:9091/api/admin/products/export", "_blank"); };
  const toggleStatus = async (id, active) => { try{await api.put("/api/admin/products/"+id+"/status",{active}); loadProducts(); }catch{}; };
  const delProduct = async (id) => { if(!window.confirm("Delete this product?"))return; try{await api.delete(isSeller?"/api/seller/products/"+id:"/api/admin/products/"+id); loadProducts(); setMsg({type:"success",text:"Deleted"}); }catch(e){setMsg({type:"error",text:"Delete failed"});} };
  const saveEdit = async () => { try{const ep=isSeller?"/api/seller/products/"+editing.id:"/api/admin/products/"+editing.id; await api.put(ep,editForm); setMsg({type:"success",text:"Updated!"}); setEditing(null); setTab("list"); loadProducts(); }catch(e){setMsg({type:"error",text:e.response?.data?.error||"Update failed"});} };
  const fldEdit = (label,key,type="text") => (<div style={S.field}><label style={S.label}>{label}</label>{type==="textarea"?<textarea style={S.textarea} value={editForm[key]||""} onChange={e=>setEditForm({...editForm,[key]:e.target.value})}/>:<input style={S.input} type={type} value={editForm[key]||""} onChange={e=>setEditForm({...editForm,[key]:e.target.value})}/>}</div>);

  const tabStyle = (t) => ({
    padding: "10px 20px", border: "none", background: tab===t?"#146C43":"transparent",
    color: tab===t?"#7FFFD4":"#64748B", borderRadius: "6px 6px 0 0", cursor: "pointer",
    fontWeight: 600, fontSize: "0.85rem", whiteSpace: "nowrap", transition: "all 0.15s"
  });

  const tabs = [
    { key: "add", label: "Add Product" },
    { key: "bulk", label: "Bulk Upload" },
    { key: "list", label: "Products" },
    { key: "inventory", label: "Inventory" },
    { key: "reports", label: "Reports" },
    ...(canManageCategories ? [{ key: "categories", label: "Categories" }] : []),
  ];

  return (
    <div style={{ maxWidth: "920px", padding: "1.5rem" }}>
      <h1 style={{ fontSize: "1.4rem", fontWeight: 700, color: "#0B3D2E", marginBottom: "1rem" }}>Product Management</h1>

      <div style={{ display: "flex", gap: "4px", marginBottom: "1rem", borderBottom: "2px solid #EAF7EE", overflowX: "auto" }}>
        {tabs.map(t => <button key={t.key} onClick={()=>switchTab(t.key)} style={tabStyle(t.key)}>{t.label}</button>)}
      </div>

      {msg && (<div style={{ padding: "10px 14px", borderRadius: "6px", marginBottom: "0.75rem", background: msg.type==="success"?"#EAF7EE":"#fef2f2", color: msg.type==="success"?"#146C43":"#dc2626", border: `1px solid ${msg.type==="success"?"#CFE8D6":"#fecaca"}`, fontSize:"0.85rem" }}>{msg.type==="success"?"✓ ":"✕ "}{msg.text}</div>)}

      {/* Add Product */}
      {tab === "add" && (<>
        <div style={S.section}><h3 style={S.sTitle}>Basic Info</h3>
          {fld("Product Name *", "name")}
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:"0.75rem"}}>          {fld("Brand","brand")}{fld("Category","category",null,categories.length>0?categories.map(c=>c.name):CATEGORIES)}</div>
          {fld("Description","description","textarea")}
        </div>
        <div style={S.section}><h3 style={S.sTitle}>Pricing & Inventory</h3>
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr 1fr",gap:"0.75rem"}}>{fld("Price (₹) *","price","number")}{fld("Stock *","stock","number")}{fld("SKU","sku")}</div>
        </div>
        <div style={S.section}><h3 style={S.sTitle}>Details</h3>
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:"0.75rem"}}>{fld("Manufacturer","manufacturer")}{fld("Model Number","modelNumber")}{fld("Barcode","barcode",null,null,"12-13 digits")}{fld("HSN Code","hsnCode")}{fld("Country","countryOfOrigin",null,COUNTRIES)}{fld("Warranty","warranty",null,WARRANTY)}{fld("Return Policy","returnPolicy",null,RETURN_POLICY)}{fld("Badges","badges",null,BADGES)}</div>
          {fld("Short Description","shortDescription","textarea",null,"Max 150 chars")}
          {fld("Tech Specs","technicalSpecs","textarea",null,"Format: RAM:8GB; Storage:256GB")}
        </div>
        <div style={S.section}><h3 style={S.sTitle}>SEO</h3>
          {fld("Meta Title","metaTitle",null,null,"Max 60 chars")}{fld("Meta Description","metaDescription","textarea",null,"Max 160 chars")}
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:"0.75rem"}}>{fld("Keywords","keywords",null,null,"Comma-separated")}{fld("Slug","slug",null,null,"Auto-generated")}</div>
        </div>
        <div style={S.section}><h3 style={S.sTitle}>Logistics</h3>
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr 1fr",gap:"0.75rem"}}>{fld("Weight (kg)","weight","number",null,"e.g. 0.25")}{fld("Dimensions","dimensions",null,null,"LxWxH in cm")}{fld("Delivery Days","deliveryDays","number")}</div>
          {fld("Shipping Class","shippingClass",null,SHIPPING_CLASS)}
        </div>
        <div style={{display:"flex",justifyContent:"flex-end",gap:"10px",marginTop:"0.25rem"}}>
          <button onClick={handleAdd} disabled={submitting} style={{padding:"10px 32px",background:submitting?"#94a3b8":"#2E9B57",color:"#fff",border:"none",borderRadius:"6px",cursor:submitting?"not-allowed":"pointer",fontWeight:600,fontSize:"0.9rem"}}>{submitting?"Creating...":"Create Product"}</button>
        </div>
      </>)}

      {/* Bulk Upload */}
      {tab === "bulk" && (
        <div style={S.section}>
          <h3 style={S.sTitle}>Bulk Upload Products</h3>
          <a href={isSeller?"http://localhost:9091/api/seller/template.xlsx":"http://localhost:9091/api/admin/template.xlsx"} download style={{display:"inline-flex",alignItems:"center",gap:"6px",padding:"8px 16px",background:"#EAF7EE",color:"#146C43",borderRadius:"6px",textDecoration:"none",fontWeight:600,fontSize:"0.85rem",marginBottom:"1rem",border:"1px solid #CFE8D6"}}><Download size={16} /> Download Template</a>
          <div style={{border:"2px dashed #CFE8D6",borderRadius:"8px",padding:"2rem",textAlign:"center",background:"#fafdfb",marginBottom:"1rem",cursor:"pointer"}} onClick={()=>document.getElementById("bfile")?.click()}>
            <input id="bfile" ref={fileRef} type="file" accept=".xlsx,.xls" onChange={e=>setFile(e.target.files[0])} style={{display:"none"}} />
            {file ? <div><p style={{fontWeight:600}}>{file.name}</p><p style={{fontSize:"0.8rem",color:"#64748B"}}>{(file.size/1024).toFixed(1)} KB</p></div> : <div><p style={{color:"#64748B",fontSize:"0.9rem"}}>Click to select .xlsx file</p></div>}
          </div>
          <button onClick={handleBulk} disabled={!file||submitting} style={{width:"100%",padding:"10px",background:file?"#2E9B57":"#CFE8D6",color:file?"#fff":"#94a3b8",border:"none",borderRadius:"6px",cursor:file?"pointer":"not-allowed",fontWeight:600}}>{submitting?"Uploading...":"Upload Products"}</button>
        </div>
      )}

      {/* Product List */}
      {tab === "list" && (
        <div>
          {/* Filters */}
          <div style={{...S.section,marginBottom:"0.5rem"}}>
            <div style={{display:"grid",gridTemplateColumns:"repeat(auto-fill,minmax(140px,1fr))",gap:"0.5rem"}}>
              <input style={S.input} placeholder="Code" value={filters.code} onChange={e=>setF("code",e.target.value)} />
              <input style={S.input} placeholder="Name" value={filters.name} onChange={e=>setF("name",e.target.value)} />
              <input style={S.input} type="number" placeholder="Price min" value={filters.priceMin} onChange={e=>setF("priceMin",e.target.value)} />
              <input style={S.input} type="number" placeholder="Price max" value={filters.priceMax} onChange={e=>setF("priceMax",e.target.value)} />
              <input style={S.input} type="number" placeholder="Stock min" value={filters.stockMin} onChange={e=>setF("stockMin",e.target.value)} />
              <input style={S.input} type="number" placeholder="Stock max" value={filters.stockMax} onChange={e=>setF("stockMax",e.target.value)} />
              <select style={S.select} value={filters.status} onChange={e=>setF("status",e.target.value)}>
                <option value="all">All Status</option><option value="active">Active</option><option value="suspended">Suspended</option>
              </select>
            </div>
          </div>

          {/* Bulk bar */}
          {selected.size > 0 && (
            <div style={{display:"flex",alignItems:"center",gap:"12px",padding:"8px 12px",background:"#EAF7EE",borderRadius:"6px",marginBottom:"0.5rem",fontSize:"0.85rem"}}>
              <span style={{fontWeight:600}}>{selected.size} selected</span>
              <button onClick={bulkDelete} style={{padding:"6px 16px",background:"#dc2626",color:"#fff",border:"none",borderRadius:"4px",cursor:"pointer",fontWeight:500}}>Delete Selected</button>
              <button onClick={()=>setSelected(new Set())} style={{padding:"6px 12px",border:"1px solid #CFE8D6",borderRadius:"4px",background:"#fff",cursor:"pointer"}}>Clear</button>
            </div>
          )}

          <div style={S.section}>
            <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:"0.75rem",paddingBottom:"0.4rem",borderBottom:"2px solid #EAF7EE"}}>
              <h3 style={{fontSize:"0.9rem",fontWeight:700,color:"#0B3D2E",margin:0}}>Products ({filtered.length} of {products.length})</h3>
              <button onClick={refresh} style={{padding:"4px 12px",border:"1px solid #CFE8D6",borderRadius:"4px",background:"#fff",cursor:"pointer",fontSize:"0.8rem"}}>Refresh</button>
            </div>
            {filtered.length===0 ? <p style={{color:"#64748B",textAlign:"center",padding:"2rem"}}>{products.length===0?"No products yet.":"No products match filters."}</p> : (
            <table style={{width:"100%",borderCollapse:"collapse",fontSize:"0.85rem"}}>
              <thead><tr style={{background:"#f8f8f8"}}>
                <th style={{padding:"10px",width:30}}><input type="checkbox" checked={selected.size===filtered.length&&filtered.length>0} onChange={selectAll} /></th>
                <th style={{padding:"10px",textAlign:"left"}}>Image</th><th style={{padding:"10px",textAlign:"left"}}>Code</th><th style={{padding:"10px",textAlign:"left"}}>Name</th><th style={{padding:"10px",textAlign:"left"}}>Price</th><th style={{padding:"10px",textAlign:"left"}}>Stock</th><th style={{padding:"10px",textAlign:"left"}}>Status</th><th style={{padding:"10px",textAlign:"left"}}>Actions</th>
              </tr></thead>
              <tbody>{filtered.map(p=><tr key={p.id} style={{borderBottom:"1px solid var(--sn-border)"}}>
                <td style={{padding:"10px"}}><input type="checkbox" checked={selected.has(p.id)} onChange={()=>toggleSel(p.id)} /></td>
                <td style={{padding:"10px"}}>
                  <div style={{position:"relative",width:"40px",height:"40px"}}>
                    <div style={{position:"absolute",inset:0,borderRadius:"4px",background:"#f1f5f9",border:"1px solid #e2e8f0",display:"flex",alignItems:"center",justifyContent:"center",color:"#94a3b8"}}>
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/></svg>
                    </div>
                    {(() => {
                      const url = p.images?.find(i=>i.main)?.url || p.images?.[0]?.url;
                      const full = imgUrl(url);
                      return full ? <img src={full} alt="" style={{position:"absolute",inset:0,width:"100%",height:"100%",borderRadius:"4px",objectFit:"cover",border:"1px solid #e2e8f0",display:"block"}} onError={e=>e.target.remove()} /> : null;
                    })()}
                  </div>
                </td>
                <td style={{padding:"10px",fontFamily:"monospace"}}>{p.productCode||"N/A"}</td><td style={{padding:"10px"}}>{p.name}</td>
                <td style={{padding:"10px"}}>₹{p.price?.toLocaleString()}</td>
                <td style={{padding:"10px",color:p.stock<5?"#dc2626":"inherit",fontWeight:p.stock<5?700:400}}>{p.stock??0}</td>
                <td style={{padding:"10px"}}>
                  <select value={p.active?"active":"suspended"} onChange={e=>toggleStatus(p.id,e.target.value==="active")}
                    style={{padding:"2px 6px",borderRadius:"4px",fontSize:"0.75rem",border:"1px solid #CFE8D6",background:p.active?"#EAF7EE":"#fef2f2",color:p.active?"#146C43":"#dc2626"}}>
                    <option value="active">Active</option><option value="suspended">Suspended</option>
                  </select>
                </td>
                <td style={{padding:"10px",display:"flex",gap:"6px",flexWrap:"wrap"}}>
                  <button onClick={()=>navigate(`/admin/products/${p.id}/images`)} style={{padding:"4px 10px",border:"1px solid #CFE8D6",borderRadius:"4px",background:"#EAF7EE",color:"#146C43",cursor:"pointer",fontSize:"0.75rem",fontWeight:600}}>Images</button>
                  <button onClick={()=>{setEditing(p);setEditForm({name:p.name||"",price:p.price||"",stock:p.stock||"",description:p.description||"",brand:p.brand||""});switchTab("edit")}} style={{padding:"4px 10px",border:"1px solid #CFE8D6",borderRadius:"4px",background:"#fff",cursor:"pointer",fontSize:"0.8rem"}}>Edit</button>
                  <button onClick={()=>delProduct(p.id)} style={{padding:"4px 10px",border:"1px solid #fecaca",borderRadius:"4px",background:"#fef2f2",color:"#dc2626",cursor:"pointer",fontSize:"0.8rem"}}>Delete</button>
                </td>
              </tr>)}</tbody>
            </table>
            )}
          </div>
        </div>
      )}

      {/* Edit Product */}
      {tab === "edit" && editing && (
        <div style={S.section}>
          <h3 style={S.sTitle}>Edit: {editing.name} ({editing.productCode})</h3>
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:"0.75rem"}}>
            {fldEdit("Name","name")}{fldEdit("Brand","brand")}
            {fldEdit("Price (₹)","price","number")}{fldEdit("Stock","stock","number")}
          </div>
          {fldEdit("Description","description","textarea")}
          <div style={{display:"flex",gap:"10px",marginTop:"0.75rem",justifyContent:"flex-end"}}>
            <button onClick={()=>setTab("list")} style={{padding:"8px 16px",border:"1px solid #CFE8D6",borderRadius:"6px",background:"#fff",cursor:"pointer"}}>Cancel</button>
            <button onClick={saveEdit} style={{padding:"8px 20px",background:"#2E9B57",color:"#fff",border:"none",borderRadius:"6px",cursor:"pointer",fontWeight:600}}>Save Changes</button>
          </div>
        </div>
      )}

      {/* Inventory */}
      {tab === "inventory" && (
        <div style={S.section}>
          <h3 style={S.sTitle}>Inventory Overview</h3>
          {products.length===0 ? <p style={{color:"#64748B",textAlign:"center",padding:"2rem"}}>No products in inventory.</p> : (
            <table style={{width:"100%",borderCollapse:"collapse",fontSize:"0.85rem"}}>
              <thead><tr style={{background:"#f8f8f8"}}><th style={{padding:"10px",textAlign:"left"}}>Product</th><th style={{padding:"10px",textAlign:"left"}}>Code</th><th style={{padding:"10px",textAlign:"left"}}>Stock</th><th style={{padding:"10px",textAlign:"left"}}>Status</th></tr></thead>
              <tbody>{products.map(p=><tr key={p.id} style={{borderBottom:"1px solid var(--sn-border)",background:p.stock<5?"#fff8f0":"transparent"}}><td style={{padding:"10px"}}>{p.name}</td><td style={{padding:"10px",fontFamily:"monospace"}}>{p.productCode||"N/A"}</td><td style={{padding:"10px",fontWeight:p.stock<5?700:400,color:p.stock<5?"#dc2626":"inherit"}}>{p.stock??0}{p.stock<5&&" ⚠ Low"}</td><td style={{padding:"10px"}}>{p.active?"Active":"Inactive"}</td></tr>)}</tbody>
            </table>
          )}
        </div>
      )}

      {/* Reports */}
      {tab === "reports" && (
        <div style={S.section}>
          <h3 style={S.sTitle}>Product Reports</h3>
          <p style={{color:"#64748B",marginBottom:"1rem",fontSize:"0.9rem"}}>Total Products: {products.length} | Total Stock: {products.reduce((s,p)=>s+(p.stock||0),0)}</p>
          <button onClick={exportExcel} style={{padding:"10px 24px",background:"#2E9B57",color:"#fff",border:"none",borderRadius:"6px",cursor:"pointer",fontWeight:600,fontSize:"0.9rem"}}><Download size={16} /> Export to Excel</button>
        </div>
      )}

      {/* Categories */}
      {tab === "categories" && (
        <div style={S.section}>
          <h3 style={S.sTitle}>Categories</h3>
          <div style={{display:"flex",gap:"8px",marginBottom:"0.5rem",flexWrap:"wrap"}}>
            <div style={{flex:1,minWidth:"160px"}}>
              <input style={S.input} placeholder="Category name *" value={newCat} onChange={e=>{setNewCat(e.target.value);setCatErrors({});}} />
              {catErrors.name && <div style={{color:"#dc2626",fontSize:"0.7rem",marginTop:"2px"}}>{catErrors.name}</div>}
            </div>
            <button onClick={addCategory} style={{padding:"8px 16px",background:"#2E9B57",color:"#fff",border:"none",borderRadius:"6px",cursor:"pointer",fontWeight:600,whiteSpace:"nowrap",alignSelf:"flex-start"}}>Add</button>
          </div>
          <div style={{marginBottom:"0.75rem"}}>
            <textarea style={{...S.textarea,minHeight:"60px"}} placeholder="Description (optional, 10-500 chars)" value={newCatDesc} onChange={e=>{setNewCatDesc(e.target.value);setCatErrors({});}} maxLength={500} />
            <div style={{fontSize:"0.7rem",color:"#94a3b8",textAlign:"right"}}>{newCatDesc.length}/500</div>
            {catErrors.description && <div style={{color:"#dc2626",fontSize:"0.7rem",marginTop:"2px"}}>{catErrors.description}</div>}
          </div>
          <div style={{overflowX:"auto"}}>
          <table style={{width:"100%",borderCollapse:"collapse",fontSize:"0.85rem"}}>
            <thead><tr style={{background:"#f8f8f8"}}><th style={{padding:"10px",textAlign:"left",minWidth:"140px"}}>Name</th><th style={{padding:"10px",textAlign:"left",minWidth:"180px"}}>Description</th><th style={{padding:"10px",textAlign:"right",minWidth:"130px"}}>Actions</th></tr></thead>
            <tbody>{categories.map(c=>
              <tr key={c.id} style={{borderBottom:"1px solid var(--sn-border)"}}>
                {editCatId === c.id ? (
                  <>
                    <td style={{padding:"10px",verticalAlign:"top"}}>
                      <input style={S.input} value={editCatForm.name} onChange={e=>setEditCatForm({...editCatForm,name:e.target.value})} />
                      {catErrors.name && <div style={{color:"#dc2626",fontSize:"0.7rem",marginTop:"2px"}}>{catErrors.name}</div>}
                    </td>
                    <td style={{padding:"10px",verticalAlign:"top"}}>
                      <textarea style={{...S.textarea,minHeight:"60px"}} value={editCatForm.description} onChange={e=>setEditCatForm({...editCatForm,description:e.target.value})} maxLength={500} />
                      <div style={{fontSize:"0.7rem",color:"#94a3b8",textAlign:"right"}}>{editCatForm.description.length}/500</div>
                      {catErrors.description && <div style={{color:"#dc2626",fontSize:"0.7rem",marginTop:"2px"}}>{catErrors.description}</div>}
                    </td>
                    <td style={{padding:"10px",textAlign:"right",whiteSpace:"nowrap"}}>
                      <button onClick={saveEditCat} style={{padding:"4px 12px",background:"#EAF7EE",color:"#146C43",border:"1px solid #CFE8D6",borderRadius:"4px",cursor:"pointer",fontSize:"0.8rem",marginRight:"4px"}}>Save</button>
                      <button onClick={cancelEditCat} style={{padding:"4px 12px",border:"1px solid #CFE8D6",borderRadius:"4px",background:"#fff",cursor:"pointer",fontSize:"0.8rem"}}>Cancel</button>
                    </td>
                  </>
                ) : (
                  <>
                    <td style={{padding:"10px",fontWeight:500}}>{c.name}</td>
                    <td style={{padding:"10px",color:"#64748B"}}>{c.description||<span style={{fontStyle:"italic",color:"#cbd5e1"}}>No description</span>}</td>
                    <td style={{padding:"10px",textAlign:"right",whiteSpace:"nowrap"}}>
                      <button onClick={()=>startEditCat(c)} style={{padding:"4px 12px",background:"#EAF7EE",color:"#146C43",border:"1px solid #CFE8D6",borderRadius:"4px",cursor:"pointer",fontSize:"0.8rem",marginRight:"4px"}}>Edit</button>
                      <button onClick={()=>setDeleteCatTarget(c)} style={{padding:"4px 12px",background:"#fef2f2",color:"#dc2626",border:"1px solid #fecaca",borderRadius:"4px",cursor:"pointer",fontSize:"0.8rem"}}>Delete</button>
                    </td>
                  </>
                )}
              </tr>
            )}</tbody>
          </table>
          </div>
        </div>
      )}

      {/* Delete Category Modal */}
      {deleteCatTarget && (
        <div style={{position:"fixed",inset:0,background:"rgba(0,0,0,0.4)",display:"flex",alignItems:"center",justifyContent:"center",zIndex:1000,padding:"1rem"}} onClick={()=>setDeleteCatTarget(null)}>
          <div style={{background:"#fff",borderRadius:"10px",padding:"1.5rem",maxWidth:"420px",width:"100%",boxShadow:"0 4px 24px rgba(0,0,0,0.15)"}} onClick={e=>e.stopPropagation()}>
            <h3 style={{fontSize:"1.1rem",fontWeight:600,marginBottom:"0.75rem"}}>Delete Category</h3>
            <p style={{color:"#64748B",marginBottom:"1.5rem",fontSize:"0.9rem",lineHeight:1.5}}>
              Are you sure you want to delete <strong>"{deleteCatTarget.name}"</strong>?
            </p>
            <div style={{display:"flex",gap:"10px",justifyContent:"flex-end"}}>
              <button onClick={()=>setDeleteCatTarget(null)} style={{padding:"8px 20px",border:"1px solid #CFE8D6",borderRadius:"6px",background:"#fff",cursor:"pointer",fontSize:"0.85rem",fontWeight:500}}>Cancel</button>
              <button onClick={()=>delCategory(deleteCatTarget.id)} style={{padding:"8px 20px",background:"#dc2626",color:"#fff",border:"none",borderRadius:"6px",cursor:"pointer",fontSize:"0.85rem",fontWeight:600}}>Delete</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ProductManagement;
