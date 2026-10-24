import React, { useState, useEffect } from "react";
import { useSearchParams, useNavigate, Link } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { canonicalFor } from "../i18n/canonical";
import api from "../api/axios";
import { addToCartOrLogin } from "../utils/cartActions";
import ProductTray, { LoadingSkeleton } from "../components/ProductTray";
import Pagination from "../components/Pagination";
import "../styles/products.css";
import { useTranslation } from "react-i18next";
import useCategoryNames from "../hooks/useCategoryNames";

const ProductList = () => {
  const { t } = useTranslation();
  const catLabel = useCategoryNames();

  const navigate = useNavigate();
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState("");
  const [category, setCategory] = useState("");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [mobileFilterOpen, setMobileFilterOpen] = useState(false);
  const [searchParams] = useSearchParams();

  useEffect(() => {
    const catParam = searchParams.get("category");
    if (catParam) setCategory(catParam);
  }, [searchParams]);

  useEffect(() => {
    api.get("/api/categories")
      .then(res => setCategories(Array.isArray(res.data) ? res.data : res.data?.content || res.data?.categories || []))
      .catch(() => {});
  }, []);

  useEffect(() => {
    const fetchProducts = async () => {
      setLoading(true);
      setError("");
      try {
        const params = { page: page - 1, size: 12 };
        if (search) params.name = search;
        if (category) params.category = category;
        if (sort) {
          const [sortBy, direction] = sort.split("-");
          params.sortBy = sortBy === "newest" ? "id" : sortBy === "oldest" ? "id" : sortBy;
          params.direction = sort.includes("desc") || sort === "newest" ? "desc" : "asc";
        }
        const res = await api.get("/api/products/search", { params });
        setProducts(res.data.content || []);
        setTotalPages(res.data.totalPages || 1);
      } catch (err) {
        setProducts([]);
        setError(t("categoryProducts.pleaseCheckYourInternetConnection"));
      }
      setLoading(false);
    };
    fetchProducts();
  }, [search, sort, category, page]);

  const categoryValue = (cat) => cat.name || cat.id || cat._id || cat;
  const categoryLabel = (cat) => catLabel(cat);

  const handleAddToCart = async (product) => {
    await addToCartOrLogin(navigate, product, 1);
  };

  const handleBuyNow = async (product) => {
    const res = await addToCartOrLogin(navigate, product, 1);
    if (res.ok) navigate("/checkout");
  };

  // /products?category=X shows the same items as /category/X, so point search engines at
  // the category URL. Other filters (search, sort, page) stay on /products.
  const listCanonical = category
    ? canonicalFor(`/category/${encodeURIComponent(category)}`)
    : canonicalFor("/products");

  return (
    <div className="products-page">
      <Helmet>
        <title>{category ? `${category} | Cauvery Store` : t("productList.allProductsCauveryStore")}</title>
        <meta name="description" content={t("productList.metaDescription")} />
        <link rel="canonical" href={listCanonical} />
        <meta property="og:title" content={category ? `${category} | Cauvery Store` : t("productList.allProductsCauveryStore")} />
        <meta property="og:description" content={t("productList.metaDescription")} />
        <meta property="og:type" content="website" />
        <meta property="og:url" content={listCanonical} />
        <meta property="og:image" content="https://cauverystore.in/logo512.png" />
        {/* An empty shelf is a thin page: keep it out of search results until it lists something. */}
        {!loading && !error && products.length === 0 && <meta name="robots" content="noindex, follow" />}
      </Helmet>
      <div className="pl-layout">
        <aside className="pl-sidebar">
          <div className="pl-sidebar-section">
            <h3 className="pl-sidebar-title">{t("filters.category")}</h3>
            <div className="pl-sidebar-categories">
              <button type="button" className={`pl-sidebar-cat ${category === "" ? "active" : ""}`} aria-pressed={category === ""} onClick={() => { setCategory(""); setPage(1); }}>{t("productList.allCategories")}</button>
              {categories.map((cat) => (
                <button type="button" key={categoryValue(cat)} className={`pl-sidebar-cat ${category === categoryValue(cat) ? "active" : ""}`} aria-pressed={category === categoryValue(cat)} onClick={() => { setCategory(categoryValue(cat)); setPage(1); }}>{categoryLabel(cat)}</button>
              ))}
            </div>
          </div>
          <div className="pl-sidebar-section">
            <h3 className="pl-sidebar-title">{t("productList.sortBy")}</h3>
            <select value={sort} onChange={(e) => { setSort(e.target.value); setPage(1); }} className="pl-sidebar-select" aria-label={t("productList.sortProducts")}>
              <option value="">{t("addressBook.default")}</option>
              <option value="price-asc">{t("orders.priceLowToHigh")}</option>
              <option value="price-desc">{t("orders.priceHighToLow")}</option>
              <option value="name-asc">{t("productList.nameAZ")}</option>
              <option value="name-desc">{t("productList.nameZA")}</option>
              <option value="newest">{t("orders.newestFirst")}</option>
              <option value="oldest">{t("orders.oldestFirst")}</option>
            </select>
          </div>
          <div className="pl-sidebar-section">
            <h3 className="pl-sidebar-title">{t("search.button")}</h3>
            <input type="text" placeholder={t("productList.searchProducts")} aria-label={t("productList.searchProductsh9e")} value={search} onChange={(e) => { setSearch(e.target.value); setPage(1); }} className="pl-sidebar-input" />
          </div>
          <button className="pl-sidebar-clear" onClick={() => { setCategory(""); setSort(""); setSearch(""); setPage(1); }}>{t("productList.clearAllFilters")}</button>
        </aside>

        <div className="pl-content">
          <h1 className="section-title">{category ? categoryLabel(category) : t("productList.allProductsHeading")}</h1>
          <div className="pl-toolbar">
            <button className="pl-mobile-filter-btn" onClick={() => setMobileFilterOpen(true)}>{t("filters.title")}</button>
            <select value={sort} onChange={(e) => { setSort(e.target.value); setPage(1); }} className="pl-sort-select">
              <option value="">{t("orders.sortBy")}</option>
              <option value="price-asc">{t("orders.priceLowToHigh")}</option>
              <option value="price-desc">{t("orders.priceHighToLow")}</option>
              <option value="name-asc">{t("productList.nameAZ")}</option>
              <option value="name-desc">{t("productList.nameZA")}</option>
              <option value="newest">{t("orders.newestFirst")}</option>
              <option value="oldest">{t("orders.oldestFirst")}</option>
            </select>
          </div>

          {loading ? (
            <div className="pt-grid">
              {Array.from({ length: 8 }).map((_, i) => <LoadingSkeleton key={i} />)}
            </div>
          ) : error ? (
            <div className="products-error">
              <div className="products-error-icon">!</div>
              <h3 className="products-error-title">{t("productList.weCouldnTLoadThe")}</h3>
              <p className="products-error-text">{error}</p>
              <button className="products-error-retry" onClick={() => window.location.reload()}>{t("categoryProducts.tryAgain")}</button>
            </div>
          ) : products.length === 0 ? (
            category || search ? (
              <div className="products-empty">
                <h3 className="products-empty-title">{t("productList.nothingMatchesTheseFilters")}</h3>
                <p className="products-empty-text">{t("productList.clearTheFiltersOrPick")}</p>
                <button className="products-empty-action" onClick={() => { setCategory(""); setSort(""); setSearch(""); setPage(1); }}>{t("productList.clearAllFilters1vm")}</button>
              </div>
            ) : (
              /* No filters and still nothing: the store itself has no products listed yet. */
              <div className="products-empty">
                <h3 className="products-empty-title">{t("home.newProductsAreOnThe")}</h3>
                <p className="products-empty-text">{t("home.pleaseCheckBackSoonOwn")}</p>
                <div className="products-empty-actions">
                  <Link to="/seller/register" className="products-empty-action">{t("home.startSelling")}</Link>
                  <Link to="/" className="products-empty-link">{t("productList.backToTheHomepage")}</Link>
                </div>
              </div>
            )
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

      {mobileFilterOpen && (
        <div className="pl-drawer-overlay" onClick={() => setMobileFilterOpen(false)}>
          <div className="pl-drawer" role="dialog" aria-modal="true" aria-label={t("productList.productFilters")} onClick={(e) => e.stopPropagation()}>
            <div className="pl-drawer-header">
              <h3 className="pl-drawer-title">{t("filters.title")}</h3>
              <button className="pl-drawer-close" onClick={() => setMobileFilterOpen(false)} aria-label={t("productList.closeFilters")}>{"\u2715"}</button>
            </div>
            <div className="pl-drawer-body">
              <div className="pl-sidebar-section">
                <h3 className="pl-sidebar-title">{t("filters.category")}</h3>
                <div className="pl-sidebar-categories">
                  <button type="button" className={`pl-sidebar-cat ${category === "" ? "active" : ""}`} aria-pressed={category === ""} onClick={() => { setCategory(""); setPage(1); }}>{t("productList.allCategories")}</button>
                  {categories.map((cat) => (
                    <button type="button" key={categoryValue(cat)} className={`pl-sidebar-cat ${category === categoryValue(cat) ? "active" : ""}`} aria-pressed={category === categoryValue(cat)} onClick={() => { setCategory(categoryValue(cat)); setPage(1); }}>{categoryLabel(cat)}</button>
                  ))}
                </div>
              </div>
              <div className="pl-sidebar-section">
                <h3 className="pl-sidebar-title">{t("productList.sortBy")}</h3>
                <select value={sort} onChange={(e) => { setSort(e.target.value); setPage(1); }} className="pl-sidebar-select" aria-label={t("productList.sortProducts")}>
                  <option value="">{t("addressBook.default")}</option>
                  <option value="price-asc">{t("orders.priceLowToHigh")}</option>
                  <option value="price-desc">{t("orders.priceHighToLow")}</option>
                  <option value="name-asc">{t("productList.nameAZ")}</option>
                  <option value="name-desc">{t("productList.nameZA")}</option>
                  <option value="newest">{t("orders.newestFirst")}</option>
                  <option value="oldest">{t("orders.oldestFirst")}</option>
                </select>
              </div>
              <div className="pl-sidebar-section">
                <h3 className="pl-sidebar-title">{t("search.button")}</h3>
                <input type="text" placeholder={t("productList.searchProducts")} aria-label={t("productList.searchProductsh9e")} value={search} onChange={(e) => { setSearch(e.target.value); setPage(1); }} className="pl-sidebar-input" />
              </div>
              <button className="pl-sidebar-clear" onClick={() => { setCategory(""); setSort(""); setSearch(""); setPage(1); }}>{t("productList.clearAllFilters")}</button>
            </div>
            <div className="pl-drawer-footer">
              <button className="pl-drawer-apply" onClick={() => setMobileFilterOpen(false)}>{t("productList.applyFilters")}</button>
            </div>
          </div>
        </div>
      )}

      <style>{`
        .pl-layout { display: flex; gap: 1.5rem; align-items: flex-start; }
        .pl-sidebar { width: 240px; flex-shrink: 0; background: #fff; border: 1px solid rgba(14, 92, 92, 0.14); border-radius: 16px; padding: 1.25rem; position: sticky; top: 110px; }
        .pl-sidebar-section { margin-bottom: 1.25rem; }
        .pl-sidebar-title { font-size: 0.75rem; font-weight: 600; color: #6d7c7b; margin: 0 0 0.6rem; text-transform: uppercase; letter-spacing: 0.14em; }
        .pl-sidebar-categories { display: flex; flex-direction: column; gap: 2px; max-height: 280px; overflow-y: auto; }
        .pl-sidebar-cat { width: 100%; padding: 0.4rem 0.6rem; font-size: 0.82rem; color: #475569; cursor: pointer; border-radius: 6px; transition: all 0.12s; background: none; border: none; text-align: left; font-family: inherit; }
        .pl-sidebar-cat:hover { background: #f1f5f9; }
        .pl-sidebar-cat.active { background: rgba(14, 92, 92, 0.08); color: #0E5C5C; font-weight: 600; }
        .pl-sidebar-select { width: 100%; padding: 0.45rem 0.5rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.82rem; }
        .pl-sidebar-input { width: 100%; padding: 0.45rem 0.6rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.82rem; }
        .pl-sidebar-clear { width: 100%; padding: 0.45rem; background: none; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.78rem; cursor: pointer; }
        .pl-content { flex: 1; min-width: 0; }
        .pl-toolbar { display: none; align-items: center; gap: 0.75rem; margin-bottom: 1rem; }
        .pl-mobile-filter-btn { display: none; padding: 0.45rem 0.75rem; background: #fff; border: 1px solid #d1d5db; border-radius: 8px; font-size: 0.82rem; cursor: pointer; }
        .pl-sort-select { padding: 0.45rem 0.6rem; border: 1px solid #d1d5db; border-radius: 8px; font-size: 0.82rem; background: #fff; }
        /* .pl-content shares its row with a 240px sidebar down to the 768px breakpoint below,
           so its column counts switch later than the standalone .pt-grid breakpoints - matching
           those exactly here would squeeze 3 columns into a narrower-than-expected content area. */
        .pl-content .pt-grid { grid-template-columns: repeat(2, 1fr); }
        @media (min-width: 900px) {
          .pl-content .pt-grid { grid-template-columns: repeat(3, 1fr); }
        }
        @media (min-width: 1280px) {
          .pl-content .pt-grid { grid-template-columns: repeat(4, 1fr); }
        }
        @media (max-width: 768px) {
          .pl-layout { flex-direction: column; }
          .pl-sidebar { display: none; }
          .pl-toolbar { display: flex; }
          .pl-mobile-filter-btn { display: inline-flex; }
          .pl-sort-select { flex: 1; }
        }
        .pl-drawer-overlay { position: fixed; inset: 0; background: rgba(15, 23, 42, 0.5); z-index: 2000; display: flex; justify-content: flex-end; }
        .pl-drawer { width: min(320px, 88vw); height: 100%; background: #fff; display: flex; flex-direction: column; box-shadow: -4px 0 16px rgba(0,0,0,0.12); animation: plDrawerSlide 0.25s ease-out; }
        @keyframes plDrawerSlide { from { transform: translateX(100%); } to { transform: translateX(0); } }
        .pl-drawer-header { display: flex; align-items: center; justify-content: space-between; padding: 1rem 1.25rem; border-bottom: 1px solid #e2e8f0; }
        .pl-drawer-title { font-size: 1rem; font-weight: 700; color: #1e293b; margin: 0; }
        .pl-drawer-close { background: none; border: none; font-size: 1rem; color: #475569; cursor: pointer; padding: 0.25rem; }
        .pl-drawer-body { flex: 1; overflow-y: auto; padding: 1.25rem; }
        .pl-drawer-footer { padding: 1rem 1.25rem; border-top: 1px solid #e2e8f0; }
        .pl-drawer-apply { width: 100%; padding: 0.75rem; background: #0E5C5C; color: #fff; border: none; border-radius: 999px; font-size: 0.9rem; font-weight: 600; cursor: pointer; }
      `}</style>
    </div>
  );
};

export default ProductList;
