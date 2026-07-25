import React, { useState, useEffect } from "react";
import api from "../../utils/axios";

const ROLE_LIST = ["CUSTOMER", "SELLER", "EXECUTIVE", "ADMIN", "SUPER_ADMIN"];

const PermissionsManager = () => {
  const [permissions, setPermissions] = useState([]);
  const [rolePerms, setRolePerms] = useState({});
  const [loading, setLoading] = useState(true);
  const [selectedRole, setSelectedRole] = useState("ADMIN");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    Promise.all([
      api.get("/api/super-admin/permissions"),
      api.get(`/api/super-admin/role-permissions?role=${selectedRole}`)
    ])
      .then(([p, rp]) => {
        setPermissions(Array.isArray(p.data) ? p.data : []);
        const assigned = new Set(rp.data?.permissionIds || []);
        setRolePerms(assigned);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [selectedRole]);

  const toggle = (permId) => {
    setRolePerms(prev => {
      const next = new Set(prev);
      next.has(permId) ? next.delete(permId) : next.add(permId);
      return next;
    });
  };

  const save = async () => {
    setSaving(true);
    try {
      await api.put(`/api/super-admin/role-permissions`, {
        role: selectedRole,
        permissionIds: Array.from(rolePerms)
      });
    } catch {}
    setSaving(false);
  };

  if (loading) return <div className="page-loader"><div>Loading permissions...</div></div>;

  return (
    <div>
      <h1 style={{fontSize:"1.5rem",fontWeight:700,marginBottom:"1.5rem"}}>Permission Management</h1>

      <div className="admin-filter-bar" style={{borderRadius:"8px",marginBottom:"1rem"}}>
        {ROLE_LIST.map(r => (
          <button key={r} onClick={() => setSelectedRole(r)}
            className={`admin-btn ${selectedRole === r ? 'admin-btn-primary' : 'admin-btn-secondary'} admin-btn-sm`}>
            {r.replace("_"," ")}
          </button>
        ))}
      </div>

      <div className="admin-table-wrapper">
        <table className="admin-table">
          <thead>
            <tr>
              <th style={{width:50}}></th>
              <th>Permission</th>
              <th>Resource</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {permissions.length === 0 ? (
              <tr><td colSpan={4}><div className="admin-empty-state"><div className="admin-empty-state-icon">🔑</div><div className="admin-empty-state-text">No permissions defined</div></div></td></tr>
            ) : permissions.map(p => (
              <tr key={p.id} style={{background:rolePerms.has(p.id)?"#f0fdf4":"transparent"}}>
                <td><input type="checkbox" className="admin-table-checkbox" checked={rolePerms.has(p.id)} onChange={() => toggle(p.id)} /></td>
                <td style={{fontWeight:500}}>{p.name}</td>
                <td><span className="admin-badge pending">{p.resource}</span></td>
                <td><span className={`admin-badge ${p.action === 'READ' ? 'active' : p.action === 'DELETE' ? 'inactive' : 'pending'}`}>{p.action}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div style={{marginTop:"1rem"}}>
        <button onClick={save} disabled={saving} className="admin-btn admin-btn-primary">
          {saving ? "Saving..." : `Save ${selectedRole} Permissions`}
        </button>
      </div>
    </div>
  );
};

export default PermissionsManager;
