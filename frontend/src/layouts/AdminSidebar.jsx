import React, { useEffect, useMemo, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { ChevronDown, ChevronRight, Search, X } from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { canAccessModule } from "../utils/rolePermissions";
import useAltText from "../hooks/useAltText";
import { HOME_LINK, sectionForPath, visibleSections } from "./adminNav";

const HomeIcon = HOME_LINK.icon;

const OPEN_SECTIONS_KEY = "admin_nav_open_sections";

const readOpenSections = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(OPEN_SECTIONS_KEY) || "[]");
    return Array.isArray(saved) ? saved : [];
  } catch {
    return [];
  }
};

const saveOpenSections = (ids) => {
  try { localStorage.setItem(OPEN_SECTIONS_KEY, JSON.stringify(ids)); } catch { /* storage unavailable */ }
};

const linkStyle = ({ isActive }) => ({
  display: "flex", alignItems: "center", gap: "12px", padding: "8px 20px 8px 28px",
  color: isActive ? "#fff" : "#94a3b8", textDecoration: "none", fontSize: "0.85rem",
  background: isActive ? "#1e293b" : "transparent",
  borderLeft: isActive ? "3px solid var(--color-primary, #00788C)" : "3px solid transparent",
  transition: "all 0.15s",
});

const AdminSidebar = ({ open, onNavigate }) => {
  const altText = useAltText();
  const { role } = useAuth();
  const { pathname } = useLocation();
  const [query, setQuery] = useState("");
  const [openIds, setOpenIds] = useState(readOpenSections);

  const canAccess = (module) => canAccessModule(role, module);
  const sections = useMemo(() => visibleSections(canAccess, query), [role, query]);
  const showHome = !query.trim() || HOME_LINK.label.toLowerCase().includes(query.trim().toLowerCase());
  const searching = query.trim().length > 0;
  const labelText = role === "EXECUTIVE" ? "Executive Panel" : role === "SUPER_ADMIN" ? "Super Admin" : "Admin Panel";

  // The section holding the page being viewed always opens.
  useEffect(() => {
    const current = sectionForPath(pathname);
    if (current && !openIds.includes(current)) {
      const next = [...openIds, current];
      setOpenIds(next);
      saveOpenSections(next);
    }
  }, [pathname]);

  const toggle = (id) => {
    const next = openIds.includes(id) ? openIds.filter((x) => x !== id) : [...openIds, id];
    setOpenIds(next);
    saveOpenSections(next);
  };

  const handleNavigate = () => {
    setQuery("");
    if (onNavigate) onNavigate();
  };

  return (
  <aside className={`cd-admin-sidebar${open ? " cd-open" : ""}`} style={{ position: "fixed", top: 0, left: 0, width: "240px", height: "100vh", background: "#0f172a", color: "#fff", display: "flex", flexDirection: "column", zIndex: 100 }}>
    <div style={{ padding: "20px 20px 12px", borderBottom: "1px solid #1e293b" }}>
      <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
        <img src="/images/logo.jpg" alt={altText.logo()} style={{ height: "1.15rem", width: "auto" }} />
        <div>
          <div style={{ fontSize: "1.15rem", fontWeight: 700, color: "var(--color-primary, #00788C)" }}>Cauvery Store</div>
          <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "1px" }}>{labelText}</div>
        </div>
      </div>
      <div style={{ position: "relative", marginTop: "12px" }}>
        <Search size={14} aria-hidden="true" style={{ position: "absolute", left: "9px", top: "50%", transform: "translateY(-50%)", color: "#64748b" }} />
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => { if (e.key === "Escape") setQuery(""); }}
          placeholder="Find a page"
          aria-label="Find an admin page"
          style={{ width: "100%", boxSizing: "border-box", padding: "7px 28px 7px 28px", background: "#1e293b", border: "1px solid #334155", borderRadius: "6px", color: "#e2e8f0", fontSize: "0.8rem" }}
        />
        {searching && (
          <button type="button" onClick={() => setQuery("")} aria-label="Clear search" style={{ position: "absolute", right: "6px", top: "50%", transform: "translateY(-50%)", background: "none", border: "none", color: "#94a3b8", cursor: "pointer", padding: "2px", display: "flex" }}>
            <X size={14} />
          </button>
        )}
      </div>
    </div>
    <nav aria-label="Admin pages" style={{ flex: 1, overflowY: "auto", padding: "8px 0" }}>
      {showHome && (
        <NavLink to={HOME_LINK.to} end onClick={handleNavigate} style={(s) => ({ ...linkStyle(s), paddingLeft: "20px" })}>
          <HomeIcon size={18} />
          <span>{HOME_LINK.label}</span>
        </NavLink>
      )}
      {sections.map((section) => {
        const expanded = searching || openIds.includes(section.id);
        const panelId = `admin-nav-${section.id}`;
        return (
          <div key={section.id} style={{ marginTop: "4px" }}>
            <button
              type="button"
              onClick={() => toggle(section.id)}
              aria-expanded={expanded}
              aria-controls={panelId}
              disabled={searching}
              style={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%", padding: "8px 20px", background: "none", border: "none", color: "#cbd5e1", fontSize: "0.72rem", fontWeight: 700, letterSpacing: "0.06em", textTransform: "uppercase", cursor: searching ? "default" : "pointer" }}
            >
              <span>{section.label}</span>
              {!searching && (expanded ? <ChevronDown size={14} aria-hidden="true" /> : <ChevronRight size={14} aria-hidden="true" />)}
            </button>
            {expanded && (
              <div id={panelId}>
                {section.links.map((l) => (
                  <NavLink key={l.to} to={l.to} end={l.end} onClick={handleNavigate} style={linkStyle}>
                    <l.icon size={16} />
                    <span>{l.label}</span>
                  </NavLink>
                ))}
              </div>
            )}
          </div>
        );
      })}
      {searching && sections.length === 0 && !showHome && (
        <div style={{ padding: "12px 20px", fontSize: "0.8rem", color: "#64748b" }}>No page matches "{query.trim()}".</div>
      )}
    </nav>
    <div style={{ padding: "12px 20px", borderTop: "1px solid #1e293b", fontSize: "0.75rem", color: "#475569" }}>
      v1.0.0
    </div>
  </aside>
  );
};

export default AdminSidebar;
