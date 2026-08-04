import React, { useState, useEffect } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import api from "../api/axios";
import { addToCartOrLogin } from "../utils/cartActions";
import ProductTray, { LoadingSkeleton } from "../components/ProductTray";
import Pagination from "../components/Pagination";

const ProductList = () => {
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
        setError(err.message || "Failed to load products");
      }
      setLoading(false);
    };
    fetchProducts();
  }, [search, sort, category, page]);

  const categoryValue = (cat) => cat.name || cat.id || cat._id || cat;
  const categoryLabel = (cat) => cat.name || cat.title || cat;

  const handleAddToCart = async (product) => {
    await addToCartOrLogin(navigate, product, 1);
  };

  const handleBuyNow = async (product) => {
    const res = await addToCartOrLogin(navigate, product, 1);
    if (res.ok) navigate("/checkout");
  };

  return (
    <div className="products-page">
      <div className="pl-layout">
        <aside className="pl-sidebar">
          <div className="pl-sidebar-section">
            <h3 className="pl-sidebar-title">Category</h3>
            <div className="pl-sidebar-categories">
              <div className={`pl-sidebar-cat ${category === "" ? "active" : ""}`} onClick={() => { setCategory(""); setPage(1); }}>All Categories</div>
              {categories.map((cat) => (
                <div key={categoryValue(cat)} className={`pl-sidebar-cat ${category === categoryValue(cat) ? "active" : ""}`} onClick={() => { setCategory(categoryValue(cat)); setPage(1); }}>{categoryLabel(cat)}</div>
              ))}
            </div>
          </div>
          <div className="pl-sidebar-section">
            <h3 className="pl-sidebar-title">Sort By</h3>
            <select value={sort} onChange={(e) => { setSort(e.target.value); setPage(1); }} className="pl-sidebar-select">
              <option value="">Default</option>
              <option value="price-asc">Price: Low to High</option>
              <option value="price-desc">Price: High to Low</option>
              <option value="name-asc">Name: A-Z</option>
              <option value="name-desc">Name: Z-A</option>
              <option value="newest">Newest First</option>
              <option value="oldest">Oldest First</option>
            </select>
          </div>
          <div className="pl-sidebar-section">
            <h3 className="pl-sidebar-title">Search</h3>
            <input type="text" placeholder="Search products..." value={search} onChange={(e) => { setSearch(e.target.value); setPage(1); }} className="pl-sidebar-input" />
          </div>
          <button className="pl-sidebar-clear" onClick={() => { setCategory(""); setSort(""); setSearch(""); setPage(1); }}>Clear All Filters</button>
        </aside>

        <div className="pl-content">
          <div className="pl-toolbar">
            <button className="pl-mobile-filter-btn" onClick={() => setMobileFilterOpen(true)}>Filters</button>
            <select value={sort} onChange={(e) => { setSort(e.target.value); setPage(1); }} className="pl-sort-select">
              <option value="">Sort by</option>
              <option value="price-asc">Price: Low to High</option>
              <option value="price-desc">Price: High to Low</option>
              <option value="name-asc">Name: A-Z</option>
              <option value="name-desc">Name: Z-A</option>
              <option value="newest">Newest First</option>
              <option value="oldest">Oldest First</option>
            </select>
          </div>

          {loading ? (
            <div className="pt-grid">
              {Array.from({ length: 8 }).map((_, i) => <LoadingSkeleton key={i} />)}
            </div>
          ) : error ? (
            <div className="products-error">
              <div className="products-error-icon">!</div>
              <h3 className="products-error-title">Something went wrong</h3>
              <p className="products-error-text">{error}</p>
              <button className="products-error-retry" onClick={() => window.location.reload()}>Try Again</button>
            </div>
          ) : products.length === 0 ? (
            <div className="products-empty">
              <div className="products-empty-icon">📦</div>
              <h3 className="products-empty-title">No products found</h3>
              <p className="products-empty-text">Try a different category or filter.</p>
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

      <style>{`
        .pl-layout { display: flex; gap: 1.5rem; align-items: flex-start; }
        .pl-sidebar { width: 240px; flex-shrink: 0; background: #fff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 1.25rem; position: sticky; top: 110px; }
        .pl-sidebar-section { margin-bottom: 1.25rem; }
        .pl-sidebar-title { font-size: 0.85rem; font-weight: 700; color: #1e293b; margin: 0 0 0.6rem; text-transform: uppercase; letter-spacing: 0.5px; }
        .pl-sidebar-categories { display: flex; flex-direction: column; gap: 2px; max-height: 280px; overflow-y: auto; }
        .pl-sidebar-cat { padding: 0.4rem 0.6rem; font-size: 0.82rem; color: #475569; cursor: pointer; border-radius: 6px; transition: all 0.12s; }
        .pl-sidebar-cat:hover { background: #f1f5f9; }
        .pl-sidebar-cat.active { background: #f0fdf4; font-weight: 600; }
        .pl-sidebar-select { width: 100%; padding: 0.45rem 0.5rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.82rem; }
        .pl-sidebar-input { width: 100%; padding: 0.45rem 0.6rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.82rem; }
        .pl-sidebar-clear { width: 100%; padding: 0.45rem; background: none; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.78rem; cursor: pointer; }
        .pl-content { flex: 1; min-width: 0; }
        .pl-toolbar { display: none; align-items: center; gap: 0.75rem; margin-bottom: 1rem; }
        .pl-mobile-filter-btn { display: none; padding: 0.45rem 0.75rem; background: #fff; border: 1px solid #d1d5db; border-radius: 8px; font-size: 0.82rem; cursor: pointer; }
        .pl-sort-select { padding: 0.45rem 0.6rem; border: 1px solid #d1d5db; border-radius: 8px; font-size: 0.82rem; background: #fff; }
        .products-error { padding: 40px; text-align: center; }
        .products-error-icon { font-size: 48px; color: #dc2626; margin-bottom: 16px; }
        .products-error-title { font-size: 1.25rem; font-weight: 700; margin-bottom: 8px; }
        .products-error-text { font-size: 0.875rem; color: #64748b; margin-bottom: 16px; }
        .products-error-retry { padding: 8px 20px; border: 1px solid #dc2626; border-radius: 8px; background: #fff; color: #dc2626; cursor: pointer; }
        .products-empty { padding: 40px; text-align: center; }
        .products-empty-icon { font-size: 48px; margin-bottom: 16px; opacity: 0.5; }
        .products-empty-title { font-size: 1.25rem; font-weight: 700; margin-bottom: 8px; }
        .products-empty-text { font-size: 0.875rem; color: #64748b; }
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
      `}</style>
    </div>
  );
};

export default ProductList;
