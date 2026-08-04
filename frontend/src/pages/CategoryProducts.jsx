import React, { useState, useEffect } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { searchProducts } from "../services/productService";
import { addToCartOrLogin } from "../utils/cartActions";
import ProductTray, { LoadingSkeleton } from "../components/ProductTray";
import Breadcrumb from "../components/Breadcrumb";
import Pagination from "../components/Pagination";
import "../styles/products.css";
import "../styles/product-tray.css";

const CategoryProducts = () => {
  const { category } = useParams();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const navigate = useNavigate();

  useEffect(() => {
    const fetch = async () => {
      setLoading(true);
      setError("");
      try {
        const res = await searchProducts({ category, page: page - 1, size: 20 });
        setProducts(res.data.content || res.data || []);
        setTotalPages(res.data.totalPages || 1);
      } catch (err) {
        setError(err.response?.data?.error || "Failed to load products");
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

  return (
    <div className="products-page">
      <style>{`.cat-products-content { grid-column: 1 / -1; }`}</style>
      <Helmet>
        <title>{category} - Cauvery Store</title>
        <meta name="description" content={`Shop ${category} at Cauvery Store - great deals and fast delivery`} />
      </Helmet>
      <div className="cat-products-content">
        <Breadcrumb items={[
          { label: "Home", to: "/" },
          { label: category }
        ]} />
        <div className="section-header">
          <h2 className="section-title">{category}</h2>
          <span className="products-toolbar-count">{products.length} products</span>
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
            <h3 className="products-error-title">Something went wrong</h3>
            <p className="products-error-text">{error}</p>
            <button className="products-error-retry" onClick={() => window.location.reload()}>Try Again</button>
          </div>
        ) : products.length === 0 ? (
          <div className="products-empty">
            <div className="products-empty-icon">📦</div>
            <h3 className="products-empty-title">No products in this category</h3>
            <p className="products-empty-text">Try browsing other categories.</p>
            <Link to="/products" className="products-empty-action">Browse All Products</Link>
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
