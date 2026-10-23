import React, { useState, useEffect, useCallback, useMemo } from "react";
import { useNavigate, Link } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { Headset, Truck, RotateCcw, ShieldCheck, ArrowRight } from "lucide-react";
import api from "../api/axios";
import { addToCart } from "../services/cartService";
import ProductTray, { LoadingSkeleton } from "../components/ProductTray";
import ProductRow from "../components/ProductRow";
import "../styles/shopnest-home.css";
import { useTranslation, Trans } from "react-i18next";
import i18n from "../i18n";

// The promises a first-time shopper looks for, stated once under the hero. Each one mirrors
// what the cart and order system really do - update them together.
const PROMISES = [
  {
    Icon: Truck,

    get title() {
      return i18n.t("productTray.freeDelivery");
    },

    text: "on orders of ₹500 or more"
  },
  {
    Icon: RotateCcw,

    get title() {
      return i18n.t("cart.7DayReturns");
    },

    text: "free if the item is faulty or wrong"
  },
  {
    Icon: ShieldCheck,

    get title() {
      return i18n.t("cart.securePayment");
    },

    get text() {
      return i18n.t("home.upiCardNetBankingOr");
    }
  },
  {
    Icon: Headset,

    get title() {
      return i18n.t("home.support");
    },

    get text() {
      return i18n.t("home.mondayToSaturday9Am");
    }
  },
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
  // 0 when nobody has rated it yet - the card then shows no stars rather than an invented score.
  const rating = p.rating || (p.reviews?.length > 0 ? p.reviews.reduce((s,r)=>s+(r.rating||0),0)/p.reviews.length : 0);
  const reviewCount = p.reviews?.length || 0;
  return { ...p, image: img, price, originalPrice: origPrice, discount: discPct, rating, reviews: reviewCount, stock: p.stock ?? p.stockQuantity ?? (p.active ? 1 : 0) };
}

function getCategoryName(p) {
  if (!p.category) return "";
  return typeof p.category === "object" ? (p.category.name || "") : p.category;
}

// How many products a row holds before "View all" takes over.
const ROW_LIMIT = 15;

const Home = () => {
  const { t } = useTranslation();

  const navigate = useNavigate();
  const [allProducts, setAllProducts] = useState(null);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
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
    if (toast) {
      const id = setTimeout(() => setToast(null), 2500);
      return () => clearTimeout(id);
    }
  }, [toast]);

  const products = allProducts || [];
  const normalized = useMemo(() => products.map(normalizeProduct).filter(Boolean), [products]);

  const productsWithDiscount = useMemo(() => normalized.filter(p => p.discount > 0).slice(0, ROW_LIMIT), [normalized]);
  const electronics = useMemo(() => normalized.filter(p => getCategoryName(p) === "Electronics").slice(0, ROW_LIMIT), [normalized]);
  const fashion = useMemo(() => normalized.filter(p => getCategoryName(p) === "Fashion").slice(0, ROW_LIMIT), [normalized]);

  const handleCart = useCallback(async (p) => {
    const isLoggedIn = !!localStorage.getItem("accessToken");
    if (!isLoggedIn) { navigate("/login"); return; }
    try {
      await addToCart(p.id, 1);
      setToast({ type: "success", text: `${p.name} added to your cart` });
    } catch {
      setToast({ type: "error", text: t("home.weCouldnTAddThat") });
    }
  }, [navigate]);

  const handleBuyNow = useCallback(async (p) => {
    const isLoggedIn = !!localStorage.getItem("accessToken");
    if (!isLoggedIn) { navigate("/login"); return; }
    try {
      await addToCart(p.id, 1);
      navigate("/checkout");
    } catch {
      setToast({ type: "error", text: t("cart.weCouldnTStartCheckout") });
    }
  }, [navigate]);

  return (
    <div className="sn-page">
      <Helmet>
        <title>{t("home.cauveryStoreOnlineShoppingIn")}</title>
        <meta
          name="description"
          content={t("home.shopOnlineAtCauveryStore")}
        />
        <link rel="canonical" href="https://cauverystore.in/" />
        <meta property="og:title" content={t("home.cauveryStoreOnlineShoppingIn")} />
        <meta
          property="og:description"
          content={t("home.shopOnlineAtCauveryStore")}
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

      {/* The hero states what the store really is. Add offer banners here only once real offers exist. */}
      <section className="sn-hero">
        <svg className="sn-hero-river" viewBox="0 0 600 500" fill="none" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
          <path d="M620 20C470 60 520 170 380 210S250 330 120 360 20 470 -20 500" stroke="currentColor" strokeWidth="1.25" />
          <path d="M620 60C490 95 540 200 400 240S270 360 140 390 40 490 0 520" stroke="currentColor" strokeWidth="1" opacity="0.6" />
          <path d="M620 100C510 130 560 230 420 270S290 390 160 420 60 510 20 540" stroke="currentColor" strokeWidth="0.75" opacity="0.35" />
        </svg>
        <div className="sn-container sn-hero-inner">
          <p className="sn-eyebrow">Cauvery Store</p>
          <h1 className="sn-hero-title"><Trans i18nKey="home.heroTitle" components={{ em: <em /> }} /></h1>
          <p className="sn-hero-sub">{t("home.freeDeliveryOnOrdersOf")}</p>
          <div className="sn-hero-actions">
            <Link to="/products" className="sn-btn">{t("cartDrawer.browseProducts")}{" "}<ArrowRight size={18} strokeWidth={1.8} aria-hidden="true" /></Link>
            <Link to="/seller/register" className="sn-text-link">{t("home.ownAShopStartSelling")}</Link>
          </div>
        </div>
      </section>

      <section className="sn-promises" aria-label={t("home.whyShopAtCauveryStore")}>
        <div className="sn-container">
          <ul className="sn-promises-grid">
            {PROMISES.map(({ Icon, title, text }) => (
              <li key={title} className="sn-promise">
                <Icon size={22} strokeWidth={1.6} aria-hidden="true" />
                <span><strong>{title}</strong> {text}</span>
              </li>
            ))}
          </ul>
        </div>
      </section>

      {categories.length > 0 && (
      <section className="sn-quick-cats">
        <div className="sn-container">
          <div className="sn-quick-cats-grid">
            {categories.slice(0, 8).map(cat => {
              const name = typeof cat === "string" ? cat : (cat.name || cat.title || "");
              if (!name) return null;
              return (
                <Link key={name} className="sn-quick-cat" to={`/category/${encodeURIComponent(name)}`}>
                  <span className="sn-quick-cat-label">{name}</span>
                </Link>
              );
            })}
          </div>
        </div>
      </section>
      )}

      {(loading || productsWithDiscount.length > 0) && (
      <section className="sn-section">
        <div className="sn-container">
          <div className="sn-section-top">
            <h2 className="sn-section-title">{t("home.discountedNow")}</h2>
            <Link className="sn-view-all" to="/offers">{t("home.viewAllOffers")}{" "}{"\u2192"}</Link>
          </div>
          <ProductRow>
            {productsWithDiscount.length > 0
              ? productsWithDiscount.map(p => <ProductTray key={p.id} product={p} onAddToCart={handleCart} onBuyNow={handleBuyNow} />)
              : [...Array(6)].map((_, i) => <LoadingSkeleton key={i} />)}
          </ProductRow>
        </div>
      </section>
      )}

      <section className="sn-section">
        <div className="sn-container">
          <div className="sn-section-top">
            <h2 className="sn-section-title">{t("home.newArrivals")}</h2>
            <Link className="sn-view-all" to="/products">{t("home.viewAll")}{" "}{"\u2192"}</Link>
          </div>
          {loading ? (
            <ProductRow>{[...Array(6)].map((_, i) => <LoadingSkeleton key={i} />)}</ProductRow>
          ) : normalized.length > 0 ? (
            <ProductRow>
              {normalized.slice(0, ROW_LIMIT).map(p => <ProductTray key={p.id} product={p} onAddToCart={handleCart} onBuyNow={handleBuyNow} />)}
            </ProductRow>
          ) : (
            <div className="sn-empty-section">
              <p className="sn-empty-title">{t("home.newProductsAreOnThe")}</p>
              <p>{t("home.pleaseCheckBackSoonOwn")}</p>
              <Link to="/seller/register" className="sn-btn">{t("home.startSelling")}{" "}<ArrowRight size={18} strokeWidth={1.8} aria-hidden="true" /></Link>
            </div>
          )}
        </div>
      </section>

      {/* Category rows only appear once they have products, so the page never shows an
          empty "No products available" block under a heading. */}
      {(loading || electronics.length > 0) && (
      <section className="sn-section">
        <div className="sn-container">
          <div className="sn-section-top">
            <h2 className="sn-section-title">{t("home.electronics")}</h2>
            <Link className="sn-view-all" to="/category/Electronics">{t("home.viewAll")}{" "}{"\u2192"}</Link>
          </div>
          {loading ? (
            <ProductRow>{[...Array(6)].map((_, i) => <LoadingSkeleton key={i} />)}</ProductRow>
          ) : electronics.length > 0 ? (
            <ProductRow>
              {electronics.map(p => <ProductTray key={p.id} product={p} onAddToCart={handleCart} onBuyNow={handleBuyNow} />)}
            </ProductRow>
          ) : null}
        </div>
      </section>
      )}

      {(loading || fashion.length > 0) && (
      <section className="sn-section sn-section-accent">
        <div className="sn-container">
          <div className="sn-section-top">
            <h2 className="sn-section-title">{t("home.fashion")}</h2>
            <Link className="sn-view-all" to="/category/Fashion">{t("home.viewAll")}{" "}{"\u2192"}</Link>
          </div>
          {loading ? (
            <ProductRow>{[...Array(4)].map((_, i) => <LoadingSkeleton key={i} />)}</ProductRow>
          ) : fashion.length > 0 ? (
            <ProductRow>
              {fashion.map(p => <ProductTray key={p.id} product={p} onAddToCart={handleCart} onBuyNow={handleBuyNow} />)}
            </ProductRow>
          ) : null}
        </div>
      </section>
      )}

    </div>
  );
};

export default Home;
