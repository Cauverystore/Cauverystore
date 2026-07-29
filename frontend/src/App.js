import React, { Suspense, lazy } from "react";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import { WishlistProvider } from "./context/WishlistContext";
import { Helmet, HelmetProvider } from "react-helmet-async";
import ProtectedRoute from "./components/ProtectedRoute";
import CustomerLayout from "./layouts/CustomerLayout";
import AdminLayout from "./layouts/AdminLayout";
import SuperAdminLayout from "./layouts/SuperAdminLayout";
import ImpersonationBanner from "./components/ImpersonationBanner";

const Home = lazy(() => import("./pages/Home"));
const ProductList = lazy(() => import("./pages/ProductList"));
const ProductDetails = lazy(() => import("./pages/ProductDetails"));
const Cart = lazy(() => import("./pages/Cart"));
const Checkout = lazy(() => import("./pages/Checkout"));
const Orders = lazy(() => import("./pages/Orders"));
const OrderDetail = lazy(() => import("./pages/OrderDetail"));
const OrderSuccess = lazy(() => import("./pages/OrderSuccess"));
const Login = lazy(() => import("./pages/Auth/Login"));
const Register = lazy(() => import("./pages/Auth/Register"));
const Logout = lazy(() => import("./pages/Auth/Logout"));
const Unauthorized = lazy(() => import("./pages/Auth/Unauthorized"));
const Profile = lazy(() => import("./pages/Profile"));
const AddressBook = lazy(() => import("./pages/AddressBook"));
const Wishlist = lazy(() => import("./pages/Wishlist"));
const SearchResults = lazy(() => import("./pages/SearchResults"));
const CategoryProducts = lazy(() => import("./pages/CategoryProducts"));
const OffersPage = lazy(() => import("./pages/OffersPage"));
const AboutUs = lazy(() => import("./pages/AboutUs"));
const RefundPolicy = lazy(() => import("./pages/ReturnsandRefundPolicy"));

const ContactUs = lazy(() => import("./pages/ContactUs"));
const NotFound = lazy(() => import("./pages/NotFound"));
const Notifications = lazy(() => import("./pages/Notifications"));
const Loyalty = lazy(() => import("./pages/Loyalty"));
const OtpVerification = lazy(() => import("./pages/OtpVerification"));
const SellerLogin = lazy(() => import("./pages/SellerLogin"));
const SellerDashboard = lazy(() => import("./pages/SellerDashboard"));
const SellerProducts = lazy(() => import("./pages/SellerProducts"));
const SellerOrders = lazy(() => import("./pages/SellerOrders"));
const SellerInventory = lazy(() => import("./pages/SellerInventory"));
const SellerStore = lazy(() => import("./pages/SellerStore"));
const SellerPayouts = lazy(() => import("./pages/SellerPayouts"));
const SellerAnalytics = lazy(() => import("./pages/SellerAnalytics"));
const SellerRegistration = lazy(() => import("./pages/SellerRegistration"));
const GstInvoices = lazy(() => import("./pages/GstInvoices"));
const GstInvoiceView = lazy(() => import("./pages/GstInvoiceView"));
const CustomerInvoice = lazy(() => import("./pages/CustomerInvoice"));
const ExecutiveLogin = lazy(() => import("./pages/ExecutiveLogin"));
const AdminLogin = lazy(() => import("./admin/auth/AdminLogin"));
const AdminDashboard = lazy(() => import("./admin/dashboard/AdminDashboard"));
const AdminAnalytics = lazy(() => import("./admin/analytics/AdminAnalytics"));
const ProductManagement = lazy(() => import("./admin/products/ProductManagement"));
const AddProduct = lazy(() => import("./admin/products/AddProduct"));
const EditProduct = lazy(() => import("./admin/products/EditProduct"));
const BulkUpload = lazy(() => import("./admin/products/BulkUpload"));
const ManageVariants = lazy(() => import("./admin/products/ManageVariants"));
const ManageImages = lazy(() => import("./admin/products/ManageImages"));
const ManageDiscounts = lazy(() => import("./admin/products/ManageDiscounts"));
const AdminOrders = lazy(() => import("./admin/orders/AdminOrders"));
const AdminRefunds = lazy(() => import("./admin/orders/AdminRefunds"));
const AdminUsers = lazy(() => import("./admin/users/AdminUsers"));
const AdminCustomers = lazy(() => import("./admin/customers/AdminCustomers"));
const AdminCategories = lazy(() => import("./admin/categories/AdminCategories"));
const AdminBrands = lazy(() => import("./admin/brands/AdminBrands"));
const AdminInventory = lazy(() => import("./admin/inventory/AdminInventory"));
const AdminCoupons = lazy(() => import("./admin/coupons/AdminCoupons"));
const AdminShipping = lazy(() => import("./admin/shipping/AdminShipping"));
const AdminReviews = lazy(() => import("./admin/reviews/AdminReviews"));
const AdminQna = lazy(() => import("./admin/qna/AdminQna"));
const AdminContent = lazy(() => import("./admin/content/AdminContent"));
const AdminNotifications = lazy(() => import("./admin/notifications/AdminNotifications"));
const AdminReports = lazy(() => import("./admin/reports/AdminReports"));
const AdminAuditDashboard = lazy(() => import("./admin/audit/AdminAuditDashboard"));
const AdminSettings = lazy(() => import("./admin/settings/AdminSettings"));
const ExecutiveDashboard = lazy(() => import("./admin/dashboard/ExecutiveDashboard"));
const ProductDashboard = lazy(() => import("./admin/dashboard/ProductDashboard"));
const InventoryDashboard = lazy(() => import("./admin/inventory/InventoryDashboard"));
const AdminWarehouses = lazy(() => import("./admin/warehouse/AdminWarehouses"));
const AdminSuppliers = lazy(() => import("./admin/suppliers/AdminSuppliers"));
const AdminPurchaseOrders = lazy(() => import("./admin/purchases/AdminPurchaseOrders"));
const AdminReturns = lazy(() => import("./admin/returns/AdminReturns"));
const BulkOperations = lazy(() => import("./admin/bulk/BulkOperations"));
const ProductApproval = lazy(() => import("./pages/super-admin/ProductApproval"));
const SellerApproval = lazy(() => import("./pages/super-admin/SellerApproval"));
const SuperAdminDashboard = lazy(() => import("./pages/super-admin/SuperAdminDashboard"));
const UserManagement = lazy(() => import("./pages/super-admin/UserManagement"));
const PlatformSettings = lazy(() => import("./pages/super-admin/PlatformSettings"));
const ImpersonationPage = lazy(() => import("./pages/super-admin/ImpersonationPage"));
const PermissionsManager = lazy(() => import("./pages/super-admin/PermissionsManager"));
const AuditLogs = lazy(() => import("./pages/super-admin/AuditLogs"));
const ReturnOrder = lazy(() => import("./pages/ReturnOrder"));
const FAQ = lazy(() => import("./pages/HelpCenter"));
const HelpCenter = lazy(() => import("./pages/HelpCenter"));
const Policies = lazy(() => import("./pages/Policies"));
const MyTickets = lazy(() => import("./pages/MyTickets"));
const PaymentMethods = lazy(() => import("./pages/PaymentMethods"));
const AdminBanners = lazy(() => import("./admin/banners/AdminBanners"));
const AdminFaq = lazy(() => import("./admin/faq/AdminFaq"));
const AdminSupportTickets = lazy(() => import("./admin/support/AdminSupportTickets"));
const AdminNewsletter = lazy(() => import("./admin/newsletter/AdminNewsletter"));
const AdminLoyalty = lazy(() => import("./admin/loyalty/AdminLoyalty"));
const StockMovements = lazy(() => import("./admin/inventory/StockMovements"));

function App() {
  return (
    <HelmetProvider>
    <BrowserRouter>
      <AuthProvider>
        <WishlistProvider>
        <Helmet>
          <title>Cauvery Store - Your One-Stop Shop</title>
          <meta name="description" content="Shop the best products at Cauvery Store. Electronics, fashion, home & kitchen, and more." />
          <meta property="og:title" content="Cauvery Store" />
          <meta property="og:description" content="Your one-stop shop for everything." />
          <meta property="og:type" content="website" />
          <meta property="og:image" content="/logo192.png" />
          <meta name="twitter:card" content="summary_large_image" />
        </Helmet>
        <script type="application/ld+json" dangerouslySetInnerHTML={{__html: JSON.stringify({
          "@context": "https://schema.org",
          "@type": "Store",
          "name": "Cauvery Store",
          "url": "https://cauverystore.in",
          "logo": "https://cauverystore.in/logo192.png",
          "description": "Your one-stop shop for electronics, fashion, home & kitchen, and more.",
          "address": { "@type": "PostalAddress", "addressCountry": "IN" }
        })}} />
        <ImpersonationBanner />
        <Suspense fallback={<div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "60vh", color: "#94a3b8", fontSize: "0.95rem" }}>Loading...</div>}>
        <Routes>
          <Route element={<CustomerLayout />}>
            <Route path="/" element={<Home />} />
            <Route path="/products" element={<ProductList />} />
            <Route path="/product/:id" element={<ProductDetails />} />
            <Route path="/search" element={<SearchResults />} />
            <Route path="/category/:category" element={<CategoryProducts />} />
            <Route path="/offers" element={<OffersPage />} />
            <Route path="/offers/:offerId" element={<OffersPage />} />
            <Route path="/about" element={<AboutUs />} />
            <Route path="/help" element={<HelpCenter />} />
            <Route path="/policies" element={<Policies />} />
            <Route path="/refund-policy" element={<RefundPolicy />} />
            <Route path="/contact" element={<ContactUs />} />
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/logout" element={<Logout />} />
            <Route path="/unauthorized" element={<Unauthorized />} />
            <Route path="/otp-verify" element={<OtpVerification />} />
            <Route path="/executive/login" element={<ExecutiveLogin />} />
            <Route path="/seller/login" element={<SellerLogin />} />
            <Route path="/profile" element={<ProtectedRoute><Profile /></ProtectedRoute>} />
            <Route path="/address" element={<ProtectedRoute><AddressBook /></ProtectedRoute>} />
            <Route path="/wishlist" element={<ProtectedRoute><Wishlist /></ProtectedRoute>} />
            <Route path="/cart" element={<ProtectedRoute><Cart /></ProtectedRoute>} />
            <Route path="/checkout" element={<ProtectedRoute><Checkout /></ProtectedRoute>} />
            <Route path="/orders" element={<ProtectedRoute><Orders /></ProtectedRoute>} />
            <Route path="/orders/:id" element={<ProtectedRoute><OrderDetail /></ProtectedRoute>} />
            <Route path="/orders/:id/return" element={<ProtectedRoute><ReturnOrder /></ProtectedRoute>} />
            <Route path="/orders/:orderId/invoice" element={<ProtectedRoute><CustomerInvoice /></ProtectedRoute>} />
            <Route path="/order-success" element={<ProtectedRoute><OrderSuccess /></ProtectedRoute>} />
            <Route path="/notifications" element={<ProtectedRoute><Notifications /></ProtectedRoute>} />
            <Route path="/loyalty" element={<ProtectedRoute><Loyalty /></ProtectedRoute>} />
            <Route path="/faq" element={<FAQ />} />
            <Route path="/support-tickets" element={<ProtectedRoute><MyTickets /></ProtectedRoute>} />
            <Route path="/payment-methods" element={<ProtectedRoute><PaymentMethods /></ProtectedRoute>} />
            <Route path="/seller/register" element={<SellerRegistration />} />
            <Route path="/seller/dashboard" element={<ProtectedRoute requiredRole={['SELLER','ADMIN','SUPER_ADMIN']}><SellerDashboard /></ProtectedRoute>} />
            <Route path="/seller/products" element={<ProtectedRoute requiredRole={['SELLER','ADMIN','SUPER_ADMIN']}><SellerProducts /></ProtectedRoute>} />
            <Route path="/seller/products/add" element={<ProtectedRoute requiredRole={['SELLER','ADMIN','SUPER_ADMIN']}><AddProduct /></ProtectedRoute>} />
            <Route path="/seller/products/edit/:id" element={<ProtectedRoute requiredRole={['SELLER','ADMIN','SUPER_ADMIN']}><EditProduct /></ProtectedRoute>} />
            <Route path="/seller/products/bulk-upload" element={<ProtectedRoute requiredRole={['SELLER','ADMIN','SUPER_ADMIN']}><BulkUpload /></ProtectedRoute>} />
            <Route path="/seller/inventory" element={<ProtectedRoute requiredRole={['SELLER','ADMIN','SUPER_ADMIN']}><SellerInventory /></ProtectedRoute>} />
            <Route path="/seller/orders" element={<ProtectedRoute requiredRole={['SELLER','ADMIN','SUPER_ADMIN']}><SellerOrders /></ProtectedRoute>} />
            <Route path="/seller/store" element={<ProtectedRoute requiredRole={['SELLER','ADMIN','SUPER_ADMIN']}><SellerStore /></ProtectedRoute>} />
            <Route path="/seller/payouts" element={<ProtectedRoute requiredRole={['SELLER','ADMIN','SUPER_ADMIN']}><SellerPayouts /></ProtectedRoute>} />
            <Route path="/seller/analytics" element={<ProtectedRoute requiredRole={['SELLER','ADMIN','SUPER_ADMIN']}><SellerAnalytics /></ProtectedRoute>} />
            <Route path="/seller/gst-invoices" element={<ProtectedRoute requiredRole={['SELLER','ADMIN','SUPER_ADMIN']}><GstInvoices /></ProtectedRoute>} />
            <Route path="/seller/gst-invoices/view/:id" element={<ProtectedRoute requiredRole={['SELLER','ADMIN','SUPER_ADMIN']}><GstInvoiceView /></ProtectedRoute>} />
          </Route>

          <Route path="/admin/login" element={<AdminLogin />} />
          <Route path="/admin/unauthorized" element={<div style={{ padding: "3rem", textAlign: "center" }}><h2>Unauthorized</h2><p>You do not have permission to access this page.</p></div>} />

          <Route path="/admin" element={<ProtectedRoute requiredRole={['ADMIN','EXECUTIVE','SUPER_ADMIN']}><AdminLayout /></ProtectedRoute>}>
            <Route index element={<AdminDashboard />} />
            <Route path="dashboard" element={<AdminDashboard />} />
            <Route path="products" element={<ProductManagement />} />
            <Route path="products/add" element={<AddProduct />} />
            <Route path="products/edit/:id" element={<EditProduct />} />
            <Route path="products/bulk-upload" element={<BulkUpload />} />
            <Route path="products/:id/variants" element={<ManageVariants />} />
            <Route path="products/:id/images" element={<ManageImages />} />
            <Route path="products/:id/discounts" element={<ManageDiscounts />} />
            <Route path="orders" element={<AdminOrders />} />
            <Route path="refunds" element={<AdminRefunds />} />
            <Route path="users" element={<AdminUsers />} />
            <Route path="categories" element={<AdminCategories />} />
            <Route path="inventory" element={<AdminInventory />} />
            <Route path="analytics" element={<AdminAnalytics />} />
            <Route path="customers" element={<AdminCustomers />} />
            <Route path="brands" element={<AdminBrands />} />
            <Route path="coupons" element={<AdminCoupons />} />
            <Route path="shipping" element={<AdminShipping />} />
            <Route path="reviews" element={<AdminReviews />} />
            <Route path="content" element={<AdminContent />} />
            <Route path="notifications" element={<AdminNotifications />} />
            <Route path="reports" element={<AdminReports />} />
            <Route path="qna" element={<AdminQna />} />
            <Route path="executive-dashboard" element={<ExecutiveDashboard />} />
            <Route path="product-approvals" element={<ProductApproval />} />
            <Route path="seller-approvals" element={<SellerApproval />} />
            <Route path="audit" element={<AdminAuditDashboard />} />
            <Route path="settings" element={<AdminSettings />} />
            <Route path="product-dashboard" element={<ProductDashboard />} />
            <Route path="inventory-dashboard" element={<InventoryDashboard />} />
            <Route path="warehouses" element={<AdminWarehouses />} />
            <Route path="suppliers" element={<AdminSuppliers />} />
            <Route path="purchase-orders" element={<AdminPurchaseOrders />} />
            <Route path="returns" element={<AdminReturns />} />
            <Route path="bulk-operations" element={<BulkOperations />} />
            <Route path="banners" element={<AdminBanners />} />
            <Route path="faq" element={<AdminFaq />} />
            <Route path="support-tickets" element={<AdminSupportTickets />} />
            <Route path="newsletter" element={<AdminNewsletter />} />
            <Route path="loyalty" element={<AdminLoyalty />} />
            <Route path="stock-movements" element={<StockMovements />} />
          </Route>

          <Route path="/super-admin" element={<ProtectedRoute requiredRole={['SUPER_ADMIN']}><SuperAdminLayout /></ProtectedRoute>}>
            <Route index element={<SuperAdminDashboard />} />
            <Route path="dashboard" element={<SuperAdminDashboard />} />
            <Route path="users" element={<UserManagement />} />
            <Route path="settings" element={<PlatformSettings />} />
            <Route path="impersonate" element={<ImpersonationPage />} />
            <Route path="permissions" element={<PermissionsManager />} />
            <Route path="audit-logs" element={<AuditLogs />} />
          </Route>

          <Route path="*" element={<NotFound />} />
        </Routes>
        </Suspense>
        </WishlistProvider>
      </AuthProvider>
    </BrowserRouter>
    </HelmetProvider>
  );
}

export default App;
