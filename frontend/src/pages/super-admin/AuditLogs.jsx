import React, { useState, useEffect } from 'react';
import { FileText } from 'lucide-react';
import api from '../../api/axios';

const ACTION_TYPES = [
  { value: 'ALL', label: 'All Actions' },
  { value: 'LOGIN', label: 'Login' },
  { value: 'LOGOUT', label: 'Logout' },
  { value: 'CREATE', label: 'Create' },
  { value: 'UPDATE', label: 'Update' },
  { value: 'DELETE', label: 'Delete' },
  { value: 'BLOCK', label: 'Block' },
  { value: 'IMPERSONATE', label: 'Impersonate' },
  { value: 'SETTINGS', label: 'Settings' },
  { value: 'PAYMENT', label: 'Payment' },
];

const PAGE_SIZES = [10, 25, 50, 100];

const AuditLogs = () => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [filterAction, setFilterAction] = useState('ALL');
  const [filterUser, setFilterUser] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [expandedRow, setExpandedRow] = useState(null);

  const fetchLogs = async (p = page) => {
    setLoading(true);
    setError('');
    try {
      const params = { page: p - 1, size: pageSize, action: filterAction !== 'ALL' ? filterAction : undefined, performedBy: filterUser || undefined, dateFrom: dateFrom || undefined, dateTo: dateTo || undefined };
      const res = await api.get('/api/super-admin/activity-log', { params });
      const data = res.data;
      setLogs(Array.isArray(data) ? data : data.logs || data.data || data.activities || []);
      setTotalPages(data.totalPages || data.pages || 1);
      setTotal(data.total || (Array.isArray(data) ? data.length : 0));
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load audit logs');
      setLogs([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchLogs(1); setPage(1); }, [filterAction, filterUser, dateFrom, dateTo, pageSize]);

  useEffect(() => { fetchLogs(page); }, [page]);

  const handleFilter = () => fetchLogs(1);

  const getActionBadge = (action) => {
    const a = (action || '').toUpperCase();
    if (['LOGIN', 'LOGOUT', 'CREATE'].includes(a)) return 'active';
    if (['DELETE', 'BLOCK', 'SUSPEND'].includes(a)) return 'inactive';
    return 'pending';
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700 }}>Audit Logs</h1>
      </div>

      {error && <div style={{ background: '#fef2f2', color: '#dc2626', padding: '8px 16px', borderRadius: '8px', marginBottom: '16px', fontSize: '0.85rem' }}>{error}</div>}

      <div className="admin-table-wrapper">
        <div className="admin-filter-bar">
          <select className="admin-filter-select" value={filterAction} onChange={(e) => setFilterAction(e.target.value)}>
            {ACTION_TYPES.map(a => <option key={a.value} value={a.value}>{a.label}</option>)}
          </select>
          <input
            className="admin-table-search"
            style={{ minWidth: '160px' }}
            placeholder="Performed by..."
            value={filterUser}
            onChange={(e) => setFilterUser(e.target.value)}
          />
          <input type="date" className="admin-filter-date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} title="From date" />
          <input type="date" className="admin-filter-date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} title="To date" />
          <button className="admin-btn admin-btn-primary admin-btn-sm" onClick={handleFilter}>Apply Filters</button>
          <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '0.8rem', color: '#6b7280' }}>Show:</span>
            <select className="admin-filter-select" value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setPage(1); }}>
              {PAGE_SIZES.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>
        </div>

        {loading ? (
          <div style={{ padding: '24px' }}>
            {[1,2,3,4,5,6,7,8].map(i => <div key={i} className="admin-skeleton-table-row" />)}
          </div>
        ) : logs.length === 0 ? (
          <div className="admin-empty-state">
            <div className="admin-empty-state-icon"><FileText size={32} /></div>
            <div className="admin-empty-state-text">No audit logs found matching your filters</div>
          </div>
        ) : (
          <table className="admin-table">
            <thead>
              <tr>
                <th style={{ width: '180px' }}>Timestamp</th>
                <th style={{ width: '120px' }}>Action</th>
                <th>Performed By</th>
                <th>Target</th>
                <th>Details</th>
                <th style={{ width: '140px' }}>IP Address</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log, idx) => {
                const logId = log._id || log.id || idx;
                const isExpanded = expandedRow === logId;
                return (
                  <React.Fragment key={logId}>
                    <tr onClick={() => setExpandedRow(isExpanded ? null : logId)} style={{ cursor: 'pointer' }}>
                      <td style={{ whiteSpace: 'nowrap', fontSize: '0.8rem', color: '#6b7280' }}>
                        {log.timestamp ? new Date(log.timestamp).toLocaleString() : log.createdAt ? new Date(log.createdAt).toLocaleString() : '-'}
                      </td>
                      <td>
                        <span className={`admin-badge ${getActionBadge(log.action || log.type)}`}>
                          {log.action || log.type || '-'}
                        </span>
                      </td>
                      <td style={{ fontWeight: 500 }}>
                        {log.performedBy?.fullName || log.performedBy?.name || log.performedBy?.email || log.user?.fullName || log.user?.name || log.user?.email || '-'}
                      </td>
                      <td style={{ color: '#6b7280' }}>
                        {log.target?.fullName || log.target?.name || log.target?.email || log.target?.id || log.targetId || '-'}
                      </td>
                      <td style={{ color: '#6b7280', maxWidth: '250px', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {log.details || log.description || log.message || '-'}
                      </td>
                      <td style={{ fontSize: '0.8rem', color: '#6b7280', fontFamily: 'monospace' }}>
                        {log.ipAddress || log.ip || '-'}
                      </td>
                    </tr>
                    {isExpanded && (
                      <tr>
                        <td colSpan={6} style={{ padding: '16px 20px', background: '#f8fafc' }}>
                          <div style={{ fontSize: '0.85rem' }}>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                              <div><strong>Action:</strong> {log.action || log.type || '-'}</div>
                              <div><strong>Timestamp:</strong> {log.timestamp ? new Date(log.timestamp).toLocaleString() : '-'}</div>
                              <div><strong>Performed By:</strong> {log.performedBy?.fullName || log.performedBy?.name || log.performedBy?.email || '-'} ({log.performedBy?.role || '-'})</div>
                              <div><strong>Target:</strong> {log.target?.fullName || log.target?.name || log.target?.email || log.target?.id || '-'}</div>
                              <div><strong>IP Address:</strong> {log.ipAddress || log.ip || '-'}</div>
                              <div><strong>User Agent:</strong> {log.userAgent || log.browser || '-'}</div>
                            </div>
                            {log.details && typeof log.details === 'object' ? (
                              <div style={{ marginTop: '12px' }}>
                                <strong>Full Details:</strong>
                                <pre style={{ background: '#f1f5f9', padding: '12px', borderRadius: '6px', fontSize: '0.8rem', overflowX: 'auto', maxHeight: '200px', marginTop: '4px' }}>
                                  {JSON.stringify(log.details, null, 2)}
                                </pre>
                              </div>
                            ) : log.details ? (
                              <div style={{ marginTop: '12px' }}>
                                <strong>Details:</strong> {log.details}
                              </div>
                            ) : null}
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        )}

        <div className="admin-pagination">
          <div className="admin-pagination-info">
            Page {page} of {totalPages || 1} ({total} entries)
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
    </div>
  );
};

export default AuditLogs;
