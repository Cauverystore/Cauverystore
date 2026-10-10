import React, { useState, useEffect } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { Store } from "lucide-react";
import { searchProducts } from "../services/productService";
import { addToCartOrLogin } from "../utils/cartActions";
import ProductTray, { LoadingSkeleton } from "../components/ProductTray";
import Breadcrumb from "../components/Breadcrumb";
import Pagination from "../components/Pagination";
import "../styles/products.css";
import "../styles/product-tray.css";

// Real, unique on-page copy per category - this is what was missing for SEO: the generic
// "Shop {category} at Cauvery Store" line it replaces was identical in shape on every
// category page, which reads to Google as thin/duplicate content and gives it nothing to
// rank the page on. Each entry below is written around the site's actual differentiator
// (direct-from-Tamil-Nadu-sellers) rather than generic marketplace filler, and doubles as
// the meta description crawlers/link-previews see.
//
// Keyed by the exact category name the backend returns (CategoryController's
// /api/categories, Category.name) - keep new keys in sync with however sellers name
// categories there. A category with no entry here still works fine (see FALLBACK below),
// it just won't have hand-written copy until one is added.
const CATEGORY_SEO_COPY = {
  "Electronics": {
    intro:
      "Phones, laptops, TVs and everyday gadgets from sellers across Tamil Nadu - not a " +
      "faceless warehouse listing. Every electronics seller on Cauvery Store is a real shop " +
      "or trader you can see by name and location before you buy, so you know who to call if " +
      "something needs a warranty claim or a quick exchange.",
    metaDescription:
      "Buy phones, laptops, TVs and gadgets direct from Tamil Nadu electronics sellers. See who's selling, pay by UPI or card, and get it delivered to your door.",
  },
  "Fashion": {
    intro:
      "Clothing, footwear and accessories from Tamil Nadu's own boutiques and tailoring " +
      "shops, alongside the big-brand basics you'd expect. Shopping local here means styles " +
      "you won't find on every other marketplace, and a real seller behind each listing " +
      "rather than a generic storefront.",
    metaDescription:
      "Shop clothing, footwear and accessories from Tamil Nadu fashion sellers and boutiques. Pay by UPI or card and get it delivered to your door.",
  },
  "Home & Kitchen": {
    intro:
      "Furniture, kitchen essentials and home decor from local makers and household-goods " +
      "sellers across Tamil Nadu. From everyday cookware to furniture pieces made by small " +
      "workshops, this is where to find home goods with a seller you can actually trace back " +
      "to a shop.",
    metaDescription:
      "Buy furniture, kitchen essentials and home decor direct from Tamil Nadu sellers. See who sells it and where, pay by UPI or card, and get it delivered.",
  },
  "Books": {
    intro:
      "Fiction, non-fiction and educational titles from booksellers across Tamil Nadu, " +
      "including Tamil-language titles harder to find on the bigger marketplaces. Every " +
      "listing here comes from a seller you can see by name, not an anonymous warehouse SKU.",
    metaDescription:
      "Buy fiction, non-fiction, educational and Tamil-language books from Tamil Nadu booksellers. Pay by UPI or card and get it delivered to your door.",
  },
  "Sports": {
    intro:
      "Fitness equipment and sportswear from Tamil Nadu retailers and sports shops, for " +
      "everything from a first set of weights to matchday gear. Buying here keeps that " +
      "purchase with a local seller instead of a national chain's warehouse.",
    metaDescription:
      "Shop fitness equipment and sportswear direct from Tamil Nadu sports retailers. See who sells it, pay by UPI or card, and get it delivered to your door.",
  },
};

// Any category not in the map above (a seller-added category we haven't written copy for
// yet) still gets a real sentence instead of the old generic line, so no page on the site
// is left with zero body content for a crawler to read.
const FALLBACK_SEO_COPY = (category) => ({
  intro:
    `${category} products from sellers across Tamil Nadu, each listed by a real shop or ` +
    `trader you can see before you buy. Pay by UPI or card and get it delivered to your door.`,
  metaDescription:
    `Buy ${category} direct from Tamil Nadu sellers on Cauvery Store. See who sells it, pay by UPI or card, and get it delivered.`,
});

const CategoryProducts = () => {
  const { category } = useParams();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const navigate = useNavigate();

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

  return (
    <div className="products-page">
      <style>{`.cat-products-content { grid-column: 1 / -1; }`}</style>
      <Helmet>
        <title>{`${category} | Shop Direct from Tamil Nadu Sellers - Cauvery Store`}</title>
        <meta name="description" content={seoCopy.metaDescription} />
        <link rel="canonical" href={canonicalUrl} />
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
            <p className="products-empty-text">Our sellers have not listed products in this category yet. See what is available now.</p>
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
