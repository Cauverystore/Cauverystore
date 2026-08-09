import React, { useState, useEffect, useCallback } from "react";
import { Store, ChevronLeft, Check, X, FileText, ShieldCheck, Ban, RotateCcw } from "lucide-react";
import api from "../../utils/axios";
import { useToast } from "../../admin/context/ToastContext";

const StatusBadge = ({ value }) => (
  <span className={`admin-badge ${value === "VERIFIED" || value === "APPROVED" || value === "ACTIVE" ? "active" : value === "FAILED" || value === "REJECTED" || value === "SUSPENDED" ? "danger" : "inactive"}`}>
    {value || "—"}
  </span>
);

const TABS = [
  { key: "SUBMITTED", label: "Pending" },
  { key: "APPROVED", label: "Approved" },
  { key: "REJECTED", label: "Rejected" },
  { key: "SUSPENDED", label: "Suspended" },
  { key: "ALL", label: "All" },
];

const SellerApproval = () => {
  const [registrations, setRegistrations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actioning, setActioning] = useState(null);
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [showReject, setShowReject] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [docActioning, setDocActioning] = useState(null);
  // The list only ever showed pending applications, so there was no way to look up who had been
  // approved, who was turned down and why, or who is currently suspended.
  const [statusTab, setStatusTab] = useState("SUBMITTED");
  const { showToast } = useToast();

  const role = (localStorage.getItem("role") || "").toUpperCase();
  // Overturning a colleague's decision is a super-admin power, so those buttons stay hidden for
  // an admin rather than being shown and then refused by the API.
  const isSuperAdmin = role === "SUPER_ADMIN";
  // Suspension is open to both. Stopping a seller who is doing harm is day-to-day work, and
  // waiting for a super admin would leave the listings up in the meantime.
  const canSuspend = isSuperAdmin || role === "ADMIN";

  const fetchRegistrations = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await api.get("/api/seller-registration/admin/registrations", {
        params: { status: statusTab },
      });
      const content = res.data?.content;
      setRegistrations(Array.isArray(content) ? content : []);
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Failed to load seller registrations');
      setRegistrations([]);
    } finally {
      setLoading(false);
    }
  }, [statusTab]);

  useEffect(() => { fetchRegistrations(); }, [fetchRegistrations]);

  const openDetail = async (registrationId) => {
    setDetailLoading(true);
    setError('');
    try {
      const res = await api.get(`/api/seller-registration/admin/registrations/${registrationId}`);
      setDetail(res.data);
    } catch (err) {
      showToast(err.response?.data?.error || err.response?.data?.message || 'Failed to load registration details', "error");
    } finally {
      setDetailLoading(false);
    }
  };

  const closeDetail = () => {
    setDetail(null);
    setShowReject(false);
    setRejectReason('');
    fetchRegistrations();
  };

  const handleApprove = async (registrationId) => {
    setActioning(registrationId);
    try {
      await api.post(`/api/seller-registration/admin/registrations/${registrationId}/approve`);
      showToast("Seller approved and activated", "success");
      closeDetail();
    } catch (err) {
      showToast(err.response?.data?.error || err.response?.data?.message || 'Failed to approve seller', "error");
    } finally {
      setActioning(null);
    }
  };

  const handleReject = async (registrationId) => {
    if (!rejectReason.trim()) {
      showToast("Please provide a rejection reason", "error");
      return;
    }
    setActioning(registrationId);
    try {
      await api.post(`/api/seller-registration/admin/registrations/${registrationId}/reject`, { reason: rejectReason.trim() });
      showToast("Registration rejected", "success");
      closeDetail();
    } catch (err) {
      showToast(err.response?.data?.error || err.response?.data?.message || 'Failed to reject registration', "error");
    } finally {
      setActioning(null);
    }
  };

  const handleSuspend = async (registrationId) => {
    // Suspension is a wind-down, not a lockout: the listings come down so nothing new arrives,
    // but orders already placed still have to be fulfilled, so the seller keeps access for that.
    const reason = window.prompt(
      "Suspend this seller?\n\n"
      + "Their listings come off sale at once, so no new orders can arrive. Orders already "
      + "placed still have to be fulfilled, and they keep access to do that.\n\n"
      + "Reason (sent to them):"
    );
    if (reason === null) return;
    if (!reason.trim()) { showToast("A suspension needs a reason", "error"); return; }
    setActioning(registrationId);
    try {
      const res = await api.post(
        `/api/seller-registration/admin/registrations/${registrationId}/suspend`,
        { reason: reason.trim() }
      );
      showToast(res.data?.message || "Seller suspended", "success");
      closeDetail();
    } catch (err) {
      showToast(err.response?.data?.error || 'Failed to suspend seller', "error");
    } finally {
      setActioning(null);
    }
  };

  const handleReinstate = async (registrationId) => {
    if (!window.confirm("Lift this suspension? The listings it took down go back on sale.")) return;
    setActioning(registrationId);
    try {
      const res = await api.post(
        `/api/seller-registration/admin/registrations/${registrationId}/reinstate`
      );
      const n = res.data?.listingsRestored;
      showToast(
        `Seller reinstated${typeof n === "number" ? ` — ${n} listing(s) restored` : ""}`,
        "success"
      );
      closeDetail();
    } catch (err) {
      showToast(err.response?.data?.error || 'Failed to reinstate seller', "error");
    } finally {
      setActioning(null);
    }
  };

  const handleVerifyDoc = async (documentId, approved) => {
    setDocActioning(documentId);
    try {
      await api.post(`/api/seller-registration/admin/verify-document/${documentId}`, { approved, rejectionReason: approved ? null : "Rejected by admin" });
      showToast(approved ? "Document verified" : "Document rejected", "success");
      if (detail?.registration?.id) await openDetail(detail.registration.id);
    } catch (err) {
      showToast(err.response?.data?.error || err.response?.data?.message || 'Failed to update document', "error");
    } finally {
      setDocActioning(null);
    }
  };

  if (detail) {
    const reg = detail.registration || {};
    const readiness = detail.readiness || {};
    const documents = Array.isArray(detail.documents) ? detail.documents : [];
    const compliance = Array.isArray(detail.compliance) ? detail.compliance : [];
    const regId = reg.id || detail.registrationId;
    return (
      <div>
        <button onClick={closeDetail} className="admin-btn admin-btn-sm admin-btn-outline" style={{ marginBottom: '1rem' }}>
          <ChevronLeft size={14} style={{ verticalAlign: 'middle' }} /> Back to queue
        </button>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '0.75rem' }}>
          <div>
            <h1 style={{ fontSize: '1.4rem', fontWeight: 700 }}>{reg.businessName || 'Seller Registration'}</h1>
            <div style={{ color: '#6b7280', fontSize: '0.85rem' }}>
              Submitted by {reg.businessEmail || detail.sellerEmail} · {reg.submittedAt ? new Date(reg.submittedAt).toLocaleString('en-IN') : ''}
            </div>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            {/* Withdrawing an approval is a super-admin override, so the button stays live for
                them on an approved seller and is closed to an admin, matching the API. */}
            <button className="admin-btn admin-btn-sm admin-btn-danger" onClick={() => setShowReject(true)} disabled={actioning === regId || reg.status === 'REJECTED' || (reg.status === 'APPROVED' && !isSuperAdmin)}>
              <X size={14} style={{ verticalAlign: 'middle' }} /> {reg.status === 'APPROVED' ? 'Withdraw Approval' : 'Reject'}
            </button>
            <button className="admin-btn admin-btn-sm admin-btn-success" onClick={() => handleApprove(regId)} disabled={actioning === regId || reg.status === 'APPROVED' || (reg.status === 'REJECTED' && !isSuperAdmin)}>
              {actioning === regId ? 'Approving...' : <><Check size={14} style={{ verticalAlign: 'middle' }} /> {reg.status === 'REJECTED' ? 'Overturn & Approve' : 'Approve & Activate'}</>}
            </button>
            {canSuspend && reg.status === 'APPROVED' && (
              <button className="admin-btn admin-btn-sm admin-btn-outline" onClick={() => handleSuspend(regId)} disabled={actioning === regId} style={{ color: '#dc2626', borderColor: '#fecaca' }}>
                <Ban size={14} style={{ verticalAlign: 'middle' }} /> Suspend
              </button>
            )}
            {canSuspend && reg.status === 'SUSPENDED' && (
              <button className="admin-btn admin-btn-sm admin-btn-outline" onClick={() => handleReinstate(regId)} disabled={actioning === regId} style={{ color: '#146C43', borderColor: '#CFE8D6' }}>
                <RotateCcw size={14} style={{ verticalAlign: 'middle' }} /> Reinstate
              </button>
            )}
          </div>
        </div>

        {showReject && (
          <div className="admin-alert" style={{ background: '#fff7ed', border: '1px solid #fdba74', borderRadius: '10px', padding: '1rem', marginBottom: '1.25rem' }}>
            <label style={{ display: 'block', fontWeight: 600, fontSize: '0.85rem', marginBottom: '0.4rem' }}>Rejection reason (sent to the seller)</label>
            <textarea value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} rows={2} placeholder="Explain what needs to be corrected before resubmission..." style={{ width: '100%', border: '1px solid #d1d5db', borderRadius: '8px', padding: '0.5rem 0.75rem', fontSize: '0.85rem' }} />
            <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.6rem' }}>
              <button className="admin-btn admin-btn-sm admin-btn-danger" onClick={() => handleReject(regId)} disabled={actioning === regId}>
                {actioning === regId ? 'Rejecting...' : 'Confirm Reject'}
              </button>
              <button className="admin-btn admin-btn-sm admin-btn-outline" onClick={() => { setShowReject(false); setRejectReason(''); }}>Cancel</button>
            </div>
          </div>
        )}

        <div className="admin-stat-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: '0.75rem', marginBottom: '1.25rem' }}>
          {[{ label: 'GSTIN', value: reg.gstin || '—', ok: readiness.gstinVerified },
            { label: 'Bank', value: reg.bankStatus || '—', ok: readiness.bankVerified },
            { label: 'Compliance', value: `${compliance.filter((c) => c.isCompleted).length}/${compliance.length}`, ok: readiness.complianceComplete }].map((s) => (
            <div key={s.label} className="admin-stat-card" style={{ background: '#fff', border: '1px solid #e5e7eb', borderRadius: '10px', padding: '0.9rem' }}>
              <div style={{ fontSize: '0.75rem', color: '#6b7280', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.03em' }}>{s.label}</div>
              <div style={{ fontSize: '1rem', fontWeight: 700, marginTop: '0.25rem', color: s.ok ? '#16a34a' : '#f59e0b' }}>{s.value}</div>
            </div>
          ))}
        </div>

        <div className="admin-table-wrapper" style={{ marginBottom: '1.25rem' }}>
          <table className="admin-table">
            <tbody>
              {[['Business Type', reg.businessType], ['GSTIN', reg.gstin], ['GSTIN Legal Name', reg.gstinLegalName], ['PAN', reg.panNumber], ['Account Holder', reg.bankAccountName], ['Bank / IFSC', reg.bankIfsc ? `${reg.bankName} / ${reg.bankIfsc}` : '—'], ['Address', reg.businessAddress ? `${reg.businessAddress}, ${reg.city || ''} ${reg.state || ''} ${reg.pincode || ''}`.replace(/,\s*,/g, ',') : '—']].map(([k, v]) => (
                <tr key={k}>
                  <td style={{ width: '180px', color: '#6b7280', fontSize: '0.85rem' }}>{k}</td>
                  <td style={{ fontWeight: 500 }}>{v || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <h2 style={{ fontSize: '1.05rem', fontWeight: 700, marginBottom: '0.75rem' }}>Documents ({documents.length})</h2>
        <div className="admin-table-wrapper" style={{ marginBottom: '1.25rem' }}>
          {documents.length === 0 ? (
            <div className="admin-empty-state" style={{ padding: '1.5rem' }}><div className="admin-empty-state-icon"><FileText size={24} /></div><div className="admin-empty-state-text">No documents uploaded</div></div>
          ) : (
            <table className="admin-table">
              <thead>
                <tr><th>Document</th><th>Status</th><th>Actions</th></tr>
              </thead>
              <tbody>
                {documents.map((d) => (
                  <tr key={d.id}>
                    <td>
                      <div style={{ fontWeight: 500 }}>{d.documentName || d.documentType}</div>
                      {d.fileUrl && <a href={d.fileUrl} target="_blank" rel="noreferrer" style={{ fontSize: '0.75rem', color: '#2563eb' }}>View file</a>}
                    </td>
                    <td><StatusBadge value={d.status} /></td>
                    <td>
                      <div className="admin-table-actions-cell">
                        <button className="admin-btn admin-btn-sm admin-btn-success" onClick={() => handleVerifyDoc(d.id, true)} disabled={docActioning === d.id || d.status === 'VERIFIED'}>
                          {docActioning === d.id ? '...' : 'Verify'}
                        </button>
                        <button className="admin-btn admin-btn-sm admin-btn-danger" onClick={() => handleVerifyDoc(d.id, false)} disabled={docActioning === d.id || d.status === 'REJECTED'}>
                          Reject
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <h2 style={{ fontSize: '1.05rem', fontWeight: 700, marginBottom: '0.75rem' }}>Compliance Checklist</h2>
        <div className="admin-table-wrapper">
          {compliance.length === 0 ? (
            <div className="admin-empty-state" style={{ padding: '1.5rem' }}><div className="admin-empty-state-text">No compliance items</div></div>
          ) : (
            <table className="admin-table">
              <thead><tr><th>Requirement</th><th>Mandatory</th><th>Status</th></tr></thead>
              <tbody>
                {compliance.map((c) => (
                  <tr key={c.id || c.requirementType}>
                    <td><div style={{ fontWeight: 500 }}>{c.requirementName}</div><div style={{ fontSize: '0.8rem', color: '#6b7280' }}>{c.description}</div></td>
                    <td>{c.isMandatory ? <span className="admin-badge inactive">MANDATORY</span> : <span className="admin-badge">Optional</span>}</td>
                    <td><StatusBadge value={c.isCompleted ? 'APPROVED' : 'PENDING'} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '1.5rem' }}>Seller Approval Queue</h1>
        <div className="admin-skeleton-row">
          {[1, 2, 3, 4].map(i => <div key={i} className="admin-skeleton-card" style={{ height: '48px' }} />)}
        </div>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700 }}>Sellers</h1>
        <span className="admin-badge inactive">
          {registrations.length} {(TABS.find(t => t.key === statusTab)?.label || '').toLowerCase()}
        </span>
      </div>

      <div style={{ display: 'flex', gap: '4px', marginBottom: '1rem', borderBottom: '2px solid #EAF7EE', overflowX: 'auto' }}>
        {TABS.map(t => (
          <button
            key={t.key}
            onClick={() => setStatusTab(t.key)}
            style={{
              padding: '8px 16px', border: 'none', cursor: 'pointer', fontWeight: 600,
              fontSize: '0.85rem', whiteSpace: 'nowrap',
              background: statusTab === t.key ? '#EAF7EE' : 'transparent',
              color: statusTab === t.key ? '#146C43' : '#64748B',
              borderBottom: statusTab === t.key ? '2px solid #2E9B57' : '2px solid transparent',
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      {error && <div className="admin-alert error">{error}</div>}

      <div className="admin-table-wrapper">
        {registrations.length === 0 ? (
          <div className="admin-empty-state">
            <div className="admin-empty-state-icon"><Store size={32} /></div>
            <div className="admin-empty-state-text">
              No {(TABS.find(t => t.key === statusTab)?.label || '').toLowerCase()} sellers
            </div>
          </div>
        ) : (
          <table className="admin-table">
            <thead>
              <tr>
                <th>Business</th>
                <th>Contact</th>
                <th>GSTIN</th>
                <th>Bank</th>
                <th>Documents</th>
                <th>Compliance</th>
                <th>Status</th>
                <th>Submitted</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {registrations.map(r => (
                <tr key={r.registrationId}>
                  <td>
                    <div style={{ fontWeight: 500 }}>{r.businessName}</div>
                    <div style={{ fontSize: '0.78rem', color: '#6b7280' }}>{r.businessType}</div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 500 }}>{r.sellerName}</div>
                    <div style={{ fontSize: '0.78rem', color: '#6b7280' }}>{r.sellerEmail}</div>
                  </td>
                  <td>
                    <div>{r.gstin}</div>
                    <StatusBadge value={r.gstinStatus} />
                  </td>
                  <td><StatusBadge value={r.bankStatus} /></td>
                  <td style={{ fontSize: '0.85rem' }}>{r.documentsVerified}/{r.documentsTotal}</td>
                  <td style={{ fontSize: '0.85rem' }}>{r.complianceCompleted}/{r.complianceTotal}</td>
                  <td>
                    <StatusBadge value={r.status} />
                    {/* Why somebody was turned down or stopped is the first thing anybody
                        opening this list wants, and it was nowhere on screen. */}
                    {(r.rejectionReason || r.suspensionReason) && (
                      <div style={{ fontSize: '0.72rem', color: '#6b7280', marginTop: '2px', maxWidth: '180px' }}>
                        {r.suspensionReason || r.rejectionReason}
                      </div>
                    )}
                  </td>
                  <td style={{ fontSize: '0.8rem', color: '#6b7280' }}>{r.submittedAt ? new Date(r.submittedAt).toLocaleDateString('en-IN') : '—'}</td>
                  <td>
                    <div className="admin-table-actions-cell">
                      <button className="admin-btn admin-btn-sm admin-btn-outline" onClick={() => openDetail(r.registrationId)}>
                        <ShieldCheck size={14} style={{ verticalAlign: 'middle' }} /> Review
                      </button>
                      {canSuspend && r.status === 'APPROVED' && (
                        <button
                          className="admin-btn admin-btn-sm admin-btn-outline"
                          disabled={actioning === r.registrationId}
                          onClick={() => handleSuspend(r.registrationId)}
                          style={{ color: '#dc2626', borderColor: '#fecaca' }}
                        >
                          <Ban size={14} style={{ verticalAlign: 'middle' }} /> Suspend
                        </button>
                      )}
                      {canSuspend && r.status === 'SUSPENDED' && (
                        <button
                          className="admin-btn admin-btn-sm admin-btn-outline"
                          disabled={actioning === r.registrationId}
                          onClick={() => handleReinstate(r.registrationId)}
                          style={{ color: '#146C43', borderColor: '#CFE8D6' }}
                        >
                          <RotateCcw size={14} style={{ verticalAlign: 'middle' }} /> Reinstate
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        <div className="admin-pagination">
          <div className="admin-pagination-info">{registrations.length} registration(s)</div>
        </div>
      </div>
    </div>
  );
};

export default SellerApproval;
