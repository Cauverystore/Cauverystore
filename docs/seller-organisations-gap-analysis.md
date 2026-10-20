# Seller organisations, roles and security: gap analysis

Audit of the existing code against the "multi-seller authentication and organisation" request, 20 October 2026.
(The request pasted in started at "Role APIs" and Phase 14, so the earlier phases it refers to were not included.)

## What already exists

| Area | Status | Where |
|---|---|---|
| Seller login | **Have** | `SellerLogin.jsx`, `/api/auth/login` with role `seller` |
| Seller registration | **Have** | 6-step wizard `SellerRegistration.jsx`, `/api/seller-registration/*` (KYC made private on 2026-10-20) |
| Roles | **Partial** | Fixed list in code: `Role` enum (CUSTOMER, SELLER, ADMIN, SUPER_ADMIN, EXECUTIVE). Admins can change a user's role. No way to create roles. |
| Permissions | **Partial, not enforced** | `Permission` and `RolePermission` tables, `GET /api/super-admin/permissions`, `GET/PUT /api/super-admin/role-permissions`, and a Super Admin "Permissions" screen. **Nothing in the backend reads these**: access is decided by role checks written in each controller, so changing a tick box on that screen changes nothing. |
| MFA | **Have** | Authenticator-app (TOTP) setup with QR link, confirm and disable, in Profile → Security. Secret stored encrypted. |
| Sessions / devices | **Have** | List sessions, sign out one device or all (`/api/auth/sessions`), Profile → Security |
| Audit logging | **Have** | `AuditService` and the Audit Logs admin page |
| Seller organisations (several people in one seller account) | **Missing** | Every product, order, payout, invoice and licence is tied to one seller *user* (`seller_id` = that person's user id). |
| Invitations | **Missing** | No invite tokens or invite emails. |
| Multi-store per seller | **Missing** | One `SellerStore` per seller. |
| Custom roles (`POST/PUT/DELETE /roles`) | **Missing** | |
| Public `GET /permissions` | **Partial** | Exists under `/api/super-admin/permissions` (super admin only). |

## The decision that shapes everything

Today a seller account is one person. To let a seller add staff (an orders person, a catalogue person, an accountant), there are two ways:

1. **Team members on the existing seller account (recommended).** The seller user stays the owner, and everything keeps its current `seller_id`. Staff are separate logins linked to that owner with a seller-team role (Manager, Catalogue, Orders, Finance). When a staff member signs in, the server works out the owner and acts for that seller, limited by their role. No existing data moves and nothing in orders, GST invoices or payouts changes.
2. **A full "organisation" table that owns everything.** Every table that has `seller_id` (14 of them, including GST invoices, credit notes and TCS records) would move to an organisation id. This is a large data migration on live tax records, with a real risk of breaking invoices. It only pays off at a scale the store isn't at yet.

## Proposed phases

**Phase 1: make permissions honest (small).**
Either wire the Permissions screen into the backend checks, or relabel it as "reference only" until it is. Wiring it in carelessly could lock admins out, so it needs care either way.

**Phase 2: seller team members and invitations (main piece).**
- `seller_team_members` table (owner seller id, user id, team role, status) and `seller_invitations` (email, role, single-use token, expiry 7 days).
- The owner invites by email (sent through the existing Resend email). The invitee sets a password or signs in, and the invite links them.
- Team roles: Owner (everything), Manager (everything except bank, payouts and team), Catalogue (products, inventory), Orders (orders, returns, shipping), Finance (payouts, GST invoices, read-only orders).
- One place in the server works out "which seller is this person acting for, and may they do this", replacing the `getCurrentSellerId()` copied into several controllers.
- Screens: Team page (invite, change role, remove), invite-acceptance page.
- Every invite, join, role change and removal is audit-logged; owners can require team members to use MFA.

**Phase 3: custom roles for seller teams.**
`POST/PUT/DELETE /api/seller/team/roles` and `GET /api/seller/team/permissions`, so an owner can make roles beyond the five built-in ones.

**Later, if needed:** several stores per seller, and a full organisation model.

## Not needed / already covered
- A new MFA wizard and security settings screen: these exist in Profile → Security. They could be added to the seller panel menu so sellers find them.
- Session and device tracking: exists.
