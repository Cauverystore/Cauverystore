# Cauvery Store: GST runbook

Rates, due dates and thresholds change. Confirm every figure on gst.gov.in or cbic-gst.gov.in before acting. This runbook says what to do and where; the portal says what the numbers are. Not tax advice: have a CA review the first few months.

## Once, before real sales

1. **Remove the demo accounts' public passwords.** Sign in as super admin and change the passwords of admin, customer, executive and seller @cauverystore.in, or delete the accounts you do not use.
2. **Suspend the demo seller** (seller@cauverystore.in, ID 46). Its GSTIN `33ABCDE1234F1Z5` fails the check digit. Use Admin > Seller Approvals > Suspend (this also takes its listings off sale). Check the backend log after deploy: a line starting "GST: approved seller" names any other seller with a bad or missing GSTIN.
3. **Register the marketplace's own GST and TCS number.** Set the TCS GSTIN in the GST configuration. Only that registration gets a GSTR-8 deadline.
4. **Decide on a live GST provider (GSP).** Until then portal checks are simulated: `gstn.simulated=true`, so GSTIN verification, GSTR-2B and e-invoice (IRN) are not backed by the portal.
5. **Ask your CA** about the marketplace's own registration, TCS, and whether e-invoicing applies at your turnover.

## Approving a new seller (every time)

1. Open the seller's GSTIN on the GST portal (Search Taxpayer). Confirm: status Active, legal or trade name matches the application, state matches the first two digits.
2. Only then approve. The app checks format and check digit, not the portal.
3. Never approve a seller whose GSTIN shows Cancelled or Suspended.

## Each month

| When | Do |
|---|---|
| Start of month | Open the GST compliance screen, sync deadlines for the new period. Treat the due dates as a guide: confirm on the portal (QRMP, extensions and holidays are not modelled). |
| Before GSTR-1 due date | Export GSTR-1 for the previous month. Check totals tie to the sales register, credit notes are included, and place of supply looks right. |
| Before GSTR-3B due date | Review the GSTR-3B data. Note the ITC block shows credit on sales invoices, not credit you can claim on purchases; get purchase ITC from your CA or accounting tool and GSTR-2B. |
| Marketplace operator only | Prepare GSTR-8 (TCS collected). Confirm the TCS rate on the portal. |
| After filing | Mark the deadline filed in the app and keep the portal acknowledgement. |

## Each year

- GSTR-9 and 9C (if applicable) are due 31 December after the financial year ends in March. Confirm on the portal.
- Review the HSN rate table against any notification changes.

## When something is rejected at checkout

| Message | Cause | Fix |
|---|---|---|
| Seller's GST registration state is not recorded | Seller has no GSTIN saved | Seller saves GSTIN in GST settings |
| Cannot resolve a rate | The product's HSN has no active rate row | Admin adds or corrects the rate; there is no fallback rate on purpose |
| GSTIN has a mistyped character | Buyer GSTIN fails the check digit | Buyer rechecks the number |
