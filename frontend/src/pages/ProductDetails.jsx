import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import api from "../api/axios";
import { getProductById } from "../services/productService";
import { addToCart } from "../services/cartService";
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

  useEffect(() => {
    const fetchProduct = async () => {
      try {
        const res = await getProductById(id);
        setProduct(res.data);
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

  const handleAddToCart = async () => {
    try {
      await addToCart(product.id || product._id, quantity);
      setCartMsg("Added to cart!");
      setTimeout(() => setCartMsg(""), 2000);
    } catch (err) { setCartMsg("Failed to add to cart"); setTimeout(() => setCartMsg(""), 2000); }
  };

  const handleBuyNow = async () => {
    try {
      await addToCart(product.id || product._id, quantity);
      navigate("/checkout");
    } catch { setCartMsg("Failed to add to cart"); setTimeout(() => setCartMsg(""), 2000); }
  };

  const toUrl = (img) => typeof img === "object" ? img?.url || "" : img || "";
  const categoryName = typeof product?.category === "object" ? product?.category?.name || "" : product?.category || "";
  // Escaping "<" prevents a "</script>" inside a product name/description from
  // closing this script tag early and letting injected markup execute as real
  // script in every visitor's browser (stored XSS via JSON-LD).
  const safeJsonLd = (obj) => JSON.stringify(obj, null, 2).replace(/</g, "\\u003c");

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;
  if (!product) return <div style={{ textAlign: "center", padding: "3rem" }}>Product not found</div>;

  return (
    <div className="product-detail-page">
      <Helmet>
        <title>{product.name} - Cauvery Store</title>
        <meta name="description" content={product.description?.substring(0, 160) || `${product.name} at Cauvery Store`} />
        <meta property="og:title" content={`${product.name} - Cauvery Store`} />
        <meta property="og:description" content={product.description?.substring(0, 200) || `${product.name} at Cauvery Store`} />
        <meta property="og:image" content={toUrl(product.images?.[0]) || product.image || ""} />
        <meta property="og:type" content="product" />
      </Helmet>
      <script type="application/ld+json" dangerouslySetInnerHTML={{__html: safeJsonLd({
        "@context": "https://schema.org/",
        "@type": "Product",
        "name": product.name,
        "image": toUrl(product.images?.[0]) || product.image || "",
        "description": product.description,
        "sku": product.sku || product.id?.toString(),
        "brand": product.brand ? { "@type": "Brand", "name": product.brand } : undefined,
        "offers": {
          "@type": "Offer",
          "url": window.location.href,
          "priceCurrency": "INR",
          "price": product.dealPrice || product.price,
          "priceValidUntil": new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString().split("T")[0],
          "itemCondition": "https://schema.org/NewCondition",
          "availability": (product.stock > 0) ? "https://schema.org/InStock" : "https://schema.org/OutOfStock"
        },
        "aggregateRating": product.rating ? {
          "@type": "AggregateRating",
          "ratingValue": product.rating,
          "reviewCount": product.reviewCount || 0
        } : undefined
      })}} />
      <script type="application/ld+json" dangerouslySetInnerHTML={{__html: safeJsonLd({
        "@context": "https://schema.org",
        "@type": "BreadcrumbList",
        "itemListElement": [
          { "@type": "ListItem", "position": 1, "name": "Home", "item": window.location.origin + "/" },
          ...(categoryName ? [{ "@type": "ListItem", "position": 2, "name": categoryName, "item": window.location.origin + "/category/" + encodeURIComponent(categoryName) }] : []),
          { "@type": "ListItem", "position": categoryName ? 3 : 2, "name": product.name, "item": window.location.href }
        ]
      })}} />
      <Breadcrumb items={[
        { label: "Home", to: "/" },
        ...(categoryName ? [{ label: categoryName, to: `/category/${encodeURIComponent(categoryName)}` }] : []),
        { label: product.name }
      ]} />
      <div className="pd-main">
        <div className="product-image-section">
          <img className="product-main-image" src={toUrl(product.images?.[selectedImage]) || product.image || "/images/placeholder.svg"} alt={product.name} width="400" height="400" />
          {(product.images || []).length > 1 && (
            <div className="product-thumbnails">
              {product.images.map((img, i) => (
                <img key={i} src={toUrl(img)} alt="" width="60" height="60" className={i === selectedImage ? "active" : ""} onClick={() => setSelectedImage(i)} />
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

        {(product.reviews || []).length > 0 && (
          <div className="reviews-section">
            <h3>Reviews</h3>
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
            <h3>Q&A</h3>
            {product.qnas.map((q, i) => (
              <div key={i} className="review-card">
                <p style={{ fontWeight: 500 }}>Q: {q.question}</p>
                <p style={{ color: "#475569" }}>A: {q.answer || "Awaiting response..."}</p>
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
          <h2 className="related-products-title">Related Products</h2>
          <div className="related-products-scroll">
            {relatedProducts.map((p) => {
              const pid = p.id || p._id;
              const img = p.images?.[0]?.url || p.image || "";
              const price = p.dealPrice || p.price || 0;
              return (
                <div key={pid} className="related-product-card" onClick={() => navigate(`/product/${pid}`)}>
                  <img src={img} alt={p.name} width="200" height="200" loading="lazy" className="related-product-img" />
                  <div className="related-product-info">
                    <p className="related-product-name">{p.name}</p>
                    <p className="related-product-price">{"\u20B9"}{price.toLocaleString()}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      )}

      <style>{`
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
