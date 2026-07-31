import React, { useState, useRef, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import CartDrawer from "./CartDrawer";

const CATEGORIES = ["Electronics", "Fashion", "Home & Kitchen", "Grocery", "Beauty", "Appliances", "Books", "Sports", "Toys", "Deals"];

const Navbar = () => {
  const navigate = useNavigate();
  const token = !!localStorage.getItem("accessToken");
  const userRole = localStorage.getItem("role") || "";
  const [search, setSearch] = useState("");
  const [mobileOpen, setMobileOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const [cartCount, setCartCount] = useState(0);
  const [mobileSearchOpen, setMobileSearchOpen] = useState(false);
  const [cartDrawerOpen, setCartDrawerOpen] = useState(false);
  const searchRef = useRef(null);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 40);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    const refreshCartCount = () => {
      import("../services/cartService").then(({ getCart }) => {
        getCart().then(res => {
          if (!cancelled) {
            const data = res.data;
            setCartCount(data?.totalItems ?? (Array.isArray(data) ? data.length : 0));
          }
        }).catch(() => {});
      });
    };
    refreshCartCount();
    window.addEventListener("cart:updated", refreshCartCount);
    return () => { cancelled = true; window.removeEventListener("cart:updated", refreshCartCount); };
  }, [token]);

  const handleSearch = (e) => {
    e.preventDefault();
    if (search.trim()) navigate(`/search?q=${encodeURIComponent(search.trim())}`);
  };

  return (
    <>
      {/* Utility Bar */}
      <div className="sn-utility-bar" style={{
        background: "var(--green-800, #166534)", color: "#fff", fontSize: "0.75rem",
        padding: "0.35rem 1.5rem", display: "flex", justifyContent: "space-between",
        alignItems: "center", position: "sticky", top: 0, zIndex: 101
      }}>
        <div style={{ display: "flex", gap: "1.5rem", alignItems: "center" }}>
          <span className="sn-lang">| &#127470;&#127475; EN</span>
        </div>
        <div style={{ display: "flex", gap: "1.25rem", alignItems: "center" }}>
          <Link to="/contact" style={{ color: "#fff", textDecoration: "none", opacity: 0.85 }}>Customer Support</Link>
          {["SELLER", "ADMIN", "SUPER_ADMIN"].includes(userRole) ? (
            <Link to="/seller/dashboard" style={{ color: "#86efac", textDecoration: "none", opacity: 0.85 }}>Seller Dashboard</Link>
          ) : (
            <Link to="/seller/register" style={{ color: "#fff", textDecoration: "none", opacity: 0.85 }}>Become a Seller</Link>
          )}
          <Link to="/orders" style={{ color: "#fff", textDecoration: "none", opacity: 0.85 }}>Orders</Link>
          {token ? (
            <>
              <Link to="/profile" style={{ color: "#fff", textDecoration: "none", opacity: 0.85 }}>My Account</Link>
              <Link to="/logout" style={{ color: "#fca5a5", textDecoration: "none", opacity: 0.85 }}>Logout</Link>
            </>
          ) : (
            <Link to="/login" style={{ color: "#fff", textDecoration: "none", opacity: 0.85 }}>Sign In</Link>
          )}
        </div>
      </div>

      {/* Main Header */}
      <header className={`sn-header ${scrolled ? "scrolled" : ""} ${mobileSearchOpen ? "mobile-search-open" : ""}`} style={{
        background: "#fff", borderBottom: "1px solid var(--color-border, #e2e8f0)",
        position: "sticky", top: "28px", zIndex: 100, transition: "box-shadow 0.2s",
        boxShadow: scrolled ? "0 2px 8px rgba(0,0,0,0.06)" : "none"
      }}>
        <div style={{
          maxWidth: "var(--container-max, 1200px)", margin: "0 auto",
          display: "flex", alignItems: "center", justifyContent: "space-between",
          padding: "0.6rem 1.5rem", gap: "1rem"
        }}>
          {/* Hamburger */}
          <button onClick={() => setMobileOpen(!mobileOpen)} style={{
            display: "none", background: "none", border: "none", cursor: "pointer",
            flexDirection: "column", gap: "4px", padding: "4px"
          }} className="sn-hamburger" aria-label="Menu">
            <span style={{ display: "block", width: 20, height: 2, background: "var(--gray-700)", borderRadius: 1 }} />
            <span style={{ display: "block", width: 20, height: 2, background: "var(--gray-700)", borderRadius: 1 }} />
            <span style={{ display: "block", width: 20, height: 2, background: "var(--gray-700)", borderRadius: 1 }} />
          </button>

          {/* Logo */}
          <Link to="/" style={{ display: "flex", alignItems: "center", textDecoration: "none", gap: "8px" }}>
            <img src="/images/logo.jpg" alt="" style={{ height: "2rem", width: "auto" }} />
            <span style={{ fontSize: "1.4rem", fontWeight: 800, color: "var(--color-primary, #16a34a)", fontFamily: "var(--font-heading, Inter)" }}>Cauvery Store</span>
          </Link>

          {/* Search Bar */}
          <form onSubmit={handleSearch} style={{
            flex: 1, maxWidth: "520px", display: "flex",
            borderRadius: "8px", overflow: "hidden",
            border: "2px solid var(--color-primary, #16a34a)"
          }}>
            <input
              ref={searchRef}
              type="text"
              placeholder="Search for products, brands and more..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              style={{
                flex: 1, padding: "0.55rem 0.75rem", border: "none",
                fontSize: "0.85rem", outline: "none", background: "#fff"
              }}
            />
            <button type="submit" aria-label="Search" style={{
              padding: "0.55rem 1rem", background: "var(--color-primary, #16a34a)",
              color: "#fff", border: "none", cursor: "pointer", display: "flex",
              alignItems: "center", justifyContent: "center"
            }}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>
              </svg>
            </button>
          </form>

          {/* Header Actions */}
          <div className="sn-header-actions" style={{ display: "flex", alignItems: "center", gap: "0.75rem" }}>
            <button className="sn-mobile-search-toggle" onClick={() => { setMobileSearchOpen(!mobileSearchOpen); if (!mobileSearchOpen) setTimeout(() => searchRef.current?.focus(), 100); }} title="Search" style={{
              display: "none", background: "none", border: "none", cursor: "pointer", padding: "0.25rem 0.4rem"
            }}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--gray-700)" strokeWidth="2">
                <circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>
              </svg>
            </button>
            <button onClick={() => navigate(token ? "/profile" : "/login")} title="Account" style={{
              background: "none", border: "none", cursor: "pointer", display: "flex",
              flexDirection: "column", alignItems: "center", gap: "2px", padding: "0.25rem 0.4rem"
            }}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--gray-700)" strokeWidth="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
              </svg>
              <span style={{ fontSize: "0.65rem", color: "var(--gray-600)", fontWeight: 500 }}>
                {token ? "Account" : "Sign In"}
              </span>
            </button>
            <button onClick={() => navigate("/wishlist")} title="Wishlist" style={{
              background: "none", border: "none", cursor: "pointer", display: "flex",
              flexDirection: "column", alignItems: "center", gap: "2px", padding: "0.25rem 0.4rem"
            }}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--gray-700)" strokeWidth="2">
                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
              </svg>
              <span style={{ fontSize: "0.65rem", color: "var(--gray-600)", fontWeight: 500 }}>Wishlist</span>
            </button>
            <button onClick={() => setCartDrawerOpen(true)} title="Cart" style={{
              background: "none", border: "none", cursor: "pointer", display: "flex",
              flexDirection: "column", alignItems: "center", gap: "2px", padding: "0.25rem 0.4rem", position: "relative"
            }}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--gray-700)" strokeWidth="2">
                <circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/>
                <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"/>
              </svg>
              <span style={{ fontSize: "0.65rem", color: "var(--gray-600)", fontWeight: 500 }}>Cart</span>
              {cartCount > 0 && (
                <span style={{
                  position: "absolute", top: 0, right: 0,
                  background: "var(--color-primary, #16a34a)", color: "#fff",
                  fontSize: "0.6rem", fontWeight: 700, minWidth: 16, height: 16,
                  borderRadius: "50%", display: "flex", alignItems: "center", justifyContent: "center",
                  lineHeight: 1
                }}>{cartCount}</span>
              )}
            </button>
          </div>
        </div>
      </header>

      {mobileSearchOpen && (
        <div className="sn-mobile-search-overlay">
          <form onSubmit={(e) => { handleSearch(e); setMobileSearchOpen(false); }} className="sn-mobile-search-form">
            <input ref={searchRef} type="text" placeholder="Search products..." value={search} onChange={(e) => setSearch(e.target.value)} className="sn-mobile-search-input" />
            <button type="submit" className="sn-mobile-search-submit" aria-label="Search">Go</button>
          </form>
          <button type="button" className="sn-mobile-search-close" onClick={() => setMobileSearchOpen(false)} aria-label="Close search">&times;</button>
        </div>
      )}

      {/* Category Navigation */}
      <nav style={{
        background: "#fff", borderBottom: "1px solid var(--color-border, #e2e8f0)",
        display: "flex", justifyContent: "center", overflowX: "auto",
        position: "sticky", top: "73px", zIndex: 99
      }}>
        <div style={{
          maxWidth: "var(--container-max, 1200px)", display: "flex",
          gap: "0.25rem", padding: "0 1rem", width: "100%"
        }}>
          {CATEGORIES.map((cat) => (
            <Link key={cat} to={`/category/${cat}`} style={{
              padding: "0.55rem 0.7rem", fontSize: "0.82rem", fontWeight: 500,
              color: "var(--gray-600)", textDecoration: "none", whiteSpace: "nowrap",
              display: "flex", alignItems: "center", gap: "2px",
              transition: "color 0.15s", borderBottom: "2px solid transparent"
            }}
            onMouseEnter={(e) => { e.target.style.color = "var(--color-primary)"; e.target.style.borderBottomColor = "var(--color-primary)"; }}
            onMouseLeave={(e) => { e.target.style.color = ""; e.target.style.borderBottomColor = "transparent"; }}
            >
              {cat}
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <path d="M6 9l6 6 6-6"/>
              </svg>
            </Link>
          ))}
        </div>
      </nav>

      {/* Mobile Menu Overlay */}
      {mobileOpen && (
        <div style={{
          position: "fixed", inset: 0, background: "rgba(0,0,0,0.4)", zIndex: 200,
          display: "flex"
        }} onClick={() => setMobileOpen(false)}>
          <div style={{
            width: "280px", background: "#fff", height: "100%", overflowY: "auto",
            padding: "1rem", boxShadow: "2px 0 12px rgba(0,0,0,0.1)"
          }} onClick={(e) => e.stopPropagation()}>
            <div style={{
              display: "flex", justifyContent: "space-between", alignItems: "center",
              marginBottom: "1rem", paddingBottom: "0.75rem", borderBottom: "1px solid var(--color-border)"
            }}>
              <span style={{ fontWeight: 600, fontSize: "0.95rem" }}>
                {token ? "Hello, User" : "Welcome"}
              </span>
              <button onClick={() => setMobileOpen(false)} style={{
                background: "none", border: "none", cursor: "pointer", fontSize: "1.2rem"
              }}>&times;</button>
            </div>
            {CATEGORIES.map((cat) => (
              <Link key={cat} to={`/category/${cat}`} onClick={() => setMobileOpen(false)} style={{
                display: "block", padding: "0.6rem 0.5rem", fontSize: "0.9rem",
                color: "var(--gray-700)", textDecoration: "none", borderBottom: "1px solid var(--color-border-light)"
              }}>{cat}</Link>
            ))}
            <div style={{ marginTop: "1rem", paddingTop: "0.75rem", borderTop: "1px solid var(--color-border)" }}>
              {token ? (
                <Link to="/profile" onClick={() => setMobileOpen(false)} style={{
                  display: "block", padding: "0.6rem 0.5rem", fontSize: "0.9rem",
                  color: "var(--color-primary)", textDecoration: "none", fontWeight: 600
                }}>My Account</Link>
              ) : (
                <Link to="/login" onClick={() => setMobileOpen(false)} style={{
                  display: "block", padding: "0.6rem 0.5rem", fontSize: "0.9rem",
                  color: "var(--color-primary)", textDecoration: "none", fontWeight: 600
                }}>Sign In</Link>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Mobile bottom bar */}
      <div style={{
        display: "none", position: "fixed", bottom: 0, left: 0, right: 0,
        background: "#fff", borderTop: "1px solid var(--color-border, #e2e8f0)",
        zIndex: 99, padding: "0.35rem 0"
      }} className="sn-mobile-bottom-bar">
        <div style={{ display: "flex", justifyContent: "space-around", alignItems: "center" }}>
          <button onClick={() => navigate("/")} style={bottomBtnStyle}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--gray-600)" strokeWidth="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg>
            <span>Home</span>
          </button>
          <button onClick={() => navigate("/products")} style={bottomBtnStyle}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--gray-600)" strokeWidth="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>
            <span>Shop</span>
          </button>
          <button onClick={() => setCartDrawerOpen(true)} style={bottomBtnStyle}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--gray-600)" strokeWidth="2"><circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"/></svg>
            <span>Cart</span>
          </button>
          <button onClick={() => navigate(token ? "/wishlist" : "/login")} style={bottomBtnStyle}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--gray-600)" strokeWidth="2"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
            <span>Wishlist</span>
          </button>
          <button onClick={() => navigate(token ? "/profile" : "/login")} style={bottomBtnStyle}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--gray-600)" strokeWidth="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            <span>Profile</span>
          </button>
        </div>
      </div>

      <CartDrawer open={cartDrawerOpen} onClose={() => setCartDrawerOpen(false)} />

      <style>{`
        @media (max-width: 768px) {
          .sn-hamburger { display: flex !important; }
          .sn-utility-bar > div:last-child { display: none; }
          .sn-header > div > form { display: none; }
          .sn-header > div > nav { display: none; }
          .sn-mobile-bottom-bar { display: block !important; }
          .sn-header > div { padding: 0.6rem 1rem; }
          .sn-mobile-search-toggle { display: flex !important; }
        }
        .sn-mobile-search-overlay {
          position: fixed; top: 0; left: 0; right: 0; z-index: 150;
          display: flex; align-items: center; gap: 0.5rem;
          background: #fff; padding: 0.6rem 1rem;
          box-shadow: 0 2px 8px rgba(0,0,0,0.08);
          border-bottom: 1px solid var(--color-border, #e2e8f0);
        }
        .sn-mobile-search-form {
          flex: 1; display: flex; border-radius: 8px; overflow: hidden;
          border: 2px solid var(--color-primary, #16a34a);
        }
        .sn-mobile-search-input {
          flex: 1; padding: 0.55rem 0.75rem; border: none;
          font-size: 0.85rem; outline: none;
        }
        .sn-mobile-search-submit {
          padding: 0.55rem 1rem; background: var(--color-primary, #16a34a);
          color: #fff; border: none; font-weight: 600; font-size: 0.85rem; cursor: pointer;
        }
        .sn-mobile-search-close {
          background: none; border: none; font-size: 1.5rem; line-height: 1;
          color: var(--gray-600, #64748b); cursor: pointer; padding: 0 0.25rem;
        }
      `}</style>
    </>
  );
};

const bottomBtnStyle = {
  display: "flex", flexDirection: "column", alignItems: "center", gap: "2px",
  background: "none", border: "none", cursor: "pointer", padding: "0.25rem 0.5rem",
  fontSize: "0.6rem", color: "var(--gray-600)", fontWeight: 500
};

export default Navbar;
