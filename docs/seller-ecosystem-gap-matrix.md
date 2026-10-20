# Seller ecosystem: gap matrix (all 23 phases)

Audit of the Cauvery Store code against the "Flipkart/Amazon-style seller ecosystem" request, 20 October 2026.
More detail on onboarding and on roles is in `seller-onboarding-gap-analysis.md` and `seller-organisations-gap-analysis.md`.
**Have** = built · **Partial** = some of it · **Missing** = not built · **Outside** = needs a paid outside service or account first.

| Phase | Area | Status | What exists | Main gaps |
|---|---|---|---|---|
| 2 | Multi-user seller organisation | **Building now** | One seller = one login | Team members, roles, invitations, store switching (this branch) |
| 3 | Registration wizard | **Have** (6 steps) | Business, tax & ID, bank, documents, compliance, review; approval; store created on approval | Seller types listed differ (Retail, Wholesale…; legal form asked separately as "constitution"); UPI ID field |
| 4 | KYC documents | **Partial** | Private encrypted storage, verify/reject with reason, expiry date and alert, re-upload keeps history, audit | OCR, automatic checks (Outside); "Draft / Expired" statuses |
| 5 | Sign-in & security | **Partial** | Email + password (BCrypt), Google, email OTP, JWT + refresh cookie, MFA (authenticator app), sessions and device sign-out, password history, login audit | Mobile/SMS OTP login (Outside: SMS provider), Microsoft/Apple sign-in, a login-history screen |
| 6 | RBAC | **Partial** | Fixed platform roles; Permissions screen exists but **is not enforced** | Seller team roles with permissions (this branch); wiring the platform Permissions screen in |
| 7 | Brands | **Partial** | Admin brand list (name, logo) | Brand registration by sellers, trademark documents, approval workflow |
| 8 | Category approvals | **Missing** | Licences now recorded and checked | Restricted categories locked until a licence is approved |
| 9 | Catalogue | **Partial** | Single product, Excel bulk upload, SKU, HSN, variants, images, approval status | CSV, videos, dimensions/weight check, duplicate and SEO checks |
| 10 | Inventory | **Partial** | Stock, low-stock flag, admin warehouses, stock movements | Per-seller warehouses, stock per location, reservations, transfers, batches, forecasting |
| 11 | Orders | **Partial** | Placed → confirmed → packed → shipped → delivered, cancelled, returned; timeline; seller status updates | "Ready to ship / in transit" stages, partial fulfilment, replacements |
| 12 | Shipping | **Partial** | Shiprocket booking (admin), labels, tracking webhook, courier entry | Shiprocket for sellers, pickup scheduling, NDR. Delhivery, XpressBees and the others come through Shiprocket, so separate integrations are not needed yet |
| 13 | Returns | **Have** | Request, seller review, approve/reject, pickup, received, refund, auto-refund on cancellation | Replacement requests |
| 14 | Settlements | **Partial** | Payout ledger (record and void payments), TCS records, commission invoices | Settlement engine (gross, fees, GST, TCS, returns → net), schedules, seller wallet. **Waits on a GSTIN** |
| 15 | Commission | **Partial** | Rates by seller and category, fixed fee, effective dates, monthly commission invoice | Shipping, collection, return and cancellation fees; campaign rates; simulator |
| 16 | Seller performance | **Missing** | Order, return and rating data exist | Scores, grades (Bronze to Platinum) |
| 17 | Marketing & ads | **Partial** | Coupons (admin), banners, offers page | Seller coupons, sponsored listings, campaign analytics |
| 18 | Analytics & reports | **Partial** | Seller analytics page, admin reports, GST reports (GSTR-1, 3B, 8, 9) | Profitability, settlement and performance reports |
| 19 | Support | **Partial** | Support tickets (customer → admin), help centre, FAQs, chat assistant | Seller tickets, seller knowledge base, escalation |
| 20 | Notifications | **Partial** | Email (Resend) for orders, returns, seller approval and more; in-app notifications | SMS, WhatsApp, push (Outside) |
| 21 | Admin portal | **Partial** | Seller approval, KYC review, licence review, product approval, payouts, audit logs | Settlement and performance monitoring, fraud flags |
| 22 | Audit | **Partial** | Audit log of logins, role changes, documents, payouts, licences; no delete route | Tamper-proofing (hash chain), price-change logging |

## Security issue found during this audit (fixed in this branch)
`ProductService.checkSellerOwnership` let a seller through when a product has **no** seller, so any seller could edit or delete products the store itself listed. Fixed: a seller can now change only products that are theirs.

## Build order
1. **Seller teams** (phases 2 and 6): this branch.
2. Restricted categories that need a licence (phase 8) and seller brand approval (phase 7).
3. Seller coupons, Shiprocket for sellers, stock editing (phases 10, 12, 17).
4. Seller performance scores (phase 16).
5. Settlement engine (phases 14 and 15), once the store has a GSTIN.
6. SMS, WhatsApp, OCR and other paid services, when volume justifies the cost.

Scale note: the tables use indexed seller ids, so they grow with seller count. Specific work for 1,000,000 sellers (sharding, queues, search) is not worth doing before the first real sellers arrive.
