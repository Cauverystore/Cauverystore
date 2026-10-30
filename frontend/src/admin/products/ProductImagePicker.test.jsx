import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import ProductImagePicker, { uploadProductImages } from "./ProductImagePicker";
import api from "../../api/axios";

jest.mock("../../api/axios", () => ({ __esModule: true, default: { post: jest.fn() } }));
jest.mock("../../utils/compressImage", () => ({ compressImages: (f) => Promise.resolve(f) }));
jest.mock("../../hooks/useAltText", () => () => ({ uploadPreview: (n) => `preview ${n}` }));
global.URL.createObjectURL = () => "blob:x";

const file = (name, type = "image/jpeg", size = 1000) => { const f = new File(["x"], name, { type }); Object.defineProperty(f, "size", { value: size }); return f; };

test("uploads each photo then links it to the product", async () => {
  api.post.mockImplementation((url) => Promise.resolve({ data: url.includes("upload-image") ? { thumbnail_url: "t", preview_url: "p", zoom_url: "z", original_url: "o" } : {} }));
  const failed = await uploadProductImages(42, [file("a.jpg"), file("b.png", "image/png")]);
  expect(failed).toEqual([]);
  expect(api.post.mock.calls.map((c) => c[0])).toEqual(["/api/uploads/upload-image", "/api/admin/products/42/images/url", "/api/uploads/upload-image", "/api/admin/products/42/images/url"]);
});

test("reports which photos failed", async () => {
  api.post.mockReset();
  api.post.mockImplementation((url) => url.includes("upload-image") ? Promise.reject(new Error("502")) : Promise.resolve({ data: {} }));
  expect(await uploadProductImages(7, [file("bad.jpg")])).toEqual(["bad.jpg"]);
});

test("picker accepts photos and rejects wrong type and big files", () => {
  const onChange = jest.fn();
  const { container } = render(<ProductImagePicker images={[]} onChange={onChange} />);
  expect(screen.getByText("Product Images")).toBeTruthy();
  fireEvent.change(container.querySelector('input[type="file"]'), { target: { files: [file("ok.jpg"), file("doc.pdf", "application/pdf"), file("huge.jpg", "image/jpeg", 3 * 1024 * 1024)] } });
  expect(onChange.mock.calls[0][0].map((f) => f.name)).toEqual(["ok.jpg"]);
  expect(screen.getByText(/doc.pdf: Invalid format/)).toBeTruthy();
  expect(screen.getByText(/huge.jpg: Exceeds 2MB/)).toBeTruthy();
});
