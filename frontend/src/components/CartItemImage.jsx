import React, { useState, useMemo, useCallback } from "react";
import { imgUrl } from "../utils/images";
import useAltText from "../hooks/useAltText";

const toUrl = (img) => typeof img === "object" ? img?.url || "" : img || "";
const toThumb = (img) => typeof img === "object" && img?.thumbUrl ? img.thumbUrl : "";

const CartItemImage = ({ src, name, width = 60, height = 60, className = "" }) => {
  const altText = useAltText();
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
        <span className="cart-item-img-placeholder-text">{altText.product(name)}</span>
      </div>
    );
  }

  return (
    <img
      src={finalSrc}
      alt={altText.product(name)}
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
