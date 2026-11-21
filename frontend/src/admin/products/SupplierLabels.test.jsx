import React from "react";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import SupplierLabels from "./SupplierLabels";
import api from "../../api/axios";

jest.mock("../../api/axios", () => ({ __esModule: true, default: { get: jest.fn(), put: jest.fn() } }));

const waiting = [{ jobId: 1, name: "Egg Box", sku: "10715", mrp: 299, hsnCode: "", countryOfOrigin: "India", note: "Still needed: label." }];
const button = (el, name) => [...el.querySelectorAll("button")].find((b) => b.textContent === name);

test("fills the form from a waiting draft and saves it", async () => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  api.get.mockResolvedValue({ data: { labels: [], waiting } });
  api.put.mockResolvedValue({ data: { sweep: { ready: 1 } } });
  window.scrollTo = jest.fn();
  const el = document.body.appendChild(document.createElement("div"));
  const root = createRoot(el);
  await act(async () => { root.render(<SupplierLabels />); });

  await act(async () => { button(el, "Enter label").click(); });
  expect(el.querySelector("input[type=text]").value).toBe("10715");
  const permission = el.querySelector("input[type=checkbox]");
  expect(permission.checked).toBe(false);

  await act(async () => { permission.click(); });
  await act(async () => { button(el, "Save label").click(); });
  expect(api.put.mock.calls[0][1]).toMatchObject({ sku: "10715", mrp: 299, photosAllowed: true });
  expect(el.querySelector("[role=status]").textContent).toContain("preview mode");
  await act(async () => { root.unmount(); });
});
