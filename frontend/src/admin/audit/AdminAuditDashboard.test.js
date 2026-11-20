import { actionLabel, isWarning, toQueryParams } from "./AdminAuditDashboard";

test("only filled-in filters are sent, with a zero-based page", () => {
  expect(toQueryParams({ action: "", email: "  ", from: "2026-11-01", to: "" }, 3))
    .toEqual({ page: 2, size: 50, from: "2026-11-01" });
  expect(toQueryParams({ action: "LOGIN_FAILED", email: " a@b.in ", from: "", to: "" }, 1))
    .toEqual({ page: 0, size: 50, action: "LOGIN_FAILED", email: "a@b.in" });
});

test("failed sign-ins and blocks stand out; names are readable", () => {
  expect(isWarning("LOGIN_FAILED")).toBe(true);
  expect(isWarning("SUSPEND_USER")).toBe(true);
  expect(isWarning("LOGIN_SUCCESS")).toBe(false);
  expect(actionLabel("LOGIN_FAILED")).toBe("Failed sign-in");
  expect(actionLabel("SOMETHING_NEW")).toBe("SOMETHING_NEW");
});
