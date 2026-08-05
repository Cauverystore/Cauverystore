import React, { useState, useEffect, useCallback, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import {
  Zap, Shirt, Home as HomeIcon, BookOpen, Smartphone, Laptop, Tv,
  Sparkles, Cookie, Flame, Star, Store, Landmark, CreditCard, Package
} from "lucide-react";
import api from "../api/axios";
import { addToCart } from "../services/cartService";
import ProductTray, { LoadingSkeleton } from "../components/ProductTray";
import "../styles/shopnest-home.css";

const BANNERS = [
  { id: 1, title: "Big Summer Sale", subtitle: "Up to 70% off on Electronics", cta: "Shop Now", bg: "#0B3D2E", accent: "#1B7A45", color: "#7FFFD4" },
  { id: 2, title: "Fashion Week", subtitle: "New arrivals starting at \u20B9299", cta: "Explore Styles", bg: "#146C43", accent: "#1B7A45", color: "#7FFFD4" },
  { id: 3, title: "Home Makeover", subtitle: "Kitchen essentials & furniture deals", cta: "Upgrade Now", bg: "#115035", accent: "#0B3D2E", color: "#7FFFD4" },
  { id: 4, title: "Book Bonanza", subtitle: "Bestsellers at flat 40% off", cta: "Browse Books", bg: "#146C43", accent: "#1B7A45", color: "#7FFFD4" },
];
const BANNER_ICONS = [Zap, Shirt, HomeIcon, BookOpen];

const BRAND_STORES = [
  { name: "Samsung", icon: "https://images.unsplash.com/photo-1610945415295-d9bbf067e59c?w=100", offer: "Up to 40% Off" },
  { name: "Apple", icon: "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=100", offer: "Exchange Bonus" },
  { name: "Nike", icon: "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=100", offer: "Min 50% Off" },
  { name: "Sony", icon: "https://images.unsplash.com/photo-1618366712010-f4ae9c647dcb?w=100", offer: "Audio Fest" },
  { name: "Puma", icon: "https://images.unsplash.com/photo-1608231387042-66d1773070a5?w=100", offer: "40-70% Off" },
  { name: "OnePlus", icon: "https://images.unsplash.com/photo-1598327105666-5b89351aff97?w=100", offer: "Launch Deals" },
];

const QUICK_CATEGORIES = [
  { icon: Smartphone, label: "Mobiles", slug: "Electronics" },
  { icon: Laptop, label: "Laptops", slug: "Electronics" },
  { icon: Shirt, label: "Fashion", slug: "Fashion" },
  { icon: HomeIcon, label: "Home", slug: "Home & Kitchen" },
  { icon: Tv, label: "TVs", slug: "Electronics" },
  { icon: Sparkles, label: "Beauty", slug: "Fashion" },
  { icon: Cookie, label: "Grocery", slug: "Home & Kitchen" },
  { icon: BookOpen, label: "Books", slug: "Books" },
];

const CATEGORIES = ["Electronics", "Fashion", "Home & Kitchen", "Grocery", "Beauty", "Appliances", "Books", "Sports", "Toys", "Deals"];

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

function DealCountdown() {
  const [time, setTime] = useState(() => {
    const end = new Date();
    end.setHours(23, 59, 59, 999);
    return end.getTime() - Date.now();
  });

  useEffect(() => {
    const id = setInterval(() => setTime(prev => Math.max(0, prev - 1000)), 1000);
    return () => clearInterval(id);
  }, []);

  const h = Math.floor(time / 3600000);
  const m = Math.floor((time % 3600000) / 60000);
  const s = Math.floor((time % 60000) / 1000);

  return (
    <div className="sn-countdown">
      <span className="sn-countdown-label">Ends in</span>
      <span className="sn-countdown-block">{String(h).padStart(2, '0')}</span><span>:</span>
      <span className="sn-countdown-block">{String(m).padStart(2, '0')}</span><span>:</span>
      <span className="sn-countdown-block">{String(s).padStart(2, '0')}</span>
    </div>
  );
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
  const [brandStoresEnabled, setBrandStoresEnabled] = useState(false);

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
    api.get("/api/settings/homepage")
      .then(res => setBrandStoresEnabled(!!res.data?.brandStoresEnabled))
      .catch(() => setBrandStoresEnabled(false));
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
                    <span className="sn-hero-icon">{React.createElement(BANNER_ICONS[i], { size: 48, color: b.color })}</span>
                    <h2 style={{ color: b.color }}>{b.title}</h2>
                    <p style={{ color: b.color, opacity: 0.85 }}>{b.subtitle}</p>
                    <button className="sn-hero-cta" style={{ backgroundColor: b.accent }}>{b.cta}</button>
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
            {QUICK_CATEGORIES.map(cat => (
              <button key={cat.label} className="sn-quick-cat" onClick={() => navigate(`/products?category=${encodeURIComponent(cat.slug)}`)}>
                <span className="sn-quick-cat-icon"><cat.icon size={28} /></span>
                <span className="sn-quick-cat-label">{cat.label}</span>
              </button>
            ))}
          </div>
        </div>
      </section>

      <section className="sn-section">
        <div className="sn-container">
          <div className="sn-section-top">
            <div className="sn-section-title-row">
              <h2 className="sn-section-title"><Flame size={22} color="#fa8900" className="sn-section-icon" /> Deals of the Day</h2>
              <DealCountdown />
            </div>
            <button className="sn-view-all" onClick={() => navigate("/offers")}>View All Deals {"\u2192"}</button>
          </div>
          <div className="sn-product-scroll">
            {productsWithDiscount.length > 0
              ? productsWithDiscount.map(p => <ProductTray key={p.id} product={p} onAddToCart={handleCart} onBuyNow={handleBuyNow} />)
              : (loading ? [...Array(6)].map((_, i) => <LoadingSkeleton key={i} />) : null)}
          </div>
        </div>
      </section>

      <section className="sn-section">
        <div className="sn-container">
          <div className="sn-offer-strip">
            <div className="sn-offer-card">
              <span className="sn-offer-icon"><Landmark size={24} color="#146C43" /></span>
              <div>
                <strong>10% Instant Discount</strong>
                <p>with HDFC Bank Credit Cards on orders above {"\u20B9"}5,000</p>
              </div>
            </div>
            <div className="sn-offer-card">
              <span className="sn-offer-icon"><CreditCard size={24} color="#146C43" /></span>
              <div>
                <strong>Flat {"\u20B9"}500 Cashback</strong>
                <p>on first order with SBI Debit Card. Use code: SHOP500</p>
              </div>
            </div>
            <div className="sn-offer-card">
              <span className="sn-offer-icon"><Package size={24} color="#146C43" /></span>
              <div>
                <strong>No Cost EMI</strong>
                <p>starting at {"\u20B9"}499/month on select products</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="sn-section">
        <div className="sn-container">
          <div className="sn-section-top">
            <h2 className="sn-section-title"><Star size={22} color="#fa8900" className="sn-section-icon" /> Top Picks for You</h2>
            <button className="sn-view-all" onClick={() => navigate("/products")}>View All {"\u2192"}</button>
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
            <button className="sn-view-all" onClick={() => navigate("/products?category=Electronics")}>View All {"\u2192"}</button>
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
            <button className="sn-view-all" onClick={() => navigate("/products?category=Fashion")}>View All {"\u2192"}</button>
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

      {brandStoresEnabled && (
        <section className="sn-section">
          <div className="sn-container">
            <div className="sn-section-top">
              <h2 className="sn-section-title"><Store size={22} color="#2E9B57" className="sn-section-icon" /> Brand Stores</h2>
              <button className="sn-view-all" onClick={() => navigate("/products")}>View All {"\u2192"}</button>
            </div>
            <div className="sn-brand-scroll">
              {BRAND_STORES.map(b => (
                <div key={b.name} className="sn-brand-card">
                  <img src={b.icon} alt={b.name} width="120" height="60" className="sn-brand-img" />
                  <span className="sn-brand-name">{b.name}</span>
                  <span className="sn-brand-offer">{b.offer}</span>
                </div>
              ))}
            </div>
          </div>
        </section>
      )}

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
