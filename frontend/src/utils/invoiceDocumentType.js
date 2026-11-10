// What an invoice is called on screen. Mirrors the backend's InvoiceDocumentType (Rule 49 of the
// CGST Rules): a document with no tax on any line is a Bill of Supply, a mix of taxed and
// tax-free lines is an Invoice-cum-Bill of Supply, anything else is a Tax Invoice.

const zero = (v) => v == null || Math.abs(Number(v)) < 0.005;

const taxFree = (it) =>
  zero(it?.cgstRate) && zero(it?.sgstRate) && zero(it?.igstRate) && zero(it?.cessRate) &&
  zero(it?.cgstAmount) && zero(it?.sgstAmount) && zero(it?.igstAmount) && zero(it?.cessAmount);

export function invoiceDocumentTitle(invoice) {
  const items = Array.isArray(invoice?.items) ? invoice.items : [];
  if (items.length === 0) return "Tax Invoice";
  const free = items.filter(taxFree).length;
  if (free === 0) return "Tax Invoice";
  return free === items.length ? "Bill of Supply" : "Invoice-cum-Bill of Supply";
}
