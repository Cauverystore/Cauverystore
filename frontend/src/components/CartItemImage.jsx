import React, { useState, useMemo, useCallback } from "react";
import { imgUrl } from "../utils/images";

const toUrl = (img) => typeof img === "object" ? img?.url || "" : img || "";
const toThumb = (img) => typeof img === "object" && img?.thumbUrl ? img.thumbUrl : "";

const CartItemImage = ({ src, name, width = 60, height = 60, className = "" }) => {
  const [failed, setFailed] = useState(false);

  const finalSrc = useMemo(() => {
    const thumb = toThumb(src);
    if (thumb) return imgUrl(thumb);
    const raw = toUrl(src);
    return raw ? imgUrl(raw) : null;
  }, [src]);

  const handleError = useCallback(() => setFailed(true), []);

  if (!finalSrc || failed) {
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
      src={finalSrc}
      alt={name || ""}
      width={width}
      height={height}
      className={className}
      loading="lazy"
      onError={handleError}
      style={{ objectFit: "cover" }}
    />
  );
};

export default CartItemImage;
