// GSTIN check-digit validation, the same base-36 algorithm the server uses
// (GstComplianceUtil.isGstinChecksumValid). Catching a mistyped character here saves the buyer
// a failed order at the payment step.
const ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
const FORMAT = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/;

export function isGstinFormatValid(gstin) {
  return FORMAT.test((gstin || "").trim().toUpperCase());
}

export function isGstinChecksumValid(gstin) {
  const g = (gstin || "").trim().toUpperCase();
  if (!FORMAT.test(g)) return false;
  let sum = 0;
  for (let i = 0; i < 14; i++) {
    const product = ALPHABET.indexOf(g[i]) * (i % 2 === 0 ? 1 : 2);
    sum += Math.floor(product / 36) + (product % 36);
  }
  return ALPHABET[(36 - (sum % 36)) % 36] === g[14];
}
