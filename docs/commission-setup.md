# Seller commission: setup

The tiers, the price floor, the flat handling fee and the launch offer are built into the
admin commission API. All calls need an admin login (`/api/admin/commission/...`).

## The policy

| Tier | Commission |
| --- | --- |
| ESSENTIALS | 0% |
| STANDARD | 10% |
| HIGH_VALUE | 20% on items priced Rs 1,000 or more, 10% on cheaper items |

Every order also pays a flat Rs 10 handling fee, whatever the tier. The figures live in
`util/CommissionPolicy.java`. Change them there and in the seller terms together.

## First-time setup (nothing is billed until a rate exists)

1. Set a default so no order goes unbilled:
   `POST /api/admin/commission/rates/tier` with `{"tier": "STANDARD"}`
2. Set each category's tier, using the category id from `/api/categories`:
   `POST /api/admin/commission/rates/tier` with `{"tier": "ESSENTIALS", "categoryId": 3}`
3. Give each new seller the launch offer (first 50 sellers, 60 days at 0%, handling fee still
   applies; their category rate takes over by itself afterwards):
   `POST /api/admin/commission/launch-offer?sellerId=12`

A rate is never edited in place. To change one, close it with
`POST /rates/{id}/close` and add the new one.

## Billing and paying

- `GET /settlement-preview?sellerId=12&period=102026` shows, order by order, the commission,
  handling fee, Razorpay gateway and transfer fees, what the seller nets, and the first day they
  can be paid (8 days after delivery). It records nothing. The period is `MMyyyy`.
- `POST /invoices/generate-period?period=102026` raises the monthly commission invoices as
  before. The percentage on each line is now weighted across the items in the order.
- The preview is before tax collected at source (1%) and refunds. Record the actual payment
  with the existing seller payout screen.

## What this does not do

- It does not take payment fees out of Razorpay itself: Razorpay charges them to your account
  balance, so pay sellers the "sellerNet" figure to recover them.
- It does not decide GST on the handling fee. The fee is billed inside the commission invoice,
  which already carries GST; confirm that treatment with your CA.
