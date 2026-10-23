import React from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { Home, ShoppingBag, ShoppingCart, Heart, User } from "lucide-react";
import "../styles/product-tray.css";
import { useTranslation } from "react-i18next";

const MobileBottomBar = ({ cartCount = 0 }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();

  const isActive = (path) => location.pathname === path;

  const items = [
    { icon: Home, labelKey: "nav.home", path: "/" },
    { icon: ShoppingBag, labelKey: "nav.shop", path: "/products" },
    { icon: ShoppingCart, labelKey: "nav.cart", path: "/cart", count: cartCount },
    { icon: Heart, labelKey: "nav.wishlist", path: "/wishlist" },
    { icon: User, labelKey: "nav.profileShort", path: "/profile" },
  ];

  return (
    <nav className="pt-bottom-bar" aria-label={t("nav.mobileNav")}>
      {items.map((item) => (
        <button
          key={item.path}
          className={`pt-bottom-btn ${isActive(item.path) ? "active" : ""}`}
          onClick={() => navigate(item.path)}
          aria-label={t(item.labelKey)}
        >
          <span className="pt-bottom-cart">
            <item.icon size={20} />
            {item.count != null && item.count > 0 && (
              <span className="pt-bottom-cart-count">{item.count > 99 ? "99+" : item.count}</span>
            )}
          </span>
          <span>{t(item.labelKey)}</span>
        </button>
      ))}
    </nav>
  );
};

export default MobileBottomBar;
