import React, { useState, useEffect } from "react";
import { Users, ShieldOff, Shield } from "lucide-react";
import api from "../../utils/axios";

const ROLES = ['CUSTOMER', 'SELLER', 'EXECUTIVE', 'ADMIN'];

const AdminUsers = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [filterRole, setFilterRole] = useState('ALL');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const pageSize = 10;

  const fetchUsers = async (p = page) => {
    setLoading(true);
    setError('');
    try {
      const params = { page: p, pageSize, role: filterRole !== 'ALL' ? filterRole : undefined, search: search || undefined };
      const res = await api.get('/api/admin/users', { params });
      const data = res.data;
      setUsers(Array.isArray(data) ? data : data.users || data.data || []);
      setTotalPages(data.totalPages || data.pages || 1);
      setTotal(data.total || (Array.isArray(data) ? data.length : 0));
    } catch (err) {
      setError('Failed to load users');
      setUsers([]);
    } finally {
      setLoading(false);
    }
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
    if (!window.confirm(`Suspend ${name}? They will lose all access until revoked.`)) return;
    try {
      await api.post(`/api/admin/users/${id}/suspend`);
      alert(`${name} suspended successfully`);
      fetchUsers(page);
    } catch (err) { alert(err.response?.data?.message || 'Failed to suspend user'); }
  };

  const handleRevoke = async (id, name) => {
    if (!window.confirm(`Revoke suspension for ${name}? They will regain full access.`)) return;
    try {
      await api.post(`/api/admin/users/${id}/revoke`);
      alert(`${name} restored successfully`);
      fetchUsers(page);
    } catch (err) { alert(err.response?.data?.message || 'Failed to revoke suspension'); }
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
      <h1 style={{ fontSize: '1.5rem', fontWeight: 600, marginBottom: '1.5rem' }}>Users</h1>

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
              <th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.length === 0 ? (
              <tr><td colSpan={5}><div className="admin-empty-state"><div className="admin-empty-state-icon"><Users size={32} /></div><div className="admin-empty-state-text">No users found</div></div></td></tr>
            ) : users.map(u => (
              <tr key={u.id || u._id}>
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
    </div>
  );
};

export default AdminUsers;