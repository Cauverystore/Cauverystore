import React, { useState, useEffect } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { Truck, RotateCcw, ShieldCheck, Store } from "lucide-react";
import api from "../api/axios";
import { getProductById } from "../services/productService";
import { addToCartOrLogin } from "../utils/cartActions";
import { trackViewItem } from "../utils/analytics";
import { imgUrl } from "../utils/images";
import Breadcrumb from "../components/Breadcrumb";
import "../styles/productDetails.css";

const ProductDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [selectedImage, setSelectedImage] = useState(0);
  const [quantity, setQuantity] = useState(1);
  const [selectedVariant, setSelectedVariant] = useState(null);
  const [relatedProducts, setRelatedProducts] = useState([]);
  const [cartMsg, setCartMsg] = useState("");
  // Shop name and town of an approved seller, or null when there is none to name.
  const [seller, setSeller] = useState(null);

  useEffect(() => {
    const fetchProduct = async () => {
      setSeller(null);
      try {
        const res = await getProductById(id);
        setProduct(res.data);
        // Loaded separately and allowed to fail: the page works without it.
        api.get(`/api/products/${id}/seller`).then(r => setSeller(r.data?.name ? r.data : null)).catch(() => {});
        if (res.data.images?.length > 0) setSelectedImage(0);
        if (res.data.variants?.length > 0) setSelectedVariant(res.data.variants[0]);
        const cat = res.data.category;
        const catQ = typeof cat === "object" ? cat?.name || "" : cat || "";
        if (catQ) {
          api.get(`/api/products/search?category=${encodeURIComponent(catQ)}&size=6`).then(r => {
            const list = r.data?.content || [];
            setRelatedProducts(list.filter(p => String(p.id || p._id) !== String(id)).slice(0, 5));
          }).catch(() => {});
        }
      } catch (err) { void err; }
      setLoading(false);
    };
    fetchProduct();
  }, [id]);

  useEffect(() => {
    if (product) trackViewItem(product);
  }, [product]);

  const handleAddToCart = async () => {
    const res = await addToCartOrLogin(navigate, product, quantity);
    if (res.needLogin) return;
    setCartMsg(res.ok ? "Added to your cart" : "We couldn't add that to your cart. Please try again.");
    setTimeout(() => setCartMsg(""), 2000);
  };

  const handleBuyNow = async () => {
    const res = await addToCartOrLogin(navigate, product, quantity);
    if (res.ok) navigate("/checkout");
    else if (!res.needLogin) { setCartMsg("We couldn't start checkout. Please try again."); setTimeout(() => setCartMsg(""), 2000); }
  };

  const toUrl = (img) => (typeof img === "object" ? img?.previewUrl || img?.url || "" : img || "");
  const fullUrl = (img) => { const raw = toUrl(img); return raw ? imgUrl(raw) : ""; };
  const siteDefaultImage = window.location.origin + "/images/logo.jpg";
  const categoryName = typeof product?.category === "object" ? product?.category?.name || "" : product?.category || "";
  // Escaping "<" prevents a "</script>" inside a product name/description from
  // closing this script tag early and letting injected markup execute as real
  // script in every visitor's browser (stored XSS via JSON-LD).
  const safeJsonLd = (obj) => JSON.stringify(obj, null, 2).replace(/</g, "\\u003c");

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;
  if (!product) return (
    <div style={{ textAlign: "center", padding: "3rem" }}>
      {/* Tells Prerender.io to return a real 404 to crawlers instead of a 200 "soft 404". */}
      <Helmet>
        <title>Product not found - Cauvery Store</title>
        <meta name="prerender-status-code" content="404" />
        <meta name="robots" content="noindex" />
      </Helmet>
      Product not found
    </div>
  );

  const canonicalUrl = `https://cauverystore.in/product/${id}`;
  // The seller's own return period; 7 days is the marketplace default. "No Returns" on the
  // product overrides it, the same way the order system decides.
  const returnDays = /no return/i.test(product.returnPolicy || "") ? 0 : (product.returnWindow ?? 7);
  // Cut at a word boundary instead of mid-word, and fall back to a sentence that carries
  // the site's real differentiator rather than a bare product name.
  const trimAt = (text, max) => {
    const t = (text || "").replace(/\s+/g, " ").trim();
    if (t.length <= max) return t;
    const cut = t.slice(0, max - 1);
    return cut.slice(0, cut.lastIndexOf(" ") > 40 ? cut.lastIndexOf(" ") : cut.length).replace(/[,.;:\s]+$/, "") + "…";
  };
  const metaDescription = product.description
    ? trimAt(product.description, 155)
    : `Buy ${product.name} direct from Tamil Nadu sellers on Cauvery Store. Pay by UPI or card and get it delivered to your door.`;

  const selImage = product.images?.[selectedImage];
  const mainSrc = fullUrl(selImage) || fullUrl(product.image) || "/images/placeholder.svg";
  const mainThumb = typeof selImage === "object" && selImage?.thumbUrl ? imgUrl(selImage.thumbUrl) : "";
  const mainZoom = typeof selImage === "object" && selImage?.zoomUrl ? imgUrl(selImage.zoomUrl) : "";
  // srcset: 150px thumbnail, 600px preview, 1200px zoom (WebP from the image pipeline).
  const mainSrcSet = mainSrc.startsWith("http")
    ? [mainThumb && `${mainThumb} 150w`, mainSrc && `${mainSrc} 600w`, mainZoom && `${mainZoom} 1200w`].filter(Boolean).join(", ")
    : undefined;

  return (
    <div className="product-detail-page">
      <Helmet>
        <title>{product.name} - Cauvery Store</title>
        <meta name="description" content={metaDescription} />
        <link rel="canonical" href={canonicalUrl} />
        <meta property="og:title" content={`${product.name} - Cauvery Store`} />
        <meta property="og:description" content={metaDescription} />
        <meta property="og:image" content={fullUrl(product.images?.[0]) || fullUrl(product.image) || siteDefaultImage} />
        <meta property="og:type" content="product" />
        <meta property="og:url" content={canonicalUrl} />
        <meta name="twitter:card" content="summary_large_image" />
        <meta name="twitter:title" content={`${product.name} - Cauvery Store`} />
        <meta name="twitter:description" content={metaDescription} />
        <meta name="twitter:image" content={fullUrl(product.images?.[0]) || fullUrl(product.image) || siteDefaultImage} />
      </Helmet>
      <script type="application/ld+json" dangerouslySetInnerHTML={{__html: safeJsonLd({
        "@context": "https://schema.org/",
        "@type": "Product",
        "name": product.name,
        "image": fullUrl(product.images?.[0]) || fullUrl(product.image) || siteDefaultImage,
        "description": product.description,
        "sku": product.sku || product.id?.toString(),
        "brand": product.brand ? { "@type": "Brand", "name": product.brand } : undefined,
        "offers": {
          "@type": "Offer",
          "url": canonicalUrl,
          "priceCurrency": "INR",
          "price": product.dealPrice || product.price,
          "itemCondition": "https://schema.org/NewCondition",
          "availability": (product.stock > 0) ? "https://schema.org/InStock" : "https://schema.org/OutOfStock"
        },
        // Google rejects a rating with zero reviews, so only emit it when there are some.
        "aggregateRating": (product.rating && product.reviewCount > 0) ? {
          "@type": "AggregateRating",
          "ratingValue": product.rating,
          "reviewCount": product.reviewCount
        } : undefined
      })}} />
      <script type="application/ld+json" dangerouslySetInnerHTML={{__html: safeJsonLd({
        "@context": "https://schema.org",
        "@type": "BreadcrumbList",
        "itemListElement": [
          { "@type": "ListItem", "position": 1, "name": "Home", "item": window.location.origin + "/" },
          ...(categoryName ? [{ "@type": "ListItem", "position": 2, "name": categoryName, "item": window.location.origin + "/category/" + encodeURIComponent(categoryName) }] : []),
          { "@type": "ListItem", "position": categoryName ? 3 : 2, "name": product.name, "item": canonicalUrl }
        ]
      })}} />
      <Breadcrumb items={[
        { label: "Home", to: "/" },
        ...(categoryName ? [{ label: categoryName, to: `/category/${encodeURIComponent(categoryName)}` }] : []),
        { label: product.name }
      ]} />
      <div className="pd-main">
        <div className="product-image-section">
          <img className="product-main-image" src={mainSrc} srcSet={mainSrcSet} sizes="(min-width: 768px) 400px, 100vw" alt={product.name} width="400" height="400" fetchPriority="high" onError={(e) => { e.target.src = "/images/placeholder.svg"; }} />
          {(product.images || []).length > 1 && (
            <div className="product-thumbnails">
              {product.images.map((img, i) => (
                <button
                  key={i}
                  type="button"
                  className={`product-thumb-btn${i === selectedImage ? " active" : ""}`}
                  onClick={() => setSelectedImage(i)}
                  aria-label={`View image ${i + 1} of ${product.images.length}`}
                  aria-pressed={i === selectedImage}
                  style={{ border: "none", padding: 0, background: "none", cursor: "pointer" }}
                >
                  <img src={typeof img === "object" && img?.thumbUrl ? imgUrl(img.thumbUrl) : fullUrl(img)} alt="" width="60" height="60" loading="lazy" className={i === selectedImage ? "active" : ""} onError={(e) => { e.target.src = "/images/placeholder.svg"; }} />
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="product-info-section">
        <h1>{product.name}</h1>
        {product.rating && <div className="rating-row">{'★'.repeat(Math.round(product.rating))} {product.rating} ({product.reviewCount || 0} reviews)</div>}
        <div className="price-block">
          <span className="deal-price">&#8377;{product.dealPrice || product.price}</span>
          {product.mrp > (product.dealPrice || product.price) && <><span className="mrp-price">&#8377;{product.mrp}</span><span className="discount-tag">{Math.round((1 - (product.dealPrice || product.price) / product.mrp) * 100)}% OFF</span></>}
        </div>
        <p className="description">{product.description}</p>

        {(product.variants || []).length > 0 && (
          <div className="variant-section">
            <h3>{product.variantType || "Options"}</h3>
            <div className="variant-options">
              {product.variants.map((v) => (
                <button key={v.id || v._id} className={`variant-btn${selectedVariant?.id === v.id || selectedVariant?._id === v._id ? " active" : ""}`}
                  onClick={() => setSelectedVariant(v)}>{v.name || v.value}</button>
              ))}
            </div>
          </div>
        )}

        <div className="qty-section">
          <button className="qty-btn" onClick={() => setQuantity(Math.max(1, quantity - 1))}>-</button>
          <span className="qty-display">{quantity}</span>
          <button className="qty-btn" onClick={() => setQuantity(quantity + 1)}>+</button>
        </div>

        <div className="action-buttons">
          <button className="btn-buy-now" onClick={handleBuyNow}>Buy Now</button>
          <button className="btn-add-cart" onClick={handleAddToCart}>Add to Cart</button>
        </div>

        {/* Answers the questions that stop a purchase - delivery cost, returns, payment, who
            sells it - next to the buttons. Figures mirror the cart (free delivery from 500,
            otherwise 40) and the product's own return period, so keep them in step. */}
        <ul className="pd-assurance">
          <li>
            <Truck size={18} />
            <span>
              <strong>{(product.dealPrice || product.price) >= 500 ? "Free delivery" : "Free delivery on orders of ₹500 or more"}</strong>
              {(product.dealPrice || product.price) < 500 && <> ({"₹"}40 below that)</>}
            </span>
          </li>
          <li>
            <RotateCcw size={18} />
            <span>
              {returnDays > 0
                ? <><strong>{returnDays}-day returns</strong>, free if the item is faulty or wrong. <Link to="/refund-policy">Returns policy</Link></>
                : <><strong>This item cannot be returned</strong> unless it arrives damaged or wrong. <Link to="/refund-policy">Returns policy</Link></>}
            </span>
          </li>
          <li>
            <ShieldCheck size={18} />
            <span><strong>Secure payment</strong> by UPI, card or net banking through Razorpay</span>
          </li>
          <li>
            <Store size={18} />
            {seller
              ? <span>Sold by <strong>{seller.name}</strong>{seller.city ? `, ${seller.city}` : ""}{seller.state && seller.state !== seller.city ? `, ${seller.state}` : ""}</span>
              : <span><strong>Sold by a Tamil Nadu seller</strong>, a real shop or trader</span>}
          </li>
        </ul>

        {(product.reviews || []).length > 0 && (
          <div className="reviews-section">
            <h3>Customer reviews</h3>
            {product.reviews.map((r, i) => (
              <div key={i} className="review-card">
                <div className="review-header"><span className="review-user">{r.user || r.name || "Anonymous"}</span><span>{'★'.repeat(r.rating)}</span></div>
                <p className="review-text">{r.comment || r.review}</p>
              </div>
            ))}
          </div>
        )}

        {(product.qnas || []).length > 0 && (
          <div className="qna-section">
            <h3>Questions and answers</h3>
            {product.qnas.map((q, i) => (
              <div key={i} className="review-card">
                <p style={{ fontWeight: 500 }}>Q: {q.question}</p>
                <p style={{ color: "#475569" }}>A: {q.answer || "The seller has not answered this yet."}</p>
              </div>
            ))}
          </div>
        )}
      </div>

      </div>{/* .pd-main */}

      {cartMsg && (
        <div style={{ position: "fixed", bottom: "2rem", right: "2rem", zIndex: 9999, background: "var(--color-primary, #16a34a)", color: "#fff", padding: "0.75rem 1.25rem", borderRadius: "10px", fontWeight: 600, boxShadow: "0 4px 12px rgba(0,0,0,0.15)" }}>
          {cartMsg}
        </div>
      )}

      {/* Related Products Carousel */}
      {relatedProducts.length > 0 && (
        <section className="related-products-section">
          <h2 className="related-products-title">You may also like</h2>
          <div className="related-products-scroll">
            {relatedProducts.map((p) => {
              const pid = p.id || p._id;
              const img = fullUrl(p.images?.[0]) || fullUrl(p.image);
              const price = p.dealPrice || p.price || 0;
              return (
                <Link
                  key={pid}
                  to={`/product/${pid}`}
                  className="related-product-card"
                  style={{ display: "block", fontFamily: "inherit", color: "inherit", textAlign: "left", textDecoration: "none" }}
                >
                  <img src={img || "/images/placeholder.svg"} alt={p.name} width="200" height="200" loading="lazy" className="related-product-img" onError={(e) => { e.target.src = "/images/placeholder.svg"; }} />
                  <div className="related-product-info">
                    <p className="related-product-name">{p.name}</p>
                    <p className="related-product-price">{"\u20B9"}{price.toLocaleString()}</p>
                  </div>
                </Link>
              );
            })}
          </div>
        </section>
      )}

      <style>{`
        .pd-assurance { list-style: none; margin: 1.25rem 0 0; padding: 0.9rem 1rem; border: 1px solid #e2e8f0; border-radius: 10px; background: #f8fafc; display: grid; gap: 0.6rem; }
        .pd-assurance li { display: flex; gap: 0.6rem; align-items: flex-start; font-size: 0.88rem; color: #334155; line-height: 1.45; }
        .pd-assurance svg { flex-shrink: 0; color: #16a34a; margin-top: 1px; }
        .pd-assurance strong { color: #0f172a; }
        .pd-assurance a { color: #16a34a; white-space: nowrap; }
        .related-products-section { margin-top: 2.5rem; padding-top: 1.5rem; border-top: 1px solid #e2e8f0; }
        .related-products-title { font-size: 1.25rem; font-weight: 700; margin: 0 0 1rem; color: #1e293b; }
        .related-products-scroll { display: flex; gap: 1rem; overflow-x: auto; padding-bottom: 0.5rem; }
        .related-products-scroll::-webkit-scrollbar { height: 6px; }
        .related-products-scroll::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 3px; }
        .related-product-card {
          flex-shrink: 0; width: 180px; background: #fff; border: 1px solid #e2e8f0; border-radius: 10px;
          overflow: hidden; cursor: pointer; transition: box-shadow 0.2s;
        }
        .related-product-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
        .related-product-img { width: 100%; height: 180px; object-fit: cover; }
        .related-product-info { padding: 0.6rem; }
        .related-product-name { margin: 0 0 0.25rem; font-size: 0.8rem; color: #1e293b; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .related-product-price { margin: 0; font-size: 0.9rem; font-weight: 700; color: var(--color-primary); }
        @media (max-width: 768px) { .related-product-card { width: 150px; } .related-product-img { height: 150px; } }
      `}</style>
    </div>
  );
};
export default ProductDetails;
