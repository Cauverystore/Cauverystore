// The chosen option on a cart or order line, as text: "Size: M".
// Cart lines carry { type, value }, order lines carry the stored variant
// ({ variantType, variantValue }), and order summaries carry a ready-made variantLabel.
export const variantLabel = (item) => {
  if (!item) return "";
  if (item.variantLabel) return item.variantLabel;
  const v = item.variant;
  if (!v) return "";
  const value = v.value ?? v.variantValue;
  if (!value) return "";
  const type = v.type ?? v.variantType;
  return type ? `${type}: ${value}` : String(value);
};

// What to call the choice on a button: "size" for a product sold in sizes, else "options".
export const variantNoun = (product) => {
  const type = product?.variants?.[0]?.variantType;
  return type ? type.toLowerCase() : "options";
};
