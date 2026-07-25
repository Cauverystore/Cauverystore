import React, { useState } from "react";

const PLACEHOLDER_SVG = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='100' height='100' viewBox='0 0 100 100'%3E%3Crect fill='%23f1f5f9' width='100' height='100'/%3E%3Cpath d='M35 55l10-12 8 10 12-15 10 17H35z' fill='%23cbd5e1'/%3E%3Ccircle cx='38' cy='38' r='6' fill='%23cbd5e1'/%3E%3C/svg%3E";

const CartItemImage = ({ src, name, width = 60, height = 60, className = "" }) => {
  const [failed, setFailed] = useState(false);
  const hasValidSrc = Boolean(src) && !failed;

  if (!hasValidSrc) {
    return (
      <div
        className={`cart-item-img-placeholder ${className}`}
        style={{ width, height }}
        title={name}
      >
        <span className="cart-item-img-placeholder-text">{name || "Product"}</span>
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={name || ""}
      width={width}
      height={height}
      className={className}
      onError={() => setFailed(true)}
      style={{ objectFit: "cover" }}
    />
  );
};

export default CartItemImage;
