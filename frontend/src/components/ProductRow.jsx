import React, { useState, useEffect, useCallback, useRef } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import "../styles/product-tray.css";
import { useTranslation } from "react-i18next";

// The narrowest a card may get before the row drops to one fewer card.
const MIN_CARD = 200;

/*
 * A row of whole product cards - none is ever cut off at the edge. The row measures the space it
 * is given (a full page, a cart column, a phone) and shows as many whole cards as fit, never
 * fewer than two. The arrows page through the rest, so every product is reachable from the row.
 */
const ProductRow = ({ children }) => {
  const { t } = useTranslation();

  const scroller = useRef(null);
  const [cols, setCols] = useState(null);
  const [can, setCan] = useState({ prev: false, next: false });
  const count = React.Children.count(children);

  const measure = useCallback(() => {
    const el = scroller.current;
    if (!el) return;
    const gap = parseFloat(getComputedStyle(el).columnGap) || 0;
    setCols(Math.max(2, Math.floor((el.clientWidth + gap) / (MIN_CARD + gap))));
    setCan({ prev: el.scrollLeft > 4, next: el.scrollLeft + el.clientWidth < el.scrollWidth - 4 });
  }, []);

  useEffect(() => {
    measure();
    const el = scroller.current;
    if (!el) return undefined;
    el.addEventListener("scroll", measure, { passive: true });
    const observer = typeof ResizeObserver !== "undefined" ? new ResizeObserver(measure) : null;
    if (observer) observer.observe(el);
    else window.addEventListener("resize", measure);
    return () => {
      el.removeEventListener("scroll", measure);
      if (observer) observer.disconnect();
      else window.removeEventListener("resize", measure);
    };
  }, [measure, count]);

  // The arrows depend on the column count, which is only known after the first measure.
  useEffect(() => { measure(); }, [cols, measure]);

  const page = (dir) => {
    const el = scroller.current;
    if (!el) return;
    const gap = parseFloat(getComputedStyle(el).columnGap) || 0;
    el.scrollBy({ left: dir * (el.clientWidth + gap), behavior: "smooth" });
  };

  return (
    <div className="pt-row">
      {can.prev && (
        <button type="button" className="pt-row-arrow pt-row-arrow-prev" onClick={() => page(-1)} aria-label={t("productRow.showPreviousProducts")}>
          <ChevronLeft size={22} />
        </button>
      )}
      <div className="pt-row-scroll" ref={scroller} style={cols ? { "--pt-cols": cols } : undefined}>{children}</div>
      {can.next && (
        <button type="button" className="pt-row-arrow pt-row-arrow-next" onClick={() => page(1)} aria-label={t("productRow.showMoreProducts")}>
          <ChevronRight size={22} />
        </button>
      )}
    </div>
  );
};

export default ProductRow;
