import React, { useState, useEffect } from 'react';
import { Users, AlertTriangle, Edit, RefreshCw, Key, ShieldOff, Shield, Trash2, Lock, Unlock, Copy } from 'lucide-react';
import api from '../../api/axios';
import { useToast } from '../../admin/context/ToastContext';

const TABS = [
  { key: 'SUPER_ADMIN', label: 'SuperAdmin' },
  { key: 'ADMIN', label: 'Admin' },
  { key: 'SELLER', label: 'Seller' },
  { key: 'CUSTOMER', label: 'Customer' },
];

const MAX_LOGIN_ATTEMPTS = 5;
const isLocked = (u) => (u.failedLoginAttempts || 0) >= MAX_LOGIN_ATTEMPTS;

const STATUSES = ['ACTIVE', 'SUSPENDED', 'BLOCKED', 'DELETED'];

const initialForm = { fullName: '', email: '', password: '', role: 'ADMIN' };

const UserManagement = () => {
  const { showToast } = useToast();
  const [activeTab, setActiveTab] = useState('SUPER_ADMIN');
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [searchId, setSearchId] = useState('');
  const [filterStatus, setFilterStatus] = useState('ACTIVE');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const pageSize = 10;

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(null);
  const [form, setForm] = useState(initialForm);
  const [formError, setFormError] = useState('');
  const [formLoading, setFormLoading] = useState(false);

  const [confirmAction, setConfirmAction] = useState(null);
  const [newRoleValue, setNewRoleValue] = useState('');
  const [pwConfirm, setPwConfirm] = useState(null);
  const [pwResult, setPwResult] = useState(null);
  const [pwLoading, setPwLoading] = useState(false);
  const [unlockConfirm, setUnlockConfirm] = useState(null);
  const [unlockLoading, setUnlockLoading] = useState(false);

  const fetchUsers = async (p = page) => {
    setLoading(true);
    setError('');
    try {
      const params = { page: p, pageSize, role: activeTab, status: filterStatus !== 'ALL' ? filterStatus : undefined, search: search || undefined, id: searchId || undefined };
      const res = await api.get('/api/super-admin/users', { params });
      const data = res.data;
      let userList = Array.isArray(data) ? data : data.users || data.data || [];
      userList = userList.filter(u => u.status !== 'DELETED' && !u.isDeleted);
      setUsers(userList);
      setTotalPages(data.totalPages || data.pages || 1);
      setTotal(data.total || userList.length);
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Failed to load users');
      setUsers([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchUsers(1); setPage(1); }, [activeTab, filterStatus, search, searchId]);

  useEffect(() => { fetchUsers(page); }, [page]);

  const handleCreate = async (e) => {
    e.preventDefault();
    setFormError('');
    if (!form.fullName.trim() || !form.email.trim() || !form.password.trim()) {
      setFormError('All fields are required');
      return;
    }
    setFormLoading(true);
    try {
      await api.post('/api/super-admin/users', form);
      setShowCreateModal(false);
      setForm(initialForm);
      fetchUsers(1);
      setPage(1);
    } catch (err) {
      setFormError(err.response?.data?.error || err.response?.data?.message || 'Failed to create user');
    } finally {
      setFormLoading(false);
    }
  };

  const handleEdit = async (e) => {
    e.preventDefault();
    setFormError('');
    if (!form.fullName.trim() || !form.email.trim()) {
      setFormError('Name and email are required');
      return;
    }
    setFormLoading(true);
    try {
      await api.put(`/api/super-admin/users/${showEditModal._id || showEditModal.id}`, { fullName: form.fullName, email: form.email, role: form.role });
      setShowEditModal(null);
      setForm(initialForm);
      fetchUsers(page);
    } catch (err) {
      setFormError(err.response?.data?.error || err.response?.data?.message || 'Failed to update user');
    } finally {
      setFormLoading(false);
    }
  };

  const openEdit = (user) => {
    setForm({ fullName: user.fullName || user.name || '', email: user.email || '', password: '', role: user.role || 'ADMIN' });
    setFormError('');
    setShowEditModal(user);
  };

  const handleChangeRole = async (user, newRole) => {
    try {
      await api.put(`/api/super-admin/users/${user._id || user.id}/role`, { role: newRole });
      setConfirmAction(null);
      fetchUsers(page);
    } catch (err) {
      showToast(err.response?.data?.error || err.response?.data?.message || 'Failed to change role', 'error');
      setConfirmAction(null);
    }
  };

  const handleSuspend = async (user) => {
    if (!window.confirm(`Suspend ${user.fullName || user.name || user.email} (${user.role})? They will lose all access until revoked.`)) return;
    try {
      await api.post(`/api/super-admin/users/${user._id || user.id}/suspend`);
      showToast(`${user.fullName || user.email} suspended successfully`, 'success');
      fetchUsers(page);
    } catch (err) {
      showToast(err.response?.data?.error || err.response?.data?.message || 'Failed to suspend user', 'error');
    }
  };

  const handleRevoke = async (user) => {
    if (!window.confirm(`Revoke suspension for ${user.fullName || user.name || user.email}? They will regain full access.`)) return;
    try {
      await api.post(`/api/super-admin/users/${user._id || user.id}/revoke`);
      showToast(`${user.fullName || user.email} restored successfully`, 'success');
      fetchUsers(page);
    } catch (err) {
      showToast(err.response?.data?.error || err.response?.data?.message || 'Failed to revoke suspension', 'error');
    }
  };

  const handleDelete = async (user) => {
    const roleLabel = TABS.find(t => t.key === activeTab)?.label || 'user';
    if (!window.confirm(`Are you sure you want to delete this ${roleLabel.toLowerCase()}?`)) return;
    try {
      await api.post(`/api/super-admin/users/${user._id || user.id}/delete`);
      showToast(`${user.fullName || user.email} deleted successfully`, 'success');
      fetchUsers(page);
    } catch (err) {
      showToast(err.response?.data?.error || err.response?.data?.message || 'Failed to delete user', 'error');
    }
  };

  const confirmResetPassword = async () => {
    const user = pwConfirm;
    if (!user) return;
    setPwLoading(true);
    try {
      const res = await api.post(`/api/super-admin/users/${user._id || user.id}/reset-password`, {});
      setPwConfirm(null);
      setPwResult({ user, password: res.data?.newPassword || '' });
      fetchUsers(page);
    } catch (err) {
      showToast(err.response?.data?.error || err.response?.data?.message || 'Failed to reset password', 'error');
      setPwConfirm(null);
    } finally {
      setPwLoading(false);
    }
  };

  const confirmUnlock = async () => {
    const user = unlockConfirm;
    if (!user) return;
    setUnlockLoading(true);
    try {
      await api.post(`/api/super-admin/users/${user._id || user.id}/unlock`, {});
      showToast(`${user.fullName || user.email} unlocked successfully`, 'success');
      setUnlockConfirm(null);
      fetchUsers(page);
    } catch (err) {
      showToast(err.response?.data?.error || err.response?.data?.message || 'Failed to unlock account', 'error');
      setUnlockConfirm(null);
    } finally {
      setUnlockLoading(false);
    }
  };

  const copyPassword = async () => {
    try {
      await navigator.clipboard.writeText(pwResult?.password || '');
      showToast('Password copied to clipboard', 'success');
    } catch {
      showToast('Could not copy automatically — please select and copy manually', 'error');
    }
  };

  const statusLabel = (u) => {
    if (u.status === 'DELETED' || u.isDeleted) return 'Deleted';
    if (u.status === 'SUSPENDED' || u.isBlocked) return 'Suspended';
    return 'Active';
  };
  const statusClass = (u) => {
    if (u.status === 'DELETED' || u.isDeleted) return 'inactive';
    if (u.status === 'SUSPENDED' || u.isBlocked) return 'inactive';
    return 'active';
  };

  const renderTable = () => {
    if (loading) {
      return (
        <tbody>
          {[1,2,3,4,5].map(i => (
            <tr key={i}>
              {[1,2,3,4,5,6].map(j => (
                <td key={j}><div className="admin-skeleton-table-row" style={{ height: '20px' }} /></td>
              ))}
            </tr>
          ))}
        </tbody>
      );
    }

    if (users.length === 0) {
      return (
        <tbody>
          <tr>
            <td colSpan={6}>
              <div className="admin-empty-state">
                <div className="admin-empty-state-icon"><Users size={32} /></div>
                <div className="admin-empty-state-text">No {TABS.find(t => t.key === activeTab)?.label || ''} users found</div>
              </div>
            </td>
          </tr>
        </tbody>
      );
    }

    return (
      <tbody>
        {users.map((u) => {
          const uid = u._id || u.id;
          return (
            <tr key={uid}>
              <td style={{ color: '#6b7280', fontSize: '0.8rem', fontFamily: 'monospace' }}>{uid}</td>
              <td style={{ fontWeight: 500 }}>{u.fullName || u.name || 'N/A'}</td>
              <td>{u.email}</td>
              <td>
                <span className={`admin-badge ${statusClass(u)}`}>
                  {statusLabel(u)}
                </span>
                {(u.status === 'SUSPENDED' || u.isBlocked) && (
                  <div style={{ fontSize: '0.7rem', color: '#6b7280', marginTop: '2px' }}>
                    {u.suspendedAt ? new Date(u.suspendedAt).toLocaleDateString() : ''}
                  </div>
                )}
                {isLocked(u) && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '3px', fontSize: '0.7rem', color: '#dc2626', marginTop: '3px', fontWeight: 600 }}>
                    <Lock size={11} /> Locked ({u.failedLoginAttempts} attempts)
                  </div>
                )}
              </td>
              <td style={{ fontSize: '0.8rem', color: '#6b7280' }}>
                {u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '-'}
              </td>
              <td>
                <div className="admin-table-actions-cell">
                  <button className="admin-table-action-btn edit" onClick={() => openEdit(u)} title="Edit"><Edit size={16} /></button>
                  <button className="admin-table-action-btn view" onClick={() => {
                    setNewRoleValue(u.role);
                    setConfirmAction({ user: u, action: 'changeRole', message: `Change role of ${u.fullName || u.name || u.email} from ${u.role}?` });
                  }} title="Change Role"><RefreshCw size={16} /></button>
                  {u.status === 'SUSPENDED' || u.isBlocked ? (
                    <button className="admin-table-action-btn" onClick={() => handleRevoke(u)} title="Revoke Suspension" style={{ color: '#16a34a' }}>
                      <Shield size={16} />
                    </button>
                  ) : u.status !== 'DELETED' && (
                    <button className="admin-table-action-btn" onClick={() => handleSuspend(u)} title="Suspend User" style={{ color: '#dc2626' }}>
                      <ShieldOff size={16} />
                    </button>
                  )}
                  {isLocked(u) && (
                    <button className="admin-table-action-btn" onClick={() => setUnlockConfirm(u)} title="Unlock Account" style={{ color: '#16a34a' }}><Unlock size={16} /></button>
                  )}
                  <button className="admin-table-action-btn" onClick={() => setPwConfirm(u)} title="Reset Password"><Key size={16} /></button>
                  {u.role === 'SUPER_ADMIN' ? (
                    <span style={{ fontSize: '0.75rem', color: '#94a3b8', padding: '0 8px' }}>Protected</span>
                  ) : (
                    <button className="admin-table-action-btn" onClick={() => handleDelete(u)} title="Delete User" style={{ color: '#dc2626' }}><Trash2 size={16} /></button>
                  )}
                </div>
              </td>
            </tr>
          );
        })}
      </tbody>
    );
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700 }}>User Management</h1>
        <button className="admin-btn admin-btn-primary" onClick={() => { setForm(initialForm); setFormError(''); setShowCreateModal(true); }}>
          + Create User
        </button>
      </div>

      {error && <div style={{ background: '#fef2f2', color: '#dc2626', padding: '8px 16px', borderRadius: '8px', marginBottom: '16px', fontSize: '0.85rem' }}>{error}</div>}

      <div className="admin-tabs" style={{ display: 'flex', gap: 0, borderBottom: '2px solid #e5e7eb', marginBottom: '1rem' }}>
        {TABS.map((tab) => (
          <button
            key={tab.key}
            onClick={() => { setActiveTab(tab.key); setPage(1); setSearchId(''); }}
            style={{
              padding: '10px 20px',
              border: 'none',
              borderBottom: activeTab === tab.key ? '2px solid var(--color-primary, #0E5C5C)' : '2px solid transparent',
              background: activeTab === tab.key ? 'var(--color-primary-light, #f0fdf4)' : 'transparent',
              color: activeTab === tab.key ? 'var(--color-primary, #0E5C5C)' : '#6b7280',
              fontWeight: activeTab === tab.key ? 600 : 400,
              fontSize: '0.875rem',
              cursor: 'pointer',
              transition: 'all 0.15s',
              marginBottom: '-2px',
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="admin-table-wrapper">
        <div className="admin-filter-bar">
          <input
            className="admin-table-search"
            placeholder="Search by name or email..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          {(activeTab === 'SELLER' || activeTab === 'CUSTOMER') && (
            <input
              className="admin-table-search"
              placeholder="Search by ID..."
              value={searchId}
              onChange={(e) => setSearchId(e.target.value)}
            />
          )}
          <select className="admin-filter-select" value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)}>
            <option value="ALL">All Status</option>
            {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
          <span style={{ fontSize: '0.8rem', color: '#6b7280', marginLeft: 'auto' }}>
            {total} {TABS.find(t => t.key === activeTab)?.label || ''} users
          </span>
        </div>

        <table className="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Email</th>
              <th>Status</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          {renderTable()}
        </table>

        <div className="admin-pagination">
          <div className="admin-pagination-info">
            Page {page} of {totalPages || 1} ({total} users)
          </div>
          <div className="admin-pagination-controls">
            <button className="admin-pagination-btn" disabled={page <= 1} onClick={() => setPage(p => Math.max(1, p - 1))}>Prev</button>
            {Array.from({ length: Math.min(totalPages || 1, 5) }, (_, i) => {
              const start = Math.max(1, page - 2);
              const pNum = start + i;
              return pNum <= (totalPages || 1) ? (
                <button key={pNum} className={`admin-pagination-btn ${pNum === page ? 'active' : ''}`} onClick={() => setPage(pNum)}>{pNum}</button>
              ) : null;
            })}
            <button className="admin-pagination-btn" disabled={page >= (totalPages || 1)} onClick={() => setPage(p => p + 1)}>Next</button>
          </div>
        </div>
      </div>

      {showCreateModal && (
        <div className="admin-modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="admin-modal" onClick={e => e.stopPropagation()}>
            <div className="admin-modal-header">
              <span className="admin-modal-title">Create New User</span>
              <button className="admin-modal-close" onClick={() => setShowCreateModal(false)}>✕</button>
            </div>
            <form onSubmit={handleCreate}>
              <div className="admin-modal-body">
                {formError && <div style={{ background: '#fef2f2', color: '#dc2626', padding: '8px 12px', borderRadius: '6px', marginBottom: '16px', fontSize: '0.85rem' }}>{formError}</div>}
                <div className="admin-form-group">
                  <label className="admin-form-label">Full Name <span className="required">*</span></label>
                  <input className="admin-form-input" value={form.fullName} onChange={e => setForm({...form, fullName: e.target.value})} placeholder="John Doe" required />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Email <span className="required">*</span></label>
                  <input className="admin-form-input" type="email" value={form.email} onChange={e => setForm({...form, email: e.target.value})} placeholder="john@example.com" required />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Password <span className="required">*</span></label>
                  <input className="admin-form-input" type="password" value={form.password} onChange={e => setForm({...form, password: e.target.value})} placeholder="Min 8 characters" required minLength={8} />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Role <span className="required">*</span></label>
                  <select className="admin-form-select" value={form.role} onChange={e => setForm({...form, role: e.target.value})}>
                    {TABS.map(t => <option key={t.key} value={t.key}>{t.label}</option>)}
                  </select>
                </div>
              </div>
              <div className="admin-modal-footer">
                <button type="button" className="admin-btn admin-btn-secondary" onClick={() => setShowCreateModal(false)}>Cancel</button>
                <button type="submit" className="admin-btn admin-btn-primary" disabled={formLoading}>
                  {formLoading ? 'Creating...' : 'Create User'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showEditModal && (
        <div className="admin-modal-overlay" onClick={() => setShowEditModal(null)}>
          <div className="admin-modal" onClick={e => e.stopPropagation()}>
            <div className="admin-modal-header">
              <span className="admin-modal-title">Edit User</span>
              <button className="admin-modal-close" onClick={() => setShowEditModal(null)}>✕</button>
            </div>
            <form onSubmit={handleEdit}>
              <div className="admin-modal-body">
                {formError && <div style={{ background: '#fef2f2', color: '#dc2626', padding: '8px 12px', borderRadius: '6px', marginBottom: '16px', fontSize: '0.85rem' }}>{formError}</div>}
                <div className="admin-form-group">
                  <label className="admin-form-label">Full Name <span className="required">*</span></label>
                  <input className="admin-form-input" value={form.fullName} onChange={e => setForm({...form, fullName: e.target.value})} required />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Email <span className="required">*</span></label>
                  <input className="admin-form-input" type="email" value={form.email} onChange={e => setForm({...form, email: e.target.value})} required />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Role</label>
                  <select className="admin-form-select" value={form.role} onChange={e => setForm({...form, role: e.target.value})}>
                    {TABS.map(t => <option key={t.key} value={t.key}>{t.label}</option>)}
                  </select>
                </div>
              </div>
              <div className="admin-modal-footer">
                <button type="button" className="admin-btn admin-btn-secondary" onClick={() => setShowEditModal(null)}>Cancel</button>
                <button type="submit" className="admin-btn admin-btn-primary" disabled={formLoading}>
                  {formLoading ? 'Saving...' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {confirmAction && (
        <div className="admin-modal-overlay" onClick={() => setConfirmAction(null)}>
          <div className="admin-modal admin-modal-sm" onClick={e => e.stopPropagation()}>
            <div className="admin-modal-body" style={{ textAlign: 'center' }}>
              <div className="admin-confirm-icon warning"><AlertTriangle size={32} /></div>
              <div className="admin-confirm-text">
                <h3 style={{ margin: '0 0 8px', fontSize: '1.1rem' }}>Confirm Action</h3>
                <p>{confirmAction.message}</p>
                <div style={{ marginTop: '12px', textAlign: 'left' }}>
                  <label className="admin-form-label">New Role</label>
                  <select className="admin-form-select" value={newRoleValue} onChange={e => setNewRoleValue(e.target.value)}>
                    {TABS.filter(t => t.key !== 'SUPER_ADMIN').map(t => <option key={t.key} value={t.key}>{t.label}</option>)}
                  </select>
                </div>
              </div>
            </div>
            <div className="admin-modal-footer" style={{ justifyContent: 'center' }}>
              <button className="admin-btn admin-btn-secondary" onClick={() => setConfirmAction(null)}>Cancel</button>
              <button className="admin-btn admin-btn-primary" onClick={() => handleChangeRole(confirmAction.user, newRoleValue)}>
                Confirm
              </button>
            </div>
          </div>
        </div>
      )}

      {pwConfirm && (
        <div className="admin-modal-overlay" onClick={() => !pwLoading && setPwConfirm(null)}>
          <div className="admin-modal admin-modal-sm" onClick={e => e.stopPropagation()}>
            <div className="admin-modal-body" style={{ textAlign: 'center' }}>
              <div className="admin-confirm-icon warning"><Key size={32} /></div>
              <div className="admin-confirm-text">
                <h3 style={{ margin: '0 0 8px', fontSize: '1.1rem' }}>Reset Password</h3>
                <p>
                  Generate a new random password for <strong>{pwConfirm.fullName || pwConfirm.name || pwConfirm.email}</strong>?
                  {isLocked(pwConfirm) && ' This will also unlock their account.'}
                </p>
              </div>
            </div>
            <div className="admin-modal-footer" style={{ justifyContent: 'center' }}>
              <button className="admin-btn admin-btn-secondary" onClick={() => setPwConfirm(null)} disabled={pwLoading}>Cancel</button>
              <button className="admin-btn admin-btn-primary" onClick={confirmResetPassword} disabled={pwLoading}>
                {pwLoading ? 'Resetting...' : 'Reset Password'}
              </button>
            </div>
          </div>
        </div>
      )}

      {pwResult && (
        <div className="admin-modal-overlay" onClick={() => setPwResult(null)}>
          <div className="admin-modal admin-modal-sm" onClick={e => e.stopPropagation()}>
            <div className="admin-modal-header">
              <span className="admin-modal-title">Password Reset</span>
              <button className="admin-modal-close" onClick={() => setPwResult(null)}>✕</button>
            </div>
            <div className="admin-modal-body">
              <p style={{ fontSize: '0.85rem', color: '#6b7280', marginBottom: '10px' }}>
                New password for <strong>{pwResult.user.fullName || pwResult.user.name || pwResult.user.email}</strong> — copy this now, it won't be shown again:
              </p>
              <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                <code style={{ flex: 1, padding: '10px 12px', background: '#f3f4f6', borderRadius: '6px', fontSize: '0.95rem', wordBreak: 'break-all', userSelect: 'all' }}>
                  {pwResult.password}
                </code>
                <button className="admin-table-action-btn" onClick={copyPassword} title="Copy"><Copy size={16} /></button>
              </div>
            </div>
            <div className="admin-modal-footer" style={{ justifyContent: 'center' }}>
              <button className="admin-btn admin-btn-primary" onClick={() => setPwResult(null)}>Done</button>
            </div>
          </div>
        </div>
      )}

      {unlockConfirm && (
        <div className="admin-modal-overlay" onClick={() => !unlockLoading && setUnlockConfirm(null)}>
          <div className="admin-modal admin-modal-sm" onClick={e => e.stopPropagation()}>
            <div className="admin-modal-body" style={{ textAlign: 'center' }}>
              <div className="admin-confirm-icon warning"><Unlock size={32} /></div>
              <div className="admin-confirm-text">
                <h3 style={{ margin: '0 0 8px', fontSize: '1.1rem' }}>Unlock Account</h3>
                <p>
                  Unlock <strong>{unlockConfirm.fullName || unlockConfirm.name || unlockConfirm.email}</strong>? They'll be able to log in again with their existing password.
                </p>
              </div>
            </div>
            <div className="admin-modal-footer" style={{ justifyContent: 'center' }}>
              <button className="admin-btn admin-btn-secondary" onClick={() => setUnlockConfirm(null)} disabled={unlockLoading}>Cancel</button>
              <button className="admin-btn admin-btn-primary" onClick={confirmUnlock} disabled={unlockLoading}>
                {unlockLoading ? 'Unlocking...' : 'Unlock'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default UserManagement;
