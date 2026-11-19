import { buildSalesSheet, buildProductSheet, buildCategorySheet, buildItemsSheet, formatDay, RUPEES } from "./reportExcel";

const now = new Date(2026, 10, 19, 10, 15);
const values = (row) => row.map((c) => (c && typeof c === "object" ? c.value : c));

test("formats dates as day month year", () => {
  expect(formatDay("2026-10-20")).toBe("20 Oct 2026");
  expect(formatDay("")).toBe("");
});

test("sales sheet has a title, the period and labelled figures in rupees", () => {
  const { data, sheet, stickyRowsCount } = buildSalesSheet(
    { totalOrders: 9, cancelledOrders: 2, totalRevenue: 292, from: "2026-10-20", to: "2026-11-19" }, now);
  expect(sheet).toBe("Sales");
  expect(stickyRowsCount).toBe(6);
  expect(data[1][0].value).toBe("Sales Report");
  expect(data[2][0].value).toBe("Period: 20 Oct 2026 to 19 Nov 2026");
  expect(data[3][0].value).toBe("Generated on 19 Nov 2026, 10:15");
  expect(values(data[5])).toEqual(["Measure", "Value"]);
  expect(values(data[6])).toEqual(["Orders placed", 9]);
  expect(values(data[8])).toEqual(["Orders not cancelled", 7]);
  expect(data[9][1]).toMatchObject({ value: 292, format: RUPEES });
  expect(data[10][1].value).toBeCloseTo(292 / 7);
});

test("sales sheet copes with no orders", () => {
  const { data } = buildSalesSheet({ totalOrders: 0, cancelledOrders: 0, totalRevenue: 0, from: "2026-11-01", to: "2026-11-02" }, now);
  expect(data[10][1].value).toBe(0);
});

test("product sheet lists products by id with a totals row", () => {
  const { data } = buildProductSheet([
    { id: 61, name: "Bedsheet", price: 899, stock: 12, active: true },
    { id: 58, name: "Test", price: 1, stock: 0, active: false },
  ], now);
  expect(data[2][0].value).toBe("2 products: 1 active, 1 inactive");
  expect(values(data[5])).toEqual(["ID", "Product", "Price", "Stock", "Status"]);
  expect(values(data[6])).toEqual([58, "Test", 1, 0, "Inactive"]);
  expect(data[6][3].textColor).toBe("#B91C1C");
  expect(values(data[7])).toEqual([61, "Bedsheet", 899, 12, "Active"]);
  expect(values(data[8])).toEqual(["Total", "2 products", null, 12, null]);
});

const items = [
  { orderId: 5, orderDate: "2026-11-02", orderStatus: "CANCELLED", category: "Home", productName: "Bedsheet", sku: "BS1", quantity: 1, unitPrice: 899, lineTotal: 899 },
  { orderId: 6, orderDate: "2026-11-02", orderStatus: "PLACED", category: "Kitchen", productName: "Bottle", sku: "BT1", quantity: 1, unitPrice: 349.5, lineTotal: 349.5 },
  { orderId: 7, orderDate: "2026-11-10", orderStatus: "DELIVERED", category: "Kitchen", productName: "Bottle", sku: "BT1", quantity: 2, unitPrice: 349.5, lineTotal: 699 },
];
const period = { from: "2026-11-01", to: "2026-11-19" };

test("category sheet totals each category without cancelled orders", () => {
  const { data, sheet } = buildCategorySheet(period, items, now);
  expect(sheet).toBe("By category");
  expect(values(data[5])).toEqual(["Category", "Orders", "Items sold", "Sales value", "Share"]);
  expect(values(data[6])).toEqual(["Kitchen", 2, 3, 1048.5, 1]);
  expect(values(data[7])).toEqual(["Total", 2, 3, 1048.5, 1]);
});

test("items sheet groups by category with subtotals and strikes cancelled items", () => {
  const { data, sheet } = buildItemsSheet(period, items, now);
  expect(sheet).toBe("Order items");
  expect(data[6][0].value).toBe("Home");
  expect(values(data[7]).slice(0, 3)).toEqual(["#5", "2 Nov 2026", "Bedsheet"]);
  expect(data[7][2].textDecoration).toEqual({ strikethrough: true });
  expect(data[8][0].value).toBe("Home subtotal");
  expect(data[8][6].value).toBe(0);
  expect(data[10][0].value).toBe("Kitchen");
  expect(data[13][0].value).toBe("Kitchen subtotal");
  expect(data[13][4].value).toBe(3);
  expect(data[13][6].value).toBe(1048.5);
  const totalRow = data.find((r) => r[0] && r[0].value === "Total (not cancelled)");
  expect(totalRow[6].value).toBe(1048.5);
});

test("empty period gives a clear message", () => {
  expect(buildItemsSheet(period, [], now).data[6][0].value).toBe("No items sold in this period.");
  expect(buildCategorySheet(period, [], now).data[6][0].value).toBe("No items sold in this period.");
});
