// Delivery charge by parcel weight. Mirrors DeliveryChargeService on the server (its default
// rates); the cart and checkout always show the server's own figure, this is only for places
// with no cart, such as the shipping rate in a product page's structured data.
const SLABS = [[0.5, 65], [1, 100], [1.5, 135]];
const EXTRA_PER_HALF_KG = 35;
const DEFAULT_ITEM_KG = 0.5;

export function unitWeightKg(product) {
  const pkg = Number(product?.packageWeight);
  if (pkg > 0) return pkg;
  const w = Number(product?.weight);
  if (w > 0) return w;
  return DEFAULT_ITEM_KG;
}

export function deliveryChargeForWeight(kg) {
  if (!(kg > 0)) return 0;
  for (const [upTo, charge] of SLABS) if (kg <= upTo + 1e-9) return charge;
  const [lastKg, lastCharge] = SLABS[SLABS.length - 1];
  return lastCharge + Math.ceil((kg - lastKg) / 0.5 - 1e-9) * EXTRA_PER_HALF_KG;
}
