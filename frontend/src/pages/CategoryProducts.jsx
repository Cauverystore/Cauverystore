import React, { useState, useEffect } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { Store } from "lucide-react";
import { searchProducts } from "../services/productService";
import api from "../api/axios";
import NotFound from "./NotFound";
import { addToCartOrLogin } from "../utils/cartActions";
import ProductTray, { LoadingSkeleton } from "../components/ProductTray";
import Breadcrumb from "../components/Breadcrumb";
import Pagination from "../components/Pagination";
import "../styles/products.css";
import "../styles/product-tray.css";

// Unique on-page copy per category, so category pages do not all read the same to a
// search engine. Each entry doubles as the meta description crawlers and link previews see.
// Keep it plain and factual: what the category holds, plus terms that are true store-wide.
//
// Keyed by the exact category name the backend returns (CategoryController's
// /api/categories, Category.name). A category with no entry here still works (see
// FALLBACK below); it just has no hand-written copy until one is added.
const CATEGORY_SEO_COPY = {
  "T-Shirts": {
    intro:
      "T-shirts in a range of colours and sizes. Choose your size on the product page, pay " +
      "online, and get free delivery on orders of ₹500 or more.",
    metaDescription:
      "Shop t-shirts online at Cauvery Store. Choose your size, pay securely, and get free delivery on orders of ₹500 or more.",
  },
  "Electronics": {
    intro:
      "Phones, laptops, TVs and everyday gadgets. Each product page shows the price, " +
      "delivery time and return period before you buy.",
    metaDescription:
      "Shop phones, laptops, TVs and gadgets online at Cauvery Store. Secure payment and free delivery on orders of ₹500 or more.",
  },
  "Fashion": {
    intro:
      "Clothing, footwear and accessories for everyday wear. Sizes are listed on each " +
      "product page, and most items can be returned within 7 days of delivery.",
    metaDescription:
      "Shop clothing, footwear and accessories online at Cauvery Store. Secure payment and free delivery on orders of ₹500 or more.",
  },
  "Home & Kitchen": {
    intro:
      "Furniture, kitchen essentials and home decor, delivered to your door. Delivery is " +
      "free on orders of ₹500 or more.",
    metaDescription:
      "Shop furniture, kitchen essentials and home decor online at Cauvery Store. Secure payment and free delivery on orders of ₹500 or more.",
  },
  "Books": {
    intro:
      "Fiction, non-fiction and educational titles, delivered across India.",
    metaDescription:
      "Shop fiction, non-fiction and educational books online at Cauvery Store. Secure payment and free delivery on orders of ₹500 or more.",
  },
  "Sports": {
    intro:
      "Fitness equipment and sportswear, from a first set of weights to matchday gear.",
    metaDescription:
      "Shop fitness equipment and sportswear online at Cauvery Store. Secure payment and free delivery on orders of ₹500 or more.",
  },
};

// Any category not in the map above still gets a real sentence, so no category page is
// left with no body copy for a crawler to read.
const FALLBACK_SEO_COPY = (category) => ({
  intro:
    `Shop ${category} at Cauvery Store, with secure payment and free delivery on orders of ₹500 or more.`,
  metaDescription:
    `Shop ${category} online at Cauvery Store. Secure payment and free delivery on orders of ₹500 or more.`,
});

const CategoryProducts = () => {
  const { category } = useParams();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const navigate = useNavigate();
  // The product search ignores a category it does not know and returns everything, so a
  // made-up /category/anything would otherwise show the whole catalogue as a real page.
  // null = not checked yet; a failed check leaves the page showing as before.
  const [known, setKnown] = useState(null);

  useEffect(() => {
    setKnown(null);
    api.get("/api/categories")
      .then((r) => {
        // Exact match: the search itself is case-sensitive about category names.
        const names = (Array.isArray(r.data) ? r.data : []).map((c) => c.name);
        setKnown(names.includes(category));
      })
      .catch(() => {});
  }, [category]);

  useEffect(() => {
    const fetch = async () => {
      setLoading(true);
      setError("");
      try {
        const res = await searchProducts({ category, page: page - 1, size: 20 });
        setProducts(res.data.content || res.data || []);
        setTotalPages(res.data.totalPages || 1);
      } catch (err) {
        setError(err.response?.data?.error || "Please check your internet connection and try again.");
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

  const seoCopy = CATEGORY_SEO_COPY[category] || FALLBACK_SEO_COPY(category);
  const canonicalUrl = `https://cauverystore.in/category/${encodeURIComponent(category || "")}`;
  const breadcrumbJsonLd = {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    "itemListElement": [
      { "@type": "ListItem", "position": 1, "name": "Home", "item": "https://cauverystore.in/" },
      { "@type": "ListItem", "position": 2, "name": category, "item": canonicalUrl },
    ],
  };

  if (known === false) return <NotFound />;

  return (
    <div className="products-page">
      <style>{`.cat-products-content { grid-column: 1 / -1; }`}</style>
      <Helmet>
        <title>{`${category} | Cauvery Store`}</title>
        <meta name="description" content={seoCopy.metaDescription} />
        <link rel="canonical" href={canonicalUrl} />
        {/* An empty or made-up category is a thin page: keep it out of search results. */}
        {!loading && !error && products.length === 0 && <meta name="robots" content="noindex, follow" />}
        <meta property="og:title" content={`${category} | Cauvery Store`} />
        <meta property="og:description" content={seoCopy.metaDescription} />
        <meta property="og:type" content="website" />
        <meta property="og:url" content={canonicalUrl} />
        <meta property="og:image" content="https://cauverystore.in/logo512.png" />
        <script type="application/ld+json">{JSON.stringify(breadcrumbJsonLd)}</script>
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
        <div className="cat-products-banner">
          <Store size={22} className="cat-products-banner-icon" aria-hidden="true" />
          <div>
            <strong className="cat-products-banner-label">Why shop {category} here</strong>
            <p className="cat-products-banner-text">{seoCopy.intro}</p>
          </div>
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
            <h3 className="products-error-title">We couldn't load this category</h3>
            <p className="products-error-text">{error}</p>
            <button className="products-error-retry" onClick={() => window.location.reload()}>Try Again</button>
          </div>
        ) : products.length === 0 ? (
          <div className="products-empty">
            <div className="products-empty-icon">📦</div>
            <h3 className="products-empty-title">Nothing here yet</h3>
            <p className="products-empty-text">There are no products in this category yet. See what is available now.</p>
            <Link to="/products" className="products-empty-action">Browse all products</Link>
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
