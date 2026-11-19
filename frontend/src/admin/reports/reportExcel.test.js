import { buildSalesSheet, buildProductSheet, formatDay, RUPEES } from "./reportExcel";

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
