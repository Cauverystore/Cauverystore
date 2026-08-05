import React, { useState, useEffect } from "react";
import { useNavigate, Link } from "react-router-dom";
import { GitCompareArrows, Trash2, X, ShoppingCart, Eye } from "lucide-react";
import { getCompareProducts, removeCompare, clearCompare } from "../utils/compare";
import { addToCartOrLogin } from "../utils/cartActions";
import { imgUrl } from "../utils/images";

const PLACEHOLDER = "/images/placeholder.svg";

const Compare = () => {
  const navigate = useNavigate();
  const [products, setProducts] = useState([]);

  const reload = () => setProducts(getCompareProducts());

  useEffect(() => {
    reload();
    const onStorage = () => reload();
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);

  const pidOf = (p) => p?.id || p?._id;
  const nameOf = (p) => p?.name || "Product";
  const priceOf = (p) => p?.price || p?.dealPrice || p?.sellingPrice || 0;
  const originalPriceOf = (p) => p?.originalPrice || p?.mrp || priceOf(p);
  const imageOf = (p) => {
    const raw = p?.images?.[0]?.url || p?.image || p?.images?.[0] || "";
    return raw ? imgUrl(raw) : PLACEHOLDER;
  };

  const handleRemove = (e, p) => {
    e.stopPropagation();
    removeCompare(pidOf(p));
    reload();
  };

  const handleClear = () => {
    clearCompare();
    reload();
  };

  const handleAddToCart = async (p) => {
    const res = await addToCartOrLogin(navigate, p, 1);
    if (res.needLogin) return;
  };

  const bestPrice = products.length ? Math.min(...products.map(priceOf)) : 0;

  return (
    <div className="cmp-page">
      <div className="cmp-header">
        <h1 className="cmp-title"><GitCompareArrows size={22} color="#16a34a" /> Compare Products</h1>
        {products.length > 0 && (
          <button className="cmp-clear" onClick={handleClear}>Clear All</button>
        )}
      </div>

      {products.length === 0 ? (
        <div className="cmp-empty">
          <div className="cmp-empty-icon">{"\u2696"}</div>
          <h3>No products to compare</h3>
          <p>Add products using the compare button on any product card, then view the differences here.</p>
          <Link className="cmp-empty-cta" to="/products">Browse Products</Link>
        </div>
      ) : (
        <>
          <div className="cmp-scroll">
            <div className="cmp-table" style={{ gridTemplateColumns: `minmax(110px, 140px) repeat(${products.length}, minmax(180px, 1fr))` }}>
              <div className="cmp-cell cmp-label">Product</div>
              {products.map((p) => (
                <div className="cmp-cell cmp-product" key={pidOf(p)}>
                  <button className="cmp-remove" onClick={(e) => handleRemove(e, p)} aria-label={`Remove ${nameOf(p)} from compare`}><X size={16} /></button>
                  <img src={imageOf(p)} alt={nameOf(p)} width="120" height="120" loading="lazy" className="cmp-img" onClick={() => navigate(`/product/${pidOf(p)}`)} />
                  <div className="cmp-name" onClick={() => navigate(`/product/${pidOf(p)}`)}>{nameOf(p)}</div>
                </div>
              ))}

              <div className="cmp-cell cmp-label">Price</div>
              {products.map((p) => (
                <div className="cmp-cell cmp-price" key={pidOf(p)}>
                  <span className="cmp-price-now">{"\u20B9"}{Math.round(priceOf(p)).toLocaleString()}</span>
                  {originalPriceOf(p) > priceOf(p) && <span className="cmp-price-orig">{"\u20B9"}{Math.round(originalPriceOf(p)).toLocaleString()}</span>}
                </div>
              ))}

              <div className="cmp-cell cmp-label">Discount</div>
              {products.map((p) => {
                const orig = originalPriceOf(p);
                const disc = orig > priceOf(p) ? Math.round((1 - priceOf(p) / orig) * 100) : 0;
                return <div className="cmp-cell" key={pidOf(p)}>{disc > 0 ? `${disc}% OFF` : "—"}</div>;
              })}

              <div className="cmp-cell cmp-label">Rating</div>
              {products.map((p) => {
                const rating = p?.rating || 4.0;
                return <div className="cmp-cell" key={pidOf(p)}>{"\u2605"} {rating.toFixed(1)}</div>;
              })}

              <div className="cmp-cell cmp-label">Availability</div>
              {products.map((p) => {
                const stock = p?.stock ?? p?.stockQuantity ?? (p?.inStock ? 1 : 0);
                return (
                  <div className="cmp-cell" key={pidOf(p)}>
                    {stock > 0
                      ? <span className="cmp-instock">{stock <= 5 ? `Only ${stock} left` : "In Stock"}</span>
                      : <span className="cmp-ostock">Out of Stock</span>}
                  </div>
                );
              })}

              {bestPrice > 0 && (
                <>
                  <div className="cmp-cell cmp-label">Best Value</div>
                  {products.map((p) => (
                    <div className="cmp-cell" key={pidOf(p)}>
                      {priceOf(p) === bestPrice && <span className="cmp-best">{"\u2713"} Lowest price</span>}
                    </div>
                  ))}
                </>
              )}
            </div>
          </div>

          <div className="cmp-actions-bar">
            {products.map((p) => (
              <div className="cmp-actions" key={pidOf(p)}>
                <button className="cmp-btn cmp-btn-cart" onClick={() => handleAddToCart(p)}><ShoppingCart size={15} /> Add to Cart</button>
                <button className="cmp-btn cmp-btn-view" onClick={() => navigate(`/product/${pidOf(p)}`)}><Eye size={15} /> View</button>
                <button className="cmp-btn cmp-btn-remove" onClick={() => { removeCompare(pidOf(p)); reload(); }}><Trash2 size={15} /> Remove</button>
              </div>
            ))}
          </div>

          <style>{`
            .cmp-page { max-width: 1200px; margin: 0 auto; padding: 1.5rem 1rem 4rem; }
            .cmp-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 1.25rem; }
            .cmp-title { display: flex; align-items: center; gap: 0.5rem; font-size: 1.4rem; font-weight: 700; color: #1e293b; margin: 0; }
            .cmp-clear { padding: 0.45rem 0.9rem; border: 1px solid #d1d5db; border-radius: 8px; background: #fff; color: #475569; font-size: 0.82rem; cursor: pointer; }
            .cmp-scroll { overflow-x: auto; }
            .cmp-table { display: grid; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden; min-width: 640px; }
            .cmp-cell { padding: 0.9rem 1rem; border-right: 1px solid #e2e8f0; border-bottom: 1px solid #e2e8f0; background: #fff; font-size: 0.85rem; color: #334155; }
            .cmp-cell:last-child { border-right: none; }
            .cmp-label { background: #f8fafc; font-weight: 600; color: #475569; display: flex; align-items: center; }
            .cmp-product { text-align: center; position: relative; }
            .cmp-img { width: 120px; height: 120px; object-fit: cover; border-radius: 8px; cursor: pointer; }
            .cmp-name { font-weight: 600; margin-top: 0.5rem; cursor: pointer; }
            .cmp-remove { position: absolute; top: 8px; right: 8px; background: #f1f5f9; border: none; border-radius: 50%; width: 26px; height: 26px; cursor: pointer; color: #475569; display: flex; align-items: center; justify-content: center; }
            .cmp-price-now { font-weight: 700; color: #16a34a; margin-right: 0.4rem; }
            .cmp-price-orig { text-decoration: line-through; color: #94a3b8; font-size: 0.78rem; }
            .cmp-instock { color: #16a34a; font-weight: 600; }
            .cmp-ostock { color: #dc2626; font-weight: 600; }
            .cmp-best { color: #b45309; font-weight: 600; }
            .cmp-empty { text-align: center; padding: 4rem 1rem; }
            .cmp-empty-icon { font-size: 3rem; opacity: 0.4; margin-bottom: 1rem; }
            .cmp-empty h3 { font-size: 1.25rem; color: #1e293b; margin-bottom: 0.5rem; }
            .cmp-empty p { color: #64748b; max-width: 420px; margin: 0 auto 1.5rem; }
            .cmp-empty-cta { display: inline-block; padding: 0.6rem 1.4rem; background: #16a34a; color: #fff; border-radius: 8px; text-decoration: none; font-weight: 600; }
            .cmp-actions-bar { display: flex; flex-wrap: wrap; gap: 1rem; margin-top: 1.25rem; }
            .cmp-actions { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; }
            .cmp-btn { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.5rem 0.9rem; border-radius: 8px; font-size: 0.8rem; cursor: pointer; border: 1px solid #d1d5db; background: #fff; color: #334155; }
            .cmp-btn-cart { background: #16a34a; color: #fff; border-color: #16a34a; font-weight: 600; }
            .cmp-btn-view { background: #2563eb; color: #fff; border-color: #2563eb; }
            .cmp-btn-remove:hover { border-color: #dc2626; color: #dc2626; }
          `}</style>
        </>
      )}
    </div>
  );
};

export default Compare;
