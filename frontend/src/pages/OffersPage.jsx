import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api/axios";
import { addToCart } from "../services/cartService";
import ProductTray, { LoadingSkeleton } from "../components/ProductTray";
import Pagination from "../components/Pagination";
import "../styles/products.css";
import "../styles/product-tray.css";

const PAGE_SIZE = 12;

const OffersPage = () => {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [cartMsg, setCartMsg] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    const fetch = async () => {
      setLoading(true);
      try {
        const res = await api.get("/api/products/search", {
          params: { page: page - 1, size: PAGE_SIZE, sortBy: "id", direction: "desc" }
        });
        const all = res.data.content || [];
        const withDiscount = all.filter(p => {
          const ds = p.discounts;
          return ds && ds.length > 0 && ds.some(d => d.active);
        });
        setProducts(withDiscount.length > 0 ? withDiscount : all);
        setTotalPages(res.data.totalPages || 1);
      } catch {
        setProducts([]);
      }
      setLoading(false);
    };
    fetch();
  }, [page]);

  const handleAddToCart = async (product) => {
    const token = localStorage.getItem("accessToken");
    if (!token) { navigate("/login"); return; }
    try {
      await addToCart(product.id || product._id, 1);
      setCartMsg(`${product.name} added to cart!`);
      setTimeout(() => setCartMsg(null), 2500);
    } catch {
      setCartMsg("Failed to add to cart");
      setTimeout(() => setCartMsg(null), 2500);
    }
  };

  const handleBuyNow = async (product) => {
    const token = localStorage.getItem("accessToken");
    if (!token) { navigate("/login"); return; }
    try {
      await addToCart(product.id || product._id, 1);
      navigate("/checkout");
    } catch {
      setCartMsg("Failed to add to cart");
      setTimeout(() => setCartMsg(null), 2500);
    }
  };

  return (
    <div className="products-page">
      {cartMsg && <div className="pt-toast" role="status" aria-live="polite">{cartMsg}</div>}
      <div className="section-header">
        <h2 className="section-title">Deals of the Day</h2>
        <span className="products-toolbar-count">{products.length} products</span>
      </div>

      {loading ? (
        <div className="pt-grid">
          {Array.from({ length: 8 }).map((_, i) => <LoadingSkeleton key={i} />)}
        </div>
      ) : products.length === 0 ? (
        <div className="products-empty">
          <div className="products-empty-icon">&#128722;</div>
          <h3 className="products-empty-title">No deals available right now</h3>
          <p className="products-empty-text">Check back later for fresh deals.</p>
          <button className="products-empty-action" onClick={() => navigate("/products")}>Browse All Products</button>
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

      <style>{`
        .section-header { display: flex; align-items: baseline; gap: 0.75rem; margin-bottom: 1rem; padding: 0 1.5rem; }
        .section-title { font-size: 1.3rem; font-weight: 700; color: #0f172a; }
        .products-toolbar-count { font-size: 0.85rem; color: #64748b; }
        .pt-toast { position: fixed; top: 100px; right: 20px; z-index: 9999; padding: 12px 20px; border-radius: 8px; font-weight: 600; background: #16a34a; color: #fff; box-shadow: 0 4px 12px rgba(0,0,0,0.15); }
        .products-empty { text-align: center; padding: 3rem; }
        .products-empty-icon { font-size: 3rem; margin-bottom: 0.5rem; }
        .products-empty-title { font-size: 1.2rem; font-weight: 600; color: #0f172a; margin-bottom: 0.5rem; }
        .products-empty-text { color: #64748b; margin-bottom: 1rem; }
        .products-empty-action { display: inline-block; padding: 0.6rem 1.5rem; background: var(--color-primary); color: #fff; border: none; border-radius: 8px; font-weight: 600; cursor: pointer; }
      `}</style>
    </div>
  );
};

export default OffersPage;
