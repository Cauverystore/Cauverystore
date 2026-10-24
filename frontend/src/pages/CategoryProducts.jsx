import React, { useState, useEffect } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { canonicalFor } from "../i18n/canonical";
import { Store } from "lucide-react";
import { searchProducts } from "../services/productService";
import api from "../api/axios";
import NotFound from "./NotFound";
import { addToCartOrLogin } from "../utils/cartActions";
import ProductTray, { LoadingSkeleton } from "../components/ProductTray";
import Breadcrumb from "../components/Breadcrumb";
import useCategoryNames from "../hooks/useCategoryNames";
import Pagination from "../components/Pagination";
import "../styles/products.css";
import "../styles/product-tray.css";
import { useTranslation } from "react-i18next";
import i18n from "../i18n";

// Unique on-page copy per category, so category pages do not all read the same to a
// search engine. Each entry doubles as the meta description crawlers and link previews see.
// Keep it plain and factual: what the category holds, plus terms that are true store-wide.
//
// Keyed by the exact category name the backend returns (CategoryController's
// /api/categories, Category.name). A category with no entry here still works (see
// FALLBACK below); it just has no hand-written copy until one is added.
const seoT = (k, o) => i18n.t("categorySeo." + k, o);

// Getters, not plain strings, so the text is read in the language that is active when the page renders.
const CATEGORY_SEO_COPY = {
  "T-Shirts": {
    get intro() { return seoT("tshirtsIntro"); },
    get metaDescription() { return seoT("tshirtsMeta"); },
  },
  "Electronics": {
    get intro() { return seoT("electronicsIntro"); },
    get metaDescription() { return seoT("electronicsMeta"); },
  },
  "Fashion": {
    get intro() { return seoT("fashionIntro"); },
    get metaDescription() { return seoT("fashionMeta"); },
  },
  "Home & Kitchen": {
    get intro() { return seoT("homeIntro"); },
    get metaDescription() { return seoT("homeMeta"); },
  },
  "Books": {
    get intro() { return i18n.t("categoryProducts.fictionNonFictionAndEducational"); },
    get metaDescription() { return seoT("booksMeta"); },
  },
  "Sports": {
    get intro() { return i18n.t("categoryProducts.fitnessEquipmentAndSportswearFrom"); },
    get metaDescription() { return seoT("sportsMeta"); },
  },
};

// Any category not in the map above still gets a real sentence, so no category page is
// left with no body copy for a crawler to read.
const FALLBACK_SEO_COPY = (label) => ({
  intro: seoT("fallbackIntro", { category: label }),
  metaDescription: seoT("fallbackMeta", { category: label }),
});

const CategoryProducts = () => {
  const { t } = useTranslation();

  const { category } = useParams();
  const catLabel = useCategoryNames();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const navigate = useNavigate();
  // The product search ignores a category it does not know and returns everything, so a
  // made-up /category/anything would otherwise show the whole catalogue as a real page.
  // null = not checked yet; a failed check leaves the page showing as before.
  const [known, setKnown] = useState(null);

  useEffect(() => {
    setKnown(null);
    api.get("/api/categories")
      .then((r) => {
        // Exact match: the search itself is case-sensitive about category names.
        const names = (Array.isArray(r.data) ? r.data : []).map((c) => c.name);
        setKnown(names.includes(category));
      })
      .catch(() => {});
  }, [category]);

  useEffect(() => {
    const fetch = async () => {
      setLoading(true);
      setError("");
      try {
        const res = await searchProducts({ category, page: page - 1, size: 20 });
        setProducts(res.data.content || res.data || []);
        setTotalPages(res.data.totalPages || 1);
      } catch (err) {
        setError(err.response?.data?.error || t("categoryProducts.pleaseCheckYourInternetConnection"));
      }
      setLoading(false);
    };
    fetch();
  }, [category, page]);

  const handleAddToCart = async (product) => {
    await addToCartOrLogin(navigate, product, 1);
  };

  const handleBuyNow = async (product) => {
    const res = await addToCartOrLogin(navigate, product, 1);
    if (res.ok) navigate("/checkout");
  };

  const seoCopy = CATEGORY_SEO_COPY[category] || FALLBACK_SEO_COPY(catLabel(category));
  const canonicalUrl = canonicalFor(`/category/${encodeURIComponent(category || "")}`);
  const breadcrumbJsonLd = {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    "itemListElement": [
      { "@type": "ListItem", "position": 1, "name": t("nav.home"), "item": "https://cauverystore.in/" },
      { "@type": "ListItem", "position": 2, "name": catLabel(category), "item": canonicalUrl },
    ],
  };

  if (known === false) return <NotFound />;

  return (
    <div className="products-page">
      <style>{`.cat-products-content { grid-column: 1 / -1; }`}</style>
      <Helmet>
        <title>{`${catLabel(category)} | Cauvery Store`}</title>
        <meta name="description" content={seoCopy.metaDescription} />
        <link rel="canonical" href={canonicalUrl} />
        {/* An empty or made-up category is a thin page: keep it out of search results. */}
        {!loading && !error && products.length === 0 && <meta name="robots" content="noindex, follow" />}
        <meta property="og:title" content={`${catLabel(category)} | Cauvery Store`} />
        <meta property="og:description" content={seoCopy.metaDescription} />
        <meta property="og:type" content="website" />
        <meta property="og:url" content={canonicalUrl} />
        <meta property="og:image" content="https://cauverystore.in/logo512.png" />
        <script type="application/ld+json">{JSON.stringify(breadcrumbJsonLd)}</script>
      </Helmet>
      <div className="cat-products-content">
        <Breadcrumb items={[
          { label: t("nav.home"), to: "/" },
          { label: catLabel(category) }
        ]} />
        <div className="section-header">
          <h2 className="section-title">{catLabel(category)}</h2>
          <span className="products-toolbar-count">{products.length}{" "}{t("categoryProducts.products")}</span>
        </div>
        <div className="cat-products-banner">
          <Store size={22} className="cat-products-banner-icon" aria-hidden="true" />
          <div>
            <strong className="cat-products-banner-label">{t("categorySeo.whyShopHere", { category: catLabel(category) })}</strong>
            <p className="cat-products-banner-text">{seoCopy.intro}</p>
          </div>
        </div>

        {loading ? (
          <div className="pt-grid">
            {Array.from({ length: 8 }).map((_, i) => (
              <LoadingSkeleton key={i} />
            ))}
          </div>
        ) : error ? (
          <div className="products-error">
            <div className="products-error-icon">!</div>
            <h3 className="products-error-title">{t("categoryProducts.weCouldnTLoadThis")}</h3>
            <p className="products-error-text">{error}</p>
            <button className="products-error-retry" onClick={() => window.location.reload()}>{t("categoryProducts.tryAgain")}</button>
          </div>
        ) : products.length === 0 ? (
          <div className="products-empty">
            <div className="products-empty-icon">📦</div>
            <h3 className="products-empty-title">{t("categoryProducts.nothingHereYet")}</h3>
            <p className="products-empty-text">{t("categoryProducts.thereAreNoProductsIn")}</p>
            <Link to="/products" className="products-empty-action">{t("search.browseAll")}</Link>
          </div>
        ) : (
          <>
            <div className="pt-grid">
              {products.map((p) => (
                <ProductTray key={p.id || p._id} product={p} onAddToCart={handleAddToCart} onBuyNow={handleBuyNow} />
              ))}
            </div>
            <Pagination page={page} totalPages={totalPages} onPage={setPage} />
          </>
        )}
      </div>
    </div>
  );
};
export default CategoryProducts;
