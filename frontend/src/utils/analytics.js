const isGtagReady = () => typeof window !== "undefined" && typeof window.gtag === "function";

export function trackEvent(name, params = {}) {
  if (!isGtagReady()) return;
  try {
    window.gtag("event", name, params);
  } catch {
    /* analytics must never break the store */
  }
}

export function trackPageView(path, title) {
  if (!isGtagReady()) return;
  try {
    window.gtag("config", process.env.REACT_APP_GA_MEASUREMENT_ID, {
      page_path: path,
      page_title: title || document.title,
    });
  } catch {
    /* ignore */
  }
}

export function trackViewItemList(listName, products) {
  trackEvent("view_item_list", {
    item_list_name: listName,
    items: (products || []).slice(0, 12).map(toItem),
  });
}

export function trackViewItem(product) {
  trackEvent("view_item", { currency: "INR", value: priceOf(product), items: [toItem(product)] });
}

export function trackSelectItem(product, listName) {
  trackEvent("select_item", {
    item_list_name: listName || "",
    items: [toItem(product)],
  });
}

export function trackAddToCart(product, quantity = 1) {
  trackEvent("add_to_cart", {
    currency: "INR",
    value: priceOf(product) * quantity,
    items: [{ ...toItem(product), quantity }],
  });
}

export function trackBeginCheckout(items) {
  trackEvent("begin_checkout", {
    currency: "INR",
    value: (items || []).reduce((sum, i) => sum + priceOf(i) * (i.quantity || 1), 0),
    items: (items || []).map((i) => ({ ...toItem(i), quantity: i.quantity || 1 })),
  });
}

export function trackPurchase(order) {
  const items = order?.items || [];
  trackEvent("purchase", {
    currency: "INR",
    transaction_id: String(order?.id || order?.orderId || ""),
    value: Number(order?.totalAmount || order?.amount || 0),
    tax: Number(order?.tax || 0),
    shipping: Number(order?.deliveryCharge || 0),
    items: items.map((i) => ({ ...toItem(i), quantity: i.quantity || 1 })),
  });
}

export function trackSearch(searchTerm) {
  if (!searchTerm) return;
  trackEvent("search", { search_term: searchTerm });
}

function toItem(p) {
  if (!p) return {};
  const id = p.id || p._id || p.productId;
  return {
    item_id: String(id),
    item_name: p.name || p.productName || "Product",
    price: priceOf(p),
    item_category: typeof p.category === "object" ? p.category?.name || "" : p.category || "",
    item_brand: typeof p.brand === "object" ? p.brand?.name || "" : p.brand || "",
  };
}

function priceOf(p) {
  return Number(p?.price || p?.dealPrice || p?.sellingPrice || p?.totalPrice || 0);
}

export default trackEvent;
