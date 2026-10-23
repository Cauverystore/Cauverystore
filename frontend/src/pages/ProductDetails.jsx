import React, { useState, useEffect } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { Truck, RotateCcw, ShieldCheck, Store } from "lucide-react";
import api from "../api/axios";
import { getProductById } from "../services/productService";
import { useTranslation } from "react-i18next";
import { getLocalizedProduct } from "../services/i18nService";
import { addToCartOrLogin } from "../utils/cartActions";
import { trackViewItem } from "../utils/analytics";
import { imgUrl } from "../utils/images";
import Breadcrumb from "../components/Breadcrumb";
import ProductTray from "../components/ProductTray";
import ProductRow from "../components/ProductRow";
import "../styles/productDetails.css";

const ProductDetails = () => {
  const {
    t: tr
  } = useTranslation();

  const { id } = useParams();
  const { i18n } = useTranslation();
  const lang = (i18n.language || "en").slice(0, 2);
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
        // Show the published translation when there is one; any failure leaves the original text in place.
        if (lang !== "en") {
          getLocalizedProduct(id, lang).then((r) => {
            const l = r.data || {};
            setProduct((p) => (p && String(p.id) === String(id) ? {
              ...p,
              name: l.name || p.name,
              shortDescription: l.shortDescription || p.shortDescription,
              description: l.description || p.description,
              metaTitle: l.metaTitle || p.metaTitle,
              metaDescription: l.metaDescription || p.metaDescription,
            } : p));
          }).catch(() => {});
        }
        // Loaded separately and allowed to fail: the page works without it.
        api.get(`/api/products/${id}/seller`).then(r => setSeller(r.data?.name ? r.data : null)).catch(() => {});
        if (res.data.images?.length > 0) setSelectedImage(0);
        // Start on the first size that can actually be bought.
        if (res.data.variants?.length > 0) setSelectedVariant(res.data.variants.find((v) => (v.stock ?? 1) > 0) || res.data.variants[0]);
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
  }, [id, lang]);

  useEffect(() => {
    if (product) trackViewItem(product);
  }, [product]);

  // A product sold in sizes goes into the cart as the chosen size, never as "a t-shirt".
  const hasVariants = (product?.variants || []).length > 0;
  const chosenVariantId = hasVariants ? (selectedVariant?.id ?? selectedVariant?._id ?? null) : null;
  const variantWord = (product?.variants?.[0]?.variantType || "option").toLowerCase();

  const flash = (text) => { setCartMsg(text); setTimeout(() => setCartMsg(""), 2500); };

  const handleAddToCart = async () => {
    if (hasVariants && chosenVariantId == null) { flash(`Please choose a ${variantWord} first.`); return; }
    const res = await addToCartOrLogin(navigate, product, quantity, chosenVariantId);
    if (res.needLogin) return;
    flash(res.ok ? tr("productDetails.addedToYourCart") : (res.message || tr("home.weCouldnTAddThat")));
  };

  const handleBuyNow = async () => {
    if (hasVariants && chosenVariantId == null) { flash(`Please choose a ${variantWord} first.`); return; }
    const res = await addToCartOrLogin(navigate, product, quantity, chosenVariantId);
    if (res.ok) navigate("/checkout");
    else if (!res.needLogin) flash(res.message || tr("cart.weCouldnTStartCheckout"));
  };

  // The same two actions, for a card in the "You may also like" row.
  const handleRelatedCart = async (p) => {
    const res = await addToCartOrLogin(navigate, p, 1);
    if (res.needLogin) return;
    setCartMsg(res.ok ? `${p.name} added to your cart` : tr("home.weCouldnTAddThat"));
    setTimeout(() => setCartMsg(""), 2000);
  };

  const handleRelatedBuyNow = async (p) => {
    const res = await addToCartOrLogin(navigate, p, 1);
    if (res.ok) navigate("/checkout");
    else if (!res.needLogin) { setCartMsg(tr("cart.weCouldnTStartCheckout")); setTimeout(() => setCartMsg(""), 2000); }
  };

  const toUrl = (img) => (typeof img === "object" ? img?.previewUrl || img?.url || "" : img || "");
  const fullUrl = (img) => { const raw = toUrl(img); return raw ? imgUrl(raw) : ""; };
  const siteDefaultImage = window.location.origin + "/images/logo.jpg";
  const categoryName = typeof product?.category === "object" ? product?.category?.name || "" : product?.category || "";
  // Escaping "<" prevents a "</script>" inside a product name/description from
  // closing this script tag early and letting injected markup execute as real
  // script in every visitor's browser (stored XSS via JSON-LD).
  const safeJsonLd = (obj) => JSON.stringify(obj, null, 2).replace(/</g, "\\u003c");

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>{tr("cartDrawer.loading")}</div>;
  if (!product) return (
    <div style={{ textAlign: "center", padding: "3rem" }}>
      {/* Tells Prerender.io to return a real 404 to crawlers instead of a 200 "soft 404". */}
      <Helmet>
        <title>{tr("productDetails.productNotFoundCauveryStore")}</title>
        <meta name="prerender-status-code" content="404" />
        <meta name="robots" content="noindex" />
      </Helmet>{tr("productDetails.productNotFound")}</div>
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
    : `Buy ${product.name} online at Cauvery Store. Pay securely and get it delivered to your door.`;

  // The seller's own search title wins when they wrote one; otherwise the product name.
  const pageTitle = product.metaTitle?.trim() || `${product.name} - Cauvery Store`;

  const selImage = product.images?.[selectedImage];
  const mainSrc = fullUrl(selImage) || fullUrl(product.image) || "/images/placeholder.svg";
  const mainThumb = typeof selImage === "object" && selImage?.thumbUrl ? imgUrl(selImage.thumbUrl) : "";
  const mainZoom = typeof selImage === "object" && selImage?.zoomUrl ? imgUrl(selImage.zoomUrl) : "";
  // srcset: 150px thumbnail, 600px preview, 1200px zoom (WebP from the image pipeline).
  const mainSrcSet = mainSrc.startsWith("http")
    ? [mainThumb && `${mainThumb} 150w`, mainSrc && `${mainSrc} 600w`, mainZoom && `${mainZoom} 1200w`].filter(Boolean).join(", ")
    : undefined;

  const schemaImages = [...(product.images || []), product.image].map(fullUrl).filter((u, i, all) => u && all.indexOf(u) === i);

  return (
    <div className="product-detail-page">
      <Helmet>
        <title>{pageTitle}</title>
        <meta name="description" content={metaDescription} />
        <link rel="canonical" href={canonicalUrl} />
        <meta property="og:title" content={pageTitle} />
        <meta property="og:description" content={metaDescription} />
        <meta property="og:image" content={fullUrl(product.images?.[0]) || fullUrl(product.image) || siteDefaultImage} />
        <meta property="og:type" content="product" />
        <meta property="og:url" content={canonicalUrl} />
        <meta name="twitter:card" content="summary_large_image" />
        <meta name="twitter:title" content={pageTitle} />
        <meta name="twitter:description" content={metaDescription} />
        <meta name="twitter:image" content={fullUrl(product.images?.[0]) || fullUrl(product.image) || siteDefaultImage} />
      </Helmet>
      <script type="application/ld+json" dangerouslySetInnerHTML={{__html: safeJsonLd({
        "@context": "https://schema.org/",
        "@type": "Product",
        "name": product.name,
        "image": schemaImages.length ? schemaImages : [siteDefaultImage],
        "description": product.description,
        "sku": product.sku || product.id?.toString(),
        "brand": product.brand ? { "@type": "Brand", "name": product.brand } : undefined,
        "offers": {
          "@type": "Offer",
          "url": canonicalUrl,
          "priceCurrency": "INR",
          "price": product.dealPrice || product.price,
          "itemCondition": "https://schema.org/NewCondition",
          "availability": (product.stock > 0) ? "https://schema.org/InStock" : "https://schema.org/OutOfStock",
          // Marketplace: name the shop that sells it, the same one shown as "Sold by".
          "seller": seller ? { "@type": "Organization", "name": seller.name } : undefined,
          // Mirrors the returns line shown on the page.
          "hasMerchantReturnPolicy": returnDays > 0 ? {
            "@type": "MerchantReturnPolicy",
            "applicableCountry": "IN",
            "returnPolicyCategory": "https://schema.org/MerchantReturnFiniteReturnWindow",
            "merchantReturnDays": returnDays,
            "returnMethod": "https://schema.org/ReturnByMail",
            "returnFees": "https://schema.org/ReturnFeesCustomerResponsibility",
            "itemDefectReturnFees": "https://schema.org/FreeReturn",
            "merchantReturnLink": "https://cauverystore.in/refund-policy"
          } : {
            "@type": "MerchantReturnPolicy",
            "applicableCountry": "IN",
            "returnPolicyCategory": "https://schema.org/MerchantReturnNotPermitted"
          }
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
          { "@type": "ListItem", "position": 1, "name": "Home", "item": "https://cauverystore.in/" },
          ...(categoryName ? [{ "@type": "ListItem", "position": 2, "name": categoryName, "item": "https://cauverystore.in/category/" + encodeURIComponent(categoryName) }] : []),
          { "@type": "ListItem", "position": categoryName ? 3 : 2, "name": product.name, "item": canonicalUrl }
        ]
      })}} />
      <Breadcrumb items={[
        { label: tr("nav.home"), to: "/" },
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
        {product.rating && <div className="rating-row">{'★'.repeat(Math.round(product.rating))} {product.rating}({product.reviewCount || 0}{" "}{tr("productDetails.reviews")}</div>}
        <div className="price-block">
          <span className="deal-price">&#8377;{product.dealPrice || product.price}</span>
          {product.mrp > (product.dealPrice || product.price) && <><span className="mrp-price">&#8377;{product.mrp}</span><span className="discount-tag">{Math.round((1 - (product.dealPrice || product.price) / product.mrp) * 100)}{tr("productDetails.off")}</span></>}
          <span className="tax-note">{tr("cart.inclusiveOfAllTaxes")}</span>
        </div>
        <p className="description">{product.description}</p>

        {(product.variants || []).length > 0 && (
          <div className="variant-section">
            {/* The server names these variantType ("Size") and variantValue ("M"). */}
            <h3>{product.variants[0]?.variantType || product.variantType || tr("productDetails.options")}</h3>
            <div className="variant-options">
              {product.variants.map((v) => {
                const vid = v.id ?? v._id;
                const active = (selectedVariant?.id ?? selectedVariant?._id) === vid;
                const soldOut = v.stock === 0;
                return (
                  <button key={vid} type="button" className={`variant-btn${active ? " active" : ""}`}
                    onClick={() => setSelectedVariant(v)} disabled={soldOut} aria-pressed={active}
                    title={soldOut ? tr("product.outOfStock") : undefined}>{v.variantValue || v.name || v.value}</button>
                );
              })}
            </div>
          </div>
        )}

        <div className="qty-section">
          <button className="qty-btn" onClick={() => setQuantity(Math.max(1, quantity - 1))}>-</button>
          <span className="qty-display">{quantity}</span>
          <button className="qty-btn" onClick={() => setQuantity(quantity + 1)}>+</button>
        </div>

        <div className="action-buttons">
          <button className="btn-buy-now" onClick={handleBuyNow}>{tr("cart.buyNow")}</button>
          <button className="btn-add-cart" onClick={handleAddToCart}>{tr("compare.addToCart")}</button>
        </div>

        {/* Answers the questions that stop a purchase - delivery cost, returns, payment, who
            sells it - next to the buttons. Figures mirror the cart (free delivery from 500,
            otherwise 40) and the product's own return period, so keep them in step. */}
        <ul className="pd-assurance">
          <li>
            <Truck size={18} />
            <span>
              <strong>{(product.dealPrice || product.price) >= 500 ? tr("productTray.freeDelivery") : tr("productDetails.freeDeliveryOnOrdersOf")}</strong>
              {(product.dealPrice || product.price) < 500 && <> ({"₹"}{tr("productDetails.40BelowThat")}</>}
            </span>
          </li>
          <li>
            <RotateCcw size={18} />
            <span>
              {returnDays > 0
                ? <><strong>{returnDays}{tr("productTray.dayReturns")}</strong>{tr("productDetails.freeIfTheItemIs")}{" "}<Link to="/refund-policy">{tr("productDetails.returnsPolicy")}</Link></>
                : <><strong>{tr("productDetails.thisItemCannotBeReturned")}</strong>{" "}{tr("productDetails.unlessItArrivesDamagedOr")}{" "}<Link to="/refund-policy">{tr("productDetails.returnsPolicy")}</Link></>}
            </span>
          </li>
          <li>
            <ShieldCheck size={18} />
            <span><strong>{tr("cart.securePayment")}</strong>{" "}{tr("productDetails.byUpiCardNetBanking")}</span>
          </li>
          <li>
            <Store size={18} />
            {seller
              ? <span>{tr("productDetails.soldBy")}{" "}<strong>{seller.name}</strong>{seller.city ? `, ${seller.city}` : ""}{seller.state && seller.state !== seller.city ? `, ${seller.state}` : ""}
              {/* Shown because the e-commerce rules and FSSAI ask for them on every listing. */}
              {(seller.gstin || (seller.licences || []).length > 0) && (
                <span style={{ display: "block", fontSize: "0.8rem", color: "var(--color-text-secondary)", marginTop: 2 }}>
                  {[seller.gstin && `GSTIN ${seller.gstin}`, ...(seller.licences || []).map((l) => `${l.label} ${l.number}`)].filter(Boolean).join(" · ")}
                </span>
              )}
            </span>
              : <span><strong>{tr("productDetails.soldByAnApprovedSeller")}</strong>{" "}{tr("productDetails.onCauveryStore")}</span>}
          </li>
        </ul>

        {(product.reviews || []).length > 0 && (
          <div className="reviews-section">
            <h3>{tr("productDetails.customerReviews")}</h3>
            {product.reviews.map((r, i) => (
              <div key={i} className="review-card">
                <div className="review-header"><span className="review-user">{r.user || r.name || tr("productDetails.anonymous")}</span><span>{'★'.repeat(r.rating)}</span></div>
                <p className="review-text">{r.comment || r.review}</p>
              </div>
            ))}
          </div>
        )}

        {(product.qnas || []).length > 0 && (
          <div className="qna-section">
            <h3>{tr("productDetails.questionsAndAnswers")}</h3>
            {product.qnas.map((q, i) => (
              <div key={i} className="review-card">
                <p style={{ fontWeight: 500 }}>Q: {q.question}</p>
                <p style={{ color: "#475569" }}>A: {q.answer || tr("productDetails.theSellerHasNotAnswered")}</p>
              </div>
            ))}
          </div>
        )}
      </div>

      </div>{/* .pd-main */}

      {cartMsg && (
        <div style={{ position: "fixed", bottom: "2rem", right: "2rem", zIndex: 9999, background: "var(--color-primary, #0E5C5C)", color: "#fff", padding: "0.75rem 1.25rem", borderRadius: "10px", fontWeight: 600, boxShadow: "0 4px 12px rgba(0,0,0,0.15)" }}>
          {cartMsg}
        </div>
      )}

      {/* Related Products Carousel */}
      {relatedProducts.length > 0 && (
        <section className="related-products-section">
          <h2 className="related-products-title">{tr("productDetails.youMayAlsoLike")}</h2>
          <ProductRow>
            {relatedProducts.map((p) => (
              <ProductTray key={p.id || p._id} product={p} onAddToCart={handleRelatedCart} onBuyNow={handleRelatedBuyNow} />
            ))}
          </ProductRow>
        </section>
      )}

      <style>{`
        .pd-assurance { list-style: none; margin: 1.25rem 0 0; padding: 0.9rem 1rem; border: 1px solid #e2e8f0; border-radius: 10px; background: #f8fafc; display: grid; gap: 0.6rem; }
        .pd-assurance li { display: flex; gap: 0.6rem; align-items: flex-start; font-size: 0.88rem; color: #334155; line-height: 1.45; }
        .pd-assurance svg { flex-shrink: 0; color: #0E5C5C; margin-top: 1px; }
        .pd-assurance strong { color: #0f172a; }
        .pd-assurance a { color: #0E5C5C; white-space: nowrap; }
        .related-products-section { margin-top: 2.5rem; padding-top: 1.5rem; border-top: 1px solid #e2e8f0; }
        .related-products-title { font-size: 1.25rem; font-weight: 700; margin: 0 0 1rem; color: #1e293b; }
      `}</style>
    </div>
  );
};
export default ProductDetails;
