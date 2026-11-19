import React, { useState, useEffect } from 'react';
import { DollarSign, ClipboardList, Users, Store, Package, Clock, CreditCard, XCircle, Mail } from 'lucide-react';
import api from '../../api/axios';

const statCards = [
  { key: 'totalRevenue', label: 'Total Revenue', icon: <DollarSign size={22} />, color: '#16a34a', prefix: '₹' },
  { key: 'totalOrders', label: 'Total Orders', icon: <ClipboardList size={22} />, color: '#2563eb' },
  { key: 'totalCustomers', label: 'Total Customers', icon: <Users size={22} />, color: '#7c3aed' },
  { key: 'totalSellers', label: 'Total Sellers', icon: <Store size={22} />, color: '#0891b2' },
  { key: 'activeProducts', label: 'Active Products', icon: <Package size={22} />, color: '#d97706' },
  { key: 'pendingApprovals', label: 'Pending Approvals', icon: <Clock size={22} />, color: '#ea580c' },
  { key: 'totalRefunds', label: 'Refunds', icon: <CreditCard size={22} />, color: '#dc2626' },
  { key: 'failedPayments', label: 'Failed Payments', icon: <XCircle size={22} />, color: '#4f46e5' },
];

// Short rupee amounts for the chart labels: 292 -> ₹292, 1250 -> ₹1.3k, 345000 -> ₹3.5L.
export const shortRupees = (v) => {
  const n = Number(v) || 0;
  if (n >= 10000000) return `₹${(n / 10000000).toFixed(1).replace(/\.0$/, '')}Cr`;
  if (n >= 100000) return `₹${(n / 100000).toFixed(1).replace(/\.0$/, '')}L`;
  if (n >= 1000) return `₹${(n / 1000).toFixed(1).replace(/\.0$/, '')}k`;
  return `₹${Math.round(n)}`;
};

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

  // Real revenue for the last 12 months, from the dashboard data (cancelled and refunded orders left out).
  const barData = (stats?.monthlyRevenue || []).map((m) => ({ key: m.month, label: m.label, year: m.year, value: Number(m.revenue) || 0 }));
  const maxBarValue = Math.max(...barData.map(b => b.value), 1);
  const hasSales = barData.some(b => b.value > 0);

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
            <span className="text-xs text-tertiary">Last 12 months, excluding cancelled and refunded orders</span>
          </div>
          <div className="card-body">
            {barData.length === 0 ? (
              <div className="text-sm text-tertiary" style={{ padding: '24px 0', textAlign: 'center' }}>Monthly figures are not available.</div>
            ) : (
            <div style={{ display: 'flex', alignItems: 'flex-end', gap: '8px', height: '200px', padding: '8px 0' }}>
              {barData.map((bar) => (
                <div key={bar.key} title={`${bar.label} ${bar.year}: ₹${bar.value.toLocaleString('en-IN')}`} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', height: '100%', justifyContent: 'flex-end' }}>
                  <span className="text-xs text-tertiary mb-1">{bar.value > 0 ? shortRupees(bar.value) : ''}</span>
                  <div style={{ width: '100%', maxWidth: '32px', height: bar.value > 0 ? `${(bar.value / maxBarValue) * 100}%` : '2px', background: bar.value > 0 ? 'linear-gradient(to top, #f59e0b, #d97706)' : '#e5e7eb', borderRadius: '4px 4px 0 0', transition: 'height 0.3s', minHeight: '2px' }} />
                  <span className="text-xs text-tertiary mt-1">{bar.label}</span>
                </div>
              ))}
            </div>
            )}
            {barData.length > 0 && !hasSales && (
              <div className="text-xs text-tertiary" style={{ textAlign: 'center' }}>No sales in the last 12 months yet.</div>
            )}
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
            <div className="empty-state-icon"><Mail size={32} /></div>
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
