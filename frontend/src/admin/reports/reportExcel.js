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

const isCancelled = (status) => ["CANCELLED", "REFUNDED"].includes(String(status || "").toUpperCase());

const niceStatus = (status) => {
  const s = String(status || "").replace(/_/g, " ").toLowerCase();
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : "";
};

/** One row per category: orders, items sold and sales value (cancelled and refunded left out). */
export function buildCategorySheet(report, items, now = new Date()) {
  const byCat = new Map();
  (items || []).forEach((it) => {
    if (isCancelled(it.orderStatus)) return;
    const c = byCat.get(it.category) || { orders: new Set(), qty: 0, value: 0 };
    c.orders.add(it.orderId);
    c.qty += num(it.quantity);
    c.value += num(it.lineTotal);
    byCat.set(it.category, c);
  });
  const cats = [...byCat.entries()].sort((a, b) => b[1].value - a[1].value);
  const grand = cats.reduce((s, [, c]) => s + c.value, 0);
  const allOrders = new Set(); cats.forEach(([, c]) => c.orders.forEach((o) => allOrders.add(o)));
  const totalQty = cats.reduce((s, [, c]) => s + c.qty, 0);
  const total = (props) => ({ fontWeight: "bold", borderColor: BORDER, borderStyle: "thin", topBorderStyle: "medium", topBorderColor: BRAND, ...props });
  const data = [
    ...titleRows("Sales by Category", `Period: ${formatDay(report.from)} to ${formatDay(report.to)}`, 5, now),
    [headerCell("Category"), headerCell("Orders", "right"), headerCell("Items sold", "right"), headerCell("Sales value", "right"), headerCell("Share", "right")],
    ...(cats.length === 0
      ? [[{ value: "No items sold in this period.", textColor: MUTED, fontStyle: "italic", columnSpan: 5 }, null, null, null, null]]
      : cats.map(([name, c], i) => [
          bodyCell({ value: name }, i % 2 === 1),
          bodyCell({ value: c.orders.size, type: Number, format: "#,##0", align: "right" }, i % 2 === 1),
          bodyCell({ value: c.qty, type: Number, format: "#,##0", align: "right" }, i % 2 === 1),
          bodyCell({ value: c.value, type: Number, format: RUPEES, align: "right" }, i % 2 === 1),
          bodyCell({ value: grand > 0 ? c.value / grand : 0, type: Number, format: "0.0%", align: "right" }, i % 2 === 1),
        ])),
    [total({ value: "Total" }), total({ value: allOrders.size, type: Number, format: "#,##0", align: "right" }),
      total({ value: totalQty, type: Number, format: "#,##0", align: "right" }),
      total({ value: grand, type: Number, format: RUPEES, align: "right" }), total({ value: grand > 0 ? 1 : 0, type: Number, format: "0.0%", align: "right" })],
    [null, null, null, null, null],
    [{ value: "Item prices only: shipping, gift wrap and discounts are not included,", textColor: MUTED, fontStyle: "italic", columnSpan: 5 }, null, null, null, null],
    [{ value: "so these figures can differ from the order totals.", textColor: MUTED, fontStyle: "italic", columnSpan: 5 }, null, null, null, null],
    [{ value: "Cancelled and refunded orders are left out.", textColor: MUTED, fontStyle: "italic", columnSpan: 5 }, null, null, null, null],
  ];
  return { data, columns: [{ width: 34 }, { width: 10 }, { width: 12 }, { width: 16 }, { width: 10 }], sheet: "By category", stickyRowsCount: 6, showGridLines: false };
}

/** Every item sold, under a heading per category, with a subtotal for each category. */
export function buildItemsSheet(report, items, now = new Date()) {
  const W = 8;
  const groups = new Map();
  (items || []).forEach((it) => { if (!groups.has(it.category)) groups.set(it.category, []); groups.get(it.category).push(it); });
  const rows = [];
  let grandQty = 0;
  let grandValue = 0;
  groups.forEach((list, name) => {
    rows.push([{ value: name, fontWeight: "bold", textColor: BRAND, fontSize: 12, columnSpan: W, height: 20, alignVertical: "bottom" }, ...Array(W - 1).fill(null)]);
    let qty = 0;
    let value = 0;
    list.forEach((it, i) => {
      const off = isCancelled(it.orderStatus);
      const muted = off ? { textColor: "#94A3B8", textDecoration: { strikethrough: true } } : {};
      if (!off) { qty += num(it.quantity); value += num(it.lineTotal); }
      const stripe = i % 2 === 1;
      rows.push([
        bodyCell({ value: `#${it.orderId}`, ...muted }, stripe),
        bodyCell({ value: formatDay(it.orderDate), ...muted }, stripe),
        bodyCell({ value: it.productName || "", ...muted }, stripe),
        bodyCell({ value: it.sku || "", ...muted }, stripe),
        bodyCell({ value: num(it.quantity), type: Number, format: "#,##0", align: "right", ...muted }, stripe),
        bodyCell({ value: num(it.unitPrice), type: Number, format: RUPEES, align: "right", ...muted }, stripe),
        bodyCell({ value: num(it.lineTotal), type: Number, format: RUPEES, align: "right", ...muted }, stripe),
        bodyCell({ value: niceStatus(it.orderStatus), indent: 1, ...(off ? { textColor: "#B91C1C" } : {}) }, stripe),
      ]);
    });
    const sub = (props) => ({ fontWeight: "bold", backgroundColor: "#F1F5F9", borderColor: BORDER, borderStyle: "thin", ...props });
    rows.push([sub({ value: `${name} subtotal`, columnSpan: 4 }), null, null, null,
      sub({ value: qty, type: Number, format: "#,##0", align: "right" }), sub({ value: null }),
      sub({ value: value, type: Number, format: RUPEES, align: "right" }), sub({ value: null })]);
    rows.push(Array(W).fill(null));
    grandQty += qty;
    grandValue += value;
  });
  const total = (props) => ({ fontWeight: "bold", textColor: HEADER_TEXT, backgroundColor: BRAND, ...props });
  const data = [
    ...titleRows("Order Items by Category", `Period: ${formatDay(report.from)} to ${formatDay(report.to)}`, W, now),
    [headerCell("Order"), headerCell("Date"), headerCell("Product"), headerCell("SKU"), headerCell("Qty", "right"),
      headerCell("Unit price", "right"), headerCell("Line total", "right"), { ...headerCell("Status"), indent: 1 }],
    ...(groups.size === 0
      ? [[{ value: "No items sold in this period.", textColor: MUTED, fontStyle: "italic", columnSpan: W }, ...Array(W - 1).fill(null)]]
      : rows),
    [total({ value: "Total (not cancelled)", columnSpan: 4 }), null, null, null,
      total({ value: grandQty, type: Number, format: "#,##0", align: "right" }), total({ value: null }),
      total({ value: grandValue, type: Number, format: RUPEES, align: "right" }), total({ value: null })],
    Array(W).fill(null),
    [{ value: "Cancelled and refunded items are shown struck through and left out of the subtotals.", textColor: MUTED, fontStyle: "italic", columnSpan: W }, ...Array(W - 1).fill(null)],
  ];
  return {
    data,
    columns: [{ width: 9 }, { width: 13 }, { width: 40 }, { width: 14 }, { width: 7 }, { width: 13 }, { width: 13 }, { width: 14 }],
    sheet: "Order items",
    stickyRowsCount: 6,
    showGridLines: false,
  };
}
