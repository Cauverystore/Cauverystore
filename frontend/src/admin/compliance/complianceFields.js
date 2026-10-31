/*
  The compliance fields, grouped as the forms show them. Shared by the admin compliance editor
  and the seller product wizard. The backend (ProductComplianceService) decides what is
  required; this only says how to ask for it. Keys match ProductCompliance on the server.
*/

export const CATEGORY_TYPES = [
  { value: "GENERAL", label: "General (common fields only)" },
  { value: "FOOD", label: "Food" },
  { value: "ELECTRONICS", label: "Electronics" },
  { value: "COSMETICS", label: "Cosmetics" },
  { value: "MEDICAL", label: "Medical" },
  { value: "TOYS", label: "Toys" },
  { value: "CLOTHING", label: "Clothing" },
];

export const UNITS = ["g", "kg", "ml", "l", "mm", "cm", "m", "sq m", "N (number)", "pair", "set"];

// Product-level fields: stored on the product, edited through /basics.
export const BASIC_FIELDS = [
  { key: "mrp", label: "MRP (₹, incl. all taxes)", type: "number" },
  { key: "countryOfOrigin", label: "Country of origin" },
  { key: "manufacturer", label: "Manufacturer / brand owner (short name)" },
  { key: "warranty", label: "Warranty" },
  { key: "returnPolicy", label: "Return and refund policy" },
];

export const SECTIONS = [
  {
    id: "legalMetrology", title: "Legal Metrology (quantity on the pack)", fields: [
      { key: "netQuantity", label: "Net quantity", type: "number" },
      { key: "grossQuantity", label: "Gross quantity", type: "number" },
      { key: "quantityUnit", label: "Unit", options: UNITS },
    ],
  },
  {
    id: "inventory", title: "Order limits", fields: [
      { key: "minOrderQty", label: "Minimum order quantity", type: "number" },
      { key: "maxOrderQty", label: "Maximum order quantity", type: "number" },
    ],
  },
  {
    id: "parties", title: "Manufacturer, packer and importer", fields: [
      { key: "manufacturerName", label: "Manufacturer name" },
      { key: "manufacturerAddress", label: "Manufacturer address", type: "textarea" },
      { key: "packerSameAsManufacturer", label: "Packed by the manufacturer", type: "checkbox" },
      { key: "packerName", label: "Packer name", hideIf: (d) => d.packerSameAsManufacturer },
      { key: "packerAddress", label: "Packer address", type: "textarea", hideIf: (d) => d.packerSameAsManufacturer },
      { key: "importerName", label: "Importer name", importedOnly: true },
      { key: "importerAddress", label: "Importer address", type: "textarea", importedOnly: true },
    ],
  },
  {
    id: "origin", title: "Origin", fields: [
      { key: "stateOfOrigin", label: "State of origin (made in India)", indiaOnly: true },
    ],
  },
  {
    id: "customerCare", title: "Customer care", fields: [
      { key: "customerCareName", label: "Contact name / department" },
      { key: "customerCarePhone", label: "Phone", check: (v) => !v || /^[+0-9 ()-]{6,20}$/.test(v) || "Enter a phone number" },
      { key: "customerCareEmail", label: "Email", type: "email", check: (v) => !v || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v) || "Enter an email address" },
      { key: "customerCareAddress", label: "Address", type: "textarea" },
    ],
  },
  {
    id: "dates", title: "Dates and batch", fields: [
      { key: "mfgDate", label: "Manufacturing date", type: "date" },
      { key: "bestBeforeDate", label: "Best before", type: "date" },
      { key: "expiryDate", label: "Expiry date", type: "date" },
      { key: "batchNumber", label: "Batch number" },
      { key: "lotNumber", label: "Lot number" },
      { key: "serialNumber", label: "Serial number" },
    ],
  },
];

const fssai = (v) => !v || /^\d{14}$/.test(v.replace(/\s/g, "")) || "14 digits";
const bis = (v) => !v || /^(R-\d{8}|CM\/L-\d{7,10})$/i.test(v.replace(/\s/g, "")) || "R-12345678 or CM/L-1234567";
const cdsco = (v) => !v || /^[A-Z0-9][A-Z0-9/\-. ]{3,39}$/i.test(v.trim()) || "4 to 40 letters, digits, / - .";

export const CATEGORY_SECTIONS = {
  FOOD: [
    { key: "fssaiLicenceNumber", label: "FSSAI licence / registration number", check: fssai },
    { key: "ingredients", label: "Ingredients", type: "textarea" },
    { key: "nutritionInfo", label: "Nutrition information", type: "textarea" },
    { key: "allergens", label: "Allergen information", type: "textarea" },
    { key: "foodPreference", label: "Veg / non-veg", options: [["VEG", "Veg (green mark)"], ["NON_VEG", "Non-veg (brown mark)"]] },
    { key: "storageInstructions", label: "Storage instructions", type: "textarea" },
  ],
  ELECTRONICS: [
    { key: "serviceCentreDetails", label: "Service centre details", type: "textarea" },
    { key: "bisNumber", label: "BIS registration / licence number", check: bis },
    { key: "voltage", label: "Voltage" },
    { key: "powerRating", label: "Power (watts)" },
  ],
  COSMETICS: [
    { key: "ingredients", label: "Ingredients", type: "textarea" },
    { key: "cosmeticLicenceNumber", label: "Manufacturing licence number" },
  ],
  MEDICAL: [
    { key: "cdscoRegistrationNumber", label: "CDSCO registration number", check: cdsco },
    { key: "prescriptionRequired", label: "Needs a prescription", options: [["true", "Yes"], ["false", "No"]], bool: true },
    { key: "warnings", label: "Warnings", type: "textarea" },
    { key: "disclaimer", label: "Disclaimer", type: "textarea" },
  ],
  TOYS: [
    { key: "bisNumber", label: "BIS registration / licence number", check: bis },
    { key: "ageGroup", label: "Age group (e.g. 3+ years)" },
    { key: "safetyWarning", label: "Safety warning", type: "textarea" },
  ],
  CLOTHING: [
    { key: "fabric", label: "Fabric / material" },
    { key: "washCare", label: "Wash care", type: "textarea" },
    { key: "sizeChart", label: "Size chart", type: "textarea" },
  ],
};

export const isIndia = (c) => ["india", "in", "ind", "bharat"].includes(String(c || "").trim().toLowerCase());

/** The fields to show for a product, given its category type and country of origin. */
export function visibleSections(categoryType, countryOfOrigin, details) {
  const imported = countryOfOrigin && !isIndia(countryOfOrigin);
  const india = isIndia(countryOfOrigin);
  const keep = (f) => !(f.importedOnly && !imported) && !(f.indiaOnly && !india) && !(f.hideIf && f.hideIf(details || {}));
  const out = SECTIONS.map((s) => ({ ...s, fields: s.fields.filter(keep) })).filter((s) => s.fields.length > 0);
  const extra = CATEGORY_SECTIONS[categoryType];
  if (extra) {
    const label = CATEGORY_TYPES.find((t) => t.value === categoryType)?.label || categoryType;
    out.push({ id: "category", title: `${label}: category requirements`, fields: extra });
  }
  return out;
}

export const BAND_STYLE = {
  PUBLISHABLE: { bg: "#EAF7EE", fg: "#146C43", text: "Ready to publish" },
  WARNING: { bg: "#fff7ed", fg: "#b45309", text: "Publishable with warnings" },
  BLOCKED: { bg: "#fef2f2", fg: "#b91c1c", text: "Blocked" },
};

export const STATUS_LABEL = {
  DRAFT: "Draft", COMPLIANCE_CHECK: "Compliance check", SELLER_REVIEW: "Seller review",
  ADMIN_REVIEW: "Admin review", APPROVED: "Approved", PUBLISHED: "Published",
};
