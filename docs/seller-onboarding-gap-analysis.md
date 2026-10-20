# Seller onboarding: gap analysis

Audit of the existing seller onboarding code against the 19-module checklist (A to S), 20 October 2026.
Status key: **Have** = built and working · **Partial** = some of it exists · **Missing** = not built.

## Fix first: two privacy problems in what already exists

1. **KYC documents go to a public demo account.** `SellerRegistration.jsx` uploads the seller's PAN card, Aadhaar, GST certificate and other documents to `api.cloudinary.com/v1_1/demo`, Cloudinary's shared public demo account. The store doesn't control it, anyone with the link can open the file, and Cloudinary can wipe it at any time. When that upload fails, the page saves a `blob:` link, which only opens in the seller's own browser, so admins can't see the document at all.
2. **Aadhaar and bank account numbers are stored as plain text** (`SellerRegistration.aadhaarNumber`, `bankAccountNumber`). The code already has `CryptoUtil` (it encrypts MFA secrets), but nothing uses it for these fields. In addition, a business that is not authorised by UIDAI should not keep full Aadhaar numbers at all: the usual practice is to keep only the last 4 digits, or a masked copy of the card.

Both put sellers' personal data at risk under the DPDP Act. They should be fixed before any real seller signs up.

## Module by module

| # | Module | Status | What exists | What is missing |
|---|---|---|---|---|
| A | Registration | **Partial** | Email signup, email OTP, password, Google sign-in, MFA (authenticator app; secret encrypted). Seller registration is a 6-step wizard (Business, Tax & ID, Bank, Documents, Compliance, Review) with save-and-resume. | Mobile/SMS signup, invitation links. Seller types are Retail, Wholesale, Distributor, Service, Manufacturing, Others; there is no Individual, Brand Owner or Dropshipper. |
| B | Business profile | **Have** | Legal name (from GSTIN), contact, business address, constitution, GSTIN, PAN, signatory and signature, extra places of business/pickup addresses (APOB) with their own verification. | Trade name as a separate field. Document version history (a re-upload replaces the old file). DOC files are not accepted (PDF/JPG/PNG only). |
| C | KYC & compliance | **Partial** | Document list (GST, PAN, business registration, FSSAI, BIS, drug licence, trademark, IEC, MSME, Aadhaar) with PENDING / VERIFIED / REJECTED, rejection reason and expiry date. Format checks for GSTIN and PAN. Admin verifies each document. Compliance alerts. | Private document storage (see above). Passport and driving licence. "Under review" and "re-submit required" statuses. OCR, automatic checks, risk score, fraud flags. |
| D | Banking & settlements | **Partial** | Bank details with IFSC and account-number format checks, and a bank status field. Payout ledger (added today). | Real account verification: today's verifier only checks the format (simulated), with no penny-drop test. Settlement schedule (daily, weekly, monthly). Working out the amount owed. |
| E | Tax configuration | **Have** | GSTIN verification (live GSTN client built, currently switched to simulated), composition-scheme check, state codes, HSN/GST rates with approval and audit, GST invoices, TCS records, GST compliance pages. | Live GSTN switched on (needs a GSP account). Seller-level tax exemptions. |
| F | Brand verification | **Missing** | Brands exist as a plain list (name, logo). A trademark certificate can be uploaded as a general document. | Brand ownership claims, authorisation letters, and Pending / Verified / Rejected status per seller-brand pair. |
| G | Category approvals | **Partial** | The seller picks categories. FSSAI, drug licence and BIS can be uploaded. | Restricted categories that stay locked until the right licence is approved. Today any approved seller can list in any category. |
| H | Store setup | **Partial** | Store name, slug, logo, banner, description, contact details, return and shipping policy. | Social links on the store page. SEO meta title and description. |
| I | Product readiness | **Partial** | Single product form, Excel bulk upload (.xlsx), export, SKU, HSN, variants, images, product approval status. | CSV import. A checking step before upload (missing fields, duplicates, image quality, price errors). |
| J | Inventory | **Partial** | Stock per product, low-stock warning under 5 units, admin warehouses, stock movements. | Sellers can't change stock from the Inventory page (it only shows it). Seller warehouses, stock per location, a reorder level the seller sets. |
| K | Logistics | **Partial** | Admin shipping zones, shipping labels, courier and tracking entry by sellers, Shiprocket booking (admin only), returns flow. | Shiprocket for sellers, delivery-time promises, a choice of fulfilment model (seller or marketplace). |
| L | Commission & fees | **Partial** | Commission rates by seller and category with a fixed fee and effective dates, a rate lookup, monthly commission invoices. | Subscription plans, shipping fees charged to sellers, a "what will I earn" calculator for sellers. |
| M | Onboarding workflow | **Partial** | Fixed 6-step wizard; statuses DRAFT, SUBMITTED, PENDING, COMPLETED, plus approved, rejected and suspended dates; save and resume. | Steps that change by seller type, workflow versions, and the separate "verification pending" and "review pending" stages. |
| N | Approval management | **Partial** | Approve, reject (with reason), suspend (wind-down, reason sent), reinstate, verify or reject each document and APOB. Actions are audit-logged. | Hold, escalate, and "request more documents" (sends the seller back to fix one item). An approval history shown on the seller's page. |
| O | Notifications | **Partial** | Email on approval, rejection, suspension and reinstatement. In-app seller notifications. | Emails for "application received", "document rejected, please re-upload" and "store is live". SMS and WhatsApp (need a paid provider). |
| P | Training & guidance | **Partial** | Help centre, FAQs (admin-managed), tooltips in the registration form. | Seller-specific guides, walkthroughs and videos. |
| Q | Dashboards | **Partial** | Seller dashboard (sales, notifications, GST info prompt). Admin approval list. | A seller "getting started" checklist (KYC, bank, store, first product). Risk indicators on the admin queue. |
| R | Security | **Have** | Roles and permissions, MFA, sessions with sign-out per device, audit logs, login rate limits, impersonation logging. | Encryption of Aadhaar and bank numbers (see above). |
| S | Analytics | **Missing** | None for onboarding. | Signup-to-approval conversion, drop-off by step, approval time, document rejection reasons. |

## Duplicates and tidy-ups

- Document storage has two routes: the signature goes through the store's own image service, while KYC documents go to the Cloudinary demo account. Both should use one private store.
- Seller document types are listed in the page code rather than coming from the server, so the admin can't change them.
- `getCurrentUserId()` is copied into several controllers; `AuthorizationService.getCurrentUserId()` already does the same job.

## Recommended order

**Now (privacy, before any real seller):**
1. Move KYC documents to private storage that only the seller and admins can open.
2. Encrypt Aadhaar and bank numbers, show them masked, and keep only the last 4 Aadhaar digits.

**Next (makes approval work day to day):**
3. "Request more documents" and "Hold" in the approval screen, with an email to the seller.
4. "Application received" and "document rejected" emails.
5. A getting-started checklist on the seller dashboard.
6. Seller types: Individual, Brand Owner, Dropshipper.

**Later (when sellers are active):**
7. Seller promotions, stock editing, Shiprocket for sellers.
8. Restricted categories that need a licence.
9. Brand verification.
10. Onboarding analytics.

**Needs a paid outside service (decide when volume justifies the cost):**
real bank verification (penny-drop), live GSTN lookups, SMS and WhatsApp, OCR, risk scoring.
