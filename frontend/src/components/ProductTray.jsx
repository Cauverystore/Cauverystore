import React, { useState, useEffect } from "react";
import { useNavigate, Link } from "react-router-dom";
import { Heart, Bookmark, Share2, ShoppingCart, Zap, ArrowRightCircle, GitCompareArrows, ChevronLeft, ChevronRight } from "lucide-react";
import api from "../api/axios";
import { useWishlist } from "../context/WishlistContext";
import { imgUrl } from "../utils/images";
import { trackSelectItem, trackAddToCart } from "../utils/analytics";
import { toggleCompare, isCompared } from "../utils/compare";
import "../styles/product-tray.css";

/*
 * The Cauvery Store product card. One standard for every list on the site:
 *   photo gallery > brand > title > rating > price > delivery / returns / stock line > Add to cart + Buy now.
 * Everything shown comes from the product record - no guessed badges, ratings or urgency.
 */

const PLACEHOLDER = "/images/placeholder.svg";

const rupees = (n) => `₹${Math.round(n).toLocaleString("en-IN")}`;

// 918 -> "918", 2540 -> "2.5K", 59871 -> "60K"
const compactCount = (n) => (n >= 1000 ? `${(n / 1000).toFixed(n >= 10000 ? 0 : 1).replace(/\.0$/, "")}K` : String(n));

const StarRating = ({ rating, reviewCount }) => (
  <div className="pt-rating" aria-label={`Rated ${Number(rating).toFixed(1)} out of 5${reviewCount ? ` by ${reviewCount} buyers` : ""}`}>
    <span className="pt-rating-pill">
      {Number(rating).toFixed(1)}
      <svg viewBox="0 0 20 20" fill="currentColor" width="11" height="11" aria-hidden="true">
        <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
      </svg>
    </span>
    {reviewCount > 0 && <span className="pt-review-count">({compactCount(reviewCount)})</span>}
  </div>
);

const PriceBlock = ({ price, originalPrice }) => (
  <div className="pt-pricing">
    <span className="pt-price">{rupees(price)}</span>
    {originalPrice > price && <span className="pt-original-price">{rupees(originalPrice)}</span>}
  </div>
);

const LoadingSkeleton = () => (
  <div className="pt-skeleton">
    <div className="pt-skel-img" />
    <div className="pt-skel-body">
      <div className="pt-skel-line w40" />
      <div className="pt-skel-line w80" />
      <div className="pt-skel-line w50" />
      <div className="pt-skel-btn" />
    </div>
  </div>
);

const ProductTray = ({ product, onAddToCart, onBuyNow, onMoveToCart, onRemove, quickActions = true }) => {
  const navigate = useNavigate();
  const [imgLoaded, setImgLoaded] = useState(false);
  const [activeImg, setActiveImg] = useState(0);
  const imgErrorFixed = React.useRef(false);
  const imageWrapRef = React.useRef(null);
  const touchStart = React.useRef(null);
  const swiped = React.useRef(false);
  const { ids: wishlistIds, toggle: toggleWishlist } = useWishlist();
  const [adding, setAdding] = useState(false);
  const [moving, setMoving] = useState(false);
  const [removing, setRemoving] = useState(false);
  const [compared, setCompared] = useState(() => isCompared(product));
  const [linkCopied, setLinkCopied] = useState(false);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  const pid = product?.id || product?._id;
  const name = product?.name || "Product";

  useEffect(() => { setActiveImg(0); imgErrorFixed.current = false; }, [pid]);

  const brand = (typeof product?.brand === "object" ? product?.brand?.name : product?.brand) || "";
  const price = product?.price || product?.dealPrice || product?.sellingPrice || 0;
  const originalPrice = product?.originalPrice || product?.mrp || price;
  const rating = product?.rating || 0;
  const reviewCount = product?.reviews?.length || product?.reviewCount || 0;
  const stock = product?.stock ?? product?.stockQuantity ?? (product?.inStock ? 1 : 0);
  const inStock = stock > 0;
  const isLowStock = inStock && stock <= 5;
  const optionCount = Array.isArray(product?.variants) ? product.variants.length : 0;
  // The seller's own return period for this product; 7 days is the marketplace default.
  const returnDays = product?.returnWindow ?? 7;

  const toUrl = (img) => typeof img === "object" ? img?.url || "" : img || "";
  const toThumb = (img) => typeof img === "object" && img?.thumbUrl ? img.thumbUrl : "";
  const images = product?.images || (product?.image ? [toUrl(product.image)] : []);
  const shown = images.length > 0 ? Math.min(activeImg, images.length - 1) : 0;
  const rawImage = images.length > 0 ? toUrl(images[shown]) : null;
  const image = rawImage ? imgUrl(rawImage) : PLACEHOLDER;
  const thumbImage = toThumb(images[shown]) ? imgUrl(toThumb(images[shown])) : "";
  const imgSrc = thumbImage && thumbImage !== image ? thumbImage : image;
  const srcSet = thumbImage && thumbImage !== image && image.startsWith("http") ? `${thumbImage} 400w, ${image} 1200w` : undefined;

  // One tag at most, and only one the product record really carries.
  const tag = product?.badge
    || (Array.isArray(product?.badges) && typeof product.badges[0] === "string" ? product.badges[0] : "")
    || (product?.bestSeller ? "Best seller" : product?.newArrival ? "New" : product?.trending ? "Trending" : "");
  const wishlisted = wishlistIds.has(pid);

  const showImage = (i) => {
    if (images.length === 0) return;
    imgErrorFixed.current = false;
    setActiveImg((i + images.length) % images.length);
  };

  // Phones have no hover, so the photos are swiped. The title link is stretched over the whole
  // card, so touches are read on the card and counted only when they start on the photo.
  const handleTouchStart = (e) => {
    swiped.current = false;
    const box = imageWrapRef.current?.getBoundingClientRect();
    const t = e.touches[0];
    touchStart.current = box && images.length > 1 && t.clientY >= box.top && t.clientY <= box.bottom
      ? { x: t.clientX, y: t.clientY }
      : null;
  };

  const handleTouchEnd = (e) => {
    if (!touchStart.current) return;
    const t = e.changedTouches[0];
    const dx = t.clientX - touchStart.current.x;
    const dy = t.clientY - touchStart.current.y;
    touchStart.current = null;
    if (Math.abs(dx) > 30 && Math.abs(dx) > Math.abs(dy) * 1.5) {
      swiped.current = true;
      showImage(shown + (dx < 0 ? 1 : -1));
    }
  };

  const handleOpen = (e) => {
    // A swipe across the photos must not also open the product.
    if (swiped.current) { swiped.current = false; e.preventDefault(); return; }
    trackSelectItem(product, "product_list");
  };

  const handleBuyNow = () => {
    if (onBuyNow) onBuyNow(product);
  };

  const handleShare = async () => {
    const url = window.location.origin + `/product/${pid}`;
    try {
      if (navigator.share) await navigator.share({ title: name, url });
      else {
        await navigator.clipboard.writeText(url);
        setLinkCopied(true);
        setTimeout(() => setLinkCopied(false), 1800);
      }
    } catch { /* the shopper closed the share sheet */ }
  };

  const handleAddToCart = async () => {
    if (adding || !inStock) return;
    setAdding(true);
    try {
      trackAddToCart(product, 1);
      if (onAddToCart) await onAddToCart(product);
    } catch { /* ignore */ }
    setAdding(false);
  };

  const handleMoveToCart = async () => {
    if (moving || !inStock) return;
    setMoving(true);
    try {
      if (onMoveToCart) await onMoveToCart(product);
    } catch { /* ignore */ }
    setMoving(false);
  };

  const handleWishlist = () => {
    const token = localStorage.getItem("accessToken");
    if (!token) { navigate("/login"); return; }
    toggleWishlist(pid);
  };

  // "Save for later" is the cart's own holding list, separate from the wishlist: the item is
  // put in the cart and moved straight to its saved section.
  const handleSaveForLater = async () => {
    const token = localStorage.getItem("accessToken");
    if (!token) { navigate("/login"); return; }
    if (saving || saved) return;
    setSaving(true);
    try {
      const { data: cartItem } = await api.post(`/api/cart/add?productId=${pid}&quantity=1`);
      await api.post(`/api/cart/save-for-later/${cartItem.id}`);
      setSaved(true);
    } catch { /* ignore */ }
    setSaving(false);
  };

  const handleCompare = () => {
    const next = toggleCompare(product);
    setCompared(next.some((p) => String(p.id ?? p._id) === String(pid)));
  };

  const handleRemove = async () => {
    if (removing) return;
    setRemoving(true);
    try {
      if (onRemove) await onRemove(product);
    } catch { /* ignore */ }
    setRemoving(false);
  };

  return (
    <article
      className={`pt-card ${!inStock ? "pt-out-of-stock" : ""}`}
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
    >
      <div className="pt-image-wrap" ref={imageWrapRef}>
        {!imgLoaded && <div className="pt-img-placeholder" />}
        <img
          src={imgSrc}
          srcSet={srcSet}
          sizes="(min-width: 960px) 240px, 45vw"
          alt=""
          width="240" height="240"
          className={`pt-image ${imgLoaded ? "loaded" : ""}`}
          loading="lazy"
          onLoad={() => setImgLoaded(true)}
          onError={(e) => { if (imgErrorFixed.current) return; imgErrorFixed.current = true; setImgLoaded(true); e.target.src = PLACEHOLDER; }}
        />
        {tag && inStock && <span className="pt-badge">{tag}</span>}
        {!inStock && <span className="pt-out-label">Out of stock</span>}

        {quickActions && (
          <div className="pt-quick-actions">
            <button
              type="button"
              className={`pt-action-btn ${wishlisted ? "wishlisted" : ""}`}
              onClick={handleWishlist}
              aria-pressed={wishlisted}
              aria-label={wishlisted ? `Remove ${name} from wishlist` : `Add ${name} to wishlist`}
            >
              <Heart size={18} strokeWidth={1.8} fill={wishlisted ? "currentColor" : "none"} />
            </button>
            {inStock && (
              <button
                type="button"
                className={`pt-action-btn pt-action-extra ${saved ? "saved" : ""}`}
                onClick={handleSaveForLater}
                disabled={saving}
                aria-pressed={saved}
                aria-label={saved ? `${name} saved for later` : `Save ${name} for later`}
                title={saved ? "Saved for later" : "Save for later"}
              >
                <Bookmark size={16} strokeWidth={1.8} fill={saved ? "currentColor" : "none"} />
              </button>
            )}
            <button
              type="button"
              className="pt-action-btn pt-action-extra"
              onClick={handleShare}
              aria-label={`Share ${name}`}
              title={linkCopied ? "Link copied" : "Share"}
            >
              <Share2 size={16} strokeWidth={1.8} />
            </button>
            <button
              type="button"
              className={`pt-action-btn pt-action-extra ${compared ? "compared" : ""}`}
              onClick={handleCompare}
              aria-pressed={compared}
              aria-label={compared ? `Remove ${name} from compare` : `Add ${name} to compare`}
              title="Compare"
            >
              <GitCompareArrows size={17} strokeWidth={1.8} />
            </button>
          </div>
        )}
        {linkCopied && <span className="pt-copied" role="status">Link copied</span>}

        {images.length > 1 && (
          <>
            <button type="button" className="pt-gallery-nav pt-gallery-nav-prev" onClick={() => showImage(shown - 1)} aria-label="Previous photo">
              <ChevronLeft size={18} />
            </button>
            <button type="button" className="pt-gallery-nav pt-gallery-nav-next" onClick={() => showImage(shown + 1)} aria-label="Next photo">
              <ChevronRight size={18} />
            </button>
            <div className="pt-gallery-dots">
              {images.map((_, i) => (
                <button
                  type="button"
                  key={i}
                  className={`pt-gallery-dot ${i === shown ? "active" : ""}`}
                  onClick={() => showImage(i)}
                  aria-label={`Photo ${i + 1} of ${images.length}`}
                  aria-current={i === shown}
                />
              ))}
            </div>
          </>
        )}
      </div>

      <div className="pt-body">
        {brand && <div className="pt-brand">{brand}</div>}
        {/* A real <a href> that crawlers can follow. Its ::after covers the whole card, so the
            card is one big link while the buttons stay separate, properly nested controls. */}
        <h3 className="pt-name">
          <Link to={`/product/${pid}`} className="pt-link" onClick={handleOpen}>
            {name}
          </Link>
        </h3>
        {optionCount > 1 && <div className="pt-options">{optionCount} options</div>}

        {/* No stars until a product has a real rating - a made-up default misleads shoppers. */}
        {rating > 0 && <StarRating rating={rating} reviewCount={reviewCount} />}

        <PriceBlock price={price} originalPrice={originalPrice} />

        <div className="pt-meta">
          {/* Matches the cart rule: delivery is free on orders of 500 or more. */}
          {inStock && price >= 500 && <span className="pt-meta-item">Free delivery</span>}
          {inStock && returnDays > 0 && <span className="pt-meta-item">{returnDays}-day returns</span>}
          {isLowStock && <span className="pt-meta-low">Only {stock} left</span>}
        </div>

        {onMoveToCart ? (
          <div className="pt-actions pt-actions-stacked">
            <button type="button" className="pt-btn pt-btn-buy" onClick={handleMoveToCart} disabled={!inStock || moving}>
              <ArrowRightCircle size={16} aria-hidden="true" />
              <span>{!inStock ? "Out of stock" : moving ? "Moving..." : "Move to cart"}</span>
            </button>
            {onRemove && (
              <button type="button" className="pt-btn pt-btn-remove" onClick={handleRemove} disabled={removing}>
                {removing ? "Removing..." : "Remove"}
              </button>
            )}
          </div>
        ) : !inStock ? (
          <div className="pt-actions">
            <button type="button" className="pt-btn pt-btn-buy" disabled>Out of stock</button>
          </div>
        ) : (
          <div className="pt-actions">
            <button type="button" className="pt-btn pt-btn-cart" onClick={handleAddToCart} disabled={adding} aria-label={`Add ${name} to cart`}>
              <ShoppingCart size={16} aria-hidden="true" />
              <span className="pt-btn-label">{adding ? "Adding..." : "Add to cart"}</span>
            </button>
            <button type="button" className="pt-btn pt-btn-buy" onClick={handleBuyNow} aria-label={`Buy ${name} now`}>
              <Zap size={15} aria-hidden="true" />
              <span>Buy now</span>
            </button>
          </div>
        )}
      </div>
    </article>
  );
};

export default ProductTray;
export { StarRating, PriceBlock, LoadingSkeleton };
