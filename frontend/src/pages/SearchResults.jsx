import React, { useState, useEffect } from "react";
import { Link, useSearchParams, useNavigate } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { searchProducts } from "../services/productService";
import { addToCartOrLogin } from "../utils/cartActions";
import ProductTray, { LoadingSkeleton } from "../components/ProductTray";
import Breadcrumb from "../components/Breadcrumb";
import Pagination from "../components/Pagination";
import "../styles/products.css";
import "../styles/product-tray.css";
import { useTranslation } from "react-i18next";

const SearchResults = () => {
  const { t } = useTranslation();

  const [searchParams] = useSearchParams();
  const query = searchParams.get("q") || "";
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const navigate = useNavigate();

  useEffect(() => {
    if (!query) {
      setLoading(false);
      setProducts([]);
      return;
    }
    const fetch = async () => {
      setLoading(true);
      setError("");
      try {
        const res = await searchProducts({ name: query, page: page - 1, size: 20 });
        setProducts(res.data.content || res.data || []);
        setTotalPages(res.data.totalPages || 1);
      } catch (err) {
        setError(err.response?.data?.error || t("categoryProducts.pleaseCheckYourInternetConnection"));
      }
      setLoading(false);
    };
    fetch();
  }, [query, page]);

  const handleAddToCart = async (product) => {
    await addToCartOrLogin(navigate, product, 1);
  };

  const handleBuyNow = async (product) => {
    const res = await addToCartOrLogin(navigate, product, 1);
    if (res.ok) navigate("/checkout");
  };

  if (!query) {
    return (
      <div className="products-page">
        <div className="products-empty">
          <div className="products-empty-icon">🔍</div>
          <h3 className="products-empty-title">{t("searchResults.whatAreYouLookingFor")}</h3>
          <p className="products-empty-text">{t("searchResults.typeAProductNameOr")}</p>
        </div>
      </div>
    );
  }

  return (
    <>
      <style>{`.search-results-content { grid-column: 1 / -1; }`}</style>
      <Helmet>
        <title>{query ? `Search: ${query} - Cauvery Store` : t("searchResults.searchCauveryStore")}</title>
        <meta name="description" content={query ? `Search results for "${query}" at Cauvery Store` : "Search for products at Cauvery Store"} />
      </Helmet>
      <div className="products-page">
        <div className="search-results-content">
          <Breadcrumb items={[
            { label: t("nav.home"), to: "/" },
            ...(query ? [{ label: `Search: ${query}` }] : [{ label: t("search.button") }])
          ]} />
          <div className="section-header">
            <h2 className="section-title">{t("searchResults.searchResultsFor")}{query}"</h2>
            <span className="products-toolbar-count">{products.length}{" "}{t("searchResults.productsFound")}</span>
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
              <h3 className="products-error-title">{t("searchResults.weCouldnTRunThat")}</h3>
              <p className="products-error-text">{error}</p>
              <button className="products-error-retry" onClick={() => window.location.reload()}>{t("categoryProducts.tryAgain")}</button>
            </div>
          ) : products.length === 0 ? (
            <div className="products-empty">
              <div className="products-empty-icon">📦</div>
              <h3 className="products-empty-title">{t("searchResults.noResultsFor")}{query}"</h3>
              <p className="products-empty-text">{t("searchResults.checkTheSpellingTryA")}</p>
              <Link to="/products" className="products-empty-action">{t("search.browseAll")}</Link>
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
    </>
  );
};
export default SearchResults;
