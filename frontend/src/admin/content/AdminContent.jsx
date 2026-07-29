import React, { useState, useEffect } from "react";
import api from "../../api/axios";

const TABS = ["Banners", "Pages", "FAQ", "Homepage Sections"];

const AdminContent = () => {
  const [tab, setTab] = useState(0);
  const [banners, setBanners] = useState([]);
  const [pages, setPages] = useState([]);
  const [faqs, setFaqs] = useState([]);
  const [homepageSettings, setHomepageSettings] = useState({ brandStoresEnabled: false });
  const [savingHomepage, setSavingHomepage] = useState(false);

  const emptyBanner = { title:"", subtitle:"", imageUrl:"", link:"", position:"HOME_TOP", sortOrder:0, active:true };
  const emptyPage = { slug:"", title:"", content:"", type:"ABOUT", active:true, metaTitle:"", metaDescription:"" };
  const emptyFaq = { question:"", answer:"", category:"General", sortOrder:0, active:true };

  const [bForm, setBForm] = useState(emptyBanner);
  const [pForm, setPForm] = useState(emptyPage);
  const [fForm, setFForm] = useState(emptyFaq);
  const [showBForm, setShowBForm] = useState(false);
  const [showPForm, setShowPForm] = useState(false);
  const [showFForm, setShowFForm] = useState(false);
  const [editing, setEditing] = useState(null);

  const fetchAll = async () => {
    try {
      const [b, p, f, h] = await Promise.all([
        api.get("/api/admin/content/banners"),
        api.get("/api/admin/content/pages"),
        api.get("/api/admin/content/faqs"),
        api.get("/api/admin/content/homepage-settings"),
      ]);
      setBanners(b.data || []); setPages(p.data || []); setFaqs(f.data || []);
      setHomepageSettings(h.data || { brandStoresEnabled: false });
    } catch {}
  };

  useEffect(() => { fetchAll(); }, []);

  const handleToggleBrandStores = async () => {
    const next = !homepageSettings.brandStoresEnabled;
    setSavingHomepage(true);
    try {
      const res = await api.put("/api/admin/content/homepage-settings", { brandStoresEnabled: next });
      setHomepageSettings(res.data);
    } catch { alert("Failed to update setting"); }
    setSavingHomepage(false);
  };

  const handleBSubmit = async (e) => {
    e.preventDefault();
    try {
      if (editing) { await api.put(`/api/admin/content/banners/${editing}`, { ...bForm, sortOrder: parseInt(bForm.sortOrder) }); }
      else { await api.post("/api/admin/content/banners", { ...bForm, sortOrder: parseInt(bForm.sortOrder) }); }
      setShowBForm(false); setEditing(null); setBForm(emptyBanner); fetchAll();
    } catch { alert("Failed"); }
  };

  const handlePSubmit = async (e) => {
    e.preventDefault();
    try {
      if (editing) { await api.put(`/api/admin/content/pages/${editing}`, pForm); }
      else { await api.post("/api/admin/content/pages", pForm); }
      setShowPForm(false); setEditing(null); setPForm(emptyPage); fetchAll();
    } catch { alert("Failed"); }
  };

  const handleFSubmit = async (e) => {
    e.preventDefault();
    try {
      if (editing) { await api.put(`/api/admin/content/faqs/${editing}`, { ...fForm, sortOrder: parseInt(fForm.sortOrder) }); }
      else { await api.post("/api/admin/content/faqs", { ...fForm, sortOrder: parseInt(fForm.sortOrder) }); }
      setShowFForm(false); setEditing(null); setFForm(emptyFaq); fetchAll();
    } catch { alert("Failed"); }
  };

  const handleDelete = async (type, id) => {
    if (!window.confirm("Delete this item?")) return;
    try { await api.delete(`/api/admin/content/${type}/${id}`); fetchAll(); } catch { alert("Failed"); }
  };

  return (
    <div>
      <h1 style={{ fontSize:"1.5rem", fontWeight:700, marginBottom:"1.5rem" }}>Content Management</h1>
      <div style={{ display:"flex", gap:"0.25rem", marginBottom:"1.5rem", borderBottom:"1px solid #e2e8f0" }}>
        {TABS.map((t,i) => (
          <button key={t} onClick={() => setTab(i)}
            style={{ padding:"0.5rem 1.25rem", border:"none", background:tab===i?"#16a34a":"transparent", color:tab===i?"#fff":"#475569", borderRadius:"6px 6px 0 0", cursor:"pointer", fontWeight:500, fontSize:"0.85rem" }}>{t}</button>
        ))}
      </div>

      {tab === 0 && (
        <div>
          <div style={{ display:"flex", justifyContent:"space-between", marginBottom:"1rem" }}>
            <span style={{ fontWeight:600, fontSize:"0.95rem" }}>Homepage Banners</span>
            <button onClick={() => { setShowBForm(true); setEditing(null); setBForm(emptyBanner); }} style={{ padding:"0.4rem 0.8rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.8rem" }}>Add Banner</button>
          </div>
          {showBForm && (
            <form onSubmit={handleBSubmit} style={{ background:"#f9fafb", border:"1px solid #e5e7eb", borderRadius:8, padding:"1rem", marginBottom:"1rem" }}>
              <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fill,minmax(200px,1fr))", gap:"0.75rem", marginBottom:"0.75rem" }}>
                <div><label style={{ fontSize:"0.8rem", display:"block" }}>Title</label><input value={bForm.title} onChange={e => setBForm({...bForm,title:e.target.value})} style={inp} /></div>
                <div><label style={{ fontSize:"0.8rem", display:"block" }}>Subtitle</label><input value={bForm.subtitle} onChange={e => setBForm({...bForm,subtitle:e.target.value})} style={inp} /></div>
                <div><label style={{ fontSize:"0.8rem", display:"block" }}>Image URL</label><input value={bForm.imageUrl} onChange={e => setBForm({...bForm,imageUrl:e.target.value})} style={inp} /></div>
                <div><label style={{ fontSize:"0.8rem", display:"block" }}>Link</label><input value={bForm.link} onChange={e => setBForm({...bForm,link:e.target.value})} style={inp} /></div>
                <div><label style={{ fontSize:"0.8rem", display:"block" }}>Position</label><select value={bForm.position} onChange={e => setBForm({...bForm,position:e.target.value})} style={inp}><option value="HOME_TOP">Home Top</option><option value="HOME_MID">Home Middle</option></select></div>
                <div><label style={{ fontSize:"0.8rem", display:"block" }}>Sort</label><input type="number" value={bForm.sortOrder} onChange={e => setBForm({...bForm,sortOrder:e.target.value})} style={inp} /></div>
              </div>
              <button type="submit" style={{ padding:"0.4rem 1rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer" }}>Save</button>
            </form>
          )}
          <Table
            headers={["Title","Position","Link","Status","Actions"]}
            rows={banners.map(b => ({
              cells: [b.title, b.position, b.link||"-", <Badge active={b.active}/>,
                <Actions onEdit={() => { setBForm(b); setEditing(b.id); setShowBForm(true); }} onDelete={() => handleDelete("banners",b.id)} />]
            }))}
          />
        </div>
      )}

      {tab === 1 && (
        <div>
          <div style={{ display:"flex", justifyContent:"space-between", marginBottom:"1rem" }}>
            <span style={{ fontWeight:600, fontSize:"0.95rem" }}>Static Pages</span>
            <button onClick={() => { setShowPForm(true); setEditing(null); setPForm(emptyPage); }} style={{ padding:"0.4rem 0.8rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.8rem" }}>Add Page</button>
          </div>
          {showPForm && (
            <form onSubmit={handlePSubmit} style={{ background:"#f9fafb", border:"1px solid #e5e7eb", borderRadius:8, padding:"1rem", marginBottom:"1rem" }}>
              <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fill,minmax(250px,1fr))", gap:"0.75rem", marginBottom:"0.75rem" }}>
                <div><label style={{ fontSize:"0.8rem", display:"block" }}>Slug</label><input value={pForm.slug} onChange={e => setPForm({...pForm,slug:e.target.value})} style={inp} /></div>
                <div><label style={{ fontSize:"0.8rem", display:"block" }}>Title</label><input value={pForm.title} onChange={e => setPForm({...pForm,title:e.target.value})} style={inp} /></div>
                <div><label style={{ fontSize:"0.8rem", display:"block" }}>Type</label><select value={pForm.type} onChange={e => setPForm({...pForm,type:e.target.value})} style={inp}><option value="ABOUT">About</option><option value="CONTACT">Contact</option><option value="POLICY">Policy</option><option value="TERMS">Terms</option></select></div>
              </div>
              <div style={{ marginBottom:"0.75rem" }}><label style={{ fontSize:"0.8rem", display:"block" }}>Content (HTML)</label><textarea value={pForm.content} onChange={e => setPForm({...pForm,content:e.target.value})} style={{...inp,minHeight:120}} /></div>
              <button type="submit" style={{ padding:"0.4rem 1rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer" }}>Save</button>
            </form>
          )}
          <Table
            headers={["Title","Slug","Type","Status","Actions"]}
            rows={pages.map(p => ({
              cells: [p.title, p.slug, p.type, <Badge active={p.active}/>,
                <Actions onEdit={() => { setPForm(p); setEditing(p.id); setShowPForm(true); }} onDelete={() => handleDelete("pages",p.id)} />]
            }))}
          />
        </div>
      )}

      {tab === 2 && (
        <div>
          <div style={{ display:"flex", justifyContent:"space-between", marginBottom:"1rem" }}>
            <span style={{ fontWeight:600, fontSize:"0.95rem" }}>FAQ Entries</span>
            <button onClick={() => { setShowFForm(true); setEditing(null); setFForm(emptyFaq); }} style={{ padding:"0.4rem 0.8rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.8rem" }}>Add FAQ</button>
          </div>
          {showFForm && (
            <form onSubmit={handleFSubmit} style={{ background:"#f9fafb", border:"1px solid #e5e7eb", borderRadius:8, padding:"1rem", marginBottom:"1rem" }}>
              <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fill,minmax(250px,1fr))", gap:"0.75rem", marginBottom:"0.75rem" }}>
                <div><label style={{ fontSize:"0.8rem", display:"block" }}>Question</label><input value={fForm.question} onChange={e => setFForm({...fForm,question:e.target.value})} style={inp} /></div>
                <div><label style={{ fontSize:"0.8rem", display:"block" }}>Category</label><input value={fForm.category} onChange={e => setFForm({...fForm,category:e.target.value})} style={inp} /></div>
                <div><label style={{ fontSize:"0.8rem", display:"block" }}>Sort Order</label><input type="number" value={fForm.sortOrder} onChange={e => setFForm({...fForm,sortOrder:e.target.value})} style={inp} /></div>
              </div>
              <div style={{ marginBottom:"0.75rem" }}><label style={{ fontSize:"0.8rem", display:"block" }}>Answer</label><textarea value={fForm.answer} onChange={e => setFForm({...fForm,answer:e.target.value})} style={{...inp,minHeight:80}} /></div>
              <button type="submit" style={{ padding:"0.4rem 1rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer" }}>Save</button>
            </form>
          )}
          <Table
            headers={["Question","Category","Sort","Status","Actions"]}
            rows={faqs.map(f => ({
              cells: [f.question, f.category, f.sortOrder, <Badge active={f.active}/>,
                <Actions onEdit={() => { setFForm(f); setEditing(f.id); setShowFForm(true); }} onDelete={() => handleDelete("faqs",f.id)} />]
            }))}
          />
        </div>
      )}
      {tab === 3 && (
        <div>
          <div style={{ display:"flex", alignItems:"center", justifyContent:"space-between", padding:"1rem", background:"#fff", border:"1px solid #e5e7eb", borderRadius:10 }}>
            <div>
              <div style={{ fontWeight:600, fontSize:"0.95rem" }}>Brand Stores</div>
              <div style={{ fontSize:"0.8rem", color:"#6b7280", marginTop:"2px" }}>Show the "Brand Stores" section on the homepage.</div>
            </div>
            <label style={{ position:"relative", display:"inline-block", width:44, height:24, cursor: savingHomepage ? "not-allowed" : "pointer", opacity: savingHomepage ? 0.6 : 1 }}>
              <input type="checkbox" checked={homepageSettings.brandStoresEnabled} disabled={savingHomepage} onChange={handleToggleBrandStores} style={{ opacity:0, width:0, height:0 }} />
              <span style={{
                position:"absolute", inset:0, borderRadius:9999,
                background: homepageSettings.brandStoresEnabled ? "#16a34a" : "#d1d5db",
                transition:"background 0.15s"
              }}>
                <span style={{
                  position:"absolute", top:3, left: homepageSettings.brandStoresEnabled ? 23 : 3,
                  width:18, height:18, borderRadius:"50%", background:"#fff", transition:"left 0.15s",
                  boxShadow:"0 1px 3px rgba(0,0,0,0.3)"
                }} />
              </span>
            </label>
          </div>
        </div>
      )}
    </div>
  );
};

const Badge = ({ active }) => (
  <span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:active?"#f0fdf4":"#fef2f2", color:active?"#16a34a":"#dc2626" }}>{active ? "Active" : "Inactive"}</span>
);

const Actions = ({ onEdit, onDelete }) => (
  <div style={{ display:"flex", gap:"4px" }}>
    <button onClick={onEdit} style={{ padding:"0.2rem 0.6rem", background:"#2563eb", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem" }}>Edit</button>
    <button onClick={onDelete} style={{ padding:"0.2rem 0.6rem", background:"#dc2626", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem" }}>Delete</button>
  </div>
);

const Table = ({ headers, rows }) => (
  <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflow:"hidden" }}>
    <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
      <thead><tr style={{ background:"#f9fafb" }}>
        {headers.map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
      </tr></thead>
      <tbody>
        {rows.length === 0 && <tr><td colSpan={headers.length} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No items</td></tr>}
        {rows.map((r,i) => (
          <tr key={i} style={{ borderBottom:"1px solid #f3f4f6" }}>
            {r.cells.map((c,j) => <td key={j} style={{ padding:"10px 12px" }}>{c}</td>)}
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);

const inp = { padding:"0.4rem 0.5rem", border:"1px solid #d1d5db", borderRadius:5, fontSize:"0.8rem", width:"100%", boxSizing:"border-box" };

export default AdminContent;
