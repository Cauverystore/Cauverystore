import React, { useState, useEffect, useCallback } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import api from "../api/axios";
import { addToCart } from "../services/cartService";
import Pagination from "../components/Pagination";
import ProductTray, { LoadingSkeleton } from "../components/ProductTray";
import "../styles/product-tray.css";
import "../styles/products.css";

const ProductList = () => {
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState("");
  const [category, setCategory] = useState("");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [cartMessage, setCartMessage] = useState(null);
  const [mobileFilterOpen, setMobileFilterOpen] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  useEffect(() => {
    const catParam = searchParams.get("category");
    if (catParam) setCategory(catParam);
  }, [searchParams]);

  useEffect(() => {
    const fetchCategories = async () => {
      try {
        const res = await api.get("/api/categories");
        setCategories(Array.isArray(res.data) ? res.data : res.data?.content || res.data?.categories || []);
      } catch (err) { void err; }
    };
    fetchCategories();
  }, []);

  const fetchProducts = useCallback(async () => {
    setLoading(true);
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
      const data = res.data;
      setProducts(data.content || []);
      setTotalPages(data.totalPages || 1);
    } catch (err) { void err; setProducts([]); }
    setLoading(false);
  }, [search, sort, category, page]);

  useEffect(() => { fetchProducts(); }, [fetchProducts]);

  const handleAddToCart = async (product) => {
    try {
      await addToCart(product.id || product._id, 1);
      setCartMessage(`${product.name} added to cart!`);
      setTimeout(() => setCartMessage(null), 2500);
    } catch (err) {
      setCartMessage("Failed to add to cart");
      setTimeout(() => setCartMessage(null), 2500);
    }
  };

  const handleBuyNow = async (product) => {
    try {
      await addToCart(product.id || product._id, 1);
      navigate("/checkout");
    } catch (err) {
      setCartMessage("Failed to add to cart");
      setTimeout(() => setCartMessage(null), 2500);
    }
  };

  const handleSearch = (e) => { setSearch(e.target.value); setPage(1); };
  const handleSort = (e) => { setSort(e.target.value); setPage(1); };
  const handleCategory = (value) => { setCategory(value); setPage(1); };

  const categoryValue = (cat) => cat.id || cat._id || cat.name || cat;
  const categoryLabel = (cat) => cat.name || cat.title || cat;
  const skeletonCount = 8;

  return (
    <div className="products-page">
      {cartMessage && <div className="pt-toast">{cartMessage}</div>}

      <div className="pl-layout">
        {/* Sidebar Filters - Desktop */}
        <aside className="pl-sidebar">
          <div className="pl-sidebar-section">
            <h3 className="pl-sidebar-title">Category</h3>
            <div className="pl-sidebar-categories">
              <div
                className={`pl-sidebar-cat ${category === "" ? "active" : ""}`}
                onClick={() => handleCategory("")}
              >All Categories</div>
              {categories.map((cat) => (
                <div
                  key={categoryValue(cat)}
                  className={`pl-sidebar-cat ${category === categoryValue(cat) ? "active" : ""}`}
                  onClick={() => handleCategory(categoryValue(cat))}
                >{categoryLabel(cat)}</div>
              ))}
            </div>
          </div>

          <div className="pl-sidebar-section">
            <h3 className="pl-sidebar-title">Sort By</h3>
            <select value={sort} onChange={handleSort} className="pl-sidebar-select">
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
            <input
              type="text"
              placeholder="Search products..."
              value={search}
              onChange={handleSearch}
              className="pl-sidebar-input"
            />
          </div>

          <button className="pl-sidebar-clear" onClick={() => { setCategory(""); setSort(""); setSearch(""); setPage(1); }}>
            Clear All Filters
          </button>
        </aside>

        {/* Product Grid */}
        <div className="pl-content">
          {/* Mobile filter toggle + sort bar */}
          <div className="pl-toolbar">
            <button className="pl-mobile-filter-btn" onClick={() => setMobileFilterOpen(true)}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="4" y1="6" x2="20" y2="6"/><line x1="8" y1="12" x2="20" y2="12"/><line x1="12" y1="18" x2="20" y2="18"/></svg>
              Filters
            </button>
            <select value={sort} onChange={handleSort} className="pl-sort-select">
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
              {Array.from({ length: skeletonCount }).map((_, i) => <LoadingSkeleton key={i} />)}
            </div>
          ) : products.length === 0 ? (
            <div style={{ textAlign: "center", padding: "3rem" }}>
              <div style={{ fontSize: "3rem", marginBottom: "0.75rem", opacity: 0.4 }}>&#128230;</div>
              <h3 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "0.5rem", color: "#0f172a" }}>
                {category ? `No products in "${category}"` : "No products found"}
              </h3>
              <p style={{ color: "#64748b", marginBottom: "1rem" }}>Try a different category or filter.</p>
              {category && <button className="products-empty-action" onClick={() => { setCategory(""); setSearch(""); setSort(""); setPage(1); }}
                style={{ padding: "0.5rem 1.25rem", background: "var(--color-primary)", color: "#fff", border: "none", borderRadius: 8, fontWeight: 600, cursor: "pointer" }}>
                Clear Filters
              </button>}
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

      {/* Mobile filter drawer */}
      <div className={`mobile-filter-overlay ${mobileFilterOpen ? "open" : ""}`} onClick={() => setMobileFilterOpen(false)} />
      <div className={`mobile-filter-drawer ${mobileFilterOpen ? "open" : ""}`}>
        <div className="mobile-filter-header">
          <h3>Filters</h3>
          <button className="mobile-filter-close" onClick={() => setMobileFilterOpen(false)} aria-label="Close filters">&times;</button>
        </div>
        <div className="products-filter-sidebar">
          <div className="products-filter-section">
            <div className="products-filter-title">Category</div>
            <div className={`products-filter-category ${category === "" ? "active" : ""}`} onClick={() => { handleCategory(""); setMobileFilterOpen(false); }}>All Categories</div>
            {categories.map((cat) => (
              <div key={categoryValue(cat)} className={`products-filter-category ${category === categoryValue(cat) ? "active" : ""}`} onClick={() => { handleCategory(categoryValue(cat)); setMobileFilterOpen(false); }}>
                {categoryLabel(cat)}
              </div>
            ))}
          </div>
          <div className="products-filter-actions">
            <button className="products-filter-clear" onClick={() => { setCategory(""); setSort(""); setSearch(""); setPage(1); setMobileFilterOpen(false); }}>Clear All</button>
            <button className="products-filter-apply" onClick={() => setMobileFilterOpen(false)}>Apply</button>
          </div>
        </div>
      </div>

      <style>{`
        .pl-layout { display: flex; gap: 1.5rem; align-items: flex-start; }
        .pl-sidebar { width: 240px; flex-shrink: 0; background: #fff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 1.25rem; position: sticky; top: 110px; }
        .pl-sidebar-section { margin-bottom: 1.25rem; }
        .pl-sidebar-title { font-size: 0.85rem; font-weight: 700; color: #1e293b; margin: 0 0 0.6rem; text-transform: uppercase; letter-spacing: 0.5px; }
        .pl-sidebar-categories { display: flex; flex-direction: column; gap: 2px; max-height: 280px; overflow-y: auto; }
        .pl-sidebar-cat { padding: 0.4rem 0.6rem; font-size: 0.82rem; color: #475569; cursor: pointer; border-radius: 6px; transition: all 0.12s; }
        .pl-sidebar-cat:hover { background: #f1f5f9; color: var(--color-primary); }
        .pl-sidebar-cat.active { background: #f0fdf4; color: var(--color-primary); font-weight: 600; }
        .pl-sidebar-select { width: 100%; padding: 0.45rem 0.5rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.82rem; color: #475569; background: #fff; }
        .pl-sidebar-input { width: 100%; padding: 0.45rem 0.6rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.82rem; outline: none; }
        .pl-sidebar-input:focus { border-color: var(--color-primary); }
        .pl-sidebar-clear { width: 100%; padding: 0.45rem; background: none; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.78rem; color: #64748b; cursor: pointer; }
        .pl-sidebar-clear:hover { border-color: #ef4444; color: #ef4444; }
        .pl-content { flex: 1; min-width: 0; }
        .pl-toolbar { display: none; align-items: center; gap: 0.75rem; margin-bottom: 1rem; }
        .pl-mobile-filter-btn { display: none; padding: 0.45rem 0.75rem; background: #fff; border: 1px solid #d1d5db; border-radius: 8px; font-size: 0.82rem; cursor: pointer; align-items: center; gap: 0.4rem; }
        .pl-sort-select { padding: 0.45rem 0.6rem; border: 1px solid #d1d5db; border-radius: 8px; font-size: 0.82rem; background: #fff; }

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
