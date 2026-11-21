import { ADMIN_NAV_SECTIONS, sectionForPath, visibleSections } from "./adminNav";

const all = () => true;

test("every admin page appears exactly once", () => {
  const paths = ADMIN_NAV_SECTIONS.flatMap((s) => s.links.map((l) => l.to));
  expect(paths).toHaveLength(46);
  expect(new Set(paths).size).toBe(paths.length);
});

test("pages someone may not open are hidden, and empty sections dropped", () => {
  const onlyOrders = (module) => module === "orders";
  const sections = visibleSections(onlyOrders);
  expect(sections.map((s) => s.id)).toEqual(["orders"]);
  expect(sections[0].links.map((l) => l.label)).toEqual(["Orders", "Supplier Orders"]);
});

test("search matches any part of a page name, in any case", () => {
  const labels = visibleSections(all, "gst").flatMap((s) => s.links.map((l) => l.label));
  expect(labels).toEqual(["GST Rates", "GST Compliance", "GST Masters"]);
  expect(visibleSections(all, "  AUDIT ")[0].links[0].to).toBe("/admin/audit");
  expect(visibleSections(all, "no such page")).toEqual([]);
});

test("the section of the page being viewed is found, longest match first", () => {
  expect(sectionForPath("/admin/products/bulk-upload")).toBe("catalogue");
  expect(sectionForPath("/admin/orders/123")).toBe("orders");
  expect(sectionForPath("/admin/gst-compliance")).toBe("gst");
  expect(sectionForPath("/admin")).toBeNull();
});
