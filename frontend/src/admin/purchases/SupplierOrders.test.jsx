import React from "react";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import SupplierOrders from "./SupplierOrders";
import api from "../../api/axios";

jest.mock("../../api/axios", () => ({ __esModule: true, default: { get: jest.fn(), post: jest.fn() } }));

const row = {
  id: 5, orderId: 90, productName: "Egg Box", sku: "10715", supplierUrl: "javascript:alert(1)", quantity: 2,
  salePrice: 209, expectedCost: 100, status: "TO_ORDER", payment: "Paid online", shipTo: "A Customer\nCoimbatore",
  supplierOutOfStock: true,
};
const setValue = (input, value) => {
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set.call(input, value);
  input.dispatchEvent(new Event("input", { bubbles: true }));
};
const button = (el, name) => [...el.querySelectorAll("button")].find((b) => b.textContent === name);

test("shows the line, refuses unsafe links, and records the supplier order", async () => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  api.get.mockResolvedValue({ data: { rows: [row], counts: { todo: 1 } } });
  api.post.mockResolvedValue({ data: {} });
  const el = document.body.appendChild(document.createElement("div"));
  const root = createRoot(el);
  await act(async () => { root.render(<SupplierOrders />); });

  expect(el.textContent).toContain("out of stock");
  expect(el.textContent).toContain("Coimbatore");
  expect(el.querySelector("a")).toBeNull();
  expect(button(el, "I have ordered and paid").disabled).toBe(true);

  await act(async () => { setValue(el.querySelector("[aria-label='Supplier order number']"), "DD-77"); });
  await act(async () => { setValue(el.querySelector("[aria-label='Amount paid']"), "200"); });
  await act(async () => { button(el, "I have ordered and paid").click(); });
  expect(api.post).toHaveBeenCalledWith("/api/admin/supplier-orders/5/ordered", { supplierOrderRef: "DD-77", amountPaid: "200" });
  await act(async () => { root.unmount(); });
});
