import React, { useState, useEffect, useCallback, useMemo } from "react";
import { useNavigate, Link } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { Flame, Star, Package, Store, MapPin, Zap, Shirt } from "lucide-react";
import api from "../api/axios";
import { addToCart } from "../services/cartService";
import ProductTray, { LoadingSkeleton } from "../components/ProductTray";
import "../styles/shopnest-home.css";

// Hero slides describe what the store really is. Add real offers here only once they exist.
const BANNERS = [
  { id: 1, title: "Shop direct from Tamil Nadu sellers", subtitle: "See who sells it and where they are. Pay by UPI or card.", cta: "Browse products", to: "/products", bg: "#0B3D2E", accent: "#1B7A45", color: "#7FFFD4", Icon: MapPin },
  { id: 2, title: "Sell on Cauvery Store", subtitle: "Reach customers across Tamil Nadu with your own seller page.", cta: "Become a seller", to: "/seller/register", bg: "#146C43", accent: "#1B7A45", color: "#7FFFD4", Icon: Store },
];

function normalizeProduct(p) {
  if (!p) return null;
  const img = p.images?.[0]?.url || p.image || "";
  const price = p.price || 0;
  const discount = p.discounts?.[0];
  let origPrice = price;
  let discPct = 0;
  if (discount?.active) {
    discPct = Math.round(discount.value);
    origPrice = Math.round(price / (1 - discPct / 100));
  }
  const rating = p.rating || (p.reviews?.length > 0 ? p.reviews.reduce((s,r)=>s+(r.rating||0),0)/p.reviews.length : 4.0);
  const reviewCount = p.reviews?.length || 0;
  return { ...p, image: img, price, originalPrice: origPrice, discount: discPct, rating, reviews: reviewCount, stock: p.stock ?? p.stockQuantity ?? (p.active ? 1 : 0) };
}

function getCategoryName(p) {
  if (!p.category) return "";
  return typeof p.category === "object" ? (p.category.name || "") : p.category;
}

const Home = () => {
  const navigate = useNavigate();
  const [allProducts, setAllProducts] = useState(null);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [bannerIdx, setBannerIdx] = useState(0);
  const [toast, setToast] = useState(null);

  useEffect(() => {
    Promise.all([api.get("/api/products"), api.get("/api/categories")])
      .then(([p, c]) => {
        setAllProducts(Array.isArray(p.data) ? p.data : []);
        setCategories(Array.isArray(c.data) ? c.data : []);
      })
      .catch(() => { setAllProducts([]); setCategories([]); })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const id = setInterval(() => setBannerIdx(i => (i + 1) % BANNERS.length), 4000);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    if (toast) {
      const id = setTimeout(() => setToast(null), 2500);
      return () => clearTimeout(id);
    }
  }, [toast]);

  const products = allProducts || [];
  const normalized = useMemo(() => products.map(normalizeProduct).filter(Boolean), [products]);

  const productsWithDiscount = useMemo(() => normalized.filter(p => p.discount > 0).slice(0, 6), [normalized]);
  const electronics = useMemo(() => normalized.filter(p => getCategoryName(p) === "Electronics").slice(0, 6), [normalized]);
  const fashion = useMemo(() => normalized.filter(p => getCategoryName(p) === "Fashion").slice(0, 4), [normalized]);

  const handleCart = useCallback(async (p) => {
    const isLoggedIn = !!localStorage.getItem("accessToken");
    if (!isLoggedIn) { navigate("/login"); return; }
    try {
      await addToCart(p.id, 1);
      setToast({ type: "success", text: `${p.name} added to cart!` });
    } catch {
      setToast({ type: "error", text: "Failed to add to cart" });
    }
  }, [navigate]);

  const handleBuyNow = useCallback(async (p) => {
    const isLoggedIn = !!localStorage.getItem("accessToken");
    if (!isLoggedIn) { navigate("/login"); return; }
    try {
      await addToCart(p.id, 1);
      navigate("/checkout");
    } catch {
      setToast({ type: "error", text: "Failed to process Buy Now" });
    }
  }, [navigate]);

  return (
    <div className="sn-page">
      <Helmet>
        <title>Cauvery Store | Shop Direct from Tamil Nadu Sellers</title>
        <meta
          name="description"
          content="Buy straight from Tamil Nadu makers and local shops. See who sells it and where they are, pay by UPI or card, and get it delivered to your door."
        />
        <link rel="canonical" href="https://cauverystore.in/" />
        <meta property="og:title" content="Cauvery Store | Shop Direct from Tamil Nadu Sellers" />
        <meta
          property="og:description"
          content="Buy straight from Tamil Nadu makers and local shops. See who sells it and where they are, pay by UPI or card, and get it delivered to your door."
        />
        <meta property="og:image" content="https://cauverystore.in/logo512.png" />
        <meta property="og:type" content="website" />
        <meta property="og:url" content="https://cauverystore.in/" />
      </Helmet>
      {toast && (
        <div className={`sn-toast sn-toast-${toast.type}`} role="status" aria-live="polite">
          {toast.text}
        </div>
      )}

      <section className="sn-hero">
        <div className="sn-container">
          <div className="sn-hero-carousel">
            <div className="sn-hero-carousel-inner">
              {BANNERS.map((b, i) => (
                <div key={b.id} className={`sn-hero-slide ${i === bannerIdx ? "active" : ""}`} style={{ backgroundColor: b.bg }}>
                  <div className="sn-hero-content">
                    <span className="sn-hero-icon"><b.Icon size={48} color={b.color} /></span>
                    <h2 style={{ color: b.color }}>{b.title}</h2>
                    <p style={{ color: b.color, opacity: 0.85 }}>{b.subtitle}</p>
                    <Link to={b.to} className="sn-hero-cta" style={{ backgroundColor: b.accent, display: "inline-block", textDecoration: "none" }}>{b.cta}</Link>
                  </div>
                  <div className="sn-hero-visual">
                    <div className="sn-hero-shape" />
                  </div>
                </div>
              ))}
            </div>
            <button className="sn-hero-prev" onClick={() => setBannerIdx(i => (i - 1 + BANNERS.length) % BANNERS.length)} aria-label="Previous slide"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M15 18l-6-6 6-6"/></svg></button>
            <button className="sn-hero-next" onClick={() => setBannerIdx(i => (i + 1) % BANNERS.length)} aria-label="Next slide"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M9 18l6-6-6-6"/></svg></button>
            <div className="sn-hero-dots">
              {BANNERS.map((_, i) => <button key={i} className={`sn-hero-dot ${i === bannerIdx ? "active" : ""}`} onClick={() => setBannerIdx(i)} aria-label={`Slide ${i + 1}`} />)}
            </div>
          </div>
        </div>
      </section>

      <section className="sn-quick-cats">
        <div className="sn-container">
          <div className="sn-quick-cats-grid">
            {categories.slice(0, 8).map(cat => {
              const name = typeof cat === "string" ? cat : (cat.name || cat.title || "");
              if (!name) return null;
              return (
                <Link key={name} className="sn-quick-cat" to={`/category/${encodeURIComponent(name)}`} style={{ textDecoration: "none" }}>
                  <span className="sn-quick-cat-icon"><Package size={28} /></span>
                  <span className="sn-quick-cat-label">{name}</span>
                </Link>
              );
            })}
          </div>
        </div>
      </section>

      {(loading || productsWithDiscount.length > 0) && (
      <section className="sn-section">
        <div className="sn-container">
          <div className="sn-section-top">
            <h2 className="sn-section-title"><Flame size={22} color="#fa8900" className="sn-section-icon" /> Discounted now</h2>
            <Link className="sn-view-all" to="/offers" style={{ textDecoration: "none" }}>View all offers {"\u2192"}</Link>
          </div>
          <div className="sn-product-scroll">
            {productsWithDiscount.length > 0
              ? productsWithDiscount.map(p => <ProductTray key={p.id} product={p} onAddToCart={handleCart} onBuyNow={handleBuyNow} />)
              : [...Array(6)].map((_, i) => <LoadingSkeleton key={i} />)}
          </div>
        </div>
      </section>
      )}

      <section className="sn-section">
        <div className="sn-container">
          <div className="sn-section-top">
            <h2 className="sn-section-title"><Star size={22} color="#fa8900" className="sn-section-icon" /> New from Tamil Nadu sellers</h2>
            <Link className="sn-view-all" to="/products" style={{ textDecoration: "none" }}>View all {"\u2192"}</Link>
          </div>
          {loading ? (
            <div className="sn-product-scroll">{[...Array(6)].map((_, i) => <LoadingSkeleton key={i} />)}</div>
          ) : normalized.length > 0 ? (
            <div className="sn-product-scroll">
              {normalized.slice(0, 6).map(p => <ProductTray key={p.id} product={p} onAddToCart={handleCart} onBuyNow={handleBuyNow} />)}
            </div>
          ) : (
            <div className="sn-empty-section">No products available</div>
          )}
        </div>
      </section>

      <section className="sn-section">
        <div className="sn-container">
          <div className="sn-section-top">
            <h2 className="sn-section-title"><Zap size={22} color="#fa8900" className="sn-section-icon" /> Trending Electronics</h2>
            <Link className="sn-view-all" to="/category/Electronics" style={{ textDecoration: "none" }}>View all {"\u2192"}</Link>
          </div>
          {loading ? (
            <div className="sn-product-scroll">{[...Array(6)].map((_, i) => <LoadingSkeleton key={i} />)}</div>
          ) : electronics.length > 0 ? (
            <div className="sn-product-scroll">
              {electronics.map(p => <ProductTray key={p.id} product={p} onAddToCart={handleCart} onBuyNow={handleBuyNow} />)}
            </div>
          ) : (
            <div className="sn-empty-section">No products available</div>
          )}
        </div>
      </section>

      <section className="sn-section sn-section-accent">
        <div className="sn-container">
          <div className="sn-section-top">
            <h2 className="sn-section-title"><Shirt size={22} color="#2E9B57" className="sn-section-icon" /> Fashion Essentials</h2>
            <Link className="sn-view-all" to="/category/Fashion" style={{ textDecoration: "none" }}>View all {"\u2192"}</Link>
          </div>
          {loading ? (
            <div className="sn-product-scroll">{[...Array(4)].map((_, i) => <LoadingSkeleton key={i} />)}</div>
          ) : fashion.length > 0 ? (
            <div className="sn-product-scroll">
              {fashion.map(p => <ProductTray key={p.id} product={p} onAddToCart={handleCart} onBuyNow={handleBuyNow} />)}
            </div>
          ) : (
            <div className="sn-empty-section">No products available</div>
          )}
        </div>
      </section>

      <style>{`
        .sn-toast {
          position: fixed; top: 100px; right: 20px; z-index: 9999;
          padding: 12px 20px; border-radius: 8px; font-weight: 600; font-size: 0.9rem;
          box-shadow: 0 4px 12px rgba(0,0,0,0.15); animation: slideInRight 0.3s ease-out;
        }
        .sn-toast-success { background: #16a34a; color: #fff; }
        .sn-toast-error { background: #dc2626; color: #fff; }
        @keyframes slideInRight { from { transform: translateX(100%); opacity: 0; } to { transform: translateX(0); opacity: 1; } }
        .sn-product-scroll .pt-card { width: 200px; flex-shrink: 0; }
        .sn-product-scroll .pt-skeleton { width: 200px; flex-shrink: 0; }
      `}</style>
    </div>
  );
};

export default Home;
