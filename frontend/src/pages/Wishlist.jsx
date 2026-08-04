import React, { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { getWishlist } from "../services/wishlistService";
import { addToCartOrLogin } from "../utils/cartActions";
import { useWishlist } from "../context/WishlistContext";
import ProductTray, { LoadingSkeleton } from "../components/ProductTray";
import "../styles/account.css";

const getItemId = (item) => {
  const p = item.product || item;
  return p.id || p._id || item.productId;
};

const Wishlist = () => {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const { ids: wishlistIds, toggle: toggleWishlist } = useWishlist();

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

  // Reflects removals from anywhere in the app (this page's own wishlist-heart toggle
  // included) instead of only reacting to a locally-tracked remove action.
  const displayedItems = items.filter((item) => wishlistIds.has(getItemId(item)));

  const handleAddToCart = async (product) => {
    const res = await addToCartOrLogin(navigate, product, 1);
    if (!res.ok) setError("Failed to add item to cart");
  };

  const handleMoveToCart = async (product) => {
    const pid = product.id || product._id;
    const res = await addToCartOrLogin(navigate, product, 1);
    if (!res.ok) { setError("Failed to move item to cart"); return; }
    await toggleWishlist(pid);
  };

  const handleRemove = async (product) => {
    const pid = product.id || product._id;
    try {
      await toggleWishlist(pid);
    } catch {
      setError("Failed to remove item from wishlist");
    }
  };

  if (loading) {
    return (
      <div className="account-page">
        <div className="account-content">
          <h1 className="account-page-title">My Wishlist</h1>
          <div className="pt-grid">
            {Array.from({ length: 4 }).map((_, i) => <LoadingSkeleton key={i} />)}
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

        {displayedItems.length === 0 ? (
          <div className="wishlist-empty">
            <div className="wishlist-empty-icon">❤️</div>
            <h3 className="wishlist-empty-title">Your wishlist is empty</h3>
            <p className="wishlist-empty-text">Save items you love to your wishlist and find them here.</p>
            <Link to="/products" className="products-empty-action">Browse Products</Link>
          </div>
        ) : (
          <div className="pt-grid">
            {displayedItems.map((item) => {
              const p = item.product || item;
              const pid = getItemId(item);
              return (
                <ProductTray
                  key={pid}
                  product={p}
                  onAddToCart={handleAddToCart}
                  onMoveToCart={handleMoveToCart}
                  onRemove={handleRemove}
                />
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
export default Wishlist;
