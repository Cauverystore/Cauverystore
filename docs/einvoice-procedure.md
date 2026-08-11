# E-Invoice: Creation and Processing Procedure

Reference for how e-invoicing works under GST, compiled from the official NIC
e-Invoice API Developer's Portal (https://einv-apisandbox.nic.in) and the GSTN
documents published there. This is the IRP/e-invoice process only - it feeds
GSTR-1; monthly return filing (GSTR-1/GSTR-3B) is a separate step done on the
GST portal or returns software.

## What an e-invoice is

Each invoice uploaded to the e-invoice system gets a 64-character **IRN**
(Invoice Reference Number), unique across the whole GST system:

- IRN = SHA-256 hash of `Supplier GSTIN + Financial Year + Doc Type + Doc Number`
- Financial year runs 01-Apr to 31-Mar, rendered `YYYY-YY` (e.g. a 03-01-2020
  invoice belongs to FY `2019-20`).
- Document types: `INV` (invoice), `CRN` (credit note), `DBN` (debit note).
- Document number: max 16 alphanumeric + `/` and `-`, and must not start with
  `0`, `/` or `-`. Lowercase letters are rejected.
- The IRP also returns a digitally signed invoice (JWT/JWS, SHA256RSA) and a
  signed QR code to print on the invoice.

Example hash input:
`01AAAAA9999A19N2019-20INVABC01234`

## Who needs it (read this first)

E-invoicing is **not** required for every seller:

- Compulsory only above the aggregate-turnover threshold (Rs 5 crore since
  01-08-2023) and only for B2B / export / SEZ invoices.
- B2C retail invoices are explicitly excluded - the API should NOT request an
  IRN for them ("Business to Consumer (B2C) invoices will not be considered").
- Below the threshold, the e-invoice integration should simply stay off.

## Sandbox vs production

| | Sandbox (testing) | Production |
|---|---|---|
| Portal | https://einv-apisandbox.nic.in | https://einvoice1.gst.gov.in |
| API base | https://einv-apisandbox.nic.in | https://einvapi.gst.gov.in |
| Credentials | Self-register with GSTIN + OTP | After boarding (sandbox test report evaluated, public IPs whitelisted) |
| Auth token valid | 60 minutes | 360 minutes |
| IRNs | Test, disposable | Legal |

Registration on the sandbox requires a real, active GSTIN with e-invoicing
enabled and access to the authorised signatory's registered mobile/email (for
the OTP).

## Step-by-step flow

### Step 1 - Register on the sandbox (once)

1. Go to https://einv-apisandbox.nic.in, click Login, then "Register Here".
2. Enter the GSTIN (trade name auto-fills), the authorised signatory's mobile
   and email as registered with GSTN.
3. Verify the OTP.
4. You now have `client_id`, `client_secret`, `username`, `password` and the IRP
   public key. The public key is also published at
   https://www.gst.gov.in/download/IRP_PUBLIC_KEY_PROD (base64 DER) - never
   trust a key from anywhere else.

### Step 2 - Authenticate

`POST {base}/eivital/v1.04/auth` (sandbox: `https://einv-apisandbox.nic.in/eivital/v1.04/auth`)

- Headers: `client_id`, `client_secret`, `Gstin`.
- Body `Data` = `{UserName, Password, AppKey (random 32-byte array, base64 => 44 chars), ForceRefreshAccessToken}` -> base64 -> **RSA-encrypted with the IRP public key**.
- Response `Data` (AES-encrypted with your AppKey) contains `AuthToken`,
  `Sek` (AES-256 session key), `TokenExpiry`.
- Token is valid 360 min on production, 60 min on sandbox; re-authenticate on
  expiry. Auth tokens within validity return the same token (time is not reset).

### Step 3 - Generate IRN

`POST {base}/eicore/v1.03/Invoice` (sandbox: `https://einv-apisandbox.nic.in/eicore/v1.03/Invoice`)

- Headers: `client_id`, `client_secret`, `Gstin`, `user_name`, `AuthToken`.
- Body `Data` = full invoice JSON (schema version `1.1`) -> base64 -> **AES-encrypted with SEK**.
- Response `Data` (AES-decrypted with SEK) contains:
  - `Irn`, `AckNo`, `AckDt`
  - `SignedInvoice` (JWT) - the official e-invoice
  - `SignedQRCode` (JWT) - the QR to print/attach
  - `Status` ACT/CNL; `EwbNo`/`EwbDt`/`EwbValidTill` if an e-waybill was generated
- Failures come back as `ErrorDetails` (array of `ErrorCode`/`ErrorMessage`).

### Step 4 - After the IRN

- Print/attach the signed QR code; buyers verify via the GST verify app.
- Cancellation within the allowed window: `POST {base}/eicore/v1.03/Invoice/Cancel`.
- Report the B2B/export invoices in GSTR-1 and file returns as usual.

## Invoice JSON (payload) essentials

Mandatory blocks: `Version` ("1.1"), `TranDtls`, `DocDtls`, `SellerDtls`,
`BuyerDtls`, `ItemList`, `ValDtls`.

- `TranDtls`: `TaxSch` = "GST"; `SupTyp` in {B2B, SEZWP, SEZWOP, EXPWP, EXPWOP, DEXP}.
- `SellerDtls`: `Gstin`, `LglNm`, `Addr1`, `Loc`, `Pin`, `Stcd` required.
- `BuyerDtls`: `Gstin` (or `URP` for exports), `LglNm`, `Pos`, `Addr1`, `Loc`,
  `Stcd` required. `Pos` 96 = place of supply outside India.
- `ItemList` (mandatory): each item needs `SlNo`, `IsServc` (Y/N),
  `HsnCd` (4/6/8 digit, or SAC for services), `UnitPrice`, `TotAmt`,
  `AssAmt`, `GstRt` (combined rate; IGST for inter-state), `TotItemVal`.
- `TotItemVal = AssAmt + CGST + SGST + IGST + Cess + StateCess + OtherCharges`.
- `ValDtls`: `AssVal` and `TotInvVal` required.

### Validation highlights (from the portal)

- Request JSON must validate against the notified schema; `Version` = "1.1".
- IRN is generated by the system - never passed in the request.
- IRN only for an active supplier; cancelled/suspended suppliers are refused.
- No IRN for B2C invoices.
- Document date must be >= 01/04/2025 (older invoices rejected).
- Duplicates rejected: no second IRN for the same GSTIN + FY + doc type + doc
  number, and no re-generation for a cancelled IRN.
- Reverse-charge (B2B/SEZ only) still requires the supplier to generate the IRN.
- Direct export: buyer GSTIN `URP`, state code 96, PIN 999999, POS 96.

## How this repo implements it

- `backend-java/.../client/LiveGstnClient.java` - auth handshake, AES/RSA
  encryption, `buildInvoicePayload` (implements the ItemList/ValDtls rules
  above), `isConfigured()` gate.
- `backend-java/.../client/SimulatedGstnClient.java` - default; generates local
  `SIM...` IRNs for development (active while `gstn.simulated=true`).
- `backend-java/.../service/GstComplianceReadinessService.java` - exposes the
  INACTIVE / MISCONFIGURED / ACTIVE state at `/api/admin/gst/readiness`.
- `backend-java/src/main/resources/application.properties` - activation
  checklist and sandbox base-URL mapping (see `gstn.*`).
- `backend-java/.../util/GstComplianceUtil.java` - `requiresEInvoice(...)`,
  invoice-number validation, supply-type classification.

Never enable the live client until a sandbox run succeeds first:
`LiveGstnClient` was written from the API spec and has not made a real call yet.
