// Builds the Excel downloads on Admin > Reports: a title, the period and when the file was
// made, then clearly labelled figures in rupees. Pure functions, so the layout can be tested
// without writing a file.

export const BRAND = "#00788C";
const HEADER_TEXT = "#FFFFFF";
const BAND = "#EFFBF7";
const MUTED = "#64748B";
const BORDER = "#CBD5E1";
export const RUPEES = "[$₹-4009]#,##0.00";

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

/** "2026-10-20" or a Date -> "20 Oct 2026". */
export function formatDay(value) {
  if (!value) return "";
  const d = typeof value === "string" ? new Date(`${value.slice(0, 10)}T00:00:00`) : value;
  if (Number.isNaN(d.getTime())) return String(value);
  return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
}

function formatStamp(d) {
  const hh = String(d.getHours()).padStart(2, "0");
  const mm = String(d.getMinutes()).padStart(2, "0");
  return `${formatDay(d)}, ${hh}:${mm}`;
}

const num = (v) => {
  const n = typeof v === "number" ? v : parseFloat(v);
  return Number.isFinite(n) ? n : 0;
};

function titleRows(title, subtitle, width, now) {
  return [
    [{ value: "Cauvery Store", fontWeight: "bold", fontSize: 16, textColor: BRAND, columnSpan: width }, ...Array(width - 1).fill(null)],
    [{ value: title, fontWeight: "bold", fontSize: 13, columnSpan: width }, ...Array(width - 1).fill(null)],
    [{ value: subtitle, textColor: MUTED, columnSpan: width }, ...Array(width - 1).fill(null)],
    [{ value: `Generated on ${formatStamp(now)}`, textColor: MUTED, fontStyle: "italic", columnSpan: width }, ...Array(width - 1).fill(null)],
    Array(width).fill(null),
  ];
}

const headerCell = (value, align = "left") => ({
  value, fontWeight: "bold", textColor: HEADER_TEXT, backgroundColor: BRAND,
  align, alignVertical: "center", height: 22, borderColor: BRAND, borderStyle: "thin",
});

const bodyCell = (props, stripe) => ({
  borderColor: BORDER, borderStyle: "thin", ...(stripe ? { backgroundColor: BAND } : {}), ...props,
});

/** Sales summary for a period: one labelled row per figure. */
export function buildSalesSheet(report, now = new Date()) {
  const orders = num(report.totalOrders);
  const cancelled = num(report.cancelledOrders);
  const revenue = num(report.totalRevenue);
  const kept = Math.max(0, orders - cancelled);
  const figures = [
    ["Orders placed", orders, "#,##0"],
    ["Cancelled orders", cancelled, "#,##0"],
    ["Orders not cancelled", kept, "#,##0"],
    ["Sales value of orders not cancelled (incl. GST)", revenue, RUPEES],
    ["Average order value", kept > 0 ? revenue / kept : 0, RUPEES],
  ];
  const data = [
    ...titleRows("Sales Report", `Period: ${formatDay(report.from)} to ${formatDay(report.to)}`, 2, now),
    [headerCell("Measure"), headerCell("Value", "right")],
    ...figures.map(([label, value, format], i) => [
      bodyCell({ value: label }, i % 2 === 1),
      bodyCell({ value, type: Number, format, align: "right" }, i % 2 === 1),
    ]),
    [null, null],
    [{ value: "Orders are counted by the date they were placed.", textColor: MUTED, fontStyle: "italic", columnSpan: 2 }, null],
    [{ value: "Cancelled orders are left out of the sales value.", textColor: MUTED, fontStyle: "italic", columnSpan: 2 }, null],
  ];
  return { data, columns: [{ width: 48 }, { width: 20 }], sheet: "Sales", stickyRowsCount: 6, showGridLines: false };
}

/** Product list: one row per product, then a totals row. */
export function buildProductSheet(products, now = new Date()) {
  const list = [...(products || [])].sort((a, b) => num(a.id) - num(b.id));
  const active = list.filter((p) => p.active).length;
  const rows = list.map((p, i) => {
    const stock = num(p.stock);
    const stripe = i % 2 === 1;
    return [
      bodyCell({ value: num(p.id), type: Number, align: "left" }, stripe),
      bodyCell({ value: p.name || "" }, stripe),
      bodyCell({ value: num(p.price), type: Number, format: RUPEES, align: "right" }, stripe),
      bodyCell({ value: stock, type: Number, format: "#,##0", align: "right", ...(stock === 0 ? { textColor: "#B91C1C", fontWeight: "bold" } : {}) }, stripe),
      bodyCell({ value: p.active ? "Active" : "Inactive", textColor: p.active ? "#15803D" : "#B91C1C", indent: 1 }, stripe),
    ];
  });
  const totalStock = list.reduce((s, p) => s + num(p.stock), 0);
  const total = (props) => ({ fontWeight: "bold", borderColor: BORDER, borderStyle: "thin", topBorderStyle: "medium", topBorderColor: BRAND, ...props });
  const data = [
    ...titleRows("Product Report", `${list.length} products: ${active} active, ${list.length - active} inactive`, 5, now),
    [headerCell("ID"), headerCell("Product"), headerCell("Price", "right"), headerCell("Stock", "right"), { ...headerCell("Status"), indent: 1 }],
    ...rows,
    [total({ value: "Total" }), total({ value: `${list.length} products` }), total({ value: null }),
      total({ value: totalStock, type: Number, format: "#,##0", align: "right" }), total({ value: null })],
  ];
  return {
    data,
    columns: [{ width: 8 }, { width: 46 }, { width: 14 }, { width: 10 }, { width: 14 }],
    sheet: "Products",
    stickyRowsCount: 6,
    showGridLines: false,
  };
}
