import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Heart, Share2, Eye, ShoppingCart, Zap } from "lucide-react";
import "../styles/product-tray.css";

const PLACEHOLDER = "/images/placeholder.svg";

const StarRating = ({ rating, reviewCount }) => {
  const full = Math.floor(rating);
  const half = rating - full >= 0.5;
  return (
    <div className="pt-stars" aria-label={`${rating} out of 5 stars`}>
      {[1, 2, 3, 4, 5].map((i) => (
        <svg key={i} className={`pt-star ${i <= full ? "full" : i === full + 1 && half ? "half" : "empty"}`} viewBox="0 0 20 20" fill="currentColor" width="14" height="14">
          <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
        </svg>
      ))}
      {reviewCount != null && <span className="pt-review-count">({reviewCount.toLocaleString()})</span>}
    </div>
  );
};

const PriceBlock = ({ price, originalPrice, discount }) => {
  const hasDiscount = originalPrice > price && discount > 0;
  return (
    <div className="pt-pricing">
      <span className="pt-price">&#8377;{Math.round(price).toLocaleString()}</span>
      {hasDiscount && (
        <>
          <span className="pt-original-price">&#8377;{Math.round(originalPrice).toLocaleString()}</span>
          <span className="pt-discount-badge">{discount}% OFF</span>
        </>
      )}
    </div>
  );
};

const LoadingSkeleton = () => (
  <div className="pt-skeleton">
    <div className="pt-skel-img" />
    <div className="pt-skel-body">
      <div className="pt-skel-line w60" />
      <div className="pt-skel-line w80" />
      <div className="pt-skel-line w40" />
      <div className="pt-skel-line w50" />
      <div className="pt-skel-btn" />
    </div>
  </div>
);

const TrustBadge = ({ text, type }) => (
  <div className={`pt-trust-badge pt-trust-${type}`}>
    {type === "secure" && <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="M9 12l2 2 4-4"/></svg>}
    {type === "return" && <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M1 4v6h6"/><path d="M3.51 15a9 9 0 102.13-9.36L1 10"/></svg>}
    <span>{text}</span>
  </div>
);

const ProductTray = ({ product, onAddToCart, onBuyNow, quickActions = true }) => {
  const navigate = useNavigate();
  const [imgLoaded, setImgLoaded] = useState(false);
  const [imgError, setImgError] = useState(false);
  const imgErrorFixed = React.useRef(false);
  const [wishlisted, setWishlisted] = useState(false);
  const [adding, setAdding] = useState(false);

  const pid = product?.id || product?._id;
  const name = product?.name || "Product";
  const brand = (typeof product?.brand === "object" ? product?.brand?.name : product?.brand) || "";
  const price = product?.price || product?.dealPrice || product?.sellingPrice || 0;
  const originalPrice = product?.originalPrice || product?.mrp || price;
  const discount = product?.discount || product?.discountPercent || (originalPrice > price ? Math.round((1 - price / originalPrice) * 100) : 0);
  const rating = product?.rating || 4.0;
  const reviewCount = product?.reviews?.length || product?.reviewCount || 0;
  const stock = product?.stock ?? product?.stockQuantity ?? (product?.inStock ? 1 : 0);
  const inStock = stock > 0;
  const toUrl = (img) => typeof img === "object" ? img?.url || "" : img || "";
  const images = product?.images || (product?.image ? [toUrl(product.image)] : []);
  const image = (images.length > 0 ? toUrl(images[0]) : null) || PLACEHOLDER;
  const badge = product?.badge || (discount > 50 ? "Best Seller" : discount > 30 ? "Popular" : "");

  const handleClick = () => navigate(`/product/${pid}`);

  const handleAddToCart = async (e) => {
    e.stopPropagation();
    if (adding || !inStock) return;
    setAdding(true);
    try {
      if (onAddToCart) await onAddToCart(product);
    } catch { /* ignore */ }
    setAdding(false);
  };

  const handleBuyNow = (e) => {
    e.stopPropagation();
    if (onBuyNow) onBuyNow(product);
  };

  const handleWishlist = async (e) => {
    e.stopPropagation();
    const token = localStorage.getItem("accessToken");
    if (!token) return;
    try {
      const { addToWishlist, removeFromWishlist } = await import("../services/wishlistService");
      if (wishlisted) {
        await removeFromWishlist(pid);
        setWishlisted(false);
      } else {
        await addToWishlist(pid);
        setWishlisted(true);
      }
    } catch { /* ignore */ }
  };

  const handleShare = async (e) => {
    e.stopPropagation();
    try {
      await navigator.share({ title: name, url: window.location.origin + `/product/${pid}` });
    } catch { /* ignore */ }
  };

  const isLowStock = inStock && stock <= 5;

  return (
    <div
      className={`pt-card ${!inStock ? "pt-out-of-stock" : ""}`}
      onClick={handleClick}
      role="button"
      tabIndex={0}
      aria-label={`View ${name}`}
      onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); handleClick(); } }}
    >
      <div className="pt-image-wrap">
        {!imgLoaded && !imgError && <div className="pt-img-placeholder" />}
        <img
          src={image}
          alt={name}
          width="200" height="200"
          className={`pt-image ${imgLoaded ? "loaded" : ""}`}
          loading="lazy"
          onLoad={() => setImgLoaded(true)}
          onError={(e) => { if (imgErrorFixed.current) return; imgErrorFixed.current = true; setImgError(true); e.target.src = PLACEHOLDER; }}
        />
        {badge && <span className="pt-badge">{badge}</span>}
        {discount > 0 && !badge && <span className="pt-badge pt-badge-discount">{discount}% OFF</span>}
        {!inStock && <div className="pt-out-label">Out of Stock</div>}
        {isLowStock && <div className="pt-low-stock">Only {stock} left</div>}

        <div className="pt-image-overlay">
          <button className="pt-quick-view" onClick={(e) => { e.stopPropagation(); navigate(`/product/${pid}`); }} aria-label="Quick view">
            <Eye size={16} />
          </button>
        </div>

        {quickActions && (
          <div className="pt-quick-actions">
            <button
              className={`pt-action-btn ${wishlisted ? "wishlisted" : ""}`}
              onClick={handleWishlist}
              aria-label={wishlisted ? "Remove from wishlist" : "Add to wishlist"}
            >
              <Heart size={16} fill={wishlisted ? "currentColor" : "none"} />
            </button>
            <button className="pt-action-btn" onClick={handleShare} aria-label="Share product">
              <Share2 size={16} />
            </button>
          </div>
        )}
      </div>

      <div className="pt-body">
        {brand && <div className="pt-brand">{brand}</div>}
        <h3 className="pt-name">{name}</h3>

        <PriceBlock price={price} originalPrice={originalPrice} discount={discount} />

        <StarRating rating={rating} reviewCount={reviewCount} />

        <div className="pt-delivery">
          {inStock ? (
            <span className="pt-delivery-instock">In Stock</span>
          ) : (
            <span className="pt-delivery-ostock">Currently Unavailable</span>
          )}
          {inStock && stock > 5 && <span className="pt-delivery-est">Free Delivery</span>}
        </div>

        <div className="pt-trust-row">
          <TrustBadge text="Secure" type="secure" />
          <TrustBadge text="Easy Returns" type="return" />
        </div>

        <div className="pt-actions">
          <button
            className="pt-btn pt-btn-cart"
            onClick={handleAddToCart}
            disabled={!inStock || adding}
            aria-label="Add to cart"
          >
            <ShoppingCart size={16} />
            <span>{adding ? "Adding..." : "Add to Cart"}</span>
          </button>
          <button
            className="pt-btn pt-btn-buy"
            onClick={handleBuyNow}
            disabled={!inStock}
            aria-label="Buy now"
          >
            <Zap size={16} />
            <span>Buy Now</span>
          </button>
        </div>
      </div>
    </div>
  );
};

export default ProductTray;
export { StarRating, PriceBlock, LoadingSkeleton, TrustBadge };
