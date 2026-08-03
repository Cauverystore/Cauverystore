import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { getWishlist, removeFromWishlist } from "../services/wishlistService";
import { useWishlist } from "../context/WishlistContext";
import "../styles/account.css";
import "../styles/product-card.css";

const Wishlist = () => {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const { refresh: refreshWishlistCtx } = useWishlist();

  useEffect(() => {
    const fetch = async () => {
      try {
        const res = await getWishlist();
        setItems(res.data || []);
      } catch (err) {
        setError("Failed to load wishlist");
      }
      setLoading(false);
    };
    fetch();
  }, []);

  const handleRemove = async (productId) => {
    try {
      await removeFromWishlist(productId);
      setItems((prev) => prev.filter((i) => (i.product?.id || i.product?._id || i.id || i._id) !== productId));
      refreshWishlistCtx();
    } catch (err) {
      setError("Failed to remove item");
    }
  };

  if (loading) {
    return (
      <div className="account-page">
        <div className="account-content">
          <div className="products-loading-grid">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="product-card-skeleton">
                <div className="product-card-skeleton-image" />
                <div className="product-card-skeleton-body">
                  <div className="product-card-skeleton-line" />
                  <div className="product-card-skeleton-line" />
                  <div className="product-card-skeleton-line" />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="account-page">
      <div className="account-content">
        <h1 className="account-page-title">My Wishlist</h1>

        {error && <div style={{ marginBottom: "1rem", padding: "0.75rem", background: "#fef2f2", borderRadius: 8, color: "#dc2626" }}>{error}</div>}

        {items.length === 0 ? (
          <div className="wishlist-empty">
            <div className="wishlist-empty-icon">❤️</div>
            <h3 className="wishlist-empty-title">Your wishlist is empty</h3>
            <p className="wishlist-empty-text">Save items you love to your wishlist and find them here.</p>
            <Link to="/products" className="products-empty-action">Browse Products</Link>
          </div>
        ) : (
          <div className="wishlist-grid">
            {items.map((item) => {
              const p = item.product || item;
              const pid = p.id || p._id;
              return (
                <div key={pid} className="product-card">
                  <Link to={`/product/${pid}`} style={{ textDecoration: "none" }}>
                    <div className="product-card-image-wrap">
                      <img
                        loading="lazy"
                        width="200"
                        height="200"
                        className="product-card-image"
                        src={p.image || p.images?.[0]?.url || p.images?.[0] || "/images/placeholder.svg"}
                        alt={p.name}
                        onError={(e) => { e.target.onerror = null; e.target.src = "/images/placeholder.svg"; }}
                      />
                    </div>
                  </Link>
                  <div className="product-card-body">
                    <Link to={`/product/${pid}`} style={{ textDecoration: "none" }}>
                      <div className="product-card-title">{p.name}</div>
                    </Link>
                    <div className="product-card-price-row">
                      <span className="product-card-price">₹{p.dealPrice || p.price || 0}</span>
                      {p.originalPrice && <span className="product-card-original-price">₹{p.originalPrice}</span>}
                    </div>
                    <button className="product-card-add-cart" onClick={() => handleRemove(pid)} style={{ borderColor: "#dc2626", color: "#dc2626", background: "#fff" }}
                      onMouseEnter={(e) => { e.target.style.background = "#dc2626"; e.target.style.color = "#fff"; }}
                      onMouseLeave={(e) => { e.target.style.background = "#fff"; e.target.style.color = "#dc2626"; }}
                    >Remove</button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
export default Wishlist;
