import React, { useState, useEffect } from 'react';
import api from '../../utils/axios';

const statCards = [
  { key: 'totalRevenue', label: 'Total Revenue', icon: '💰', color: '#16a34a', prefix: '₹' },
  { key: 'totalOrders', label: 'Total Orders', icon: '📋', color: '#2563eb' },
  { key: 'totalCustomers', label: 'Total Customers', icon: '👥', color: '#7c3aed' },
  { key: 'totalSellers', label: 'Total Sellers', icon: '🏪', color: '#0891b2' },
  { key: 'activeProducts', label: 'Active Products', icon: '📦', color: '#d97706' },
  { key: 'pendingApprovals', label: 'Pending Approvals', icon: '⏳', color: '#ea580c' },
  { key: 'totalRefunds', label: 'Refunds', icon: '💳', color: '#dc2626' },
  { key: 'failedPayments', label: 'Failed Payments', icon: '❌', color: '#4f46e5' },
];

const barData = [
  { label: 'Jan', value: 65 }, { label: 'Feb', value: 78 }, { label: 'Mar', value: 90 },
  { label: 'Apr', value: 72 }, { label: 'May', value: 88 }, { label: 'Jun', value: 95 },
  { label: 'Jul', value: 82 }, { label: 'Aug', value: 70 }, { label: 'Sep', value: 85 },
  { label: 'Oct', value: 92 }, { label: 'Nov', value: 100 }, { label: 'Dec', value: 110 },
];

const SuperAdminDashboard = () => {
  const [stats, setStats] = useState(null);
  const [activities, setActivities] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await api.get('/api/super-admin/dashboard');
        setStats(res.data.stats || res.data);
        setActivities(res.data.recentActivity || res.data.activities || []);
      } catch (err) {
        setError(err.response?.data?.message || 'Failed to load dashboard data');
        setStats({ totalRevenue: 0, totalOrders: 0, totalCustomers: 0, totalSellers: 0, activeProducts: 0, pendingApprovals: 0, refunds: 0, failedPayments: 0 });
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  if (loading) {
    return (
      <div className="page">
        <h1 className="page-title" style={{ marginBottom: 'var(--spacing-6)' }}>Super Admin Dashboard</h1>
        <div className="admin-skeleton-row">
          {[1,2,3,4].map(i => <div key={i} className="admin-skeleton-card" />)}
        </div>
        <div className="admin-skeleton-row">
          {[1,2,3,4].map(i => <div key={i} className="admin-skeleton-card" />)}
        </div>
      </div>
    );
  }

  const maxBarValue = Math.max(...barData.map(b => b.value), 1);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">Super Admin Dashboard</h1>
        </div>
        {error && <span className="badge badge-danger">{error}</span>}
      </div>

      <div className="admin-stats-grid">
        {statCards.map((card) => {
          const val = stats ? stats[card.key] : 0;
          const display = card.prefix
            ? card.prefix + (typeof val === 'number' ? val.toLocaleString() : val)
            : (typeof val === 'number' ? val.toLocaleString() : val || '0');
          const colorMap = { '#16a34a': 'green', '#2563eb': 'blue', '#d97706': 'gold', '#dc2626': 'rose' };
          const colorClass = colorMap[card.color] || 'blue';
          return (
            <div key={card.key} className="admin-stat-card">
              <div className={`admin-stat-icon ${colorClass}`}>{card.icon}</div>
              <div className="admin-stat-info">
                <div className="admin-stat-value" style={{ color: card.color }}>{display}</div>
                <div className="admin-stat-label">{card.label}</div>
              </div>
            </div>
          );
        })}
      </div>

      <div className="admin-grid-2" style={{ marginBottom: 'var(--spacing-6)' }}>
        <div className="card">
          <div className="card-header">
            <span className="card-title">Monthly Revenue Trend</span>
          </div>
          <div className="card-body">
            <div style={{ display: 'flex', alignItems: 'flex-end', gap: '8px', height: '200px', padding: '8px 0' }}>
              {barData.map((bar) => (
                <div key={bar.label} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', height: '100%', justifyContent: 'flex-end' }}>
                  <span className="text-xs text-tertiary mb-1">{bar.value}k</span>
                  <div style={{ width: '100%', maxWidth: '32px', height: `${(bar.value / maxBarValue) * 100}%`, background: 'linear-gradient(to top, #f59e0b, #d97706)', borderRadius: '4px 4px 0 0', transition: 'height 0.3s', minHeight: '4px' }} />
                  <span className="text-xs text-tertiary mt-1">{bar.label}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <span className="card-title">Platform Summary</span>
          </div>
          <div className="card-body">
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {statCards.slice(0, 5).map((card) => {
                const val = stats ? stats[card.key] : 0;
                return (
                  <div key={card.key} className="d-flex justify-between items-center">
                    <span className="text-sm text-secondary">{card.icon} {card.label}</span>
                    <span className="font-bold" style={{ color: card.color }}>
                      {card.prefix ? card.prefix + (typeof val === 'number' ? val.toLocaleString() : val) : (typeof val === 'number' ? val.toLocaleString() : val || '0')}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <span className="card-title">Recent Activity</span>
        </div>
        {activities.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-icon">📭</div>
            <div className="empty-state-text">No recent activity</div>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="table">
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Action</th>
                  <th>Performed By</th>
                  <th>Details</th>
                </tr>
              </thead>
              <tbody>
                {activities.map((act, idx) => (
                  <tr key={act._id || act.id || idx}>
                    <td className="whitespace-nowrap text-sm text-tertiary">
                      {act.timestamp ? new Date(act.timestamp).toLocaleString() : '-'}
                    </td>
                    <td>
                      <span className={`badge ${act.action === 'LOGIN' || act.action === 'CREATE' ? 'badge-success' : act.action === 'DELETE' || act.action === 'BLOCK' ? 'badge-danger' : 'badge-warning'}`}>
                        {act.action || act.type || '-'}
                      </span>
                    </td>
                    <td>{act.performedBy?.name || act.performedBy?.email || act.user?.name || act.user?.email || '-'}</td>
                    <td className="text-tertiary truncate" style={{ maxWidth: '300px' }}>
                      {act.details || act.description || '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="card-footer">
          <span className="text-sm text-tertiary">{activities.length} activities</span>
        </div>
      </div>
    </div>
  );
};

export default SuperAdminDashboard;
