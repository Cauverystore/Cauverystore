import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Lock, ClipboardList } from 'lucide-react';
import api from '../../api/axios';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../admin/context/ToastContext';

const ImpersonationPage = () => {
  const { startImpersonation, stopImpersonation, isImpersonating, impersonatedUser, impersonationSession } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  const [activeSessions, setActiveSessions] = useState([]);
  const [impersonationLog, setImpersonationLog] = useState([]);
  const [loading, setLoading] = useState(true);
  const [logLoading, setLogLoading] = useState(true);
  const [error, setError] = useState('');

  const [targetEmail, setTargetEmail] = useState('');
  const [targetUser, setTargetUser] = useState(null);
  const [reason, setReason] = useState('');
  const [searching, setSearching] = useState(false);
  const [startLoading, setStartLoading] = useState(false);
  const [searchResults, setSearchResults] = useState([]);
  const [showConfirm, setShowConfirm] = useState(false);

  const fetchActiveSessions = async () => {
    try {
      const res = await api.get('/api/super-admin/impersonate/sessions');
      setActiveSessions(Array.isArray(res.data) ? res.data : res.data.sessions || []);
    } catch {
      setActiveSessions([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchLog = async () => {
    setLogLoading(true);
    try {
      const res = await api.get('/api/super-admin/impersonate/log');
      setImpersonationLog(Array.isArray(res.data) ? res.data : res.data.log || res.data.sessions || []);
    } catch {
      setImpersonationLog([]);
    } finally {
      setLogLoading(false);
    }
  };

  useEffect(() => {
    fetchActiveSessions();
    fetchLog();
  }, []);

  const handleSearch = async (query) => {
    setTargetEmail(query);
    if (query.length < 2) { setSearchResults([]); return; }
    setSearching(true);
    try {
      const res = await api.get('/api/super-admin/users', { params: { search: query } });
      setSearchResults(Array.isArray(res.data) ? res.data : res.data.users || []);
    } catch {
      setSearchResults([]);
    } finally {
      setSearching(false);
    }
  };

  const selectUser = (user) => {
    setTargetUser(user);
    setTargetEmail(user.email);
    setSearchResults([]);
  };

  const handleStartImpersonation = async () => {
    if (!targetUser || !reason.trim()) { setError('Please select a user and provide a reason'); return; }
    setStartLoading(true);
    setError('');
    try {
      await startImpersonation(targetUser._id || targetUser.id, reason);
      setShowConfirm(false);
      setTargetEmail('');
      setTargetUser(null);
      setReason('');
      fetchActiveSessions();
      fetchLog();
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to start impersonation';
      setError(msg);
      showToast(msg, 'error');
    } finally {
      setStartLoading(false);
    }
  };

  const handleStopImpersonation = async (sessionId) => {
    try {
      await stopImpersonation(sessionId);
      fetchActiveSessions();
      fetchLog();
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to stop impersonation', 'error');
    }
  };

  return (
    <div>
      <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '1.5rem' }}>Impersonation</h1>

      {error && <div style={{ background: '#fef2f2', color: '#dc2626', padding: '8px 16px', borderRadius: '8px', marginBottom: '16px', fontSize: '0.85rem' }}>{error}</div>}

      {isImpersonating && impersonatedUser && (
        <div style={{ background: '#fef3c7', border: '1px solid #f59e0b', borderRadius: '8px', padding: '12px 16px', marginBottom: '24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '8px' }}>
          <span style={{ fontSize: '0.9rem', color: '#92400e' }}>
            <strong><Lock size={14} style={{ display: 'inline' }} /> Currently impersonating:</strong> {impersonatedUser?.fullName || impersonatedUser?.name || impersonatedUser?.email} ({impersonatedUser?.role})
            {impersonationSession?.reason && <span> — Reason: <em>{impersonationSession.reason}</em></span>}
          </span>
          <button
            className="admin-btn admin-btn-danger admin-btn-sm"
            onClick={() => handleStopImpersonation(impersonationSession?._id || impersonationSession?.id)}
          >
            Exit Impersonation
          </button>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px', marginBottom: '24px' }}>
        <div className="admin-chart-card">
          <div className="admin-chart-header">
            <div className="admin-chart-title">Start Impersonation</div>
          </div>
          <div style={{ position: 'relative' }}>
            <div className="admin-form-group">
              <label className="admin-form-label">Search User by Email/Name</label>
              <input
                className="admin-form-input"
                value={targetEmail}
                onChange={(e) => handleSearch(e.target.value)}
                placeholder="Type to search users..."
              />
              {searching && <span style={{ fontSize: '0.8rem', color: '#6b7280' }}>Searching...</span>}
              {searchResults.length > 0 && (
                <div style={{ position: 'absolute', top: '100%', left: 0, right: 0, background: '#fff', border: '1px solid #e2e8f0', borderRadius: '8px', maxHeight: '200px', overflowY: 'auto', zIndex: 10, boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}>
                  {searchResults.map(u => (
                    <div key={u._id || u.id} onClick={() => selectUser(u)} style={{ padding: '8px 12px', cursor: 'pointer', borderBottom: '1px solid #f1f5f9', fontSize: '0.85rem' }}
                      onMouseEnter={e => e.target.style.background = '#f8fafc'}
                      onMouseLeave={e => e.target.style.background = '#fff'}
                    >
                      <span style={{ fontWeight: 500 }}>{u.fullName || u.name}</span>
                      <span style={{ color: '#6b7280', marginLeft: '8px' }}>{u.email}</span>
                      <span className="admin-badge" style={{ marginLeft: '8px', fontSize: '0.7rem' }}>{u.role}</span>
                    </div>
                  ))}
                </div>
              )}
              {targetUser && (
                <div style={{ marginTop: '8px', padding: '8px 12px', background: '#f0fdf4', borderRadius: '6px', fontSize: '0.85rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span>Selected: <strong>{targetUser.fullName || targetUser.name}</strong> ({targetUser.email})</span>
                  <button style={{ background: 'none', border: 'none', color: '#dc2626', cursor: 'pointer' }} onClick={() => { setTargetUser(null); setTargetEmail(''); }}>✕</button>
                </div>
              )}
            </div>
            <div className="admin-form-group">
              <label className="admin-form-label">Reason for Impersonation</label>
              <textarea
                className="admin-form-textarea"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Explain why you need to impersonate this user..."
                rows={3}
              />
            </div>
            <button
              className="admin-btn admin-btn-primary"
              disabled={!targetUser || !reason.trim() || startLoading}
              onClick={() => setShowConfirm(true)}
            >
              {startLoading ? 'Starting...' : 'Start Impersonation'}
            </button>
          </div>
        </div>

        <div className="admin-chart-card">
          <div className="admin-chart-header">
            <div className="admin-chart-title">Active Impersonations</div>
          </div>
          {loading ? (
            <div className="admin-skeleton-table-row" />
          ) : activeSessions.length === 0 ? (
            <div className="admin-empty-state">
              <div className="admin-empty-state-icon"><Lock size={32} /></div>
              <div className="admin-empty-state-text">No active impersonations</div>
            </div>
          ) : (
            <div>
              {activeSessions.map(s => (
                <div key={s._id || s.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', borderBottom: '1px solid #f1f5f9' }}>
                  <div>
                    <div style={{ fontWeight: 500, fontSize: '0.9rem' }}>{s.target?.fullName || s.target?.name || s.target?.email || 'Unknown'}</div>
                    <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>
                      Started: {s.startTime ? new Date(s.startTime).toLocaleString() : '-'}
                      {s.reason && <> — Reason: {s.reason}</>}
                    </div>
                  </div>
                  <button className="admin-btn admin-btn-danger admin-btn-sm" onClick={() => handleStopImpersonation(s._id || s.id)}>Stop</button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="admin-table-wrapper">
        <div className="admin-table-header">
          <span className="admin-table-title">Impersonation Log</span>
        </div>
        {logLoading ? (
          <div style={{ padding: '24px' }}>
            {[1,2,3].map(i => <div key={i} className="admin-skeleton-table-row" />)}
          </div>
        ) : impersonationLog.length === 0 ? (
          <div className="admin-empty-state">
            <div className="admin-empty-state-icon"><ClipboardList size={32} /></div>
            <div className="admin-empty-state-text">No impersonation history</div>
          </div>
        ) : (
          <table className="admin-table">
            <thead>
              <tr>
                <th>Impersonator</th>
                <th>Target</th>
                <th>Reason</th>
                <th>Start Time</th>
                <th>End Time</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {impersonationLog.map((entry, idx) => (
                <tr key={entry._id || entry.id || idx}>
                  <td style={{ fontWeight: 500 }}>{entry.impersonator?.fullName || entry.impersonator?.name || entry.impersonator?.email || '-'}</td>
                  <td>{entry.target?.fullName || entry.target?.name || entry.target?.email || '-'}</td>
                  <td style={{ color: '#6b7280', maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis' }}>{entry.reason || '-'}</td>
                  <td style={{ fontSize: '0.8rem', color: '#6b7280' }}>{entry.startTime ? new Date(entry.startTime).toLocaleString() : '-'}</td>
                  <td style={{ fontSize: '0.8rem', color: '#6b7280' }}>{entry.endTime ? new Date(entry.endTime).toLocaleString() : 'Active'}</td>
                  <td>
                    <span className={`admin-badge ${entry.endTime ? 'active' : 'pending'}`}>
                      {entry.endTime ? 'Completed' : 'Active'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <div className="admin-pagination">
          <div className="admin-pagination-info">{impersonationLog.length} sessions</div>
        </div>
      </div>

      {showConfirm && (
        <div className="admin-modal-overlay" onClick={() => setShowConfirm(false)}>
          <div className="admin-modal admin-modal-sm" onClick={e => e.stopPropagation()}>
            <div className="admin-modal-body" style={{ textAlign: 'center' }}>
              <div className="admin-confirm-icon warning"><Lock size={32} /></div>
              <div className="admin-confirm-text">
                <h3 style={{ margin: '0 0 8px', fontSize: '1.1rem' }}>Confirm Impersonation</h3>
                <p>You are about to impersonate <strong>{targetUser?.fullName || targetUser?.name}</strong> ({targetUser?.email}).</p>
                <p style={{ fontSize: '0.85rem', color: '#92400e', background: '#fef3c7', padding: '8px', borderRadius: '6px', marginTop: '8px' }}>
                  Reason: {reason}
                </p>
                <p style={{ fontSize: '0.8rem', color: '#dc2626', marginTop: '8px' }}>This action will be logged.</p>
              </div>
            </div>
            <div className="admin-modal-footer" style={{ justifyContent: 'center' }}>
              <button className="admin-btn admin-btn-secondary" onClick={() => setShowConfirm(false)}>Cancel</button>
              <button className="admin-btn admin-btn-primary" onClick={handleStartImpersonation} disabled={startLoading}>
                {startLoading ? 'Starting...' : 'Confirm & Start'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ImpersonationPage;
