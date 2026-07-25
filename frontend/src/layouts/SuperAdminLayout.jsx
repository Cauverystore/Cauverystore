import React, { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { LayoutDashboard, Users, Key, Settings, Eye, FileText, ArrowLeft } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import '../styles/admin.css';
import { ToastProvider } from '../admin/context/ToastContext';

const NAV_COLOR = '#0f172a';
const ACCENT = '#f59e0b';
const menuItems = [
  { to: '/super-admin/dashboard', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/super-admin/users', label: 'User Management', icon: Users },
  { to: '/super-admin/permissions', label: 'Permissions', icon: Key },
  { to: '/super-admin/settings', label: 'Platform Settings', icon: Settings },
  { to: '/super-admin/impersonate', label: 'Impersonation', icon: Eye },
  { to: '/super-admin/audit-logs', label: 'Audit Logs', icon: FileText },
];

const bottomItems = [
  { to: '/', label: 'Back to Store', icon: ArrowLeft },
];

const SuperAdminLayout = () => {
  const { user, role, logout } = useAuth();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);

  const handleLogout = async () => {
    await logout();
    navigate('/admin/login');
  };

  const sidebarWidth = collapsed ? '64px' : '260px';

  const linkStyle = ({ isActive }) => ({
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    padding: '10px 20px',
    color: isActive ? '#fff' : '#94a3b8',
    textDecoration: 'none',
    fontSize: '0.875rem',
    background: isActive ? '#1e293b' : 'transparent',
    borderLeft: isActive ? `3px solid ${ACCENT}` : '3px solid transparent',
    transition: 'all 0.15s',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
  });

  return (
    <ToastProvider>
    <div style={{ display: 'flex', minHeight: '100vh', background: '#f1f5f9' }}>
      <aside
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          width: sidebarWidth,
          height: '100vh',
          background: NAV_COLOR,
          color: '#fff',
          display: 'flex',
          flexDirection: 'column',
          zIndex: 100,
          transition: 'width 0.2s',
        }}
      >
        <div
          style={{
            padding: collapsed ? '20px 12px' : '20px 20px 16px',
            borderBottom: '1px solid #1e293b',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <div
            style={{
              width: '32px',
              height: '32px',
              borderRadius: '8px',
              background: ACCENT,
              color: '#fff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 800,
              fontSize: '1rem',
              flexShrink: 0,
            }}
          >
            S
          </div>
          {!collapsed && (
            <div>
              <div style={{ fontSize: '1rem', fontWeight: 700, color: '#fff' }}>Cauvery Store</div>
              <div style={{ fontSize: '0.7rem', color: ACCENT, marginTop: '1px' }}>Super Admin</div>
            </div>
          )}
        </div>

        <nav style={{ flex: 1, overflowY: 'auto', padding: '8px 0' }}>
          {menuItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              style={linkStyle}
              title={collapsed ? item.label : undefined}
            >
              <item.icon size={20} />
              {!collapsed && <span>{item.label}</span>}
            </NavLink>
          ))}

          <div style={{ marginTop: '16px', borderTop: '1px solid #1e293b', paddingTop: '8px' }}>
            {bottomItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                style={linkStyle}
                title={collapsed ? item.label : undefined}
              >
                <item.icon size={20} />
                {!collapsed && <span>{item.label}</span>}
              </NavLink>
            ))}
          </div>
        </nav>

        <div
          style={{
            padding: collapsed ? '12px' : '12px 16px',
            borderTop: '1px solid #1e293b',
          }}
        >
          {collapsed ? (
            <div
              style={{
                width: '36px',
                height: '36px',
                borderRadius: '50%',
                background: '#1e293b',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '0.85rem',
                color: ACCENT,
                margin: '0 auto',
              }}
            >
              {(user?.fullName || user?.name || 'SA').charAt(0).toUpperCase()}
            </div>
          ) : (
            <>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '10px' }}>
                <div
                  style={{
                    width: '36px',
                    height: '36px',
                    borderRadius: '50%',
                    background: '#1e293b',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '0.85rem',
                    color: ACCENT,
                    fontWeight: 600,
                    flexShrink: 0,
                  }}
                >
                  {(user?.fullName || user?.name || 'SA').charAt(0).toUpperCase()}
                </div>
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div style={{ fontSize: '0.8rem', fontWeight: 600, color: '#e2e8f0', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {user?.fullName || user?.name || 'Super Admin'}
                  </div>
                  <div style={{ fontSize: '0.7rem', color: ACCENT }}>{role}</div>
                </div>
              </div>
              <button
                onClick={handleLogout}
                style={{
                  width: '100%',
                  padding: '8px 12px',
                  background: 'transparent',
                  border: '1px solid #334155',
                  borderRadius: '8px',
                  color: '#94a3b8',
                  fontSize: '0.8rem',
                  cursor: 'pointer',
                  transition: 'all 0.15s',
                }}
                onMouseEnter={(e) => { e.target.style.background = '#1e293b'; e.target.style.color = '#ef4444'; }}
                onMouseLeave={(e) => { e.target.style.background = 'transparent'; e.target.style.color = '#94a3b8'; }}
              >
                Logout
              </button>
            </>
          )}
        </div>

        <button
          className="sa-sidebar-arrow"
          onClick={() => setCollapsed(!collapsed)}
        >
          {collapsed ? '→' : '←'}
        </button>
      </aside>

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', marginLeft: sidebarWidth, transition: 'margin-left 0.2s' }}>
        <header
          style={{
            position: 'sticky',
            top: 0,
            zIndex: 99,
            background: '#fff',
            borderBottom: `2px solid ${ACCENT}`,
            padding: '12px 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span style={{ fontSize: '1.1rem', fontWeight: 700, color: '#0f172a' }}>Super Admin Panel</span>
            <span style={{ fontSize: '0.7rem', color: '#94a3b8', background: '#f1f5f9', padding: '2px 8px', borderRadius: '4px' }}>
              {role}
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
              {user?.email || ''}
            </span>
          </div>
        </header>

        <main style={{ padding: '24px', flex: 1 }}>
          <Outlet />
        </main>
      </div>
    </div>
    </ToastProvider>
  );
};

export default SuperAdminLayout;
