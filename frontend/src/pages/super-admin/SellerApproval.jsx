import React, { useState, useEffect } from "react";
import api from "../../utils/axios";
import { useToast } from "../../admin/context/ToastContext";

const SellerApproval = () => {
  const [sellers, setSellers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actioning, setActioning] = useState(null);
  const { showToast } = useToast();

  const fetchSellers = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await api.get("/api/super-admin/users", { params: { role: "SELLER" } });
      const data = Array.isArray(res.data) ? res.data : [];
      setSellers(data);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load sellers');
      setSellers([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchSellers(); }, []);

  const handleAction = async (id, newStatus) => {
    setActioning(id);
    try {
      await api.put(`/api/super-admin/users/${id}/status`, { status: newStatus });
      showToast(`Seller ${newStatus === "ACTIVE" ? "approved" : "rejected"} successfully`, "success");
      fetchSellers();
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to update seller status', "error");
    } finally {
      setActioning(null);
    }
  };

  if (loading) {
    return (
      <div>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '1.5rem' }}>Seller Approval Queue</h1>
        <div className="admin-skeleton-row">
          {[1,2,3,4].map(i => <div key={i} className="admin-skeleton-card" style={{ height:'48px' }} />)}
        </div>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700 }}>Seller Approval Queue</h1>
      </div>

      {error && <div className="admin-alert error">{error}</div>}

      <div className="admin-table-wrapper">
        {sellers.length === 0 ? (
          <div className="admin-empty-state">
            <div className="admin-empty-state-icon">🏪</div>
            <div className="admin-empty-state-text">No sellers found</div>
          </div>
        ) : (
          <table className="admin-table">
            <thead>
              <tr>
                <th>Seller</th>
                <th>Email</th>
                <th>Status</th>
                <th>Active</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {sellers.map(s => (
                <tr key={s.id || s._id}>
                  <td style={{ fontWeight: 500 }}>{s.fullName || s.username || s.name || '-'}</td>
                  <td style={{ color: '#6b7280' }}>{s.email}</td>
                  <td>
                    <span className={`admin-badge ${(s.status || 'ACTIVE') === 'ACTIVE' ? 'active' : 'inactive'}`}>
                      {s.status || 'ACTIVE'}
                    </span>
                  </td>
                  <td>
                    <span className={`admin-badge ${s.active !== false ? 'active' : 'inactive'}`}>
                      {s.active !== false ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td>
                    <div className="admin-table-actions-cell">
                      <button
                        className="admin-btn admin-btn-sm admin-btn-success"
                        onClick={() => handleAction(s.id || s._id, "ACTIVE")}
                        disabled={actioning === (s.id || s._id) || s.status === 'ACTIVE'}
                      >
                        {actioning === (s.id || s._id) ? '...' : 'Approve'}
                      </button>
                      <button
                        className="admin-btn admin-btn-sm admin-btn-danger"
                        onClick={() => handleAction(s.id || s._id, "BLOCKED")}
                        disabled={actioning === (s.id || s._id) || s.status === 'BLOCKED'}
                      >
                        {actioning === (s.id || s._id) ? '...' : 'Reject'}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        <div className="admin-pagination">
          <div className="admin-pagination-info">{sellers.length} seller(s)</div>
        </div>
      </div>
    </div>
  );
};

export default SellerApproval;
