import React from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { Home, ShoppingBag, ShoppingCart, Heart, User } from "lucide-react";
import "../styles/product-tray.css";

const MobileBottomBar = ({ cartCount = 0 }) => {
  const navigate = useNavigate();
  const location = useLocation();

  const isActive = (path) => location.pathname === path;

  const items = [
    { icon: Home, label: "Home", path: "/" },
    { icon: ShoppingBag, label: "Shop", path: "/products" },
    { icon: ShoppingCart, label: "Cart", path: "/cart", count: cartCount },
    { icon: Heart, label: "Wishlist", path: "/wishlist" },
    { icon: User, label: "Profile", path: "/profile" },
  ];

  return (
    <nav className="pt-bottom-bar" aria-label="Mobile navigation">
      {items.map((item) => (
        <button
          key={item.path}
          className={`pt-bottom-btn ${isActive(item.path) ? "active" : ""}`}
          onClick={() => navigate(item.path)}
          aria-label={item.label}
        >
          <span className="pt-bottom-cart">
            <item.icon size={20} />
            {item.count != null && item.count > 0 && (
              <span className="pt-bottom-cart-count">{item.count > 99 ? "99+" : item.count}</span>
            )}
          </span>
          <span>{item.label}</span>
        </button>
      ))}
    </nav>
  );
};

export default MobileBottomBar;
