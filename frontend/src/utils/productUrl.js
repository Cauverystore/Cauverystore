// Readable product links: /product/123-blue-cotton-tee instead of /product/123.
//
// The number is what identifies the product; the words after it are for people and search
// engines only. So a link with old or missing words still opens the right product, and the
// product page then swaps the address for the current one. The backend sitemap builds the
// same words (SitemapService.productSlug) - keep the two in step.

const MAX_SLUG_LENGTH = 60;

export function productSlug(name) {
  const slug = String(name || "")
    .toLowerCase()
    .normalize("NFKD")
    .replace(/\p{M}/gu, "")
    .replace(/&/g, " and ")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  if (slug.length <= MAX_SLUG_LENGTH) return slug;
  const cut = slug.slice(0, MAX_SLUG_LENGTH);
  const lastDash = cut.lastIndexOf("-");
  return (lastDash > 20 ? cut.slice(0, lastDash) : cut).replace(/-+$/g, "");
}

export function productPath(id, name) {
  const slug = productSlug(name);
  return slug ? `/product/${id}-${slug}` : `/product/${id}`;
}

// "123-blue-cotton-tee" -> "123"; anything without a leading number -> null.
export function productIdFromParam(param) {
  const m = /^(\d+)(?:-|$)/.exec(String(param || ""));
  return m ? m[1] : null;
}
