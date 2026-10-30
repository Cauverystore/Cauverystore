import React from "react";

// Short version of the template's Instructions sheet, shown beside the upload box so
// nobody has to open the second sheet to learn the rules that block an upload.
const STEPS = [
  "Download a fresh template. Delete the 5 sample rows, but keep row 1 (the headings) exactly as it is.",
  <><strong>Leave Product Code (column A) empty.</strong> The store creates a unique code for every product. If any row has a code, the whole file is refused and nothing is uploaded.</>,
  "Fill in Product Name, Price (numbers only, no ₹) and Stock Quantity (whole number) for every product.",
  "Use the dropdowns for Category, Country, Warranty, Return Policy, Shipping Class and Badges. If the store has an 'Others' category, a row with an empty or unknown category goes there; otherwise that row is refused.",
  "Enter a valid HSN Code, and answer Yes or No in Pre-Packaged and Labelled (column AC).",
  "Photos are optional: paste one direct picture link (ending in .jpg, .png or .webp) in each of Image 1, 2 and 3 URL (columns Z, AA, AB). Image 1 is the main photo. Or leave them empty and add photos later with the Images button.",
  "Save as .xlsx and upload. Up to 1000 products per file. Any row that is rejected is listed with the reason, so you can fix just that row.",
];

export default function BulkUploadInstructions() {
  return (
    <div style={{ background: "#F8FBF9", border: "1px solid #CFE8D6", borderRadius: "8px", padding: "0.9rem 1.1rem", margin: "0 0 1rem" }}>
      <h4 style={{ margin: "0 0 0.5rem", fontSize: "0.9rem", fontWeight: 700, color: "#0B3D2E" }}>How to fill the template</h4>
      <ol style={{ margin: 0, paddingLeft: "1.2rem", fontSize: "0.82rem", color: "#374151", lineHeight: 1.55 }}>
        {STEPS.map((s, i) => <li key={i} style={{ marginBottom: "0.25rem" }}>{s}</li>)}
      </ol>
    </div>
  );
}
