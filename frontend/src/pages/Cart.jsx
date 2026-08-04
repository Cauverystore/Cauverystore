import React, { useState, useEffect, useCallback } from "react";
import { useNavigate, Link } from "react-router-dom";
import { Heart, Trash2, Bookmark } from "lucide-react";
import api from "../api/axios";
import CartItemImage from "../components/CartItemImage";
import { useWishlist } from "../context/WishlistContext";
import "../styles/cart.css";

const Cart = () => {
  const [cart, setCart] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);
  const [freqItems, setFreqItems] = useState([]);
  const [freqLoading, setFreqLoading] = useState(false);
  const [promoCode, setPromoCode] = useState("");
  const [promoMsg, setPromoMsg] = useState(null);
  const [appliedCoupon, setAppliedCoupon] = useState(null);
  const [actionLoading, setActionLoading] = useState({});
  const { ids: wishlistedIds, toggle: toggleWishlistCtx } = useWishlist();
  const navigate = useNavigate();

  const setAction = (id, loading) => setActionLoading(prev => ({ ...prev, [id]: loading }));

  const fetchCart = useCallback(async () => {
    setError(null);
    try {
      const res = await api.get("/api/cart");
      setCart(res.data);
    } catch (err) {
        void err;
      setError("Failed to load cart. Please try again.");
    }
    setLoading(false);
  }, []);

  useEffect(() => { fetchCart(); }, [fetchCart]);

  useEffect(() => {
    const fetchFreq = async () => {
      setFreqLoading(true);
      try {
        const res = await api.get("/api/cart/frequently-bought");
        const data = res.data;
        setFreqItems(Array.isArray(data) ? data : data?.products || data?.content || []);
      } catch (err) {
                void err;
      }
      setFreqLoading(false);
    };
    fetchFreq();
  }, []);

  const updateQty = async (itemId, qty) => {
    if (qty < 1) return;
    const key = `qty-${itemId}`;
    setAction(key, true);
    setError(null);
    try {
      await api.post(`/api/cart/update-quantity/${itemId}?quantity=${qty}`);
      await fetchCart();
    } catch (err) {
                void err;
      setError("Failed to update quantity. Please try again.");
    }
    setAction(key, false);
  };

  const removeItem = async (itemId) => {
    const key = `remove-${itemId}`;
    setAction(key, true);
    setError(null);
    try {
      await api.delete(`/api/cart/remove/${itemId}`);
      await fetchCart();
    } catch (err) {
                void err;
      setError("Failed to remove item. Please try again.");
    }
    setAction(key, false);
  };

  const saveForLater = async (itemId) => {
    const key = `save-${itemId}`;
    setAction(key, true);
    setError(null);
    setSuccessMsg(null);
    try {
      await api.post(`/api/cart/save-for-later/${itemId}`);
      await fetchCart();
      setSuccessMsg("Item moved to Save for Later.");
    } catch (err) {
                void err;
      setError("Failed to save item for later. Please try again.");
    }
    setAction(key, false);
  };

  const toggleWishlist = async (productId) => {
    if (!productId) return;
    const key = `wishlist-${productId}`;
    setAction(key, true);
    setError(null);
    try {
      await toggleWishlistCtx(productId);
    } catch (err) {
      void err;
      setError("Failed to update wishlist. Please try again.");
    }
    setAction(key, false);
  };

  const moveToCart = async (itemId) => {
    const key = `move-${itemId}`;
    setAction(key, true);
    setError(null);
    setSuccessMsg(null);
    try {
      await api.post(`/api/cart/move-to-cart/${itemId}`);
      await fetchCart();
      setSuccessMsg("Item moved back to your cart.");
    } catch (err) {
                void err;
      setError("Failed to move item to cart. Please try again.");
    }
    setAction(key, false);
  };

  const addFreqToCart = async (productId) => {
    const key = `freq-${productId}`;
    setAction(key, true);
    setError(null);
    try {
      await api.post(`/api/cart/add?productId=${productId}&quantity=1`);
      await fetchCart();
    } catch (err) {
                void err;
      setError("Failed to add item to cart. Please try again.");
    }
    setAction(key, false);
  };

  const buyNowFreq = async (productId) => {
    try {
      await api.post(`/api/cart/add?productId=${productId}&quantity=1`);
      navigate("/checkout");
    } catch (err) {
                void err;
      setError("Failed to process Buy Now. Please try again.");
    }
  };

  const applyCoupon = async () => {
    if (!promoCode.trim()) return;
    setPromoMsg(null);
    try {
      const res = await api.post("/api/promo/validate", { code: promoCode, cartTotal: subtotal });
      setAppliedCoupon(res.data);
      setPromoMsg({ type: "success", text: `Coupon "${promoCode}" applied!` });
    } catch (err) {
      setAppliedCoupon(null);
      setPromoMsg({ type: "error", text: err.response?.data?.error || err.response?.data?.message || "Invalid or expired coupon" });
    }
  };

  const removeCoupon = () => {
    setAppliedCoupon(null);
    setPromoCode("");
    setPromoMsg({ type: "success", text: "Coupon removed" });
  };

  if (loading) {
    return (
      <div style={{ maxWidth: "var(--container-wide)", margin: "0 auto", padding: "var(--spacing-4)", display: "flex", justifyContent: "center", alignItems: "center", minHeight: "50vh" }}>
        <div className="cart-empty">
          <div style={{ color: "var(--color-text-secondary)" }}>Loading cart...</div>
        </div>
      </div>
    );
  }

  const items = cart?.items || [];
  const savedItems = cart?.savedForLater || [];
  const subtotal = items.reduce((sum, item) => {
    const price = item.product?.price || item.price || 0;
    return sum + price * (item.quantity || 1);
  }, 0);

  let discount = 0;
  if (appliedCoupon) {
    if (appliedCoupon.type === "PERCENTAGE") {
      discount = subtotal * (appliedCoupon.value / 100);
      if (appliedCoupon.maxDiscountAmount) {
        discount = Math.min(discount, appliedCoupon.maxDiscountAmount);
      }
    } else {
      discount = appliedCoupon.value;
    }
  }

  const delivery = subtotal >= 500 ? 0 : 40;
  const taxableAmount = subtotal - discount;
  const tax = Math.round(Math.max(taxableAmount, 0) * 0.12 * 100) / 100;
  const finalTotal = Math.max(subtotal - discount + delivery + tax, 0);
  const shippingProgress = Math.min((subtotal / 500) * 100, 100);

  const safeId = (item) => item.id || item._id;
  const safeProduct = (item) => item.product || {};

  const renderStockStatus = (product) => {
    if (product.stockStatus) {
      const color = product.stockStatus === "Out of Stock" ? "#94a3b8"
        : product.stockStatus.includes("Only") ? "#d97706" : "#16a34a";
      return <span style={{ color, fontSize: "0.8rem", fontWeight: 500 }}>{product.stockStatus}</span>;
    }
    return null;
  };

  const isLoading = (key) => actionLoading[key];

  const freqItemsToShow = freqItems.slice(0, 3);

  if (items.length === 0 && !error) {
    return (
      <div style={{ maxWidth: "var(--container-wide)", margin: "0 auto", padding: "var(--spacing-4)" }}>
        <h1 className="cart-title">Shopping Cart</h1>
        <div className="cart-empty">
          <span className="cart-empty-icon">&#128722;</span>
          <span className="cart-empty-text">Your cart is empty.</span>
        </div>
      </div>
    );
  }

  if (items.length === 0 && error) {
    return (
      <div style={{ maxWidth: "var(--container-wide)", margin: "0 auto", padding: "var(--spacing-4)" }}>
        <h1 className="cart-title">Shopping Cart</h1>
        <div className="cart-summary-coupon-applied" style={{ justifyContent: "space-between" }}>
          <span>{error}</span>
          <button onClick={() => setError(null)} className="cart-summary-coupon-remove" aria-label="Dismiss error">&times;</button>
        </div>
      </div>
    );
  }

  return (
    <div className="cart-page">
      <div className="cart-header" style={{ gridColumn: "1 / -1" }}>
        <h1 className="cart-title">Shopping Cart</h1>
        <span className="cart-count">{items.length} item{items.length > 1 ? "s" : ""}</span>
      </div>

      {error && (
        <div className="cart-summary-coupon-applied" style={{ gridColumn: "1 / -1", justifyContent: "space-between" }}>
          <span>{error}</span>
          <button onClick={() => setError(null)} className="cart-summary-coupon-remove" aria-label="Dismiss error">&times;</button>
        </div>
      )}
      {successMsg && (
        <div className="cart-summary-coupon-applied" style={{ gridColumn: "1 / -1", justifyContent: "space-between" }}>
          <span>{successMsg}</span>
          <button onClick={() => setSuccessMsg(null)} className="cart-summary-coupon-remove" aria-label="Dismiss message">&times;</button>
        </div>
      )}
          {/* ─── LEFT COLUMN: Cart items → Checkout → Continue → Freq Bought ─── */}
          <div className="cart-left-col">
            {subtotal < 500 && (
              <div className="cart-shipping-progress">
                <div className="cart-shipping-progress-text">
                  {subtotal === 0 ? "Add items to get " : <>Add <strong>&#8377;{(500 - subtotal).toFixed(2)}</strong> more for </>}
                  FREE Delivery
                </div>
                <div className="cart-shipping-progress-bar">
                  <div className={`cart-shipping-progress-fill ${shippingProgress >= 100 ? "complete" : ""}`} style={{ width: `${shippingProgress}%` }} />
                </div>
              </div>
            )}
            {subtotal >= 500 && (
              <div className="cart-shipping-progress">
                <div className="cart-shipping-progress-free">&#10003; Your order qualifies for FREE Delivery!</div>
                <div className="cart-shipping-progress-bar">
                  <div className="cart-shipping-progress-fill complete" style={{ width: "100%" }} />
                </div>
              </div>
            )}

            <div className="cart-items-section">
              {items.map((item) => {
                const product = safeProduct(item);
                const itemId = safeId(item);
                const price = product.price || item.price || 0;
                const originalPrice = product.originalPrice || product.mrp || 0;
                const image = product.image || product.images?.[0] || "";
                const qty = item.quantity || 1;
                const lineTotal = price * qty;
                const deliveryEst = item.deliveryEstimate || cart?.deliveryDate || "Get it within 5-7 days";

                return (
                  <div key={itemId} className="cart-item">
                    <div className="cart-item-image">
                      <CartItemImage src={image} name={product.name} width={100} height={100} className="cart-item-img" />
                    </div>
                    <div className="cart-item-info">
                      {product.brand && <div className="cart-item-brand">{product.brand}</div>}
                      <div className="cart-item-title">{product.name || "Product"}</div>
                      <div className="cart-item-attrs">
                        <div className="cart-item-attr"><span className="cart-item-attr-label">Brand</span><span className="cart-item-attr-value">{product.brand || "Not specified"}</span></div>
                        <div className="cart-item-attr"><span className="cart-item-attr-label">Color</span><span className="cart-item-attr-value">{product.color || "Not specified"}</span></div>
                        <div className="cart-item-attr"><span className="cart-item-attr-label">Size</span><span className="cart-item-attr-value">{product.size || "Not specified"}</span></div>
                        <div className="cart-item-attr"><span className="cart-item-attr-label">Material</span><span className="cart-item-attr-value">{product.material || "Not specified"}</span></div>
                        <div className="cart-item-attr"><span className="cart-item-attr-label">Weight</span><span className="cart-item-attr-value">{product.weight ? `${product.weight} g` : "Not specified"}</span></div>
                      </div>
                      {item.variantName && <div className="cart-item-variant">{item.variantName}</div>}
                      <div>
                        <span className="cart-item-price">&#8377;{price.toFixed(2)}</span>
                        {originalPrice > price && <span className="cart-item-original-price">&#8377;{originalPrice.toFixed(2)}</span>}
                      </div>
                      {renderStockStatus(product)}
                      <div style={{ fontSize: "0.8rem", color: "#16a34a", marginTop: "0.2rem" }}>{deliveryEst}</div>
                      <div className="cart-item-actions">
                        <div className="cart-item-qty">
                          <button className="cart-item-qty-btn" onClick={() => updateQty(itemId, qty - 1)}
                            disabled={qty <= 1 || isLoading(`qty-${itemId}`)} aria-label={`Decrease quantity of ${product.name || "item"}`}>-</button>
                          <input className="cart-item-qty-value" type="number" value={qty} readOnly aria-label={`Quantity of ${product.name || "item"}`} />
                          <button className="cart-item-qty-btn" onClick={() => updateQty(itemId, qty + 1)}
                            disabled={isLoading(`qty-${itemId}`)} aria-label={`Increase quantity of ${product.name || "item"}`}>+</button>
                        </div>
                        <div className="cart-item-secondary-actions">
                          <button
                            className={`cart-item-action-btn wishlist ${wishlistedIds.has(product.id) ? "active" : ""}`}
                            onClick={() => toggleWishlist(product.id)}
                            disabled={isLoading(`wishlist-${product.id}`)}
                          >
                            <Heart size={14} fill={wishlistedIds.has(product.id) ? "currentColor" : "none"} />
                            {wishlistedIds.has(product.id) ? "Wishlisted" : "Wishlist"}
                          </button>
                          <button className={`cart-item-action-btn save ${isLoading(`save-${itemId}`) ? "disabled" : ""}`}
                            onClick={() => saveForLater(itemId)} disabled={isLoading(`save-${itemId}`)}>
                            <Bookmark size={14} />
                            {isLoading(`save-${itemId}`) ? "Saving..." : "Save for Later"}
                          </button>
                          <button className={`cart-item-action-btn delete ${isLoading(`remove-${itemId}`) ? "disabled" : ""}`}
                            onClick={() => removeItem(itemId)} disabled={isLoading(`remove-${itemId}`)}>
                            <Trash2 size={14} />
                            {isLoading(`remove-${itemId}`) ? "Removing..." : "Delete"}
                          </button>
                        </div>
                        <button className="cart-item-action-btn buy-now"
                          onClick={() => navigate("/checkout")}>
                          Buy Now
                        </button>
                      </div>
                    </div>
                    <div className="cart-item-desktop-price" style={{ fontSize: "1rem", fontWeight: 700, whiteSpace: "nowrap" }}>
                      &#8377;{lineTotal.toFixed(2)}
                    </div>
                    <div className="cart-item-mobile-price">
                      &#8377;{lineTotal.toFixed(2)}
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Proceed to Buy — directly under cart items */}
            <button className="cart-checkout-btn" onClick={() => navigate("/checkout")}>
              Proceed to Buy ({items.length} item{items.length > 1 ? "s" : ""})
            </button>

            <div style={{ marginTop: "0.75rem" }}>
              <Link to="/products" className="cart-continue-link">
                &larr; Continue Shopping
              </Link>
            </div>

            {savedItems.length > 0 && (
              <div className="save-later-section">
                <h3 className="save-later-title">
                  Saved for Later <span className="save-later-title-count">({savedItems.length} item{savedItems.length > 1 ? "s" : ""})</span>
                </h3>
                <div className="save-later-grid">
                  {savedItems.map((item) => {
                    const product = safeProduct(item);
                    const itemId = safeId(item);
                    const image = product.image || product.images?.[0] || "";
                    const price = product.price || item.price || 0;
                    return (
                      <div key={itemId} className="save-later-item">
                        <div className="save-later-item-image">
                          <CartItemImage src={image} name={product.name} width={100} height={100} className="cart-item-img" />
                        </div>
                        <div className="save-later-item-body">
                          <div className="save-later-item-title">{product.name || "Product"}</div>
                          <div className="save-later-item-price">&#8377;{price.toFixed(2)}</div>
                          <button className="save-later-item-move" onClick={() => moveToCart(itemId)}
                            disabled={isLoading(`move-${itemId}`)}>
                            {isLoading(`move-${itemId}`) ? "Moving..." : "Move to Cart"}
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Frequently Bought Together — below the checkout button */}
            {!freqLoading && freqItemsToShow.length > 0 && (
              <div className="save-later-section">
                <h3 className="save-later-title">Frequently Bought Together</h3>
                <div className="save-later-grid">
                  {freqItemsToShow.map((prod) => {
                    const pid = prod.id || prod._id;
                    const image = prod.image || prod.images?.[0] || "";
                    return (
                      <div key={pid} className="save-later-item">
                        <div className="save-later-item-image">
                          <CartItemImage src={image} name={prod.name} width={100} height={100} className="cart-item-img" />
                        </div>
                        <div className="save-later-item-body">
                          <div className="save-later-item-title">{prod.name}</div>
                          <div className="save-later-item-price">&#8377;{(prod.price || prod.dealPrice || 0).toFixed(2)}</div>
                          <div style={{ display: "flex", gap: "4px", marginTop: "0.5rem" }}>
                            <button className="save-later-item-move" onClick={() => addFreqToCart(pid)}
                              disabled={isLoading(`freq-${pid}`)} style={{ flex: 1, fontSize: "0.72rem" }}>
                              {isLoading(`freq-${pid}`) ? "Adding..." : "Add to Cart"}
                            </button>
                            <button className="save-later-item-move" onClick={() => buyNowFreq(pid)}
                              disabled={isLoading(`freq-${pid}`)} style={{ flex: 1, fontSize: "0.72rem" }}>
                              Buy Now
                            </button>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>

          {/* ─── RIGHT COLUMN: Price Details ─── */}
          <aside className="cart-summary">
            <h3 className="cart-summary-title">Price Details</h3>

            <div className="cart-summary-row">
              <span className="cart-summary-row-label">Price ({items.length} item{items.length > 1 ? "s" : ""})</span>
              <span className="cart-summary-row-value">&#8377;{subtotal.toFixed(2)}</span>
            </div>

            {discount > 0 && (
              <div className="cart-summary-row">
                <span className="cart-summary-row-label">Discount</span>
                <span className="cart-summary-row-value cart-summary-discount">-&#8377;{discount.toFixed(2)}</span>
              </div>
            )}

            <div className="cart-summary-row">
              <span className="cart-summary-row-label">Delivery</span>
              <span className="cart-summary-row-value">{delivery === 0 ? <span className="cart-summary-discount">FREE</span> : `\u20B9${delivery.toFixed(2)}`}</span>
            </div>

            <div className="cart-summary-row">
              <span className="cart-summary-row-label">Tax (12%)</span>
              <span className="cart-summary-row-value">&#8377;{tax.toFixed(2)}</span>
            </div>

            <div className="cart-summary-divider" />

            <div className="cart-summary-total-row">
              <span>Total Amount</span>
              <span className="cart-summary-total-value">&#8377;{finalTotal.toFixed(2)}</span>
            </div>

            {discount > 0 && (
              <div className="cart-summary-savings">
                You're saving &#8377;{discount.toFixed(2)} on this order!
              </div>
            )}

            <div className="cart-summary-coupon">
              <input
                value={promoCode}
                onChange={(e) => setPromoCode(e.target.value)}
                placeholder="Enter coupon code"
                aria-label="Enter coupon code"
                disabled={!!appliedCoupon}
              />
              {!appliedCoupon ? (
                <button className="cart-summary-coupon-btn" onClick={applyCoupon}>Apply</button>
              ) : (
                <button className="cart-summary-coupon-btn cart-summary-coupon-remove" onClick={removeCoupon}>Remove</button>
              )}
            </div>
            {promoMsg && (
              <div style={{ fontSize: "0.8rem", marginBottom: "0.75rem", color: promoMsg.type === "success" ? "#16a34a" : "#92400e" }}>
                {promoMsg.text}
              </div>
            )}

            <div className="cart-trust-row">
              <span className="cart-trust-badge">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="M9 12l2 2 4-4"/></svg>
                Secure Payment
              </span>
              <span className="cart-trust-badge">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M1 4v6h6"/><path d="M3.51 15a9 9 0 102.13-9.36L1 10"/></svg>
                Easy Returns
              </span>
              <span className="cart-trust-badge">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                100% Authentic
              </span>
            </div>

            <div className="cart-summary-secure">
              <span>&#128274;</span> Secure Payment &middot; Easy Returns
            </div>
            <div className="cart-summary-accepted-cards">
              <span>&#9829;</span> Cards &middot; UPI &middot; Net Banking &middot; COD
            </div>
          </aside>


    </div>
  );
};

export default Cart;
