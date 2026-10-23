import React, { useState, useEffect, useCallback } from "react";
import { useNavigate, Link } from "react-router-dom";
import { Heart, Trash2, Bookmark } from "lucide-react";
import api from "../api/axios";
import CartItemImage from "../components/CartItemImage";
import ProductTray from "../components/ProductTray";
import ProductRow from "../components/ProductRow";
import { useWishlist } from "../context/WishlistContext";
import { trackEvent } from "../utils/analytics";
import { variantLabel } from "../utils/variant";
import "../styles/cart.css";
import { useTranslation, Trans } from "react-i18next";

const Cart = () => {
  const { t } = useTranslation();

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
      setError(t("cart.weCouldnTLoadYour"));
    }
    setLoading(false);
  }, []);

  useEffect(() => { fetchCart(); }, [fetchCart]);

  useEffect(() => {
    if (cart?.items?.length) {
      trackEvent("view_cart", { currency: "INR", value: cart.subtotal || 0, items: cart.items });
    }
  }, [cart]);

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
      setError(t("cart.weCouldnTUpdateThe"));
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
      setError(t("cart.weCouldnTRemoveThat"));
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
      setSuccessMsg("Saved for later. You'll find it below your cart.");
    } catch (err) {
                void err;
      setError(t("cart.weCouldnTSaveThat"));
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
      setError(t("cart.weCouldnTUpdateYour"));
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
      setSuccessMsg("Moved back to your cart.");
    } catch (err) {
                void err;
      setError(t("cart.weCouldnTMoveThat"));
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
      setError(t("cart.weCouldnTAddThat"));
    }
    setAction(key, false);
  };

  const buyNowFreq = async (productId) => {
    try {
      await api.post(`/api/cart/add?productId=${productId}&quantity=1`);
      navigate("/checkout");
    } catch (err) {
                void err;
      setError(t("cart.weCouldnTStartCheckout"));
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
    setPromoMsg({ type: "success", text: t("cart.couponRemoved") });
  };

  if (loading) {
    return (
      <div style={{ maxWidth: "var(--container-wide)", margin: "0 auto", padding: "var(--spacing-4)", display: "flex", justifyContent: "center", alignItems: "center", minHeight: "50vh" }}>
        <div className="cart-empty">
          <div style={{ color: "var(--color-text-secondary)" }}>{t("cart.loadingYourCart")}</div>
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
  // Prices include GST, so the tax is part of the subtotal, not added to it. The figure comes
  // from the server, which works out each item's rate from its HSN; it covers the undiscounted
  // basket, so it is scaled by the share of value actually being paid. There is no flat-rate
  // fallback: if the server has no figure, none is shown rather than a guess.
  const serverTax = typeof cart?.tax === "number" ? cart.tax : null;
  const retained = subtotal > 0 ? Math.max(taxableAmount, 0) / subtotal : 0;
  const tax = serverTax !== null ? Math.round(serverTax * retained * 100) / 100 : null;
  const finalTotal = Math.max(subtotal - discount + delivery, 0);
  const shippingProgress = Math.min((subtotal / 500) * 100, 100);

  const safeId = (item) => item.id || item._id;
  const safeProduct = (item) => item.product || {};

  const renderStockStatus = (product) => {
    if (product.stockStatus) {
      const color = product.stockStatus === "Out of Stock" ? "#94a3b8"
        : product.stockStatus.includes("Only") ? "#d97706" : "#0E5C5C";
      return <span style={{ color, fontSize: "0.8rem", fontWeight: 500 }}>{product.stockStatus}</span>;
    }
    return null;
  };

  const isLoading = (key) => actionLoading[key];

  const freqItemsToShow = freqItems.slice(0, 3);

  if (items.length === 0 && !error) {
    return (
      <div style={{ maxWidth: "var(--container-wide)", margin: "0 auto", padding: "var(--spacing-4)" }}>
        <h1 className="cart-title">{t("cart.shoppingCart")}</h1>
        <div className="cart-empty">
          <span className="cart-empty-icon">&#128722;</span>
          <span className="cart-empty-text">{t("cart.empty")}</span>
          <span style={{ color: "var(--color-text-secondary)", fontSize: "0.9rem" }}>{t("cart.deliveryIsFreeOnOrders")}</span>
          <Link to="/products" className="products-empty-action" style={{ marginTop: "0.5rem" }}>{t("cartDrawer.browseProducts")}</Link>
        </div>
      </div>
    );
  }

  if (items.length === 0 && error) {
    return (
      <div style={{ maxWidth: "var(--container-wide)", margin: "0 auto", padding: "var(--spacing-4)" }}>
        <h1 className="cart-title">{t("cart.shoppingCart")}</h1>
        <div className="cart-summary-coupon-applied" style={{ justifyContent: "space-between" }}>
          <span>{error}</span>
          <button onClick={() => setError(null)} className="cart-summary-coupon-remove" aria-label={t("cart.dismissError")}>&times;</button>
        </div>
      </div>
    );
  }

  return (
    <div className="cart-page">
      <div className="cart-header" style={{ gridColumn: "1 / -1" }}>
        <h1 className="cart-title">{t("cart.shoppingCart")}</h1>
        <span className="cart-count">{t("cart.itemCount", { count: items.length })}</span>
      </div>

      {error && (
        <div className="cart-summary-coupon-applied" style={{ gridColumn: "1 / -1", justifyContent: "space-between" }}>
          <span>{error}</span>
          <button onClick={() => setError(null)} className="cart-summary-coupon-remove" aria-label={t("cart.dismissError")}>&times;</button>
        </div>
      )}
      {successMsg && (
        <div className="cart-summary-coupon-applied" style={{ gridColumn: "1 / -1", justifyContent: "space-between" }}>
          <span>{successMsg}</span>
          <button onClick={() => setSuccessMsg(null)} className="cart-summary-coupon-remove" aria-label={t("cart.dismissMessage")}>&times;</button>
        </div>
      )}
      {/* ─── LEFT COLUMN: Cart items → Checkout → Continue → Freq Bought ─── */}
      <div className="cart-left-col">
        {subtotal < 500 && (
          <div className="cart-shipping-progress">
            <div className="cart-shipping-progress-text">
              {subtotal === 0 ? t("cart.addItemsForFree") : <Trans i18nKey="cart.addMoreForFree" values={{ amount: (500 - subtotal).toFixed(2) }} components={{ b: <strong /> }} />}</div>
            <div className="cart-shipping-progress-bar">
              <div className={`cart-shipping-progress-fill ${shippingProgress >= 100 ? "complete" : ""}`} style={{ width: `${shippingProgress}%` }} />
            </div>
          </div>
        )}
        {subtotal >= 500 && (
          <div className="cart-shipping-progress">
            <div className="cart-shipping-progress-free">{t("cart.youGetFreeDeliveryOn")}</div>
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
            const deliveryEst = item.deliveryEstimate || cart?.deliveryDate || "Usually delivered within 3-9 business days";

            return (
              <div key={itemId} className="cart-item">
                <div className="cart-item-image">
                  <CartItemImage src={image} name={product.name} width={100} height={100} className="cart-item-img" />
                </div>
                <div className="cart-item-info">
                  {product.brand && <div className="cart-item-brand">{product.brand}</div>}
                  <div className="cart-item-title">{product.name || t("cart.product")}</div>
                  <div className="cart-item-attrs">
                    <div className="cart-item-attr"><span className="cart-item-attr-label">{t("filters.brand")}</span><span className="cart-item-attr-value">{product.brand || t("cart.notSpecified")}</span></div>
                    <div className="cart-item-attr"><span className="cart-item-attr-label">{t("cart.color")}</span><span className="cart-item-attr-value">{product.color || t("cart.notSpecified")}</span></div>
                    <div className="cart-item-attr"><span className="cart-item-attr-label">{t("cart.size")}</span><span className="cart-item-attr-value">{(/size/i.test(item.variant?.type || "") ? item.variant.value : product.size) || t("cart.notSpecified")}</span></div>
                    <div className="cart-item-attr"><span className="cart-item-attr-label">{t("cart.material")}</span><span className="cart-item-attr-value">{product.material || t("cart.notSpecified")}</span></div>
                    <div className="cart-item-attr"><span className="cart-item-attr-label">{t("cart.weight")}</span><span className="cart-item-attr-value">{product.weight ? `${product.weight} g` : t("cart.notSpecified")}</span></div>
                  </div>
                  {variantLabel(item) && <div className="cart-item-variant">{variantLabel(item)}</div>}
                  <div>
                    <span className="cart-item-price">&#8377;{price.toFixed(2)}</span>
                    {originalPrice > price && <span className="cart-item-original-price">&#8377;{originalPrice.toFixed(2)}</span>}
                  </div>
                  {renderStockStatus(product)}
                  <div style={{ fontSize: "0.8rem", color: "#0E5C5C", marginTop: "0.2rem" }}>{deliveryEst}</div>
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
                        {wishlistedIds.has(product.id) ? t("cart.wishlisted") : t("nav.wishlist")}
                      </button>
                      <button className={`cart-item-action-btn save ${isLoading(`save-${itemId}`) ? "disabled" : ""}`}
                        onClick={() => saveForLater(itemId)} disabled={isLoading(`save-${itemId}`)}>
                        <Bookmark size={14} />
                        {isLoading(`save-${itemId}`) ? t("addressBook.saving") : t("cart.saveForLater")}
                      </button>
                      <button className={`cart-item-action-btn delete ${isLoading(`remove-${itemId}`) ? "disabled" : ""}`}
                        onClick={() => removeItem(itemId)} disabled={isLoading(`remove-${itemId}`)}>
                        <Trash2 size={14} />
                        {isLoading(`remove-${itemId}`) ? t("productTray.removing") : t("addressBook.delete")}
                      </button>
                    </div>
                    <button className="cart-item-action-btn buy-now"
                      onClick={() => navigate("/checkout")}>{t("cart.buyNow")}</button>
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
        <button className="cart-checkout-btn" onClick={() => navigate("/checkout")}>{t("cart.proceedToBuyCount", { count: items.length })}
                      </button>

        <div style={{ marginTop: "0.75rem" }}>
          <Link to="/products" className="cart-continue-link">{t("cart.continueShopping")}</Link>
        </div>

        {savedItems.length > 0 && (
          <div className="save-later-section">
            <h3 className="save-later-title">{t("cart.savedForLater")}{" "}<span className="save-later-title-count">({t("cart.itemCount", { count: savedItems.length })})</span>
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
                      <div className="save-later-item-title">{product.name || t("cart.product")}</div>
                      <div className="save-later-item-price">&#8377;{price.toFixed(2)}</div>
                      <button className="save-later-item-move" onClick={() => moveToCart(itemId)}
                        disabled={isLoading(`move-${itemId}`)}>
                        {isLoading(`move-${itemId}`) ? t("productTray.moving") : t("cart.moveToCart")}
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
            <h3 className="save-later-title">{t("cart.frequentlyBoughtTogether")}</h3>
            <ProductRow>
              {freqItemsToShow.map((prod) => (
                <ProductTray
                  key={prod.id || prod._id}
                  product={prod}
                  onAddToCart={(p) => addFreqToCart(p.id || p._id)}
                  onBuyNow={(p) => buyNowFreq(p.id || p._id)}
                />
              ))}
            </ProductRow>
          </div>
        )}
      </div>

      {/* ─── RIGHT COLUMN: Price Details ─── */}
      <aside className="cart-summary">
        <h3 className="cart-summary-title">{t("cart.priceDetails")}</h3>

        <div className="cart-summary-row">
          <span className="cart-summary-row-label">{t("cart.priceCount", { count: items.length })}</span>
          <span className="cart-summary-row-value">&#8377;{subtotal.toFixed(2)}</span>
        </div>

        {discount > 0 && (
          <div className="cart-summary-row">
            <span className="cart-summary-row-label">{t("cart.discount")}</span>
            <span className="cart-summary-row-value cart-summary-discount">-&#8377;{discount.toFixed(2)}</span>
          </div>
        )}

        <div className="cart-summary-row">
          <span className="cart-summary-row-label">{t("cart.delivery")}</span>
          <span className="cart-summary-row-value">{delivery === 0 ? <span className="cart-summary-discount">{t("cart.free")}</span> : `\u20B9${delivery.toFixed(2)}`}</span>
        </div>

        <div className="cart-summary-divider" />

        <div className="cart-summary-total-row">
          <span>{t("cart.totalAmount")}</span>
          <span className="cart-summary-total-value">&#8377;{finalTotal.toFixed(2)}</span>
        </div>

        <div className="cart-summary-row">
          <span className="cart-summary-row-label">
            {tax !== null ? t("cart.includesGstOf") : t("cart.inclusiveOfAllTaxes")}
          </span>
          {tax !== null && (
            <span className="cart-summary-row-value">&#8377;{tax.toFixed(2)}</span>
          )}
        </div>

        {discount > 0 && (
          <div className="cart-summary-savings">{t("cart.youSaveOnOrder", { amount: discount.toFixed(2) })}</div>
        )}

        <div className="cart-summary-coupon">
          <input
            value={promoCode}
            onChange={(e) => setPromoCode(e.target.value)}
            placeholder={t("cart.couponCode")}
            aria-label={t("cart.enterCouponCode")}
            disabled={!!appliedCoupon}
          />
          {!appliedCoupon ? (
            <button className="cart-summary-coupon-btn" onClick={applyCoupon}>{t("filters.apply")}</button>
          ) : (
            <button className="cart-summary-coupon-btn cart-summary-coupon-remove" onClick={removeCoupon}>{t("cart.remove")}</button>
          )}
        </div>
        {promoMsg && (
          <div style={{ fontSize: "0.8rem", marginBottom: "0.75rem", color: promoMsg.type === "success" ? "#0E5C5C" : "#92400e" }}>
            {promoMsg.text}
          </div>
        )}

        <div className="cart-trust-row">
          <span className="cart-trust-badge">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="M9 12l2 2 4-4"/></svg>{t("cart.securePayment")}</span>
          <span className="cart-trust-badge">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M1 4v6h6"/><path d="M3.51 15a9 9 0 102.13-9.36L1 10"/></svg>{t("cart.7DayReturns")}</span>
          <span className="cart-trust-badge">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>{t("cart.supportMonToSat")}</span>
        </div>

        <div className="cart-summary-secure">
          <span>&#128274;</span>{" "}{t("cart.securePaymentEasyReturns")}</div>
        <div className="cart-summary-accepted-cards">
          <span>&#9829;</span>{" "}{t("cart.cardsUpiNetBanking")}</div>
      </aside>

    </div>
  );
};

export default Cart;
