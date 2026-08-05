import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Link } from "react-router-dom";
import { FileText, Download, RefreshCw, CheckCircle, XCircle, Clock, AlertTriangle, FileSpreadsheet, FileJson, Eye, Plus, Landmark, RefreshCw as SyncIcon, Store, FileMinus, BarChart3, CalendarClock } from "lucide-react";
import api from "../api/axios";

const GST_COMPLIANCE_STYLES = `
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
  .gst-btn:disabled { opacity: 0.5; cursor: not-allowed; }

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

  .gst-alert { display: flex; align-items: center; gap: 0.5rem; padding: 0.6rem 1rem; border-radius: 8px; font-size: 0.85rem; margin-bottom: 0.75rem; }
  .gst-alert-warn { background: #fef2f2; border: 1px solid #fecaca; color: #991b1b; }
  .gst-alert-info { background: #eff6ff; border: 1px solid #bfdbfe; color: #1e40af; }
  .gst-alert-ok { background: #f0fdf4; border: 1px solid #bbf7d0; color: #166534; }

  @media (max-width: 768px) {
    .gst-page { padding: 1rem; }
    .gst-stats { grid-template-columns: repeat(2, 1fr); }
    .gst-summary-grid { grid-template-columns: 1fr; }
  }
`;

const TABS = [
  { id: "dashboard", label: "Compliance", icon: CalendarClock },
  { id: "debitnotes", label: "Debit Notes", icon: FileMinus },
  { id: "gstr9", label: "GSTR-9", icon: BarChart3 },
  { id: "gstr9c", label: "GSTR-9C", icon: BarChart3 },
  { id: "gstr8", label: "GSTR-8", icon: Landmark },
  { id: "tcs", label: "TCS Records", icon: AlertTriangle },
  { id: "reconciliation", label: "Reconciliation", icon: RefreshCw },
  { id: "marketplace", label: "Marketplace Sync", icon: Store },
  { id: "apob", label: "APOB", icon: FileText },
];

const CHANNELS = ["AMAZON", "FLIPKART", "SHOPIFY", "WOOCOMMERCE"];

const monthOf = (period) => (period ? period.slice(0, 2) : "");
const yearOf = (period) => (period ? "20" + period.slice(2) : "");

const GstCompliance = () => {
  const [activeTab, setActiveTab] = useState("dashboard");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const [dashboard, setDashboard] = useState(null);
  const [gstnStatus, setGstnStatus] = useState(null);
  const [configs, setConfigs] = useState([]);

  const [debitNotes, setDebitNotes] = useState([]);
  const [dnTotalPages, setDnTotalPages] = useState(0);
  const [dnPage, setDnPage] = useState(0);
  const [dnInvoiceId, setDnInvoiceId] = useState("");
  const [dnAmount, setDnAmount] = useState("");
  const [dnReason, setDnReason] = useState("");

  const [fiscalYear, setFiscalYear] = useState(new Date().getFullYear());
  const [gstr9, setGstr9] = useState(null);
  const [gstr9c, setGstr9c] = useState(null);

  const [gstr8Period, setGstr8Period] = useState(() => {
    const d = new Date();
    d.setMonth(d.getMonth() - 1);
    return `${String(d.getMonth() + 1).padStart(2, "0")}${d.getFullYear()}`;
  });
  const [gstr8, setGstr8] = useState(null);

  const [tcsRecords, setTcsRecords] = useState([]);
  const [tcsTotalPages, setTcsTotalPages] = useState(0);
  const [tcsPage, setTcsPage] = useState(0);

  const [recons, setRecons] = useState([]);
  const [reconPeriod, setReconPeriod] = useState("");
  const [reconDetail, setReconDetail] = useState(null);

  const [channel, setChannel] = useState("AMAZON");
  const [mpOrders, setMpOrders] = useState(null);
  const [mpProducts, setMpProducts] = useState(null);
  const [mpSettlements, setMpSettlements] = useState(null);
  const [mpInventory, setMpInventory] = useState(null);
  const [mpLoading, setMpLoading] = useState(false);

  const [apobSellerId, setApobSellerId] = useState("");
  const [apobList, setApobList] = useState([]);
  const [apobGstin, setApobGstin] = useState("");
  const [apobText, setApobText] = useState("");

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

  const fetchDashboard = async () => {
    setLoading(true); setError("");
    try {
      const [dashRes, gstnRes, configRes] = await Promise.allSettled([
        api.get("/api/gst/compliance/dashboard"),
        api.get("/api/gst/gstn/status"),
        api.get("/api/gst/configurations"),
      ]);
      if (dashRes.status === "fulfilled") setDashboard(dashRes.value.data);
      if (gstnRes.status === "fulfilled") setGstnStatus(gstnRes.value.data);
      if (configRes.status === "fulfilled") {
        setConfigs(configRes.value.data.configurations || []);
        const c = (configRes.value.data.configurations || [])[0];
        if (c?.sellerId && !apobSellerId) setApobSellerId(String(c.sellerId));
      }
    } catch { setError("Failed to load compliance dashboard"); }
    setLoading(false);
  };

  const fetchDebitNotes = async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/gst/debitnotes", { params: { page: dnPage, size: 20 } });
      setDebitNotes(res.data.content || []);
      setDnTotalPages(res.data.totalPages || 0);
    } catch { setDebitNotes([]); }
    setLoading(false);
  };

  const handleCreateDebitNote = async () => {
    setError(""); setNotice("");
    if (!dnInvoiceId) { setError("Invoice ID is required"); return; }
    try {
      const payload = { invoiceId: parseInt(dnInvoiceId) };
      if (dnAmount) payload.amount = parseFloat(dnAmount);
      if (dnReason) payload.reason = dnReason;
      const res = await api.post("/api/gst/debitnote", payload);
      setNotice(`Debit note ${res.data.debitNote?.debitNoteNumber} generated`);
      setDnInvoiceId(""); setDnAmount(""); setDnReason("");
      fetchDebitNotes();
    } catch (err) {
      setError(err.response?.data?.error || "Failed to generate debit note");
    }
  };

  const fetchGstr9 = async () => {
    setLoading(true); setError("");
    try {
      const res = await api.get("/api/gst/reports/gstr9", { params: { fiscalYear } });
      setGstr9(res.data);
    } catch { setGstr9(null); }
    setLoading(false);
  };

  const fetchGstr9c = async () => {
    setLoading(true); setError("");
    try {
      const res = await api.get("/api/gst/reports/gstr9c", { params: { fiscalYear } });
      setGstr9c(res.data);
    } catch { setGstr9c(null); }
    setLoading(false);
  };

  const fetchGstr8 = async () => {
    setLoading(true); setError("");
    try {
      const res = await api.get("/api/gst/reports/gstr8", { params: { period: gstr8Period } });
      setGstr8(res.data);
    } catch { setGstr8(null); }
    setLoading(false);
  };

  const fetchTcs = async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/gst/reports/tcs", { params: { page: tcsPage, size: 20 } });
      setTcsRecords(res.data.content || []);
      setTcsTotalPages(res.data.totalPages || 0);
    } catch { setTcsRecords([]); }
    setLoading(false);
  };

  const fetchRecons = async () => {
    setLoading(true); setError("");
    try {
      const res = await api.get("/api/gst/reconciliation");
      setRecons(res.data.content || []);
    } catch { setRecons([]); }
    setLoading(false);
  };

  const handleGenerateRecon = async () => {
    setError(""); setNotice("");
    if (!reconPeriod) { setError("Period (MMyyyy) is required"); return; }
    try {
      const res = await api.post("/api/gst/reconciliation/generate", { period: reconPeriod });
      setReconDetail(res.data);
      setNotice("Reconciliation generated");
      fetchRecons();
    } catch (err) {
      setError(err.response?.data?.error || "Failed to generate reconciliation");
    }
  };

  const handleViewRecon = async (period) => {
    try {
      const res = await api.get(`/api/gst/reconciliation/${period}`);
      setReconDetail({ reconciliation: res.data.reconciliation });
    } catch (err) {
      setError(err.response?.data?.error || "Failed to load reconciliation");
    }
  };

  const handleUpdateReconStatus = async (id, status) => {
    try {
      await api.put(`/api/gst/reconciliation/${id}/status`, { status });
      setNotice("Reconciliation status updated");
      fetchRecons();
      if (reconDetail?.reconciliation?.id === id) {
        handleViewRecon(reconDetail.reconciliation.period);
      }
    } catch (err) {
      setError(err.response?.data?.error || "Failed to update status");
    }
  };

  const handleImportOrders = async () => {
    setMpLoading(true); setError(""); setNotice("");
    try {
      const res = await api.post("/api/gst/sync/marketplace/orders", { channel, limit: 10 });
      setMpOrders(res.data);
    } catch (err) { setError(err.response?.data?.error || "Failed to import orders"); }
    setMpLoading(false);
  };

  const handleSyncInventory = async () => {
    setMpLoading(true); setError(""); setNotice("");
    try {
      const res = await api.post("/api/gst/sync/marketplace/inventory", { channel });
      setMpInventory(res.data);
    } catch (err) { setError(err.response?.data?.error || "Failed to sync inventory"); }
    setMpLoading(false);
  };

  const handleFetchProducts = async () => {
    setMpLoading(true); setError(""); setNotice("");
    try {
      const res = await api.get(`/api/gst/sync/marketplace/products/${channel}`);
      setMpProducts(res.data);
    } catch (err) { setError(err.response?.data?.error || "Failed to fetch products"); }
    setMpLoading(false);
  };

  const handleFetchSettlements = async () => {
    setMpLoading(true); setError(""); setNotice("");
    try {
      const res = await api.get("/api/gst/sync/marketplace/settlements", { params: { channel } });
      setMpSettlements(res.data);
    } catch (err) { setError(err.response?.data?.error || "Failed to fetch settlements"); }
    setMpLoading(false);
  };

  const handleLoadApob = async () => {
    setError(""); setNotice("");
    if (!apobSellerId) { setError("Seller ID is required"); return; }
    try {
      const res = await api.get(`/api/gst/seller/${apobSellerId}/apob`);
      setApobGstin(res.data.gstin || "");
      setApobList(res.data.apobList || []);
      setApobText((res.data.apobList || []).join("\n"));
    } catch (err) {
      setError(err.response?.data?.error || "Failed to load APOB");
    }
  };

  const handleSaveApob = async () => {
    setError(""); setNotice("");
    if (!apobSellerId) { setError("Seller ID is required"); return; }
    try {
      const apobs = apobText.split("\n").map((s) => s.trim()).filter(Boolean);
      const res = await api.put(`/api/gst/seller/${apobSellerId}/apob`, { apobList: apobs });
      setApobList(res.data.apobList || []);
      setNotice(res.data.message || "APOB updated");
    } catch (err) {
      setError(err.response?.data?.error || "Failed to save APOB");
    }
  };

  useEffect(() => {
    if (activeTab === "dashboard") fetchDashboard();
    else if (activeTab === "debitnotes") fetchDebitNotes();
    else if (activeTab === "gstr9") fetchGstr9();
    else if (activeTab === "gstr9c") fetchGstr9c();
    else if (activeTab === "gstr8") fetchGstr8();
    else if (activeTab === "tcs") fetchTcs();
    else if (activeTab === "reconciliation") fetchRecons();
  }, [activeTab, dnPage, tcsPage]);

  return (
    <>
      <style>{GST_COMPLIANCE_STYLES}</style>
      <div className="gst-page">
        <Helmet><title>GST Compliance | Cauvery Store</title></Helmet>

        <h1>GST Compliance Center</h1>
        <p className="gst-subtitle">Annual returns (GSTR-9/9C), monthly returns (GSTR-8), GSTR-2B reconciliation, blocked credits, debit notes, and marketplace sync.</p>

        <div className="gst-tabs">
          {TABS.map((t) => (
            <button key={t.id} className={`gst-tab${activeTab === t.id ? " active" : ""}`} onClick={() => { setActiveTab(t.id); setError(""); setNotice(""); }}>
              {t.label}
            </button>
          ))}
        </div>

        {error && <div className="gst-alert gst-alert-warn"><XCircle size={15} /> {error}</div>}
        {notice && <div className="gst-alert gst-alert-ok"><CheckCircle size={15} /> {notice}</div>}

        {activeTab === "dashboard" && (
          <>
            {gstnStatus && (
              <div className={`gst-alert ${gstnStatus.simulated ? "gst-alert-info" : "gst-alert-ok"}`}>
                <SyncIcon size={15} />
                <span><strong>GSTN Adapter:</strong> {gstnStatus.adapter} — {gstnStatus.message}</span>
              </div>
            )}
            {loading ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>Loading compliance dashboard...</p>
            ) : (
              <>
                <div className="gst-stats">
                  <div className="gst-stat-card"><p className="gst-stat-label">GSTIN</p><p className="gst-stat-value" style={{ fontSize: "1.05rem", fontFamily: "monospace" }}>{dashboard?.gstin || "-"}</p></div>
                  <div className="gst-stat-card"><p className="gst-stat-label">Pending Returns</p><p className="gst-stat-value" style={{ color: "#f59e0b" }}>{dashboard?.deadlineSummary?.pending || 0}</p></div>
                  <div className="gst-stat-card"><p className="gst-stat-label">Filed</p><p className="gst-stat-value" style={{ color: "#16a34a" }}>{dashboard?.deadlineSummary?.filed || 0}</p></div>
                  <div className="gst-stat-card"><p className="gst-stat-label">Overdue</p><p className="gst-stat-value" style={{ color: "#dc2626" }}>{dashboard?.deadlineSummary?.overdue || 0}</p></div>
                  <div className="gst-stat-card"><p className="gst-stat-label">Due in 7 Days</p><p className="gst-stat-value" style={{ color: "#d97706" }}>{dashboard?.deadlineSummary?.dueSoon || 0}</p></div>
                  <div className="gst-stat-card"><p className="gst-stat-label">2B Recon</p><p className="gst-stat-value" style={{ color: dashboard?.reconciliationStatus === "OPEN" ? "#d97706" : "#16a34a" }}>{dashboard?.reconciliationStatus || "NOT_STARTED"}</p></div>
                </div>

                {(dashboard?.alerts || []).map((a, i) => (
                  <div key={i} className="gst-alert gst-alert-warn"><AlertTriangle size={15} /> {a}</div>
                ))}

                <h3 style={{ fontSize: "1rem", fontWeight: 600, color: "#0f172a", margin: "1.5rem 0 0.75rem" }}>Filing Deadlines</h3>
                {(dashboard?.deadlines || []).length === 0 ? (
                  <p style={{ fontSize: "0.85rem", color: "#94a3b8" }}>No deadlines yet. Visit the reconciliation tab or sync deadlines from the GSTR-8 tab.</p>
                ) : (
                  <div className="gst-table-wrap">
                    <table className="gst-table">
                      <thead><tr><th>Form</th><th>Period</th><th>Due Date</th><th>Filed Date</th><th>Status</th><th>Alert</th></tr></thead>
                      <tbody>
                        {dashboard.deadlines.map((dl, i) => (
                          <tr key={i}>
                            <td style={{ fontWeight: 600 }}>{dl.form}</td>
                            <td>{dl.period}</td>
                            <td>{dl.dueDate}</td>
                            <td>{dl.filedDate || "-"}</td>
                            <td>
                              <span className={`gst-status ${dl.status === "FILED" ? "gst-status-synced" : "gst-status-generated"}`}>
                                {dl.status === "FILED" ? <CheckCircle size={12} /> : <Clock size={12} />}{dl.status}
                              </span>
                            </td>
                            <td>
                              {dl.alert === "OVERDUE" ? <span className="gst-status gst-status-failed">OVERDUE</span>
                                : dl.alert === "DUE_SOON" ? <span className="gst-status gst-status-generated">DUE SOON</span>
                                : <span style={{ color: "#94a3b8", fontSize: "0.75rem" }}>{dl.alert || "-"}</span>}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}

                <h3 style={{ fontSize: "1rem", fontWeight: 600, color: "#0f172a", margin: "1.5rem 0 0.75rem" }}>TCS Collected (GSTR-8)</h3>
                <div className="gst-stats">
                  <div className="gst-stat-card"><p className="gst-stat-label">Total TCS</p><p className="gst-stat-value" style={{ color: "#dc2626" }}>&#8377;{(dashboard?.tcsSummary?.totalTcsCollected || 0).toFixed(2)}</p></div>
                  <div className="gst-stat-card"><p className="gst-stat-label">TCS Records</p><p className="gst-stat-value">{dashboard?.tcsSummary?.recordCount || 0}</p></div>
                </div>
              </>
            )}
          </>
        )}

        {activeTab === "debitnotes" && (
          <>
            <div className="gst-toolbar">
              <input className="gst-search" placeholder="Search debit notes..." readOnly />
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={fetchDebitNotes}><RefreshCw size={14} /> Refresh</button>
              <button className="gst-btn gst-btn-primary gst-btn-sm" onClick={() => downloadExport("/api/gst/debitnotes/export", "debit-notes.csv", "csv")}><FileSpreadsheet size={14} /> CSV</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={() => downloadExport("/api/gst/debitnotes/export", "debit-notes.json", "json")}><FileJson size={14} /> JSON</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={() => downloadExport("/api/gst/debitnotes/export", "debit-notes.xlsx", "xlsx")}><FileSpreadsheet size={14} /> XLSX</button>
            </div>

            <div className="gst-form" style={{ marginBottom: "1.5rem" }}>
              <h3>Generate Debit Note</h3>
              <p style={{ fontSize: "0.82rem", color: "#64748b", margin: "0 0 1rem" }}>
                Issue a debit note against an existing GST invoice for additional charges (positive tax adjustment). Leave amount blank for the full invoice value.
              </p>
              <div className="gst-field">
                <label>Invoice ID</label>
                <input type="number" value={dnInvoiceId} onChange={(e) => setDnInvoiceId(e.target.value)} placeholder="Enter invoice ID" />
              </div>
              <div className="gst-field">
                <label>Amount <span style={{ color: "#94a3b8", fontWeight: 400 }}>(optional)</span></label>
                <input type="number" step="0.01" value={dnAmount} onChange={(e) => setDnAmount(e.target.value)} placeholder="Partial amount or blank for full invoice" />
              </div>
              <div className="gst-field">
                <label>Reason</label>
                <input type="text" value={dnReason} onChange={(e) => setDnReason(e.target.value)} placeholder="e.g. Additional charges on invoice CS2608/00002" />
              </div>
              <button className="gst-btn gst-btn-primary" onClick={handleCreateDebitNote}><Plus size={16} /> Generate Debit Note</button>
            </div>

            {loading ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>Loading debit notes...</p>
            ) : debitNotes.length === 0 ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>No debit notes yet.</p>
            ) : (
              <div className="gst-table-wrap">
                <table className="gst-table">
                  <thead>
                    <tr><th>Debit Note #</th><th>Date</th><th>Order</th><th>Invoice</th><th>Buyer</th><th>Taxable</th><th>CGST</th><th>SGST</th><th>IGST</th><th>Total</th><th>Reason</th><th>Actions</th></tr>
                  </thead>
                  <tbody>
                    {debitNotes.map((dn) => (
                      <tr key={dn.id}>
                        <td style={{ fontFamily: "monospace", fontWeight: 600, fontSize: "0.8rem" }}>{dn.debitNoteNumber}</td>
                        <td>{dn.debitNoteDate}</td>
                        <td>#{dn.orderId}</td>
                        <td style={{ fontSize: "0.78rem" }}>{dn.originalInvoiceNumber || "-"}</td>
                        <td>{dn.buyerName}</td>
                        <td>&#8377;{(dn.taxableAmount || 0).toFixed(2)}</td>
                        <td>&#8377;{(dn.cgstAmount || 0).toFixed(2)}</td>
                        <td>&#8377;{(dn.sgstAmount || 0).toFixed(2)}</td>
                        <td>&#8377;{(dn.igstAmount || 0).toFixed(2)}</td>
                        <td style={{ fontWeight: 600 }}>&#8377;{(dn.totalAmount || 0).toFixed(2)}</td>
                        <td style={{ maxWidth: 180, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", fontSize: "0.78rem" }}>{dn.reason || "-"}</td>
                        <td>
                          <Link to={`/seller/gst-invoices/debit-note/${dn.id}`} className="gst-btn gst-btn-outline gst-btn-sm" style={{ textDecoration: "none" }}>
                            <Eye size={12} /> View
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {dnTotalPages > 1 && (
              <div style={{ display: "flex", justifyContent: "center", gap: "0.5rem", marginTop: "1rem" }}>
                <button className="gst-btn gst-btn-outline gst-btn-sm" disabled={dnPage <= 0} onClick={() => setDnPage(dnPage - 1)}>Previous</button>
                <span style={{ padding: "0.35rem 0.7rem", fontSize: "0.85rem", color: "#64748b" }}>Page {dnPage + 1} of {dnTotalPages}</span>
                <button className="gst-btn gst-btn-outline gst-btn-sm" disabled={dnPage >= dnTotalPages - 1} onClick={() => setDnPage(dnPage + 1)}>Next</button>
              </div>
            )}
          </>
        )}

        {activeTab === "gstr9" && (
          <>
            <div className="gst-toolbar">
              <div className="gst-field" style={{ margin: 0, minWidth: 140 }}>
                <select value={fiscalYear} onChange={(e) => setFiscalYear(parseInt(e.target.value))}>
                  {[new Date().getFullYear(), new Date().getFullYear() - 1, new Date().getFullYear() - 2].map((y) => (
                    <option key={y} value={y}>FY {y}-{String(y + 1).slice(2)}</option>
                  ))}
                </select>
              </div>
              <button className="gst-btn gst-btn-primary gst-btn-sm" onClick={fetchGstr9}><RefreshCw size={14} /> Load</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={() => downloadExport(`/api/gst/reports/gstr9/export?fiscalYear=${fiscalYear}`, "gstr9.json", "json")}><FileJson size={14} /> JSON</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={() => downloadExport(`/api/gst/reports/gstr9/export?fiscalYear=${fiscalYear}`, "gstr9.csv", "csv")}><FileSpreadsheet size={14} /> CSV</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={() => downloadExport(`/api/gst/reports/gstr9/export?fiscalYear=${fiscalYear}`, "gstr9.xlsx", "xlsx")}><FileSpreadsheet size={14} /> XLSX</button>
            </div>
            {loading ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>Loading GSTR-9...</p>
            ) : !gstr9 ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>No GSTR-9 data available.</p>
            ) : (
              <div className="gst-form" style={{ maxWidth: "100%" }}>
                <h3>GSTR-9 Annual Return — {gstr9.gstin}</h3>
                <p style={{ fontSize: "0.82rem", color: "#64748b", margin: "0 0 1rem" }}>Period: {gstr9.periodStart} to {gstr9.periodEnd}</p>
                <div className="gst-summary-grid">
                  <div className="gst-summary-item"><div className="gst-summary-label">Total Invoices</div><div className="gst-summary-value">{gstr9.totalInvoices}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">B2B / B2C</div><div className="gst-summary-value">{gstr9.b2bCount} / {gstr9.b2cCount}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Intra / Inter</div><div className="gst-summary-value">{gstr9.intraStateCount} / {gstr9.interStateCount}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Credit Notes</div><div className="gst-summary-value">{gstr9.creditNoteCount}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Debit Notes</div><div className="gst-summary-value">{gstr9.debitNoteCount}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Taxable Value (net)</div><div className="gst-summary-value">&#8377;{(gstr9.taxableValue || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">CGST</div><div className="gst-summary-value" style={{ color: "#2563eb" }}>&#8377;{(gstr9.cgst || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">SGST</div><div className="gst-summary-value" style={{ color: "#7c3aed" }}>&#8377;{(gstr9.sgst || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">IGST</div><div className="gst-summary-value" style={{ color: "#d97706" }}>&#8377;{(gstr9.igst || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Total Tax</div><div className="gst-summary-value">&#8377;{(gstr9.totalTax || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">TCS Collected</div><div className="gst-summary-value" style={{ color: "#dc2626" }}>&#8377;{(gstr9.totalTcsCollected || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Reported Turnover</div><div className="gst-summary-value">&#8377;{(gstr9.reportedTurnover || 0).toFixed(2)}</div></div>
                </div>
              </div>
            )}
          </>
        )}

        {activeTab === "gstr9c" && (
          <>
            <div className="gst-toolbar">
              <div className="gst-field" style={{ margin: 0, minWidth: 140 }}>
                <select value={fiscalYear} onChange={(e) => setFiscalYear(parseInt(e.target.value))}>
                  {[new Date().getFullYear(), new Date().getFullYear() - 1, new Date().getFullYear() - 2].map((y) => (
                    <option key={y} value={y}>FY {y}-{String(y + 1).slice(2)}</option>
                  ))}
                </select>
              </div>
              <button className="gst-btn gst-btn-primary gst-btn-sm" onClick={fetchGstr9c}><RefreshCw size={14} /> Load</button>
            </div>
            {loading ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>Loading GSTR-9C...</p>
            ) : !gstr9c ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>No GSTR-9C data available.</p>
            ) : (
              <div className="gst-form" style={{ maxWidth: "100%" }}>
                <h3>GSTR-9C Reconciliation Statement — {gstr9c.gstin}</h3>
                <div className="gst-alert gst-alert-ok"><CheckCircle size={15} /> <span>{gstr9c.gstr9cRequirement}</span></div>
                <div className="gst-summary-grid">
                  <div className="gst-summary-item"><div className="gst-summary-label">Turnover as per Audited Accounts</div><div className="gst-summary-value">&#8377;{(gstr9c.turnoverAsPerAuditedAccounts || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Turnover as per GST Records</div><div className="gst-summary-value">&#8377;{(gstr9c.turnoverAsPerGstRecords || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Difference</div><div className="gst-summary-value">&#8377;{(gstr9c.difference || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Reconciled</div><div className="gst-summary-value" style={{ color: gstr9c.reconciled ? "#16a34a" : "#dc2626" }}>{gstr9c.reconciled ? "Yes" : "No"}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Total Tax</div><div className="gst-summary-value">&#8377;{(gstr9c.totalTax || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">CGST / SGST / IGST</div><div className="gst-summary-value" style={{ fontSize: "0.9rem" }}>&#8377;{(gstr9c.cgst || 0).toFixed(2)} / &#8377;{(gstr9c.sgst || 0).toFixed(2)} / &#8377;{(gstr9c.igst || 0).toFixed(2)}</div></div>
                </div>
              </div>
            )}
          </>
        )}

        {activeTab === "gstr8" && (
          <>
            <div className="gst-toolbar">
              <div className="gst-field" style={{ margin: 0, minWidth: 140 }}>
                <input type="text" value={gstr8Period} onChange={(e) => setGstr8Period(e.target.value)} placeholder="MMyyyy" style={{ fontFamily: "monospace" }} />
              </div>
              <span style={{ fontSize: "0.8rem", color: "#94a3b8" }}>Period {monthOf(gstr8Period)}/{yearOf(gstr8Period)}</span>
              <button className="gst-btn gst-btn-primary gst-btn-sm" onClick={fetchGstr8}><RefreshCw size={14} /> Load</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={() => downloadExport(`/api/gst/reports/gstr8/export?period=${gstr8Period}`, "gstr8.csv", "csv")}><FileSpreadsheet size={14} /> CSV</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={() => downloadExport(`/api/gst/reports/gstr8/export?period=${gstr8Period}`, "gstr8.xlsx", "xlsx")}><FileSpreadsheet size={14} /> XLSX</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={() => downloadExport(`/api/gst/reports/gstr8/export?period=${gstr8Period}`, "gstr8.json", "json")}><FileJson size={14} /> JSON</button>
            </div>
            {loading ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>Loading GSTR-8...</p>
            ) : !gstr8 ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>No GSTR-8 data for this period.</p>
            ) : (
              <div className="gst-form" style={{ maxWidth: "100%" }}>
                <h3>GSTR-8 (TCS Return) — {gstr8.gstin}</h3>
                <div className="gst-summary-grid" style={{ marginBottom: "1.5rem" }}>
                  <div className="gst-summary-item"><div className="gst-summary-label">Period</div><div className="gst-summary-value">{gstr8.period}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Suppliers &amp; Buyers</div><div className="gst-summary-value">{gstr8.totalSuppliersAndBuyers}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Total Taxable Value</div><div className="gst-summary-value">&#8377;{(gstr8.totalTaxableValue || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Total TCS Collected</div><div className="gst-summary-value" style={{ color: "#dc2626" }}>&#8377;{(gstr8.totalTcsCollected || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Records</div><div className="gst-summary-value">{gstr8.recordCount}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">TCS Rate</div><div className="gst-summary-value">{gstr8.tcsRate}</div></div>
                </div>
                {(gstr8.customerWiseTcs || []).length === 0 ? (
                  <p style={{ fontSize: "0.85rem", color: "#94a3b8" }}>No TCS collected from customers this period.</p>
                ) : (
                  <div className="gst-table-wrap">
                    <table className="gst-table">
                      <thead><tr><th>Customer GSTIN</th><th style={{ textAlign: "right" }}>TCS Amount</th></tr></thead>
                      <tbody>
                        {gstr8.customerWiseTcs.map((row, i) => (
                          <tr key={i}>
                            <td style={{ fontFamily: "monospace", fontSize: "0.8rem" }}>{row.customerGstin}</td>
                            <td style={{ textAlign: "right", fontWeight: 600 }}>&#8377;{(row.tcsAmount || 0).toFixed(2)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}
          </>
        )}

        {activeTab === "tcs" && (
          <>
            <div className="gst-toolbar">
              <input className="gst-search" placeholder="Search TCS records..." readOnly />
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={fetchTcs}><RefreshCw size={14} /> Refresh</button>
              <button className="gst-btn gst-btn-primary gst-btn-sm" onClick={() => downloadExport("/api/gst/reports/tcs/export", "tcs-records.csv", "csv")}><FileSpreadsheet size={14} /> CSV</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={() => downloadExport("/api/gst/reports/tcs/export", "tcs-records.xlsx", "xlsx")}><FileSpreadsheet size={14} /> XLSX</button>
            </div>
            {loading ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>Loading TCS records...</p>
            ) : tcsRecords.length === 0 ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>No TCS records yet. TCS (1%) is auto-created on every marketplace invoice.</p>
            ) : (
              <div className="gst-table-wrap">
                <table className="gst-table">
                  <thead>
                    <tr><th>Invoice #</th><th>Order</th><th>Marketplace</th><th>Date</th><th>Customer GSTIN</th><th>Taxable</th><th>Rate</th><th>TCS</th><th>Period</th><th>Filing Status</th></tr>
                  </thead>
                  <tbody>
                    {tcsRecords.map((t) => (
                      <tr key={t.id}>
                        <td style={{ fontFamily: "monospace", fontSize: "0.78rem" }}>{t.invoiceNumber}</td>
                        <td>#{t.orderId}</td>
                        <td>{t.marketplace}</td>
                        <td>{t.transactionDate}</td>
                        <td style={{ fontFamily: "monospace", fontSize: "0.78rem" }}>{t.customerGstin || "URP"}</td>
                        <td>&#8377;{(t.taxableAmount || 0).toFixed(2)}</td>
                        <td>{t.tcsRate}%</td>
                        <td style={{ fontWeight: 600, color: "#dc2626" }}>&#8377;{(t.tcsAmount || 0).toFixed(2)}</td>
                        <td>{t.period}</td>
                        <td>
                          <span className={`gst-status ${t.filingStatus === "FILED" ? "gst-status-synced" : "gst-status-generated"}`}>
                            {t.filingStatus === "FILED" ? <CheckCircle size={12} /> : <Clock size={12} />}{t.filingStatus}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {tcsTotalPages > 1 && (
              <div style={{ display: "flex", justifyContent: "center", gap: "0.5rem", marginTop: "1rem" }}>
                <button className="gst-btn gst-btn-outline gst-btn-sm" disabled={tcsPage <= 0} onClick={() => setTcsPage(tcsPage - 1)}>Previous</button>
                <span style={{ padding: "0.35rem 0.7rem", fontSize: "0.85rem", color: "#64748b" }}>Page {tcsPage + 1} of {tcsTotalPages}</span>
                <button className="gst-btn gst-btn-outline gst-btn-sm" disabled={tcsPage >= tcsTotalPages - 1} onClick={() => setTcsPage(tcsPage + 1)}>Next</button>
              </div>
            )}
          </>
        )}

        {activeTab === "reconciliation" && (
          <>
            <div className="gst-toolbar">
              <div className="gst-field" style={{ margin: 0, minWidth: 140 }}>
                <input type="text" value={reconPeriod} onChange={(e) => setReconPeriod(e.target.value)} placeholder="MMyyyy" style={{ fontFamily: "monospace" }} />
              </div>
              <button className="gst-btn gst-btn-primary gst-btn-sm" onClick={handleGenerateRecon}><RefreshCw size={14} /> Generate 2B Recon</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={fetchRecons}><SyncIcon size={14} /> Refresh</button>
            </div>

            {reconDetail && (
              <div className="gst-form" style={{ maxWidth: "100%", marginBottom: "1.5rem" }}>
                <h3>Reconciliation Detail — {reconDetail.reconciliation?.period}</h3>
                {reconDetail.gstr2b && (
                  <div className="gst-alert gst-alert-info"><AlertTriangle size={15} /> <span>GSTR-2B data is simulated ({reconDetail.gstr2b.totalDocuments || 0} documents, &#8377;{(reconDetail.gstr2b.totalItcAvailable || 0).toFixed(2)} ITC available). Configure live GSTN credentials for real data.</span></div>
                )}
                <div className="gst-summary-grid">
                  <div className="gst-summary-item"><div className="gst-summary-label">ITC Available (GSTR-2B)</div><div className="gst-summary-value">&#8377;{(reconDetail.reconciliation?.totalItcAvailable || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">ITC Claimed (Invoices)</div><div className="gst-summary-value">&#8377;{(reconDetail.reconciliation?.itcClaimed || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">ITC Reversed</div><div className="gst-summary-value">&#8377;{(reconDetail.reconciliation?.itcReversed || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">ITC Not Available</div><div className="gst-summary-value">&#8377;{(reconDetail.reconciliation?.itcNotAvailable || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Difference</div><div className="gst-summary-value" style={{ color: (reconDetail.reconciliation?.itcDifference || 0) !== 0 ? "#dc2626" : "#16a34a" }}>&#8377;{(reconDetail.reconciliation?.itcDifference || 0).toFixed(2)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Status</div><div className="gst-summary-value">
                    <span className={`gst-status ${reconDetail.reconciliation?.status === "COMPLETE" || reconDetail.reconciliation?.status === "MATCHED" ? "gst-status-synced" : "gst-status-generated"}`}>{reconDetail.reconciliation?.status}</span>
                  </div></div>
                </div>
                {reconDetail.reconciliation?.blockedCredits && (
                  <div style={{ marginTop: "1rem" }}>
                    <h3 style={{ fontSize: "0.95rem" }}>Blocked Credit Sections (Sec 17(5))</h3>
                    <ul style={{ fontSize: "0.85rem", color: "#475569", margin: "0.5rem 0 0", paddingLeft: "1.25rem" }}>
                      {(reconDetail.reconciliation.blockedCredits || "").split(";").map((b, i) => <li key={i}>{b.trim()}</li>)}
                    </ul>
                  </div>
                )}
                {reconDetail.reconciliation?.remarks && <p style={{ fontSize: "0.82rem", color: "#94a3b8", marginTop: "1rem" }}>{reconDetail.reconciliation.remarks}</p>}
                <div style={{ display: "flex", gap: "0.5rem", marginTop: "1rem" }}>
                  <button className="gst-btn gst-btn-primary gst-btn-sm" onClick={() => handleUpdateReconStatus(reconDetail.reconciliation.id, "COMPLETE")}><CheckCircle size={14} /> Mark Complete</button>
                  <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={() => handleUpdateReconStatus(reconDetail.reconciliation.id, "OPEN")}><Clock size={14} /> Reopen</button>
                </div>
              </div>
            )}

            {loading ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>Loading reconciliations...</p>
            ) : recons.length === 0 ? (
              <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>No reconciliations generated yet. Enter a period (MMyyyy) and click Generate 2B Recon.</p>
            ) : (
              <div className="gst-table-wrap">
                <table className="gst-table">
                  <thead><tr><th>Period</th><th>Generated</th><th>ITC Available</th><th>ITC Claimed</th><th>Difference</th><th>Status</th><th>Actions</th></tr></thead>
                  <tbody>
                    {recons.map((r) => (
                      <tr key={r.id}>
                        <td style={{ fontFamily: "monospace", fontWeight: 600 }}>{r.period}</td>
                        <td>{r.generatedDate}</td>
                        <td>&#8377;{(r.totalItcAvailable || 0).toFixed(2)}</td>
                        <td>&#8377;{(r.itcClaimed || 0).toFixed(2)}</td>
                        <td style={{ color: (r.itcDifference || 0) !== 0 ? "#dc2626" : "#16a34a", fontWeight: 600 }}>&#8377;{(r.itcDifference || 0).toFixed(2)}</td>
                        <td>
                          <span className={`gst-status ${r.status === "COMPLETE" || r.status === "MATCHED" ? "gst-status-synced" : "gst-status-generated"}`}>
                            {r.status === "COMPLETE" || r.status === "MATCHED" ? <CheckCircle size={12} /> : <Clock size={12} />}{r.status}
                          </span>
                        </td>
                        <td>
                          <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={() => handleViewRecon(r.period)}><Eye size={12} /> View</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}

        {activeTab === "marketplace" && (
          <>
            <div className="gst-toolbar">
              <div className="gst-field" style={{ margin: 0, minWidth: 180 }}>
                <select value={channel} onChange={(e) => setChannel(e.target.value)}>
                  {CHANNELS.map((c) => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <button className="gst-btn gst-btn-primary gst-btn-sm" onClick={handleImportOrders} disabled={mpLoading}><SyncIcon size={14} /> Import Orders</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={handleSyncInventory} disabled={mpLoading}><RefreshCw size={14} /> Sync Inventory</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={handleFetchProducts} disabled={mpLoading}><FileText size={14} /> Fetch Products</button>
              <button className="gst-btn gst-btn-outline gst-btn-sm" onClick={handleFetchSettlements} disabled={mpLoading}><Download size={14} /> Settlements</button>
            </div>
            <p style={{ fontSize: "0.82rem", color: "#64748b", marginBottom: "1.5rem" }}>
              Marketplace adapters are simulated. Supported channels: {CHANNELS.join(", ")}. Configure real credentials for live order/inventory sync.
            </p>

            {mpOrders && (
              <div className="gst-form" style={{ maxWidth: "100%", marginBottom: "1rem" }}>
                <h3>Imported Orders ({mpOrders.channel})</h3>
                <p style={{ fontSize: "0.82rem", color: "#94a3b8", margin: "0 0 0.75rem" }}>{mpOrders.message} — {mpOrders.ordersImported} order(s)</p>
                {(mpOrders.orders || []).length > 0 && (
                  <div className="gst-table-wrap">
                    <table className="gst-table">
                      <thead><tr><th>Order ID</th><th>Date</th><th>Amount</th><th>SKU</th></tr></thead>
                      <tbody>
                        {mpOrders.orders.map((o, i) => (
                          <tr key={i}>
                            <td style={{ fontFamily: "monospace" }}>{o.orderId}</td>
                            <td>{o.orderDate}</td>
                            <td>&#8377;{(o.amount || 0).toFixed(2)}</td>
                            <td style={{ fontSize: "0.78rem" }}>{o.sku || "-"}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}

            {mpInventory && (
              <div className="gst-alert gst-alert-ok"><CheckCircle size={15} /> <span>{mpInventory.channel}: {mpInventory.productsSynced} product(s) inventory pushed ({mpInventory.adapter}).</span></div>
            )}

            {mpProducts && (
              <div className="gst-form" style={{ maxWidth: "100%", marginBottom: "1rem" }}>
                <h3>Marketplace Products ({mpProducts.channel})</h3>
                {(mpProducts.products || []).length === 0 ? (
                  <p style={{ fontSize: "0.85rem", color: "#94a3b8" }}>No products fetched from {mpProducts.channel}.</p>
                ) : (
                  <div className="gst-table-wrap">
                    <table className="gst-table">
                      <thead><tr><th>SKU</th><th>Name</th><th>Price</th><th>Stock</th></tr></thead>
                      <tbody>
                        {mpProducts.products.map((p, i) => (
                          <tr key={i}>
                            <td style={{ fontFamily: "monospace", fontSize: "0.8rem" }}>{p.sku}</td>
                            <td>{p.name}</td>
                            <td>&#8377;{(p.price || 0).toFixed(2)}</td>
                            <td>{p.stock}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}

            {mpSettlements && (
              <div className="gst-form" style={{ maxWidth: "100%" }}>
                <h3>Settlements ({mpSettlements.channel || channel})</h3>
                <div className="gst-summary-grid">
                  <div className="gst-summary-item"><div className="gst-summary-label">Total Settlements</div><div className="gst-summary-value">{(mpSettlements.totalSettlements || 0)}</div></div>
                  <div className="gst-summary-item"><div className="gst-summary-label">Total Amount</div><div className="gst-summary-value">&#8377;{(mpSettlements.totalAmount || 0).toFixed(2)}</div></div>
                </div>
              </div>
            )}
          </>
        )}

        {activeTab === "apob" && (
          <>
            <div className="gst-toolbar">
              <div className="gst-field" style={{ margin: 0, minWidth: 140 }}>
                <input type="number" value={apobSellerId} onChange={(e) => setApobSellerId(e.target.value)} placeholder="Seller user ID" />
              </div>
              <button className="gst-btn gst-btn-primary gst-btn-sm" onClick={handleLoadApob}><RefreshCw size={14} /> Load APOB</button>
            </div>
            <p style={{ fontSize: "0.82rem", color: "#64748b", marginBottom: "1.5rem" }}>
              Additional Places of Business (APOB) for the GST registration — linked to the seller's GSTIN and used for multi-location reporting.
            </p>
            <div className="gst-form" style={{ maxWidth: "100%" }}>
              {apobGstin && <p style={{ fontSize: "0.88rem", marginBottom: "1rem" }}><strong>GSTIN:</strong> <span style={{ fontFamily: "monospace" }}>{apobGstin}</span></p>}
              <div className="gst-field">
                <label>APOB Addresses <span style={{ color: "#94a3b8", fontWeight: 400 }}>(one per line)</span></label>
                <textarea
                  rows={5}
                  value={apobText}
                  onChange={(e) => setApobText(e.target.value)}
                  placeholder={"Primary + branch addresses\n... e.g. 22, Noyyal Street, Coimbatore, Tamil Nadu - 641001"}
                  style={{ width: "100%", padding: "0.55rem 0.75rem", border: "1.5px solid #e2e8f0", borderRadius: "8px", fontSize: "0.88rem", outline: "none", boxSizing: "border-box", fontFamily: "inherit" }}
                />
              </div>
              <button className="gst-btn gst-btn-primary" onClick={handleSaveApob}><CheckCircle size={16} /> Save APOB</button>
            </div>
          </>
        )}
      </div>
    </>
  );
};

export default GstCompliance;
