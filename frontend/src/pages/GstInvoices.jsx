import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Link } from "react-router-dom";
import { FileText, Plus, Search, Download, RefreshCw, CheckCircle, XCircle, Clock, AlertTriangle, TrendingUp, Filter, ExternalLink, Eye, FileMinus, BarChart3, FileSpreadsheet, FileJson } from "lucide-react";
import api from "../api/axios";

const GST_DASHBOARD_STYLES = `
  .gst-page { max-width: 1100px; margin: 0 auto; padding: 1.5rem; }
  .gst-page h1 { font-size: 1.5rem; font-weight: 700; color: #0f172a; margin: 0 0 0.25rem; }
  .gst-page .gst-subtitle { font-size: 0.88rem; color: #64748b; margin: 0 0 1.5rem; }

  .gst-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
  .gst-stat-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 1.25rem; }
  .gst-stat-card .gst-stat-label { font-size: 0.78rem; color: #64748b; margin: 0 0 0.35rem; text-transform: uppercase; letter-spacing: 0.3px; }
  .gst-stat-card .gst-stat-value { font-size: 1.5rem; font-weight: 700; color: #0f172a; margin: 0; }
  .gst-stat-card .gst-stat-sub { font-size: 0.78rem; color: #94a3b8; margin-top: 0.25rem; }

  .gst-tabs { display: flex; gap: 0; border-bottom: 2px solid #e2e8f0; margin-bottom: 1.5rem; overflow-x: auto; }
  .gst-tab { padding: 0.7rem 1.25rem; font-size: 0.85rem; font-weight: 500; color: #64748b; background: none; border: none; border-bottom: 2px solid transparent; margin-bottom: -2px; cursor: pointer; white-space: nowrap; }
  .gst-tab:hover { color: #0E5C5C; }
  .gst-tab.active { color: #0E5C5C; border-bottom-color: #0E5C5C; font-weight: 600; }

  .gst-toolbar { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 1rem; flex-wrap: wrap; }
  .gst-search { flex: 1; min-width: 200px; padding: 0.5rem 0.75rem; border: 1.5px solid #e2e8f0; border-radius: 8px; font-size: 0.85rem; outline: none; }
  .gst-search:focus { border-color: #0E5C5C; }
  .gst-btn { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.5rem 1rem; border-radius: 8px; font-size: 0.82rem; font-weight: 600; border: none; cursor: pointer; transition: background 0.2s; white-space: nowrap; }
  .gst-btn-primary { background: #0E5C5C; color: #fff; }
  .gst-btn-primary:hover { background: #0a4a4a; }
  .gst-btn-outline { background: transparent; color: #0E5C5C; border: 1.5px solid #0E5C5C; }
  .gst-btn-outline:hover { background: #f0fdf4; }
  .gst-btn-sm { padding: 0.35rem 0.7rem; font-size: 0.78rem; }

  .gst-table-wrap { overflow-x: auto; background: #fff; border: 1px solid #e2e8f0; border-radius: 10px; }
  .gst-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
  .gst-table th { background: #f8fafc; padding: 0.7rem 1rem; text-align: left; font-weight: 600; color: #475569; border-bottom: 1px solid #e2e8f0; white-space: nowrap; }
  .gst-table td { padding: 0.65rem 1rem; border-bottom: 1px solid #f1f5f9; color: #1e293b; }
  .gst-table tr:hover td { background: #f8fafc; }
  .gst-table .gst-status { display: inline-flex; align-items: center; gap: 0.25rem; padding: 2px 8px; border-radius: 999px; font-size: 0.72rem; font-weight: 600; }
  .gst-status-synced { background: #dcfce7; color: #166534; }
  .gst-status-generated { background: #fef9c3; color: #854d0e; }
  .gst-status-failed { background: #fee2e2; color: #991b1b; }
  .gst-status-draft { background: #f1f5f9; color: #475569; }

  .gst-form { background: #fff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 1.5rem; max-width: 600px; }
  .gst-form h3 { font-size: 1.05rem; font-weight: 600; color: #0f172a; margin: 0 0 1rem; }
  .gst-field { margin-bottom: 1rem; }
  .gst-field label { display: block; font-size: 0.82rem; font-weight: 600; color: #334155; margin-bottom: 0.3rem; }
  .gst-field input, .gst-field select { width: 100%; padding: 0.55rem 0.75rem; border: 1.5px solid #e2e8f0; border-radius: 8px; font-size: 0.88rem; outline: none; box-sizing: border-box; }
  .gst-field input:focus, .gst-field select:focus { border-color: #0E5C5C; }

  .gst-summary-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; }
  .gst-summary-item { padding: 0.5rem 0; border-bottom: 1px solid #f1f5f9; }
  .gst-summary-label { font-size: 0.78rem; color: #94a3b8; }
  .gst-summary-value { font-size: 1rem; font-weight: 600; color: #0f172a; }

  @media (max-width: 768px) {
    .gst-page { padding: 1rem; }
    .gst-stats { grid-template-columns: repeat(2, 1fr); }
    .gst-summary-grid { grid-template-columns: 1fr; }
  }
`;

const TABS = [
  { id: "dashboard", label: "Dashboard", icon: TrendingUp },
  { id: "invoices", label: "Invoices", icon: FileText },
  { id: "creditNotes", label: "Credit Notes", icon: FileMinus },
  { id: "gstr3b", label: "GSTR-3B", icon: BarChart3 },
  { id: "generate", label: "Generate", icon: Plus },
  { id: "summary", label: "GST Summary", icon: Filter },
  { id: "gstr1", label: "GSTR-1", icon: Download },
  { id: "tcs", label: "TCS", icon: AlertTriangle },
];

const GstInvoices = () => {
  const [activeTab, setActiveTab] = useState("dashboard");
  const [invoices, setInvoices] = useState([]);
  const [stats, setStats] = useState(null);
  const [summary, setSummary] = useState(null);
  const [gstr1, setGstr1] = useState([]);
  const [gstr3b, setGstr3b] = useState(null);
  const [creditNotes, setCreditNotes] = useState([]);
  const [cnTotalPages, setCnTotalPages] = useState(0);
  const [cnPage, setCnPage] = useState(0);
  const [tcs, setTcs] = useState(null);
  const [configs, setConfigs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [orderId, setOrderId] = useState("");
  const [selectedGstin, setSelectedGstin] = useState("");
  const [buyerGstin, setBuyerGstin] = useState("");
  const [generating, setGenerating] = useState(false);
  const [genResult, setGenResult] = useState(null);
  const [expandedInvoice, setExpandedInvoice] = useState(null);

  useEffect(() => {
    if (activeTab === "dashboard") fetchDashboard();
    else if (activeTab === "invoices") fetchInvoices();
    else if (activeTab === "creditNotes") fetchCreditNotes();
    else if (activeTab === "gstr3b") fetchGstr3b();
    else if (activeTab === "summary") fetchSummary();
    else if (activeTab === "gstr1") fetchGstr1();
    else if (activeTab === "tcs") fetchTcs();
  }, [activeTab, page, cnPage]);

  const fetchDashboard = async () => {
    setLoading(true);
    try {
      const [statsRes, configsRes] = await Promise.allSettled([
        api.get("/api/gst/dashboard"),
        api.get("/api/gst/configurations"),
      ]);
      if (statsRes.status === "fulfilled") setStats(statsRes.value.data);
      if (configsRes.status === "fulfilled") setConfigs(configsRes.value.data.configurations || []);
    } catch { setError("Failed to load dashboard"); }
    setLoading(false);
  };

  const fetchInvoices = async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/gst/invoices", { params: { page, size: 20 } });
      setInvoices(res.data.content || []);
      setTotalPages(res.data.totalPages || 0);
    } catch { setInvoices([]); }
    setLoading(false);
  };

  const fetchSummary = async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/gst/summary");
      setSummary(res.data);
    } catch { setSummary(null); }
    setLoading(false);
  };

  const fetchCreditNotes = async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/gst/creditnotes", { params: { page: cnPage, size: 20 } });
      setCreditNotes(res.data.content || []);
      setCnTotalPages(res.data.totalPages || 0);
    } catch { setCreditNotes([]); }
    setLoading(false);
  };

  const fetchGstr3b = async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/gst/gstr3b");
      setGstr3b(res.data);
    } catch { setGstr3b(null); }
    setLoading(false);
  };

  const downloadExport = async (url, filename, format) => {
    try {
      const res = await api.get(url, { params: { format }, responseType: "blob" });
      const blob = new Blob([res.data]);
      const link = document.createElement("a");
      const objectUrl = window.URL.createObjectURL(blob);
      link.href = objectUrl;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(objectUrl);
    } catch { setError("Failed to export"); }
  };

  const fetchGstr1 = async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/gst/gstr1");
      setGstr1(res.data || []);
    } catch { setGstr1([]); }
    setLoading(false);
  };

  const fetchTcs = async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/gst/tcs");
      setTcs(res.data);
    } catch { setTcs(null); }
    setLoading(false);
  };

  const handleGenerate = async () => {
    if (!orderId || !selectedGstin) { setError("Order ID and GSTIN are required"); return; }
    setGenerating(true); setError(""); setGenResult(null);
    try {
      const payload = { orderId: parseInt(orderId), gstin: selectedGstin };
      if (buyerGstin && buyerGstin.trim()) payload.buyerGstin = buyerGstin.trim();
      const res = await api.post("/api/gst/invoice/generate", payload);
      setGenResult(res.data);
      setOrderId("");
      setBuyerGstin("");
    } catch (err) {
      setError(err.response?.data?.error || "Failed to generate invoice");
    }
    setGenerating(false);
  };

  const handleSyncInvoice = async (id) => {
    try {
      const irn = prompt("Enter IRN (Invoice Reference Number):");
      if (!irn) return;
      await api.post(`/api/gst/invoice/${id}/sync`, { irn, qrCode: "", ackNo: "", ackDate: "" });
      fetchInvoices();
    } catch { setError("Failed to sync invoice"); }
  };

  return (
    <>
      <style>{GST_DASHBOARD_STYLES}</style>
      <div className="gst-page">
        <Helmet><title>GST Invoice System | Cauvery Store</title></Helmet>

        <h1>GST Invoice System</h1>
        <p className="gst-subtitle" style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
          Generate, manage, and sync GST-compliant invoices with HSN/SAC, tax breakup, TCS, and e-invoice support.
          <Link to="/seller/gst-compliance" style={{ fontSize: "0.8rem", fontWeight: 600, color: "#0E5C5C", textDecoration: "none", whiteSpace: "nowrap", border: "1.5px solid #0E5C5C", padding: "0.3rem 0.75rem", borderRadius: "8px", display: "inline-flex", alignItems: "center", gap: "0.35rem" }}>
            <BarChart3 size={13} /> Compliance Center
          </Link>
        </p>

        <div className="gst-tabs">
          {TABS.map((t) => (
            <button key={t.id} className={`gst-tab${activeTab === t.id ? " active" : ""}`} onClick={() => { setActiveTab(t.id); setPage(0); }}>
              {t.label}
            </button>
          ))}
        </div>

        {error && <div style={{ background: "#fef2f2", border: "1px solid #fecaca", borderRadius: "8px", padding: "0.6rem 1rem", fontSize: "0.85rem", color: "#991b1b", marginBottom: "1rem" }}>{error}</div>}

        {genResult && (
          <div style={{ background: "#f0fdf4", border: "1px solid #bbf7d0", borderRadius: "8px", padding: "1rem", marginBottom: "1rem" }}>
            <div style={{ fontWeight: 600, color: "#166534", marginBottom: "0.25rem" }}>Invoice Generated!</div>
            <div style={{ fontSize: "0.85rem", color: "#15803d" }}>
              #{genResult.invoice?.invoiceNumber} — {genResult.message}
              {genResult.invoice?.invoiceType && <span style={{ marginLeft: "0.5rem", padding: "1px 6px", background: "#bbf7d0", borderRadius: "4px", fontSize: "0.75rem" }}>{genResult.invoice.invoiceType}</span>}
            </div>
          </div>
        )}

        {activeTab === "dashboard" && (
          <>
            <div className="gst-stats">
              <div className="gst-stat-card"><p className="gst-stat-label">Total Invoices</p><p className="gst-stat-value">{stats?.totalInvoices || 0}</p></div>
              <div className="gst-stat-card"><p className="gst-stat-label">Synced to GSTN</p><p className="gst-stat-value" style={{ color: "#16a34a" }}>{stats?.syncedToGstn || 0}</p></div>
              <div className="gst-stat-card"><p className="gst-stat-label">Pending Sync</p><p className="gst-stat-value" style={{ color: "#f59e0b" }}>{stats?.pendingSync || 0}</p></div>
              <div className="gst-stat-card"><p className="gst-stat-label">Sync Failed</p><p className="gst-stat-value" style={{ color: "#dc2626" }}>{stats?.syncFailed || 0}</p></div>
              <div className="gst-stat-card"><p className="gst-stat-label">Queue Size</p><p className="gst-stat-value">{stats?.queueSize || 0}</p><p className="gst-stat-sub">Items awaiting processing</p></div>
            </div>

            <h3 style={{ fontSize: "1rem", fontWeight: 600, color: "#0f172a", margin: "1.5rem 0 0.75rem" }}>Active GSTIN Configurations</h3>
            {configs.length === 0 ? (
              <p style={{ fontSize: "0.85rem", color: "#94a3b8" }}>No GSTIN configurations found. Contact admin to add one.</p>
            ) : (
              <div className="gst-table-wrap">
                <table className="gst-table">
                  <thead><tr><th>GSTIN</th><th>Legal Name</th><th>Trade Name</th><th>State</th><th>TCS Rate</th><th>Prefix</th></tr></thead>
                  <tbody>
                    {configs.map((c) => (
                      <tr key={c.id}>
                        <td style={{ fontFamily: "monospace", fontWeight: 600 }}>{c.gstin}</td>
                        <td>{c.legalName}</td>
                        <td>{c.tradeName || "-"}</td>
                        <td>{c.stateName} ({c.stateCode})</td>
                        <td>{c.tcsRate}%</td>
                        <td>{c.invoicePrefix}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}

        {activeTab === "invoices" && (
          <>
            <div className="gst-toolbar">
              <input className="gst-search" placeholder="Search invoices..." />
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={fetchInvoices}><RefreshCw size={14} /> Refresh</button>
            </div>
            {loading ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>Loading invoices...</p>
            ) : invoices.length === 0 ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>No invoices generated yet.</p>
            ) : (
              <div className="gst-table-wrap">
                <table className="gst-table">
                  <thead>
                    <tr><th>Invoice #</th><th>Order ID</th><th>Date</th><th>Taxable Amt</th><th>CGST</th><th>SGST</th><th>IGST</th><th>Total</th><th>TCS</th><th>ITC</th><th>IRN</th><th>Status</th><th>Actions</th></tr>
                  </thead>
                  <tbody>
                    {invoices.map((inv) => (
                      <React.Fragment key={inv.id}>
                        <tr onClick={() => setExpandedInvoice(expandedInvoice === inv.id ? null : inv.id)} style={{ cursor: "pointer" }}>
                          <td style={{ fontFamily: "monospace", fontWeight: 600, fontSize: "0.8rem" }}>{inv.invoiceNumber}</td>
                          <td>#{inv.orderId}</td>
                          <td>{inv.invoiceDate}</td>
                          <td>&#8377;{(inv.taxableAmount || 0).toFixed(2)}</td>
                          <td>&#8377;{(inv.cgstAmount || 0).toFixed(2)}</td>
                          <td>&#8377;{(inv.sgstAmount || 0).toFixed(2)}</td>
                          <td>&#8377;{(inv.igstAmount || 0).toFixed(2)}</td>
                          <td style={{ fontWeight: 600 }}>&#8377;{(inv.totalAmount || 0).toFixed(2)}</td>
                          <td>&#8377;{(inv.tcsAmount || 0).toFixed(2)}</td>
                          <td>
                            {inv.itcEligible ? (
                              <span className="gst-status gst-status-synced">Eligible</span>
                            ) : (
                              <span style={{ color: "#94a3b8", fontSize: "0.75rem" }}>-</span>
                            )}
                          </td>
                          <td style={{ fontSize: "0.75rem", maxWidth: 100, overflow: "hidden", textOverflow: "ellipsis" }}>{inv.irn || "-"}</td>
                          <td>
                            <span className={`gst-status ${inv.status === "SYNCED" ? "gst-status-synced" : inv.status === "SYNC_FAILED" ? "gst-status-failed" : inv.status === "GENERATED" ? "gst-status-generated" : "gst-status-draft"}`}>
                              {inv.status === "SYNCED" ? <CheckCircle size={12} /> : inv.status === "SYNC_FAILED" ? <XCircle size={12} /> : inv.status === "GENERATED" ? <Clock size={12} /> : null}
                              {inv.status}
                            </span>
                          </td>
                          <td>
                            <div style={{ display: "flex", gap: "0.35rem", alignItems: "center" }}>
                              <Link to={`/seller/gst-invoices/view/${inv.id}`} className="gst-btn gst-btn-outline gst-btn-sm" onClick={(e) => e.stopPropagation()} style={{ textDecoration: "none" }}>
                                <Eye size={12} /> View
                              </Link>
                              {inv.status !== "SYNCED" && (
                                <button className="gst-btn gst-btn-primary gst-btn-sm" onClick={(e) => { e.stopPropagation(); handleSyncInvoice(inv.id); }}>
                                  <ExternalLink size={12} /> Sync
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                        {expandedInvoice === inv.id && (
                          <tr><td colSpan={13} style={{ padding: "0.5rem 1rem 1rem", background: "#f8fafc" }}>
                            <div style={{ fontSize: "0.82rem", color: "#475569" }}>
                              <strong>Buyer:</strong> {inv.buyerName} ({inv.buyerGstin}) |
                              <strong> Place of Supply:</strong> {inv.placeOfSupply} |
                              <strong> Inter-state:</strong> {inv.isInterState ? "Yes" : "No"}
                              {inv.syncError && <><br /><strong style={{ color: "#dc2626" }}>Error:</strong> {inv.syncError}</>}
                              {inv.items && inv.items.length > 0 && (
                                <div style={{ marginTop: "0.5rem" }}>
                                  <strong>Items:</strong>
                                  <ul style={{ margin: "0.25rem 0 0", paddingLeft: "1.25rem" }}>
                                    {inv.items.map((item, i) => (
                                      <li key={i}>{item.productName} — HSN: {item.hsnCode} — Qty: {item.quantity} x &#8377;{item.unitPrice} — Taxable: &#8377;{item.taxableValue}</li>
                                    ))}
                                  </ul>
                                </div>
                              )}
                            </div>
                          </td></tr>
                        )}
                      </React.Fragment>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {totalPages > 1 && (
              <div style={{ display: "flex", justifyContent: "center", gap: "0.5rem", marginTop: "1rem" }}>
                <button className="gst-btn gst-btn-outline gst-btn-sm" disabled={page <= 0} onClick={() => setPage(page - 1)}>Previous</button>
                <span style={{ padding: "0.35rem 0.7rem", fontSize: "0.85rem", color: "#64748b" }}>Page {page + 1} of {totalPages}</span>
                <button className="gst-btn gst-btn-outline gst-btn-sm" disabled={page >= totalPages - 1} onClick={() => setPage(page + 1)}>Next</button>
              </div>
            )}
          </>
        )}

        {activeTab === "creditNotes" && (
          <>
            <div className="gst-toolbar">
              <input className="gst-search" placeholder="Search credit notes..." />
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={fetchCreditNotes}><RefreshCw size={14} /> Refresh</button>
              <button className="gst-btn gst-btn-primary gst-btn-sm" onClick={() => downloadExport("/api/gst/creditnotes/export", "credit-notes.csv", "csv")}><FileSpreadsheet size={14} /> Export CSV</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={() => downloadExport("/api/gst/creditnotes/export", "credit-notes.json", "json")}><FileJson size={14} /> JSON</button>
            </div>
            {loading ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>Loading credit notes...</p>
            ) : creditNotes.length === 0 ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>No credit notes yet. They are auto-generated when an order is cancelled, refunded, or a return is approved - with full tax (CGST/SGST/IGST) reversal.</p>
            ) : (
              <div className="gst-table-wrap">
                <table className="gst-table">
                  <thead>
                    <tr><th>Credit Note #</th><th>Date</th><th>Order</th><th>Invoice</th><th>Buyer</th><th>Taxable</th><th>CGST</th><th>SGST</th><th>IGST</th><th>Total</th><th>Reason</th><th>Actions</th></tr>
                  </thead>
                  <tbody>
                    {creditNotes.map((cn) => (
                      <tr key={cn.id}>
                        <td style={{ fontFamily: "monospace", fontWeight: 600, fontSize: "0.8rem" }}>{cn.creditNoteNumber}</td>
                        <td>{cn.creditNoteDate}</td>
                        <td>#{cn.orderId}</td>
                        <td style={{ fontSize: "0.78rem" }}>{cn.originalInvoiceNumber || "-"}</td>
                        <td>{cn.buyerName}</td>
                        <td>&#8377;{(cn.taxableAmount || 0).toFixed(2)}</td>
                        <td>&#8377;{(cn.cgstAmount || 0).toFixed(2)}</td>
                        <td>&#8377;{(cn.sgstAmount || 0).toFixed(2)}</td>
                        <td>&#8377;{(cn.igstAmount || 0).toFixed(2)}</td>
                        <td style={{ fontWeight: 600 }}>&#8377;{(cn.totalAmount || 0).toFixed(2)}</td>
                        <td style={{ maxWidth: 180, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", fontSize: "0.78rem" }}>{cn.reason || "-"}</td>
                        <td>
                          <Link to={`/seller/gst-invoices/credit-note/${cn.id}`} className="gst-btn gst-btn-outline gst-btn-sm" style={{ textDecoration: "none" }}>
                            <Eye size={12} /> View
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {cnTotalPages > 1 && (
              <div style={{ display: "flex", justifyContent: "center", gap: "0.5rem", marginTop: "1rem" }}>
                <button className="gst-btn gst-btn-outline gst-btn-sm" disabled={cnPage <= 0} onClick={() => setCnPage(cnPage - 1)}>Previous</button>
                <span style={{ padding: "0.35rem 0.7rem", fontSize: "0.85rem", color: "#64748b" }}>Page {cnPage + 1} of {cnTotalPages}</span>
                <button className="gst-btn gst-btn-outline gst-btn-sm" disabled={cnPage >= cnTotalPages - 1} onClick={() => setCnPage(cnPage + 1)}>Next</button>
              </div>
            )}
          </>
        )}

        {activeTab === "gstr3b" && (
          <>
            <div className="gst-toolbar">
              <p style={{ margin: 0, flex: 1, fontSize: "0.88rem", color: "#64748b" }}>GSTR-3B Table 3.1(a) outward taxable supplies (net of credit notes) and ITC summary for the current month.</p>
              <button className="gst-btn gst-btn-primary gst-btn-sm" onClick={() => downloadExport("/api/gst/gstr3b/export", "gstr3b.csv", "csv")}><FileSpreadsheet size={14} /> Export CSV</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={() => downloadExport("/api/gst/gstr3b/export", "gstr3b.json", "json")}><FileJson size={14} /> JSON</button>
            </div>
            {loading ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>Loading GSTR-3B...</p>
            ) : !gstr3b ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>No GSTR-3B data available.</p>
            ) : (
              <div className="gst-form" style={{ maxWidth: "100%" }}>
                <h3>Table 3.1(a) - Outward taxable supplies</h3>
                <div className="gst-summary-grid">
                  <div className="gst-summary-item"><div className="gst-summary-label">Taxable Value</div><div className="gst-summary-value">&#8377;{(gstr3b.table3_1_outwardSupplies?.taxableValue || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">CGST</div><div className="gst-summary-value" style={{ color: "#2563eb" }}>&#8377;{(gstr3b.table3_1_outwardSupplies?.cgst || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">SGST</div><div className="gst-summary-value" style={{ color: "#7c3aed" }}>&#8377;{(gstr3b.table3_1_outwardSupplies?.sgst || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">IGST</div><div className="gst-summary-value" style={{ color: "#d97706" }}>&#8377;{(gstr3b.table3_1_outwardSupplies?.igst || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Total Tax</div><div className="gst-summary-value">&#8377;{(gstr3b.table3_1_outwardSupplies?.totalTax || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Gross Taxable Value</div><div className="gst-summary-value">&#8377;{(gstr3b.grossTaxableValue || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Credit Notes (reduction)</div><div className="gst-summary-value" style={{ color: "#dc2626" }}>&#8377;{(gstr3b.grossCreditNotesValue || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Invoices / Credit Notes</div><div className="gst-summary-value">{gstr3b.invoiceCount || 0} / {gstr3b.creditNoteCount || 0}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Period</div><div className="gst-summary-value">{gstr3b.periodStart} to {gstr3b.periodEnd}</div></div>
                </div>
                <h3 style={{ marginTop: "1.5rem" }}>Table 3.1(b) - ITC Eligible (B2B)</h3>
                <div className="gst-summary-grid">
                  <div className="gst-summary-item"><div className="gst-summary-label">Taxable Value</div><div className="gst-summary-value">&#8377;{(gstr3b.table3_1b_itc?.eligibleTaxableValue || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">CGST</div><div className="gst-summary-value" style={{ color: "#2563eb" }}>&#8377;{(gstr3b.table3_1b_itc?.eligibleCgst || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">SGST</div><div className="gst-summary-value" style={{ color: "#7c3aed" }}>&#8377;{(gstr3b.table3_1b_itc?.eligibleSgst || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">IGST</div><div className="gst-summary-value" style={{ color: "#d97706" }}>&#8377;{(gstr3b.table3_1b_itc?.eligibleIgst || 0).toFixed(2)}</div></div>
                </div>
              </div>
            )}
          </>
        )}

        {activeTab === "generate" && (
          <div className="gst-form">
            <h3>Generate GST Invoice from Order</h3>
            <div className="gst-field">
              <label>Order ID</label>
              <input type="number" value={orderId} onChange={(e) => setOrderId(e.target.value)} placeholder="Enter order ID" />
            </div>
            <div className="gst-field">
              <label>Seller GSTIN</label>
              <select value={selectedGstin} onChange={(e) => setSelectedGstin(e.target.value)}>
                <option value="">Select GSTIN</option>
                {configs.map((c) => (
                  <option key={c.id} value={c.gstin}>{c.gstin} — {c.legalName} ({c.stateName})</option>
                ))}
              </select>
            </div>
            <div className="gst-field">
              <label>Buyer GSTIN <span style={{ color: "#94a3b8", fontWeight: 400 }}>(optional - for B2B invoices)</span></label>
              <input type="text" value={buyerGstin} onChange={(e) => setBuyerGstin(e.target.value)} placeholder="Enter buyer GSTIN for B2B (or leave blank for B2C)" />
            </div>
            <button className="gst-btn gst-btn-primary" onClick={handleGenerate} disabled={generating}>
              {generating ? "Generating..." : <><FileText size={16} /> Generate Invoice</>}
            </button>
          </div>
        )}

        {activeTab === "summary" && (
          <div className="gst-form" style={{ maxWidth: "100%" }}>
            <h3>GST Summary</h3>
            {summary ? (
              <div className="gst-summary-grid">
                <div className="gst-summary-item"><div className="gst-summary-label">Total Invoices</div><div className="gst-summary-value">{summary.totalInvoices}</div></div>
                <div className="gst-summary-item"><div className="gst-summary-label">Taxable Amount</div><div className="gst-summary-value">&#8377;{(summary.totalTaxableAmount || 0).toFixed(2)}</div></div>
                <div className="gst-summary-item"><div className="gst-summary-label">Total CGST</div><div className="gst-summary-value" style={{ color: "#2563eb" }}>&#8377;{(summary.totalCgst || 0).toFixed(2)}</div></div>
                <div className="gst-summary-item"><div className="gst-summary-label">Total SGST</div><div className="gst-summary-value" style={{ color: "#7c3aed" }}>&#8377;{(summary.totalSgst || 0).toFixed(2)}</div></div>
                <div className="gst-summary-item"><div className="gst-summary-label">Total IGST</div><div className="gst-summary-value" style={{ color: "#d97706" }}>&#8377;{(summary.totalIgst || 0).toFixed(2)}</div></div>
                <div className="gst-summary-item"><div className="gst-summary-label">Total Tax</div><div className="gst-summary-value">&#8377;{(summary.totalTax || 0).toFixed(2)}</div></div>
                <div className="gst-summary-item"><div className="gst-summary-label">TCS Collected (1%)</div><div className="gst-summary-value" style={{ color: "#dc2626" }}>&#8377;{(summary.totalTcs || 0).toFixed(2)}</div></div>
                <div className="gst-summary-item"><div className="gst-summary-label">Intra-state / Inter-state</div><div className="gst-summary-value">{summary.intraStateCount} / {summary.interStateCount}</div></div>
                <div className="gst-summary-item"><div className="gst-summary-label">Period</div><div className="gst-summary-value">{summary.periodStart} to {summary.periodEnd}</div></div>
              </div>
            ) : loading ? (
              <p style={{ color: "#94a3b8" }}>Loading summary...</p>
            ) : (
              <p style={{ color: "#94a3b8" }}>No data available.</p>
            )}
          </div>
        )}

        {activeTab === "gstr1" && (
          <>
            <div className="gst-toolbar">
              <p style={{ margin: 0, flex: 1, fontSize: "0.88rem", color: "#64748b" }}>GSTR-1 data for the current month. This data can be exported for filing.</p>
              <button className="gst-btn gst-btn-primary gst-btn-sm" onClick={() => downloadExport("/api/gst/gstr1/export", "gstr1.csv", "csv")}><FileSpreadsheet size={14} /> Export CSV</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={() => downloadExport("/api/gst/gstr1/export", "gstr1.json", "json")}><FileJson size={14} /> JSON</button>
            </div>
            {gstr1.length === 0 ? (
              <p style={{ color: "#94a3b8" }}>No invoice data available for GSTR-1.</p>
            ) : (
              <div className="gst-table-wrap">
                <table className="gst-table">
                  <thead><tr><th>Invoice #</th><th>Date</th><th>Buyer</th><th>GSTIN</th><th>Taxable</th><th>CGST</th><th>SGST</th><th>IGST</th><th>Total</th><th>Place</th><th>Inter</th><th>IRN</th></tr></thead>
                  <tbody>
                    {gstr1.map((row, i) => (
                      <tr key={i}>
                        <td style={{ fontFamily: "monospace", fontSize: "0.78rem" }}>{row.invoiceNumber}</td>
                        <td>{row.invoiceDate}</td>
                        <td>{row.buyerName}</td>
                        <td style={{ fontFamily: "monospace", fontSize: "0.78rem" }}>{row.buyerGstin}</td>
                        <td>&#8377;{(row.taxableAmount || 0).toFixed(2)}</td>
                        <td>&#8377;{(row.cgst || 0).toFixed(2)}</td>
                        <td>&#8377;{(row.sgst || 0).toFixed(2)}</td>
                        <td>&#8377;{(row.igst || 0).toFixed(2)}</td>
                        <td style={{ fontWeight: 600 }}>&#8377;{(row.totalAmount || 0).toFixed(2)}</td>
                        <td style={{ fontSize: "0.78rem" }}>{row.placeOfSupply}</td>
                        <td>{row.isInterState ? "Yes" : "No"}</td>
                        <td style={{ fontSize: "0.72rem" }}>{row.irn || "-"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}

        {activeTab === "tcs" && (
          <div className="gst-form" style={{ maxWidth: "100%" }}>
            <h3>TCS Summary (GSTR-8)</h3>
            <div className="static-info-box" style={{ marginBottom: "1rem" }}>
              <strong>1% TCS</strong> is deducted on marketplace sales as per Section 52 of the CGST Act. GSTR-8 must be filed monthly.
            </div>
            {tcs ? (
              <div className="gst-summary-grid">
                <div className="gst-summary-item"><div className="gst-summary-label">Total TCS Collected</div><div className="gst-summary-value" style={{ color: "#dc2626" }}>&#8377;{(tcs.totalTcsCollected || 0).toFixed(2)}</div></div>
                <div className="gst-summary-item"><div className="gst-summary-label">TCS Rate</div><div className="gst-summary-value">{tcs.tcsRate}</div></div>
                <div className="gst-summary-item"><div className="gst-summary-label">Total Invoices</div><div className="gst-summary-value">{tcs.totalInvoices}</div></div>
                <div className="gst-summary-item"><div className="gst-summary-label">Taxable Amount</div><div className="gst-summary-value">&#8377;{(tcs.totalTaxableAmount || 0).toFixed(2)}</div></div>
                <div className="gst-summary-item"><div className="gst-summary-label">Period</div><div className="gst-summary-value">{tcs.periodStart} to {tcs.periodEnd}</div></div>
                <div className="gst-summary-item"><div className="gst-summary-label">GSTR-8 Filing</div><div className="gst-summary-value" style={{ color: "#16a34a" }}>{tcs.gstr8Applicable ? "Applicable" : "N/A"}</div></div>
              </div>
            ) : loading ? (
              <p style={{ color: "#94a3b8" }}>Loading TCS data...</p>
            ) : (
              <p style={{ color: "#94a3b8" }}>No TCS data available.</p>
            )}
          </div>
        )}
      </div>
    </>
  );
};
export default GstInvoices;
