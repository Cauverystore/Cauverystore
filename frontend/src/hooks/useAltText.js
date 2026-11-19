import { useMemo } from "react";
import { useTranslation } from "react-i18next";

/**
 * Image descriptions ("alt text") in the visitor's language.
 *
 * Every image tag on the site takes its alt text from here, so Google and screen
 * readers get a description in all six languages. The words live in the "alt"
 * section of src/i18n/<lang>.json. Product names are stored once (as the
 * seller typed them), so they go in as-is with the surrounding words translated.
 *
 * `npm run check:alt` (run automatically before every build) fails if an image
 * has no alt text or a language is missing one of these descriptions.
 */
export function makeAltText(t) {
  const product = (name) => (name && String(name).trim()) || t("alt.productFallback");
  return {
    logo: () => t("alt.logo"),
    /** A product photo; pass n/total when the product has several photos. */
    product: (name, n, total) =>
      total > 1 ? t("alt.productImage", { name: product(name), n, total }) : product(name),
    profilePhoto: () => t("alt.profilePhoto"),
    signature: (name) => (name ? t("alt.signature", { name }) : t("alt.signatureUnknown")),
    invoiceQr: () => t("alt.invoiceQr"),
    uploadPreview: (n) => t("alt.uploadPreview", { n }),
    sellerSignature: () => t("alt.sellerSignature"),
  };
}

export default function useAltText() {
  const { t } = useTranslation();
  return useMemo(() => makeAltText(t), [t]);
}
