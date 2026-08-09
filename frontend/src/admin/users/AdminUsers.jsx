import React, { useState, useEffect } from "react";
import { Users, ShieldOff, Shield } from "lucide-react";
import api from "../../utils/axios";
import WindDownStatus from "../../components/WindDownStatus";

const ROLES = ['CUSTOMER', 'SELLER', 'EXECUTIVE', 'ADMIN'];
const CREATABLE_ROLES = ['SELLER', 'CUSTOMER'];
const initialCreateForm = { fullName: '', email: '', password: '', role: 'SELLER' };

const AdminUsers = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [filterRole, setFilterRole] = useState('ALL');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [windDowns, setWindDowns] = useState({});
  const pageSize = 10;

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createForm, setCreateForm] = useState(initialCreateForm);
  const [createError, setCreateError] = useState('');
  const [createLoading, setCreateLoading] = useState(false);

  const fetchUsers = async (p = page) => {
    setLoading(true);
    setError('');
    try {
      const params = { page: p, pageSize, role: filterRole !== 'ALL' ? filterRole : undefined, search: search || undefined };
      const res = await api.get('/api/admin/users', { params });
      const data = res.data;
      const list = Array.isArray(data) ? data : data.users || data.data || [];
      setUsers(list);
      setTotalPages(data.totalPages || data.pages || 1);
      setTotal(data.total || (Array.isArray(data) ? data.length : 0));
      loadWindDowns(list);
    } catch (err) {
      setError('Failed to load users');
      setUsers([]);
    } finally {
      setLoading(false);
    }
  };

  /**
   * What each suspended account on this page still has running.
   *
   * Asked only for suspended rows, and only for the page on screen - walking every customer's
   * orders to answer a question nobody has about the active ones would make the list crawl.
   * Fetched after the table renders so a slow answer never holds up the list itself, and a
   * failure is swallowed: not knowing the wind-down is a reason to show no badge, not a reason
   * to fail the whole screen.
   */
  const loadWindDowns = async (list) => {
    const suspended = list.filter(u => u.status === 'SUSPENDED');
    if (suspended.length === 0) { setWindDowns({}); return; }
    const results = await Promise.all(suspended.map(u =>
      api.get(`/api/admin/users/${u.id}/wind-down`)
        .then(r => [u.id, r.data])
        .catch(() => null)
    ));
    setWindDowns(Object.fromEntries(results.filter(Boolean)));
  };

  useEffect(() => { fetchUsers(page); }, [page, filterRole, search]);

  const handleRoleChange = async (id, role) => {
    try {
      await api.put(`/api/admin/users/${id}/role?role=${role}`);
      fetchUsers(page);
    } catch { alert('Failed to change role'); }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Delete this user?')) return;
    try {
      await api.delete(`/api/admin/users/${id}`);
      fetchUsers(page);
    } catch { alert('Failed to delete user'); }
  };

  const handleSuspend = async (id, name) => {
    // The reason is emailed to the person, so it is written for them rather than kept as an
    // internal note. Suspension is a wind-down, not a lockout - saying so matters, because an
    // admin who thinks this removes somebody instantly will reach for it in the wrong situation.
    const reason = window.prompt(
      `Suspend ${name}?\n\nThey cannot place new orders. Orders they already have run to `
      + `completion, including the return window, and they stay able to sign in for that.\n\n`
      + `Reason (sent to them):`
    );
    if (reason === null) return;
    if (!reason.trim()) { alert('A suspension needs a reason'); return; }
    try {
      await api.post(`/api/admin/users/${id}/suspend`, { reason: reason.trim() });
      alert(`${name} suspended successfully`);
      fetchUsers(page);
    } catch (err) { alert(err.response?.data?.message || err.response?.data?.error || 'Failed to suspend user'); }
  };

  const handleRevoke = async (id, name) => {
    if (!window.confirm(`Revoke suspension for ${name}? They will regain full access.`)) return;
    try {
      await api.post(`/api/admin/users/${id}/revoke`);
      alert(`${name} restored successfully`);
      fetchUsers(page);
    } catch (err) { alert(err.response?.data?.message || 'Failed to revoke suspension'); }
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    setCreateError('');
    if (!createForm.fullName.trim() || !createForm.email.trim() || !createForm.password.trim()) {
      setCreateError('All fields are required');
      return;
    }
    setCreateLoading(true);
    try {
      await api.post('/api/admin/users', createForm);
      setShowCreateModal(false);
      setCreateForm(initialCreateForm);
      fetchUsers(1);
      setPage(1);
    } catch (err) {
      setCreateError(err.response?.data?.error || 'Failed to create user');
    } finally {
      setCreateLoading(false);
    }
  };

  const canManage = (u) => u.role !== 'SUPER_ADMIN' && u.role !== 'ADMIN';

  if (loading && users.length === 0) {
    return (
      <div>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 600, marginBottom: '1.5rem' }}>Users</h1>
        <div className="admin-skeleton-row">
          {[1,2,3,4].map(i => <div key={i} className="admin-skeleton-card" style={{ height:'48px' }} />)}
        </div>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 600, margin: 0 }}>Users</h1>
        <button className="admin-btn admin-btn-primary" onClick={() => { setCreateForm(initialCreateForm); setCreateError(''); setShowCreateModal(true); }}>
          + Create User
        </button>
      </div>

      {error && <div className="admin-alert error">{error}</div>}

      <div className="admin-table-wrapper">
        <div className="admin-filter-bar">
          <input className="admin-table-search" placeholder="Search by name or email..." value={search}
            onChange={e => setSearch(e.target.value)} />
          <select className="admin-filter-select" value={filterRole} onChange={e => { setFilterRole(e.target.value); setPage(1); }}>
            <option value="ALL">All Roles</option>
            {ROLES.map(r => <option key={r} value={r}>{r}</option>)}
          </select>
          <span style={{ fontSize: '0.8rem', color: '#6b7280', marginLeft: 'auto' }}>{total} total users</span>
        </div>

        <table className="admin-table">
          <thead>
            <tr>
              <th>ID</th><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.length === 0 ? (
              <tr><td colSpan={6}><div className="admin-empty-state"><div className="admin-empty-state-icon"><Users size={32} /></div><div className="admin-empty-state-text">No users found</div></div></td></tr>
            ) : users.map(u => (
              <tr key={u.id || u._id}>
                <td style={{ color: '#6b7280', fontSize: '0.8rem', fontFamily: 'monospace' }}>{u.id || u._id}</td>
                <td style={{ fontWeight: 500 }}>{u.fullName || u.name || u.username || '-'}</td>
                <td>{u.email}</td>
                <td>
                  {u.role === 'SUPER_ADMIN' ? (
                    <span className="admin-badge active" style={{ fontSize: '0.8rem', padding: '2px 8px' }}>SUPER ADMIN</span>
                  ) : (
                    <select className="admin-filter-select" value={u.role} onChange={e => handleRoleChange(u.id || u._id, e.target.value)}
                      style={{ padding: '2px 8px', fontSize: '0.8rem' }}>
                      {ROLES.map(r => <option key={r} value={r}>{r}</option>)}
                    </select>
                  )}
                </td>
                <td>
                  <span className={`admin-badge ${u.status === 'SUSPENDED' || u.isBlocked ? 'inactive' : 'active'}`}>
                    {u.status === 'SUSPENDED' ? 'Suspended' : u.isBlocked ? 'Blocked' : 'Active'}
                  </span>
                  {u.status === 'SUSPENDED' && u.suspendedAt && (
                    <div style={{ fontSize: '0.7rem', color: '#6b7280', marginTop: '2px' }}>
                      {new Date(u.suspendedAt).toLocaleDateString()}
                    </div>
                  )}
                  {u.status === 'SUSPENDED' && u.suspensionReason && (
                    <div style={{ fontSize: '0.7rem', color: '#6b7280', marginTop: '2px', maxWidth: '200px' }}>
                      {u.suspensionReason}
                    </div>
                  )}
                  <WindDownStatus data={windDowns[u.id || u._id]} compact />
                </td>
                <td>
                  <div className="admin-table-actions-cell">
                    {canManage(u) && u.status === 'SUSPENDED' && (
                      <button className="admin-btn admin-btn-sm" style={{ background: '#16a34a', color: '#fff', border: 'none', fontSize: '0.75rem' }}
                        onClick={() => handleRevoke(u.id || u._id, u.fullName || u.email)}>
                        Revoke
                      </button>
                    )}
                    {canManage(u) && u.status !== 'SUSPENDED' && !u.isBlocked && (
                      <button className="admin-btn admin-btn-sm" style={{ background: '#dc2626', color: '#fff', border: 'none', fontSize: '0.75rem' }}
                        onClick={() => handleSuspend(u.id || u._id, u.fullName || u.email)}>
                        Suspend
                      </button>
                    )}
                    {u.role === 'SUPER_ADMIN' ? (
                      <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>Protected</span>
                    ) : (
                      <button className="admin-btn admin-btn-sm admin-btn-danger" onClick={() => handleDelete(u.id || u._id)}>Delete</button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="admin-pagination">
          <div className="admin-pagination-info">Page {page} of {totalPages || 1} ({total} users)</div>
          <div className="admin-pagination-controls">
            <button className="admin-pagination-btn" disabled={page <= 1} onClick={() => setPage(p => p - 1)}>Prev</button>
            <span style={{ padding: '0 8px', fontSize: '0.85rem' }}>{page}</span>
            <button className="admin-pagination-btn" disabled={page >= (totalPages || 1)} onClick={() => setPage(p => p + 1)}>Next</button>
          </div>
        </div>
      </div>

      {showCreateModal && (
        <div className="admin-modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="admin-modal" onClick={e => e.stopPropagation()}>
            <div className="admin-modal-header">
              <span className="admin-modal-title">Create User</span>
              <button className="admin-modal-close" onClick={() => setShowCreateModal(false)}>✕</button>
            </div>
            <form onSubmit={handleCreate}>
              <div className="admin-modal-body">
                {createError && <div style={{ background: '#fef2f2', color: '#dc2626', padding: '8px 12px', borderRadius: '6px', marginBottom: '16px', fontSize: '0.85rem' }}>{createError}</div>}
                <div className="admin-form-group">
                  <label className="admin-form-label">Full Name <span className="required">*</span></label>
                  <input className="admin-form-input" value={createForm.fullName} onChange={e => setCreateForm({ ...createForm, fullName: e.target.value })} placeholder="John Doe" required />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Email <span className="required">*</span></label>
                  <input className="admin-form-input" type="email" value={createForm.email} onChange={e => setCreateForm({ ...createForm, email: e.target.value })} placeholder="john@example.com" required />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Password <span className="required">*</span></label>
                  <input className="admin-form-input" type="password" value={createForm.password} onChange={e => setCreateForm({ ...createForm, password: e.target.value })} placeholder="Min 8 characters" required minLength={8} />
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Role <span className="required">*</span></label>
                  <select className="admin-form-select" value={createForm.role} onChange={e => setCreateForm({ ...createForm, role: e.target.value })}>
                    {CREATABLE_ROLES.map(r => <option key={r} value={r}>{r}</option>)}
                  </select>
                  <p style={{ fontSize: '0.75rem', color: '#94a3b8', margin: '4px 0 0' }}>Admins can only create Seller or Customer accounts.</p>
                </div>
              </div>
              <div className="admin-modal-footer">
                <button type="button" className="admin-btn admin-btn-secondary" onClick={() => setShowCreateModal(false)}>Cancel</button>
                <button type="submit" className="admin-btn admin-btn-primary" disabled={createLoading}>
                  {createLoading ? 'Creating...' : 'Create User'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminUsers;