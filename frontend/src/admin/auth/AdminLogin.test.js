import { adminHomeFor, isStaffLogin } from "./AdminLogin";

test("only staff accounts get into the admin panel", () => {
  expect(isStaffLogin({ role: "ADMIN", roles: ["ADMIN"] })).toBe(true);
  expect(isStaffLogin({ role: "SUPER_ADMIN" })).toBe(true);
  expect(isStaffLogin({ role: "EXECUTIVE", roles: ["EXECUTIVE"] })).toBe(true);
  expect(isStaffLogin({ role: "CUSTOMER", roles: ["CUSTOMER"] })).toBe(false);
  expect(isStaffLogin({ role: "SELLER", roles: ["SELLER", "CUSTOMER"] })).toBe(false);
  expect(isStaffLogin(undefined)).toBe(false);
});

test("super admins land on their own page", () => {
  expect(adminHomeFor({ role: "SUPER_ADMIN" })).toBe("/super-admin");
  expect(adminHomeFor({ role: "ADMIN" })).toBe("/admin");
});
