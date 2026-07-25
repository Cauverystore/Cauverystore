import React, { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import {
  Search, Heart, Zap, Shirt, Home as HomeIcon, BookOpen, Smartphone, Laptop, Tv,
  Sparkles, Cookie, Flame, Star, Store, Landmark, CreditCard, Package, Tags,
  ShoppingCart
} from "lucide-react";
import api from "../api/axios";
import { addToCart } from "../services/cartService";
import "../styles/shopnest-home.css";

const BANNERS = [
  { id: 1, title: "Big Summer Sale", subtitle: "Up to 70% off on Electronics", cta: "Shop Now", bg: "#0B3D2E", accent: "#2E9B57", color: "#7FFFD4" },
  { id: 2, title: "Fashion Week", subtitle: "New arrivals starting at \u20B9299", cta: "Explore Styles", bg: "#146C43", accent: "#2E9B57", color: "#7FFFD4" },
  { id: 3, title: "Home Makeover", subtitle: "Kitchen essentials & furniture deals", cta: "Upgrade Now", bg: "#115035", accent: "#0B3D2E", color: "#7FFFD4" },
  { id: 4, title: "Book Bonanza", subtitle: "Bestsellers at flat 40% off", cta: "Browse Books", bg: "#146C43", accent: "#2E9B57", color: "#7FFFD4" },
];
const BANNER_ICONS = [Zap, Shirt, HomeIcon, BookOpen];

const DEALS_OF_DAY = [
  { id: "d1", name: "Wireless Earbuds Pro", price: 999, originalPrice: 4999, discount: 80, rating: 4.4, reviews: 8234, image: "https://images.unsplash.com/photo-1590658268037-6bf12f032f55?w=300", delivery: "Free Delivery", badge: "Hot Deal" },
  { id: "d2", name: "Smart Watch Series 7", price: 2499, originalPrice: 8999, discount: 72, rating: 4.2, reviews: 5621, image: "https://images.unsplash.com/photo-1546868871-af0de0ae72be?w=300", delivery: "Free Delivery", badge: "Trending" },
  { id: "d3", name: "Running Shoes Ultra", price: 1299, originalPrice: 3999, discount: 68, rating: 4.5, reviews: 12453, image: "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=300", delivery: "Free Delivery", badge: "Best Value" },
  { id: "d4", name: "Bluetooth Speaker Boom", price: 1799, originalPrice: 5499, discount: 67, rating: 4.3, reviews: 3892, image: "https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?w=300", delivery: "Free Delivery", badge: "Popular" },
  { id: "d5", name: "Laptop Backpack 32L", price: 699, originalPrice: 2499, discount: 72, rating: 4.6, reviews: 9801, image: "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=300", delivery: "Free Delivery" },
  { id: "d6", name: "Power Bank 20000mAh", price: 899, originalPrice: 2999, discount: 70, rating: 4.1, reviews: 15432, image: "https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?w=300", delivery: "Free Delivery" },
];

const TRENDING_ELECTRONICS = [
  { id: "e1", name: "Noise Cancelling Headphones", price: 2999, originalPrice: 9999, discount: 70, rating: 4.5, reviews: 4521, image: "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=300", delivery: "Free Delivery" },
  { id: "e2", name: "4K Action Camera", price: 3499, originalPrice: 7999, discount: 56, rating: 4.3, reviews: 2310, image: "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=300", delivery: "Free Delivery" },
  { id: "e3", name: "Tablet 10.4 inch WiFi", price: 12999, originalPrice: 24999, discount: 48, rating: 4.4, reviews: 9876, image: "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?w=300", delivery: "Free Delivery" },
  { id: "e4", name: "Wireless Mouse Ergonomic", price: 599, originalPrice: 1999, discount: 70, rating: 4.6, reviews: 12670, image: "https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=300", delivery: "Free Delivery" },
  { id: "e5", name: "USB-C Hub 7-in-1", price: 1499, originalPrice: 3499, discount: 57, rating: 4.2, reviews: 5432, image: "https://images.unsplash.com/photo-1625723044792-44de16ccb4e9?w=300", delivery: "Free Delivery" },
  { id: "e6", name: "LED Monitor 24 inch", price: 8999, originalPrice: 14999, discount: 40, rating: 4.4, reviews: 3456, image: "https://images.unsplash.com/photo-1527443224154-c4a3942d3ac3?w=300", delivery: "Free Delivery" },
];

const FASHION_ESSENTIALS = [
  { id: "f1", name: "Cotton T-Shirt Pack of 3", price: 599, originalPrice: 1499, discount: 60, rating: 4.1, reviews: 8765, image: "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=300", delivery: "Free Delivery" },
  { id: "f2", name: "Slim Fit Jeans", price: 999, originalPrice: 2999, discount: 67, rating: 4.3, reviews: 6543, image: "https://images.unsplash.com/photo-1541099649105-f69ad21f3246?w=300", delivery: "Free Delivery" },
  { id: "f3", name: "Casual Sneakers White", price: 1499, originalPrice: 3999, discount: 63, rating: 4.5, reviews: 11234, image: "https://images.unsplash.com/photo-1560769629-975ec94e6a86?w=300", delivery: "Free Delivery" },
  { id: "f4", name: "Leather Wallet Genuine", price: 449, originalPrice: 1299, discount: 65, rating: 4.4, reviews: 5678, image: "https://images.unsplash.com/photo-1627123424574-724758594e93?w=300", delivery: "Free Delivery" },
];

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
  const img = p.images?.[0]?.url || p.image || "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=300";
  const price = p.price || 0;
  const discount = p.discounts?.[0];
  let origPrice = price;
  let discPct = 0;
  if (discount?.active) {
    discPct = Math.round(discount.value);
    origPrice = Math.round(price / (1 - discPct / 100));
  }
  const rating = p.rating || (p.reviews?.length > 0 ? p.reviews.reduce((s,r)=>s+(r.rating||0),0)/p.reviews.length : 4.0);
  const reviewCount = p.reviews?.length || 100;
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
      {reviews != null && <span className="review-count">({reviews.toLocaleString()})</span>}
    </span>
  );
}

function ProductCard({ product, onCart }) {
  const navigate = useNavigate();
  return (
    <div className="sn-product-card" onClick={() => navigate(`/product/${product.id}`)}>
      <div className="sn-product-image-wrap">
        <img src={product.image} alt={product.name} width="200" height="200" loading="lazy" />
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
      <button className="sn-add-cart-btn" onClick={e => { e.stopPropagation(); if (onCart) onCart(product); }}>
        Add to Cart
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

const Home = () => {
  const navigate = useNavigate();
  const [allProducts, setAllProducts] = useState(null);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [bannerIdx, setBannerIdx] = useState(0);

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

  const handleCart = useCallback((p) => {
    const isLoggedIn = !!localStorage.getItem("accessToken");
    if (!isLoggedIn) { navigate("/login"); return; }
    addToCart(p.id, 1).then(() => {}).catch(() => {});
  }, [navigate]);

  const products = allProducts || [];
  const displayProducts = (products.length > 0 ? products.slice(0, 12) : []).map(normalizeProduct).filter(Boolean);

  return (
    <div className="sn-page">
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
            {DEALS_OF_DAY.map(p => <ProductCard key={p.id} product={p} onCart={handleCart} />)}
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
          ) : displayProducts.length > 0 ? (
            <div className="sn-product-scroll">
              {displayProducts.map(p => <ProductCard key={p.id} product={p} onCart={handleCart} />)}
            </div>
          ) : (
            <div className="sn-product-scroll">
              {TRENDING_ELECTRONICS.slice(0, 6).map(p => <ProductCard key={p.id} product={p} onCart={handleCart} />)}
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
            {TRENDING_ELECTRONICS.map(p => <ProductCard key={p.id} product={p} onCart={handleCart} />)}
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
            {FASHION_ESSENTIALS.map(p => <ProductCard key={p.id} product={p} onCart={handleCart} />)}
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
    </div>
  );
};

export default Home;
