import React, { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { ShoppingCart, X, Plus, Minus, Trash2 } from "lucide-react";
import { getCart, updateQuantity as updateCartItemQuantity, removeItem as removeCartItem } from "../services/cartService";
import CartItemImage from "./CartItemImage";

const CartDrawer = ({ open, onClose }) => {
  const [cartItems, setCartItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const drawerRef = useRef(null);
  const previouslyFocused = useRef(null);

  useEffect(() => {
    if (!open) return;

    previouslyFocused.current = document.activeElement;

    const getFocusable = () => {
      const drawer = drawerRef.current;
      if (!drawer) return [];
      return Array.from(drawer.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'))
        .filter((el) => !el.disabled && el.getAttribute("aria-hidden") !== "true");
    };

    const handleKeyDown = (e) => {
      if (e.key === "Escape") { onClose(); return; }
      if (e.key === "Tab") {
        const els = getFocusable();
        if (els.length === 0) return;
        const first = els[0];
        const last = els[els.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    };

    const focusTimer = setTimeout(() => {
      const els = getFocusable();
      if (els.length > 0) els[0].focus();
    }, 60);

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      clearTimeout(focusTimer);
      document.removeEventListener("keydown", handleKeyDown);
      if (previouslyFocused.current && typeof previouslyFocused.current.focus === "function") {
        previouslyFocused.current.focus();
      }
    };
  }, [open, onClose]);

  useEffect(() => {
    if (open) {
      setLoading(true);
      getCart()
        .then((res) => {
          const items = res.data?.items || res.data?.cartItems || [];
          setCartItems(items);
        })
        .catch(() => setCartItems([]))
        .finally(() => setLoading(false));
    }
  }, [open]);

  const subtotal = cartItems.reduce((sum, item) => {
    const price = item.price || item.product?.price || 0;
    const qty = item.quantity || 1;
    return sum + price * qty;
  }, 0);

  const handleQtyChange = async (itemId, newQty) => {
    if (newQty < 1) return;
    try {
      await updateCartItemQuantity(itemId, newQty);
      setCartItems((prev) =>
        prev.map((item) =>
          (item.id === itemId || item._id === itemId) ? { ...item, quantity: newQty } : item
        )
      );
    } catch { }
  };

  const handleRemove = async (itemId) => {
    try {
      await removeCartItem(itemId);
      setCartItems((prev) => prev.filter((item) => item.id !== itemId && item._id !== itemId));
    } catch { }
  };

  if (!open) return null;

  return (
    <>
      <div className="cart-drawer-overlay" onClick={onClose} aria-hidden="true" />
      <div className="cart-drawer" role="dialog" aria-modal="true" aria-label="Shopping cart" ref={drawerRef}>
        <div className="cart-drawer-header">
          <h3><ShoppingCart size={18} /> Your Cart ({cartItems.length})</h3>
          <button className="cart-drawer-close" onClick={onClose} aria-label="Close cart"><X size={20} /></button>
        </div>

        <div className="cart-drawer-body" aria-live="polite">
          {loading ? (
            <div className="cart-drawer-loading">Loading...</div>
          ) : cartItems.length === 0 ? (
            <div className="cart-drawer-empty">
              <ShoppingCart size={40} />
              <p>Your cart is empty</p>
              <button className="cart-drawer-shop-btn" onClick={() => { onClose(); navigate("/products"); }}>
                Start Shopping
              </button>
            </div>
          ) : (
            cartItems.map((item) => {
              const id = item.id || item._id;
              const product = item.product || item;
              const price = item.price || product.price || 0;
              const name = product.name || "Product";
              const image = product.images?.[0]?.url || product.image || "";
              const qty = item.quantity || 1;
              return (
                <div key={id} className="cart-drawer-item">
                  <CartItemImage src={image} name={name} width={60} height={60} className="cart-drawer-item-img" />
                  <div className="cart-drawer-item-info">
                    <p className="cart-drawer-item-name">{name}</p>
                    <p className="cart-drawer-item-price">{"\u20B9"}{(price * qty).toLocaleString()}</p>
                    <div className="cd-attrs">
                      {product.color && <span className="cd-attr">Color: {product.color}</span>}
                      {product.size && <span className="cd-attr">Size: {product.size}</span>}
                      {product.material && <span className="cd-attr">Material: {product.material}</span>}
                      {product.weight && <span className="cd-attr">Weight: {product.weight}g</span>}
                    </div>
                    <div className="cart-drawer-item-qty">
                      <button className="cart-drawer-qty-btn" onClick={() => handleQtyChange(id, qty - 1)} disabled={qty <= 1} aria-label={`Decrease quantity of ${name}`}><Minus size={14} /></button>
                      <span>{qty}</span>
                      <button className="cart-drawer-qty-btn" onClick={() => handleQtyChange(id, qty + 1)} aria-label={`Increase quantity of ${name}`}><Plus size={14} /></button>
                    </div>
                  </div>
                  <button className="cart-drawer-remove-btn" onClick={() => handleRemove(id)} aria-label={`Remove ${name} from cart`}><Trash2 size={16} /></button>
                </div>
              );
            })
          )}
        </div>

        {cartItems.length > 0 && (
          <div className="cart-drawer-footer">
            <div className="cart-drawer-subtotal">
              <span>Subtotal</span>
              <span>{"\u20B9"}{subtotal.toLocaleString()}</span>
            </div>
            <button className="cart-drawer-checkout-btn" onClick={() => { onClose(); navigate("/cart"); }}>
              View Cart & Checkout
            </button>
          </div>
        )}
      </div>

      <style>{`
        .cd-attrs { display: flex; flex-wrap: wrap; gap: 4px 10px; margin-top: 4px; }
        .cd-attr { font-size: 0.72rem; color: #475569; background: #f1f5f9; padding: 1px 6px; border-radius: 4px; }
        .cart-drawer-overlay {
          position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 999; opacity: 1; transition: opacity 0.2s;
        }
        .cart-drawer {
          position: fixed; top: 0; right: 0; width: 400px; height: 100vh; background: #fff; z-index: 1000;
          display: flex; flex-direction: column; box-shadow: -4px 0 20px rgba(0,0,0,0.15); animation: slideIn 0.25s ease-out;
        }
        @keyframes slideIn { from { transform: translateX(100%); } to { transform: translateX(0); } }
        .cart-drawer-header {
          display: flex; align-items: center; justify-content: space-between; padding: 1rem 1.25rem;
          border-bottom: 1px solid #e2e8f0; flex-shrink: 0;
        }
        .cart-drawer-header h3 { margin: 0; font-size: 1.1rem; display: flex; align-items: center; gap: 0.5rem; }
        .cart-drawer-close { background: none; border: none; cursor: pointer; color: #64748b; padding: 0.25rem; }
        .cart-drawer-close:hover { color: #1e293b; }
        .cart-drawer-body { flex: 1; overflow-y: auto; padding: 1rem 1.25rem; }
        .cart-drawer-loading { text-align: center; color: #6b7280; padding: 2rem; }
        .cart-drawer-empty { text-align: center; padding: 3rem 1rem; color: #6b7280; }
        .cart-drawer-empty svg { margin-bottom: 1rem; }
        .cart-drawer-empty p { margin: 0 0 1rem; }
        .cart-drawer-shop-btn {
          padding: 0.6rem 1.5rem; background: #16a34a !important; color: #fff !important; border: none !important;
          border-radius: 8px; font-weight: 600; cursor: pointer;
        }
        .cart-drawer-shop-btn:hover { background: #15803d !important; color: #fff !important; }
        .cart-drawer-shop-btn:active { background: #166534 !important; color: #fff !important; }
        .cart-drawer-item { display: flex; gap: 0.75rem; padding: 0.75rem 0; border-bottom: 1px solid #f1f5f9; }
        .cart-drawer-item-img { border-radius: 8px; object-fit: cover; flex-shrink: 0; width: 60px; height: 60px; }
        .cart-drawer-item-img-placeholder {
          display: flex; align-items: center; justify-content: center;
          background: #f1f5f9; border: 1px dashed #d1d5db;
          font-size: 0.6rem; color: #64748b; text-align: center; padding: 4px;
          overflow: hidden; word-break: break-word; line-height: 1.2;
        }
        .cart-drawer-item-info { flex: 1; min-width: 0; }
        .cart-drawer-item-name { margin: 0 0 0.25rem; font-size: 0.85rem; color: #1e293b; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .cart-drawer-item-price { margin: 0 0 0.5rem; font-size: 0.9rem; color: var(--color-primary, #16a34a); font-weight: 700; }
        .cart-drawer-item-qty { display: flex; align-items: center; gap: 0.5rem; }
        .cart-drawer-qty-btn {
          width: 26px; height: 26px; border: 1px solid #d1d5db; border-radius: 4px; background: #fff;
          display: flex; align-items: center; justify-content: center; cursor: pointer; color: #475569;
        }
        .cart-drawer-qty-btn:disabled { opacity: 0.4; cursor: not-allowed; }
        .cart-drawer-qty-btn:hover:not(:disabled) { background: #f1f5f9; }
        .cart-drawer-item-qty span { font-size: 0.85rem; font-weight: 600; min-width: 20px; text-align: center; }
        .cart-drawer-remove-btn {
          background: none; border: none; color: #16a34a !important; cursor: pointer; padding: 0.25rem; align-self: flex-start; flex-shrink: 0;
        }
        .cart-drawer-remove-btn:hover { color: #15803d !important; background: #f0fdf4; border-radius: 4px; }
        .cart-drawer-remove-btn:active { color: #166534 !important; background: #dcfce7; border-radius: 4px; }
        .cart-drawer-footer {
          border-top: 1px solid #e2e8f0; padding: 1rem 1.25rem; flex-shrink: 0;
        }
        .cart-drawer-subtotal {
          display: flex; justify-content: space-between; font-size: 1rem; font-weight: 700; margin-bottom: 0.75rem;
        }
        .cart-drawer-checkout-btn {
          width: 100%; padding: 0.75rem; background: #16a34a !important; color: #fff !important; border: none !important;
        }
        .cart-drawer-checkout-btn:hover { background: #15803d !important; color: #fff !important; opacity: 0.95; }
        .cart-drawer-checkout-btn:active { background: #166534 !important; color: #fff !important; opacity: 0.9; }
        @media (max-width: 480px) {
          .cart-drawer { width: 100%; }
        }
      `}</style>
    </>
  );
};

export default CartDrawer;
