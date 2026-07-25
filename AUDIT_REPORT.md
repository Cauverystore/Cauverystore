# Noyyal Store — Full Website Audit Report

**Date:** 23 July 2026
**Scope:** Full-stack e-commerce application (React 18 frontend + Spring Boot 3.2 backend)
**Environment:** Development (localhost:3000 → localhost:9091)

---

## Table of Contents
1. [UI/UX Audit](#1-uiux-audit)
2. [Performance & Technical Audit](#2-performance--technical-audit)
3. [Security & Compliance Audit](#3-security--compliance-audit)
4. [Shopping & Order Flow Audit](#4-shopping--order-flow-audit)
5. [Content & SEO Audit](#5-content--seo-audit)
6. [Analytics & Reporting Audit](#6-analytics--reporting-audit)
7. [Prioritized Recommendations](#7-prioritized-recommendations)

---

## 1. UI/UX Audit

### 1.1 Homepage Layout — GOOD FOUNDATION

| Aspect | Finding | Rating |
|--------|---------|--------|
| Utility bar | Dark green bar with location, language, support, seller, orders, account links | ✅ Good |
| Main header | Logo + sticky centered search bar + account/wishlist/cart icons | ✅ Good |
| Category nav | 10-category horizontal strip with hover underline | ✅ Good |
| Hero carousel | 4-slide auto-rotating banner with nav dots and arrows | ✅ Good |
| Quick categories | 8-icon grid (Mobiles, Laptops, Fashion, Home, TVs, Beauty, Grocery, Books) | ✅ Good |
| Product sections | Deals of the Day, Trending Electronics, Fashion Essentials with countdown timer | ✅ Good |
| Brand stores | Horizontal scroll row of brand cards | ✅ Good |
| Mobile hamburger | Slide-in overlay with categories + account footer | ✅ Good |
| Mobile bottom bar | Home / Shop / Cart / Wishlist / Profile — sticky at bottom | ✅ Good |

**Issues Found:**
1. **Cart badge hardcoded to 0** — `Navbar.jsx` line 8 reads `const cartCount = 0` instead of fetching actual cart count from API
2. **No promotional banners for bank offers** — The bank offer strip exists in CSS (`.sn-offer-strip`) but isn't populated with real data
3. **Skeleton loading on API fetch** — Home page shows "Loading..." text before displaying content; no shimmer skeleton for product cards until data loads
4. **Newsletter subscribe is non-functional** — Form calls `e.preventDefault()` with no API call

### 1.2 Product Tray / Card Design — IMPROVED (new ProductTray component)

The recently created `ProductTray.jsx` component addresses most card concerns:

| Feature | Status | Notes |
|---------|--------|-------|
| Image lazy loading | ✅ | `loading="lazy"` + shimmer placeholder |
| Image hover zoom | ✅ | `scale(1.08)` on card hover |
| Price block with MRP strike-through | ✅ | Discounted price in `--color-primary` green |
| Discount % badge | ✅ | Gold `#f59e0b` background |
| Star ratings | ✅ | SVG stars in gold with half-star support |
| Quick actions | ✅ | Wishlist (heart toggle) + Share buttons slide in on hover |
| Add to Cart + Buy Now buttons | ✅ | Green primary + orange accent |
| Trust badges | ✅ | Secure + Easy Returns inline |
| Keyboard navigation | ✅ | `tabIndex={0}`, Enter/Space handlers |

**Issues Found:**
1. **Not all product listing pages use ProductTray** — Home page still uses old inline `.sn-product-card` HTML; `ProductList` now uses `ProductTray` but `SearchResults.jsx`, `CategoryProducts.jsx`, and `Wishlist.jsx` still use their own card markup
2. **No image gallery / thumbnail selector** — `ProductTray` shows only one image; multi-image support exists only in `ProductDetails.jsx`
3. **Compare button absent** — Requirement mentioned "quick action icons (Wishlist, Compare, Share)" but Compare is not implemented
4. **No color/image variant selector on card** — Only in ProductDetails page
5. **Wishlist heart toggle is client-side only** — Heart icon toggles locally but doesn't call any API to persist the wishlist state

### 1.3 Checkout Flow

| Step | Implementation | Rating |
|------|---------------|--------|
| Step indicator | Circle numbers 1-4 with connecting lines, green active/completed | ✅ Good |
| Delivery Address | Saved address picker + new address form with 6 fields | ✅ Good |
| Payment Method | Radio selection: COD, Card, UPI, Net Banking, Wallet + EMI toggle + Gift Wrap | ✅ Good |
| Order Review | Shipping address, payment method, item list with images | ✅ Good |
| Place Order | COD direct via `/api/orders/place`; online via Razorpay popup | ✅ Good |

**Issues Found:**
1. **No form validation library** — All validation is manual inline checks (`validateStep()`); error display is basic text
2. **No "Save this address" checkbox** — New addresses entered during checkout are not saved to the user's address book
3. **Loading states are minimal** — "Loading checkout..." text; no skeleton for cart items during fetch
4. **No address autocomplete** — No Google Places / pincode lookup integration
5. **Payment method is not stored with the order** — While `paymentMethod` is included in the order payload, the backend `placeOrder()` doesn't appear to persist it to the Order entity in the database

### 1.4 Mobile Responsiveness

| Breakpoint | Behavior | Rating |
|------------|----------|--------|
| Desktop 1280+ | 5-column product grid, full navbar with all sections | ✅ Good |
| Tablet 768-1023 | 3-column grid, utility bar visible, compact hero, 4-col quick cats | ✅ Good |
| Mobile <768 | 2-column grid, hamburger menu, hidden utility bar, hidden category nav, hidden search bar, sticky bottom nav | ✅ Good |

**Issues Found:**
1. **Search bar hidden on mobile** — Users must scroll to top or navigate to a search page; no expandable search trigger in header
2. **Filter sidebar not shown on mobile** — ProductList page's filter sidebar is `display:none` below 768px with no mobile filter drawer trigger in the new rewrite
3. **Bottom bar overlaps content** — Fixed 60px bottom bar can obscure the last items in lists. Cart and checkout pages have `padding-bottom: 60px` but other pages (orders, wishlist) may not

---

## 2. Performance & Technical Audit

### 2.1 Bundle Size & Code Splitting

| Metric | Current | Recommended | Severity |
|--------|---------|-------------|----------|
| Initial JS bundle | ~243 KB gzipped (all pages) | Split into ~30-50 KB chunks per route | 🔴 High |
| React.lazy usage | ❌ None | All 65+ route imports should use `React.lazy()` | 🔴 High |
| Suspense fallback | ❌ None | Add `<Suspense fallback={<Loading />}>` | 🔴 High |
| CSS bundle | ~21 KB gzipped | Acceptable | ✅ Good |

**Impact:** Every user downloads the entire application (customer pages + admin pages + seller pages + super-admin pages) on first visit, even if they only access the homepage.

### 2.2 Image Optimization

| Aspect | Finding | Severity |
|--------|---------|----------|
| `loading="lazy"` on images | Only 2 images use it (ProductTray, Home product card) | 🟡 Medium |
| Image dimensions specified | ❌ Not used — all images lack explicit `width`/`height` | 🟡 Medium |
| WebP format | ❌ Not enforced — all images are JPEG/PNG via Cloudinary URLs | 🟡 Medium |
| Cloudinary transformations | ❌ Not used — raw Cloudinary URLs with no width/quality params | 🟡 Medium |
| Placeholder/blur-up | ✅ Shimmer skeleton on ProductTray | ✅ Good |

**Impact:** Images contribute to layout shift (CLS) and bandwidth waste. Product listing pages load full-resolution images at thumbnail size.

### 2.3 Caching Strategy

| Layer | Status | Notes |
|-------|--------|-------|
| Spring Cache (`@Cacheable`) | ✅ Present | Products cached with in-memory `ConcurrentMapCacheManager` |
| Redis | 🔶 Configured partially | `spring-boot-starter-data-redis` in pom.xml, but no explicit `RedisCacheManager` bean for production |
| HTTP caching headers | ❌ Not configured | No `Cache-Control` headers on product/category APIs |
| CDN | ❌ Not implemented | No CDN for static assets or images (Cloudinary for uploads only) |
| Service worker | ❌ Not configured | CRA supports PWA but no `service-worker.js` or manifest |

### 2.4 Core Web Vitals (Estimated)

| Metric | Estimated Score | Notes |
|--------|----------------|-------|
| LCP | 🟡 Needs improvement | Large hero image + full JS bundle delay first paint |
| CLS | 🟡 Needs improvement | No explicit image dimensions cause layout shifts |
| FID | 🔴 Poor | Full JS bundle blocks main thread on load |

---

## 3. Security & Compliance Audit

### 3.1 Authentication & Authorization

| Aspect | Status | Notes |
|--------|--------|-------|
| JWT-based auth | ✅ Implemented | JJWT 0.11.5, HMAC-SHA signing, 24h access + 7d refresh |
| Password hashing | ✅ BCrypt | `BCryptPasswordEncoder` bean configured |
| Login attempt limiting | ✅ Implemented | Max 5 failed attempts → account lock |
| Role-based access (RBAC) | ✅ Implemented | 5 roles: CUSTOMER, SELLER, ADMIN, SUPER_ADMIN, EXECUTIVE |
| Token refresh with rotation | ✅ Implemented | Axios interceptor queues failed requests, retries after refresh |
| Session timeout + inactivity logout | ✅ Implemented | 30-min timeout with warning dialog at 29 min |
| Remember Me | ✅ Implemented | Skips inactivity timer, persists across browser restarts |

### 3.2 Critical Security Gaps

| # | Issue | Severity | Details |
|---|-------|----------|---------|
| 1 | **Order endpoints publicly accessible** | 🔴 Critical | `GET /api/orders/{orderId}/items`, `invoice`, `invoice/pdf`, `timeline` have no auth — anyone with an order ID can view items, invoices, and timeline |
| 2 | **CSRF protection disabled** | 🟡 Medium | `http.csrf().disable()` — acceptable for JWT-based API but worth noting |
| 3 | **CORS allows all origins** | 🟡 Medium | `@CrossOrigin("*")` on all controllers + security config default `*` — should be locked to the actual frontend domain |
| 4 | **CORS credentials disabled** | 🟡 Medium | `.allowedCredentials(false)` — prevents cookies/auth headers in cross-origin requests (intentional for JWT) |
| 5 | **No HTTPS enforcement** | 🟡 Medium | No SSL redirect config visible; dev environment uses HTTP |
| 6 | **No rate limiting on auth endpoints** | 🟡 Medium | Login endpoint has attempt limiting but no broader rate limiting (register, forgot-password, OTP) |
| 7 | **Refresh token stored in localStorage** | 🟡 Medium | Accessible to any JS on the same origin (XSS vulnerability) — HttpOnly cookies would be more secure |
| 8 | **2FA flag exists but unused** | 🟢 Low | User entity has `mfaEnabled` field but no 2FA flow is implemented |
| 9 | **Password reset OTP stored in plaintext** | 🟢 Low | OTP stored in database without hashing (15-min expiry mitigates risk) |

### 3.3 Payment Security

| Aspect | Status | Notes |
|--------|--------|-------|
| Razorpay integration | ✅ Implemented | Standard Razorpay checkout.js integration |
| Payment verification | ✅ Implemented | Signature verification via `/api/payment/verify` |
| PCI DSS compliance | ✅ Handled | Razorpay handles card data; store never touches raw card numbers |
| Order `paid` field never updated | 🔴 Critical | Payment verifies signature but does NOT set Order.paid = true in the database |
| No payment retry logic | 🟡 Medium | If Razorpay modal fails, user sees error but no "Retry Payment" option |

---

## 4. Shopping & Order Flow Audit

### 4.1 Cart Functionality

| Feature | Status | Notes |
|---------|--------|-------|
| Add to cart | ✅ | Works via `/api/cart/add` |
| Remove item | ✅ | Delete `/api/cart/remove/{id}` |
| Quantity update | ✅ | Post `/api/cart/update-quantity/{id}?quantity=N` |
| Save for later | ✅ | Post `/api/cart/save-for-later/{id}` |
| Move to cart | ✅ | Post `/api/cart/move-to-cart/{id}` |
| Coupon application | ✅ | Post `/api/promo/validate` |
| Free shipping progress | ✅ | Visual bar with "Add ₹X more for FREE Delivery" |
| Frequently bought together | ✅ | From `/api/cart/frequently-bought` |
| Per-item loading states | ✅ | Individual action loading without blocking UI |

### 4.2 Checkout Flow Issues

| # | Issue | Severity | Details |
|---|-------|----------|---------|
| 1 | **Payment verify does not update order** | 🔴 Critical | `PaymentController.verifyPayment()` verifies Razorpay signature but never sets `order.paid = true`. Orders paid online remain marked as unpaid in the DB |
| 2 | **No stock reversion on cancel** | 🔴 Critical | `cancelOrder()` sets status to CANCELLED but does NOT restore product stock |
| 3 | **`assign-courier` endpoint missing** | 🔴 Critical | AdminOrders frontend calls `/api/admin/orders/{id}/assign-courier` but no backend handler exists |
| 4 | **`markShipped`/`markDelivered` are stubs** | 🔴 Critical | These methods in `OrderService.java` return hardcoded maps without touching the database |
| 5 | **Cancel API call missing auth header** | 🔴 Critical | `OrderDetail.jsx` line 85 calls cancel without Authorization header; backend requires it |
| 6 | **`courier` field missing on Order entity** | 🔴 Critical | Frontend displays `order.courier` but the backend entity has no such field (only `trackingNumber`) |
| 7 | **No tracking/courier integration** | 🟡 Medium | No endpoint to look up real courier tracking status; shipping status relies entirely on manual admin updates |
| 8 | **Admin orders list lacks pagination** | 🟡 Medium | Backend returns `List<Order>` with no Pageable support; frontend passes `{ page, status }` params that are ignored |
| 9 | **Status progression not enforced** | 🟡 Medium | Backend accepts any status string; no validation that PLACED→CONFIRMED→PACKED→SHIPPED→DELIVERED order is respected |
| 10 | **No return/refund workflow** | 🟡 Medium | Return button navigates to `/orders/{id}/return` but this route doesn't exist in App.js; `refundOrder()` exists in backend but frontend never calls it |

### 4.3 Order Management (Admin)

| Feature | Status | Notes |
|---------|--------|-------|
| View all orders | ✅ | `GET /api/admin/orders` |
| Update order status | ✅ | `PUT /api/admin/orders/{id}/status` — works |
| Assign courier | ❌ Broken | Frontend calls missing endpoint |
| Ship/Deliver/Cancel order | ❌ Stubs | Backend methods are no-ops |
| Refund order | ✅ | `PUT /api/admin/orders/{id}/refund` — works |
| Admin dashboard stats | ✅ | Order counts, revenue, refunds, recent orders, top products |

---

## 5. Content & SEO Audit

### 5.1 Metadata

| Aspect | Status | Details |
|--------|--------|---------|
| Page title | ❌ Static only | `<title>Noyyal Store</title>` in `index.html` — same for all pages |
| Meta description | ❌ Static only | `"Noyyal Store - Your one-stop shop"` — same on every page |
| Per-page titles | ❌ Missing | No `react-helmet` or `document.title` manipulation anywhere |
| Open Graph tags | ❌ Missing | No og:title, og:description, og:image |
| Twitter cards | ❌ Missing | No twitter:card meta tags |
| Product meta fields (backend) | ✅ Present | Product entity has `metaTitle`, `metaDescription`, `metaKeywords` fields |
| CMS meta fields (backend) | ✅ Present | Admin content has meta fields |

### 5.2 Structured Data (Schema.org)

| Schema Type | Status | Impact |
|-------------|--------|--------|
| Product | ❌ Missing | Cannot get rich search results (price, availability, rating stars) |
| Review | ❌ Missing | Star ratings not shown in SERPs |
| BreadcrumbList | ❌ Missing | No breadcrumb navigation in search results |
| Organization | ❌ Missing | No brand knowledge panel |
| LocalBusiness | ❌ Missing | No local SEO presence |
| FAQ | ❌ Missing | Q&A content not marked up for rich results |

### 5.3 Internal Linking

| Aspect | Status | Notes |
|--------|--------|-------|
| Category navigation | ✅ Good | 10-category nav + quick categories grid on homepage |
| Product details links | ✅ Good | All product cards link to `/product/{id}` |
| Search results | ✅ Good | `/search?q=` with product links |
| Breadcrumbs | ❌ Missing | No breadcrumb component on any page |
| Footer links | ✅ Good | 4-column grid with internal links |
| "View All" links | ✅ Good | Section-level "View All" buttons |

### 5.4 Content Quality

| Aspect | Status | Notes |
|--------|--------|-------|
| Product descriptions | ✅ User-submitted | Rich text with HTML support |
| Product images | ✅ Cloudinary-hosted | Upload via admin panel |
| Reviews & ratings | ✅ User-submitted | Star ratings + text reviews |
| Q&A section | ✅ User-submitted | Questions + answers |
| Return policy field | ✅ Per-product | `returnPolicy` and `returnWindow` fields |
| FAQ page | ✅ Exists | `faq/` directory in source |

---

## 6. Analytics & Reporting Audit

### 6.1 Customer Analytics

| Tool | Status | Notes |
|------|--------|-------|
| Google Analytics 4 | ❌ Not integrated | No gtag.js or measurement ID anywhere |
| Google Tag Manager | ❌ Not integrated | No GTM container |
| Hotjar / session recording | ❌ Not integrated | No heatmaps or session replays |
| Mixpanel / Amplitude | ❌ Not integrated | No product analytics |
| Facebook Pixel | ❌ Not integrated | No conversion tracking |
| Custom event tracking | ❌ Not implemented | No `window.gtag()` or custom analytics calls |

**Impact:** No visibility into user behavior — bounce rates, conversion funnels, drop-off points, popular products, search queries, or cart abandonment.

### 6.2 Admin Reporting

| Report | Status | Source |
|--------|--------|--------|
| Sales dashboard | ✅ | `/api/admin/analytics/dashboard` — recharts visualizations |
| Sales chart (time series) | ✅ | `/api/admin/analytics/sales-chart` |
| Top products | ✅ | `/api/admin/analytics/top-products` |
| Order reports | ✅ | AdminOrders + AdminReports (Excel export via xlsx) |
| Inventory reports | 🟡 Basic | AdminInventory page exists |
| Customer reports | ✅ | AdminCustomers page with filters |
| Audit logs | ✅ | SuperAdmin audit logs page |
| Automated email reports | ❌ Not implemented | No scheduled report generation |
| Export to PDF | ❌ Not implemented | Only Excel export exists |

---

## 7. Prioritized Recommendations

### 🔴 Critical — Fix Immediately

| # | Area | Recommendation | Effort | Impact |
|---|------|---------------|--------|--------|
| 1 | **Payment** | Set `order.paid = true` in `PaymentService.verifyPayment()` after successful signature verification | 2 hours | Prevents revenue loss from unpaid-but-shipped orders |
| 2 | **Order Mgmt** | Implement `markShipped()`, `markDelivered()`, `adminCancelOrder()` in `OrderService` to actually update DB | 3 hours | Order status updates currently do nothing |
| 3 | **Order Flow** | Add `assign-courier` endpoint to `AdminOrderController`; add `courier` field to Order entity | 2 hours | Courier assignment currently returns 404 |
| 4 | **Order Flow** | Revert stock on `cancelOrder()` — add `product.setStock(product.getStock() + item.getQuantity())` for each item | 2 hours | Cancelled orders don't restore inventory |
| 5 | **Security** | Add `@PreAuthorize` to public order endpoints (`/items`, `/invoice`, `/timeline`) requiring order ownership or admin role | 1 hour | Anyone can view any order's data |
| 6 | **Frontend** | Fix cancel API call in `OrderDetail.jsx` to pass Authorization header (or rely on axios interceptor which already does this — needs testing) | 1 hour | Cancel button doesn't work |

### 🟡 High Priority — Next Sprint

| # | Area | Recommendation | Effort | Impact |
|---|------|---------------|--------|--------|
| 7 | **Performance** | Add `React.lazy(() => import(...))` for all route components + `<Suspense>` wrapper | 4 hours | Reduce initial bundle from 243KB to ~50KB |
| 8 | **SEO** | Install `react-helmet-async`, add `<Helmet>` with per-page `<title>` and `<meta name="description">` | 3 hours | Fixes blank titles in search results |
| 9 | **SEO** | Add JSON-LD structured data (Product, Review, BreadcrumbList) on product detail pages | 4 hours | Enables rich snippets in Google |
| 10 | **Analytics** | Add Google Analytics 4 (gtag.js) to `index.html` + custom events (add_to_cart, purchase, checkout) | 3 hours | Zero visibility into user behavior |
| 11 | **Image Opt** | Add `loading="lazy"` to every product image across all pages (SearchResults, CategoryProducts, Wishlist, Cart, etc.) | 1 hour | Reduces initial page weight |
| 12 | **Image Opt** | Set explicit `width`/`height` on product images to prevent CLS | 2 hours | Improves Core Web Vitals |
| 13 | **Checkout** | Save new address to user's address book during checkout | 2 hours | Reduces friction on next purchase |
| 14 | **Order Mgmt** | Add backend pagination (`Pageable`) to admin orders list | 2 hours | Admin cannot browse beyond page 1 |
| 15 | **Security** | Lock CORS to specific origins in production configuration | 1 hour | Security hardening |

### 🔵 Medium Priority — Within 2 Sprints

| # | Area | Recommendation | Effort | Impact |
|---|------|---------------|--------|--------|
| 16 | **UI/UX** | Port Home page, SearchResults, CategoryProducts, Wishlist to use `ProductTray` component | 4 hours | Consistent card design everywhere |
| 17 | **UI/UX** | Add mobile expandable search bar (tap search icon → full search overlay) | 3 hours | Search is hidden on mobile |
| 18 | **UI/UX** | Add mobile filter drawer for ProductList page | 4 hours | No filtering on mobile |
| 19 | **UI/UX** | Add breadcrumb component on all product/category pages | 3 hours | Better navigation + SEO |
| 20 | **Performance** | Configure Redis cache manager for production profile | 2 hours | Scale caching beyond single-node |
| 21 | **Performance** | Add `Cache-Control` headers to product/category API responses | 2 hours | Browser-level caching |
| 22 | **Cart** | Fix Navbar cart badge to show actual item count from API | 2 hours | Badge always shows 0 |
| 23 | **Checkout** | Add pincode-based delivery date estimation | 3 hours | Better UX than "Get it within 5-7 days" |
| 24 | **Wishlist** | Make wishlist heart button call actual API to persist state | 3 hours | Wishlist state is lost on refresh |
| 25 | **Analytics** | Implement automated report scheduling (daily/weekly PDF/Excel via email) | 5 hours | Replaces manual report generation |

### 🟢 Low Priority — Future Enhancements

| # | Area | Recommendation | Effort |
|---|------|---------------|--------|
| 26 | **Auth** | Implement 2FA for admin/seller accounts (mfaEnabled field already exists) | 5 hours |
| 27 | **Auth** | Move refresh token to HttpOnly cookie instead of localStorage | 4 hours |
| 28 | **Auth** | Add rate limiting on /register, /forgot-password, /otp endpoints | 3 hours |
| 29 | **Checkout** | Add address autocomplete via Google Places API | 4 hours |
| 30 | **UI/UX** | Add "Compare" product quick action button | 3 hours |
| 31 | **Return Flow** | Implement /orders/{id}/return route and full return/replacement lifecycle | 8 hours |
| 32 | **Tracking** | Integrate with courier API (Delhivery, Shiprocket, etc.) for real-time tracking | 10 hours |
| 33 | **PWA** | Add service worker for offline support and installable PWA | 6 hours |
| 34 | **Email** | Implement automated abandoned cart emails | 4 hours |
| 35 | **CI/CD** | Add automated test pipeline and staging environment | 8 hours |

---

## Summary Dashboard

```
Category             Critical   High   Medium   Low   Total
───────────────────────────────────────────────────────────
UI/UX                    0      0       4       1      5
Performance              0      3       2       1      6
Security & Compliance    1      2       1       4      8
Shopping & Order Flow    6      2       2       3     13
Content & SEO            2      2       0       0      4
Analytics & Reporting    0      1       1       2      4
───────────────────────────────────────────────────────────
TOTAL                    9     10      10      11     40
```

### Immediate Action Items (Top 6 Critical)

1. 🔴 **Fix payment verification** — set `order.paid = true` after Razorpay signature check
2. 🔴 **Implement shipping/delivery/cancel stubs** — `markShipped`, `markDelivered`, `adminCancelOrder` need real DB updates
3. 🔴 **Add `assign-courier` endpoint + `courier` entity field** — closes frontend-backend gap
4. 🔴 **Restore stock on order cancel** — prevent phantom inventory depletion
5. 🔴 **Secure public order endpoints** — add `@PreAuthorize` to items/invoice/timeline
6. 🔴 **Route-level code splitting** — `React.lazy()` all route imports
