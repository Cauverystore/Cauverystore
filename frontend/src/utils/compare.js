const KEY = "cauvery_compare_products";
const MAX = 4;

export function getCompareProducts() {
  try {
    const raw = localStorage.getItem(KEY);
    const list = raw ? JSON.parse(raw) : [];
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

function save(list) {
  try {
    localStorage.setItem(KEY, JSON.stringify(list));
  } catch { /* ignore */ }
}

export function toggleCompare(product) {
  if (!product || product.id == null && product._id == null) return [];
  const id = String(product.id ?? product._id);
  const current = getCompareProducts();
  const next = current.some((p) => String(p.id ?? p._id) === id)
    ? current.filter((p) => String(p.id ?? p._id) !== id)
    : [...current, product].slice(-MAX);
  save(next);
  return next;
}

export function isCompared(product) {
  if (!product) return false;
  const id = String(product.id ?? product._id);
  return getCompareProducts().some((p) => String(p.id ?? p._id) === id);
}

export function removeCompare(productId) {
  const id = String(productId);
  save(getCompareProducts().filter((p) => String(p.id ?? p._id) !== id));
}

export function clearCompare() {
  save([]);
}

export default { getCompareProducts, toggleCompare, isCompared, removeCompare, clearCompare };
