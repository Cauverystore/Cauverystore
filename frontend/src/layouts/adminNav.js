import {
  LayoutDashboard, BarChart3, Package, Tags, Building2, LayoutList,
  TrendingDown, Warehouse, Truck, FileText, Undo2, Zap,
  ShoppingCart, CreditCard, Users, ClipboardList, Ticket, Ship, Percent,
  Star, HelpCircle, FileEdit, Bell, FileSpreadsheet, FolderArchive,
  Image, Mail, Trophy, ArrowUpDown, ShieldCheck, Settings, Shield,
  BookOpen, MapPin, Wallet, Inbox, Receipt, RefreshCw, Upload, Bot,
} from "lucide-react";

/** Always shown at the top, outside any section. */
export const HOME_LINK = { to: "/admin", label: "Dashboard", icon: LayoutDashboard, end: true, module: null };

/**
 * The admin menu, grouped so the 40-odd pages can be found. Each link keeps the permission
 * "module" it had before, so who can see what is unchanged.
 */
export const ADMIN_NAV_SECTIONS = [
  { id: "overview", label: "Overview", links: [
    { to: "/admin/analytics", label: "Analytics", icon: BarChart3, module: "analytics" },
    { to: "/admin/reports", label: "Reports", icon: FileSpreadsheet, module: "reports" },
    { to: "/admin/executive-dashboard", label: "Executive Panel", icon: ShieldCheck, module: "executiveDashboard" },
  ] },
  { id: "catalogue", label: "Catalogue", links: [
    { to: "/admin/products", label: "Products", icon: Package, module: "products" },
    { to: "/admin/products/bulk-upload", label: "Bulk Upload", icon: Upload, module: "products" },
    { to: "/admin/agent-drafts", label: "Agent Drafts", icon: Bot, module: "products" },
    { to: "/admin/supplier-labels", label: "Supplier Labels", icon: Tags, module: "products" },
    { to: "/admin/categories", label: "Categories", icon: Tags, module: "categories" },
    { to: "/admin/brands", label: "Brands", icon: Building2, module: "brands" },
    { to: "/admin/product-dashboard", label: "Product Dashboard", icon: LayoutList, module: "productDashboard" },
    { to: "/admin/product-compliance", label: "Product Compliance", icon: ShieldCheck, module: "productDashboard" },
  ] },
  { id: "stock", label: "Stock & suppliers", links: [
    { to: "/admin/inventory", label: "Inventory", icon: ClipboardList, module: "inventory" },
    { to: "/admin/inventory-dashboard", label: "Inventory Dashboard", icon: TrendingDown, module: "inventoryDashboard" },
    { to: "/admin/stock-movements", label: "Stock Movements", icon: ArrowUpDown, module: "stockMovements" },
    { to: "/admin/warehouses", label: "Warehouses", icon: Warehouse, module: "warehouses" },
    { to: "/admin/suppliers", label: "Suppliers", icon: Truck, module: "suppliers" },
    { to: "/admin/purchase-orders", label: "Purchase Orders", icon: FileText, module: "purchaseOrders" },
    { to: "/admin/bulk-operations", label: "Bulk Operations", icon: Zap, module: "bulkOperations" },
  ] },
  { id: "orders", label: "Orders & money", links: [
    { to: "/admin/orders", label: "Orders", icon: ShoppingCart, module: "orders" },
    { to: "/admin/returns", label: "Returns", icon: Undo2, module: "returns" },
    { to: "/admin/refunds", label: "Refunds", icon: CreditCard, module: "refunds" },
    { to: "/admin/shipping", label: "Shipping", icon: Ship, module: "shipping" },
    { to: "/admin/finance", label: "Finance", icon: Wallet, module: "sellerPayouts" },
    { to: "/admin/seller-payouts", label: "Seller Payouts", icon: Wallet, module: "sellerPayouts" },
    { to: "/admin/erp-sync", label: "ERP Sync", icon: RefreshCw, module: "erpSync" },
  ] },
  { id: "customers", label: "Customers & support", links: [
    { to: "/admin/customers", label: "Customers", icon: Users, module: "customers" },
    { to: "/admin/addresses", label: "Addresses", icon: MapPin, module: "addresses" },
    { to: "/admin/inbox", label: "Inbox", icon: Inbox, module: "support" },
    { to: "/admin/support-tickets", label: "Support Tickets", icon: Ticket, module: "support" },
    { to: "/admin/reviews", label: "Reviews", icon: Star, module: "reviews" },
    { to: "/admin/qna", label: "Q&A", icon: HelpCircle, module: "qna" },
    { to: "/admin/loyalty", label: "Loyalty", icon: Trophy, module: "loyalty" },
  ] },
  { id: "gst", label: "GST & tax", links: [
    { to: "/admin/gst-rates", label: "GST Rates", icon: Percent, module: "gstRates" },
    { to: "/admin/gst-compliance", label: "GST Compliance", icon: ShieldCheck, module: "gstRates" },
    { to: "/admin/gst-masters", label: "GST Masters", icon: BookOpen, module: "gstRates" },
    { to: "/admin/tds-194o", label: "TDS on Sellers", icon: Receipt, module: "gstRates" },
  ] },
  { id: "marketing", label: "Marketing & content", links: [
    { to: "/admin/coupons", label: "Coupons", icon: Ticket, module: "coupons" },
    { to: "/admin/banners", label: "Banners", icon: Image, module: "banners" },
    { to: "/admin/content", label: "Content", icon: FileEdit, module: "content" },
    { to: "/admin/faq", label: "FAQs", icon: HelpCircle, module: "faq" },
    { to: "/admin/newsletter", label: "Newsletter", icon: Mail, module: "newsletter" },
    { to: "/admin/notifications", label: "Notifications", icon: Bell, module: "notifications" },
  ] },
  { id: "system", label: "System", links: [
    { to: "/admin/audit", label: "Audit Logs", icon: FolderArchive, module: "audit" },
    { to: "/admin/settings", label: "Settings", icon: Settings, module: "settings" },
    { to: "/super-admin", label: "Super Admin", icon: Shield, module: "superAdmin" },
  ] },
];

/**
 * The sections this person may see, each with only the links they may open and that match the
 * search (any part of the name, any case). Sections left empty are dropped.
 */
export const visibleSections = (canAccess, query = "") => {
  const q = query.trim().toLowerCase();
  return ADMIN_NAV_SECTIONS
    .map((section) => ({
      ...section,
      links: section.links.filter((l) =>
        (!l.module || canAccess(l.module)) && (!q || l.label.toLowerCase().includes(q))),
    }))
    .filter((section) => section.links.length > 0);
};

/** The section holding the page being viewed, so it can start open. Longest match wins. */
export const sectionForPath = (pathname) => {
  let best = null;
  let bestLength = -1;
  ADMIN_NAV_SECTIONS.forEach((section) => {
    section.links.forEach((l) => {
      if ((pathname === l.to || pathname.startsWith(l.to + "/")) && l.to.length > bestLength) {
        best = section.id;
        bestLength = l.to.length;
      }
    });
  });
  return best;
};
