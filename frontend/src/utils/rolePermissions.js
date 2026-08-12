export const ROLE_HIERARCHY = {
  SUPER_ADMIN: 5,
  ADMIN: 4,
  EXECUTIVE: 3,
  SELLER: 2,
  CUSTOMER: 1,
};

export const PERMISSIONS = {
  superAdmin: ['SUPER_ADMIN'],
  admin: ['ADMIN', 'SUPER_ADMIN'],
  staff: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
  seller: ['SELLER', 'SUPER_ADMIN'],
  customer: ['CUSTOMER'],
  authenticated: ['CUSTOMER', 'SELLER', 'ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
};

export function hasPermission(userRole, allowedRoles) {
  return allowedRoles.includes(userRole);
}

export function getDefaultRoute(role) {
  const routes = {
    CUSTOMER: '/',
    SELLER: '/seller/dashboard',
    EXECUTIVE: '/admin/executive-dashboard',
    ADMIN: '/admin',
    SUPER_ADMIN: '/super-admin',
  };
  return routes[role] || '/login';
}

export function canAccessModule(role, module) {
  const moduleAccess = {
    settings: ['SUPER_ADMIN'],
    audit: ['ADMIN', 'SUPER_ADMIN'],
    users: ['ADMIN', 'SUPER_ADMIN'],
    categories: ['ADMIN', 'SUPER_ADMIN'],
    brands: ['ADMIN', 'SUPER_ADMIN'],
    coupons: ['ADMIN', 'SUPER_ADMIN'],
    gstRates: ['ADMIN', 'SUPER_ADMIN'],
    shipping: ['ADMIN', 'SUPER_ADMIN'],
    content: ['ADMIN', 'SUPER_ADMIN'],
    warehouses: ['ADMIN', 'SUPER_ADMIN'],
    suppliers: ['ADMIN', 'SUPER_ADMIN'],
    bulkOperations: ['ADMIN', 'SUPER_ADMIN'],
    analytics: ['ADMIN', 'SUPER_ADMIN'],
    reports: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    customers: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    addresses: ['ADMIN', 'SUPER_ADMIN'],
    orders: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    refunds: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    returns: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    inventory: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    products: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    reviews: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    qna: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    notifications: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    purchaseOrders: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    productDashboard: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    inventoryDashboard: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    executiveDashboard: ['EXECUTIVE', 'SUPER_ADMIN'],
    banners: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    faq: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    support: ['ADMIN', 'SUPER_ADMIN'],
    newsletter: ['ADMIN', 'SUPER_ADMIN'],
    loyalty: ['ADMIN', 'SUPER_ADMIN'],
    stockMovements: ['ADMIN', 'EXECUTIVE', 'SUPER_ADMIN'],
    superAdmin: ['SUPER_ADMIN'],
  };
  const allowed = moduleAccess[module];
  if (!allowed) return true;
  return allowed.includes(role);
}

export function getMenuByRole(role) {
  return allMenuItems.filter(item => canAccessModule(role, item.module));
}
