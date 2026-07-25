import React, { useState, useEffect } from 'react';
import { Users, AlertTriangle, Edit, RefreshCw, CheckCircle, Ban, Key } from 'lucide-react';
import api from '../../api/axios';
import { useToast } from '../../admin/context/ToastContext';

const ROLES = ['CUSTOMER', 'SELLER', 'EXECUTIVE', 'ADMIN', 'SUPER_ADMIN'];
const STATUSES = ['ACTIVE', 'SUSPENDED'];

const initialForm = { fullName: '', email: '', password: '', role: 'ADMIN' };

const UserManagement = () => {
  const { showToast } = useToast();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [filterRole, setFilterRole] = useState('ALL');
  const [filterStatus, setFilterStatus] = useState('ALL');
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

  const fetchUsers = async (p = page) => {
    setLoading(true);
    setError('');
    try {
      const params = { page: p, pageSize, role: filterRole !== 'ALL' ? filterRole : undefined, status: filterStatus !== 'ALL' ? filterStatus : undefined, search: search || undefined };
      const res = await api.get('/api/super-admin/users', { params });
      const data = res.data;
      setUsers(Array.isArray(data) ? data : data.users || data.data || []);
      setTotalPages(data.totalPages || data.pages || 1);
      setTotal(data.total || (Array.isArray(data) ? data.length : 0));
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load users');
      setUsers([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchUsers(1); setPage(1); }, [filterRole, filterStatus, search]);

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
      setFormError(err.response?.data?.message || 'Failed to create user');
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
      setFormError(err.response?.data?.message || 'Failed to update user');
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
      showToast(err.response?.data?.message || 'Failed to change role', 'error');
      setConfirmAction(null);
    }
  };

  const handleToggleStatus = async (user) => {
    try {
      const isSuspended = user.status === 'SUSPENDED' || user.isBlocked;
      const newStatus = isSuspended ? 'ACTIVE' : 'SUSPENDED';
      await api.put(`/api/super-admin/users/${user._id || user.id}/status`, { status: newStatus });
      fetchUsers(page);
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to toggle user status', 'error');
    }
  };

  const handleResetPassword = async (user) => {
    if (!window.confirm(`Reset password for ${user.fullName || user.name || user.email}?`)) return;
    try {
      await api.post(`/api/super-admin/users/${user._id || user.id}/reset-password`, {});
      showToast('Password reset email sent', 'success');
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to reset password', 'error');
    }
  };

  const isSuspended = (u) => u.status === 'SUSPENDED' || u.isBlocked;

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
                <div className="admin-empty-state-text">No users found</div>
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
              <td style={{ fontWeight: 500 }}>{u.fullName || u.name || 'N/A'}</td>
              <td>{u.email}</td>
              <td>
                <span className={`admin-badge ${u.role === 'ADMIN' ? 'active' : 'pending'}`}>
                  {u.role}
                </span>
              </td>
              <td>
                <span className={`admin-badge ${isSuspended(u) ? 'inactive' : 'active'}`}>
                  {isSuspended(u) ? 'Suspended' : 'Active'}
                </span>
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
                  <button className="admin-table-action-btn" onClick={() => handleToggleStatus(u)} title={isSuspended(u) ? 'Activate' : 'Suspend'} style={{ color: isSuspended(u) ? '#16a34a' : '#dc2626' }}>
                    {isSuspended(u) ? <CheckCircle size={16} /> : <Ban size={16} />}
                  </button>
                  <button className="admin-table-action-btn" onClick={() => handleResetPassword(u)} title="Reset Password"><Key size={16} /></button>
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

      <div className="admin-table-wrapper">
        <div className="admin-filter-bar">
          <input
            className="admin-table-search"
            placeholder="Search by name or email..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <select className="admin-filter-select" value={filterRole} onChange={(e) => setFilterRole(e.target.value)}>
            <option value="ALL">All Roles</option>
            {ROLES.map(r => <option key={r} value={r}>{r}</option>)}
          </select>
          <select className="admin-filter-select" value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)}>
            <option value="ALL">All Status</option>
            {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
          <span style={{ fontSize: '0.8rem', color: '#6b7280', marginLeft: 'auto' }}>
            {total} total users
          </span>
        </div>

        <table className="admin-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
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
                    {ROLES.map(r => <option key={r} value={r}>{r}</option>)}
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
                    {ROLES.map(r => <option key={r} value={r}>{r}</option>)}
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
                    {ROLES.filter(r => r !== 'SUPER_ADMIN').map(r => <option key={r} value={r}>{r}</option>)}
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
    </div>
  );
};

export default UserManagement;
