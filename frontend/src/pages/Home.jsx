import React, { useState, useEffect, useCallback, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import {
  Search, Heart, Zap, Shirt, Home as HomeIcon, BookOpen, Smartphone, Laptop, Tv,
  Sparkles, Cookie, Flame, Star, Store, Landmark, CreditCard, Package, Tags,
  ShoppingCart
} from "lucide-react";
import api from "../api/axios";
import { addToCart } from "../services/cartService";
import CartItemImage from "../components/CartItemImage";
import "../styles/shopnest-home.css";

const BANNERS = [
  { id: 1, title: "Big Summer Sale", subtitle: "Up to 70% off on Electronics", cta: "Shop Now", bg: "#0B3D2E", accent: "#2E9B57", color: "#7FFFD4" },
  { id: 2, title: "Fashion Week", subtitle: "New arrivals starting at \u20B9299", cta: "Explore Styles", bg: "#146C43", accent: "#2E9B57", color: "#7FFFD4" },
  { id: 3, title: "Home Makeover", subtitle: "Kitchen essentials & furniture deals", cta: "Upgrade Now", bg: "#115035", accent: "#0B3D2E", color: "#7FFFD4" },
  { id: 4, title: "Book Bonanza", subtitle: "Bestsellers at flat 40% off", cta: "Browse Books", bg: "#146C43", accent: "#2E9B57", color: "#7FFFD4" },
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
const CATEGORY_DEALS = ["Top Offers", "Grocery", "Mobiles", "Fashion", "Electronics", "Home", "Appliances", "Travel", "Toys"];

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
  return { ...p, image: img, price, originalPrice: origPrice, discount: discPct, rating, reviews: reviewCount, delivery: "Free Delivery" };
}

function Stars({ rating, reviews }) {
  const full = Math.floor(rating);
  const half = rating - full >= 0.5;
  return (
    <span className="stars">
      {[...Array(5)].map((_, i) => (
        <span key={i} className={i < full ? "star full" : (i === full && half ? "star half" : "star empty")}>
          {i < full ? "\u2605" : (i === full && half ? "\u2605" : "\u2606")}
        </span>
      ))}
      {reviews != null && reviews > 0 && <span className="review-count">({reviews.toLocaleString()})</span>}
    </span>
  );
}

function ProductCard({ product, onCart, adding }) {
  const navigate = useNavigate();
  return (
    <div className="sn-product-card" onClick={() => navigate(`/product/${product.id}`)}>
      <div className="sn-product-image-wrap">
        <CartItemImage src={product.image} name={product.name} width={200} height={200} className="sn-product-img" />
        {product.badge && <span className="sn-product-badge">{product.badge}</span>}
        <button className="sn-wishlist-btn" onClick={e => { e.stopPropagation(); }} aria-label="Add to wishlist"><Heart size={16} /></button>
      </div>
      <div className="sn-product-info">
        <h4 className="sn-product-name">{product.name || "Product"}</h4>
        {product.rating != null && <Stars rating={product.rating} reviews={product.reviews} />}
        <div className="sn-product-pricing">
          <span className="sn-price">{"\u20B9"}{(product.price || 0).toLocaleString()}</span>
          {product.originalPrice > product.price && <span className="sn-original-price">{"\u20B9"}{product.originalPrice.toLocaleString()}</span>}
          {product.discount > 0 && <span className="sn-discount">{product.discount}% off</span>}
        </div>
        <span className="sn-delivery">{product.delivery || "Free Delivery"}</span>
      </div>
      <button className="sn-add-cart-btn" onClick={e => { e.stopPropagation(); if (onCart) onCart(product); }} disabled={adding}>
        {adding ? "Adding..." : "Add to Cart"}
      </button>
    </div>
  );
}

function SkeletonCard() {
  return (
    <div className="sn-product-card sn-skeleton">
      <div className="sn-skeleton-img" />
      <div className="sn-skeleton-body">
        <div className="sn-skel-line w80" />
        <div className="sn-skel-line w60" />
        <div className="sn-skel-line w40" />
      </div>
    </div>
  );
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
  const [addingId, setAddingId] = useState(null);

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
    setAddingId(p.id);
    try {
      await addToCart(p.id, 1);
      setToast({ type: "success", text: `${p.name} added to cart!` });
    } catch {
      setToast({ type: "error", text: "Failed to add to cart" });
    }
    setAddingId(null);
  }, [navigate]);

  return (
    <div className="sn-page">
      {toast && (
        <div className={`sn-toast sn-toast-${toast.type}`}>
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
              <button key={cat.label} className="sn-quick-cat" onClick={() => navigate(`/category/${cat.slug}`)}>
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
              ? productsWithDiscount.map(p => <ProductCard key={p.id} product={p} onCart={handleCart} adding={addingId === p.id} />)
              : (loading ? [...Array(6)].map((_, i) => <SkeletonCard key={i} />) : null)}
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
            <div className="sn-product-scroll">{[...Array(6)].map((_, i) => <SkeletonCard key={i} />)}</div>
          ) : normalized.length > 0 ? (
            <div className="sn-product-scroll">
              {normalized.slice(0, 6).map(p => <ProductCard key={p.id} product={p} onCart={handleCart} adding={addingId === p.id} />)}
            </div>
          ) : (
            <div className="sn-product-scroll">
              {[...Array(6)].map((_, i) => <SkeletonCard key={i} />)}
            </div>
          )}
        </div>
      </section>

      <section className="sn-section">
        <div className="sn-container">
          <div className="sn-section-top">
            <h2 className="sn-section-title"><Zap size={22} color="#fa8900" className="sn-section-icon" /> Trending Electronics</h2>
            <button className="sn-view-all" onClick={() => navigate("/category/Electronics")}>View All {"\u2192"}</button>
          </div>
          <div className="sn-product-scroll">
            {electronics.length > 0
              ? electronics.map(p => <ProductCard key={p.id} product={p} onCart={handleCart} adding={addingId === p.id} />)
              : (loading ? [...Array(6)].map((_, i) => <SkeletonCard key={i} />) : null)}
          </div>
        </div>
      </section>

      <section className="sn-section">
        <div className="sn-container">
          <h2 className="sn-section-title sn-mb"><Tags size={22} color="#2E9B57" className="sn-section-icon" /> Shop by Category</h2>
          <div className="sn-category-grid">
            {CATEGORY_DEALS.map((cat, i) => (
              <div key={cat} className="sn-category-card" onClick={() => navigate(`/category/${cat === "Top Offers" ? "Electronics" : cat}`)}>
                <div className="sn-category-card-visual" style={{ background: `hsl(${i * 36}, 60%, 92%)` }}>
                  <span className="sn-category-card-icon">{React.createElement(QUICK_CATEGORIES[i % QUICK_CATEGORIES.length]?.icon || ShoppingCart, { size: 32 })}</span>
                </div>
                <span className="sn-category-card-label">{cat}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="sn-section sn-section-accent">
        <div className="sn-container">
          <div className="sn-section-top">
            <h2 className="sn-section-title"><Shirt size={22} color="#2E9B57" className="sn-section-icon" /> Fashion Essentials</h2>
            <button className="sn-view-all" onClick={() => navigate("/category/Fashion")}>View All {"\u2192"}</button>
          </div>
          <div className="sn-product-scroll">
            {fashion.length > 0
              ? fashion.map(p => <ProductCard key={p.id} product={p} onCart={handleCart} adding={addingId === p.id} />)
              : (loading ? [...Array(4)].map((_, i) => <SkeletonCard key={i} />) : null)}
          </div>
        </div>
      </section>

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

      <style>{`
        .sn-toast {
          position: fixed; top: 100px; right: 20px; z-index: 9999;
          padding: 12px 20px; border-radius: 8px; font-weight: 600; font-size: 0.9rem;
          box-shadow: 0 4px 12px rgba(0,0,0,0.15); animation: slideInRight 0.3s ease-out;
        }
        .sn-toast-success { background: #16a34a; color: #fff; }
        .sn-toast-error { background: #dc2626; color: #fff; }
        @keyframes slideInRight { from { transform: translateX(100%); opacity: 0; } to { transform: translateX(0); opacity: 1; } }
        .sn-product-img { width: 200px; height: 200px; object-fit: cover; display: block; }
        .sn-product-img.cart-item-img-placeholder {
          display: flex; align-items: center; justify-content: center;
          background: #f1f5f9; border: 1px dashed #d1d5db;
          font-size: 0.7rem; color: #64748b; text-align: center; padding: 8px;
          overflow: hidden; word-break: break-word; line-height: 1.3;
        }
        .sn-add-cart-btn:disabled { opacity: 0.6; cursor: not-allowed; }
      `}</style>
    </div>
  );
};

export default Home;
