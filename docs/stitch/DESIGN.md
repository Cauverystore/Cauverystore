# Design System: Cauvery Store (cauverystore.in)

## 1. Visual Theme & Atmosphere
A calm, well-organised Indian online store for clothing and home goods. The mood is
quiet confidence: light mint and sage surfaces, deep forest-green structure, and a single touch of
muted gold. Think of a tidy, well-lit textile shop where every shelf is labelled.
- Density 5 ("Daily App Balanced") — enough products per screen to browse quickly, never cramped.
- Variance 6 ("Offset Asymmetric") — split and left-aligned layouts, not everything centred.
- Motion 4 ("Fluid CSS") — soft fades and lifts, nothing flashy.
Voice is a plain online store: clear product names, prices in rupees (₹) with GST included.

## 2. Color Palette & Roles
- **Light Mint Canvas** (#F1F7F2) — Page background
- **Soft Sage** (#E6F0E8) — Category row, category strip, info bands, filter column, order summary, mobile tab bar
- **Pure Surface** (#FFFFFF) — Product cards, panels, inputs
- **Forest Green** (#1F6B45) — Brand colour: primary buttons, links, active states, icons, focus rings
- **Pressed Green** (#17583A) — Hover/pressed state of Forest Green
- **Deep Green** (#143D2B) — Footer background (cream text)
- **Muted Gold** (#C8A24B) — Sparing highlight only: sale tag, rating stars, small dividers. Never a button fill
- **Charcoal Ink** (#1E293B) — Main text and prices
- **Slate Grey** (#64748B) — Secondary text, metadata, "Inclusive of GST" notes
- **Soft Border** (#E2E8F0) — 1px lines between sections and around inputs
- **Success Green** (#2F7D4F) — "In stock", order confirmed
- **Error Brick** (#B4432F) — Form errors, "Out of stock"
No pure black. No purple, no neon, no gradient text.

## 3. Typography Rules
- **Display / Headings:** Outfit, weights 600–700, tight letter-spacing (-0.01em). Hierarchy by weight and colour, not huge size. Page title max 40px desktop, 28px mobile.
- **Body:** Outfit 400, 16px, line-height 1.6, max 65 characters per line.
- **Prices:** Outfit 600, Charcoal Ink, with tabular numbers. Old price struck through in Slate Grey.
- **Small labels:** 12–13px uppercase with 0.06em letter-spacing for category tags.
- **Banned:** Inter, Times New Roman, Georgia, Garamond.

## 4. Component Stylings
* **Header:** White bar, logo left, search bar centre (wide, rounded 12px), account / wishlist / cart icons right with a small green count badge on the cart. Category links in a thin row underneath.
* **Buttons:** Primary = Forest Green fill, white text, 12px radius, 48px tall. Secondary = white with 1px green outline. Pressed state moves down 1px. No glows.
* **Product cards:** White, 16px radius, soft shadow tinted cream. Square image on top (light grey backdrop), then product name (2 lines max), price row ("₹1,299" + struck old price), small gold stars with review count. Wishlist heart top-right of image. "Add to cart" appears as a full-width green button at the bottom of the card.
* **Badges:** Small pill tags — gold for "Sale", green outline for "New", brick for "Out of stock".
* **Inputs:** Label above, 1px Soft Border, 12px radius, 48px tall, green focus ring. Error text below in Error Brick.
* **Loaders:** Grey shimmer blocks shaped like the cards. No spinners.
* **Empty states:** Simple line illustration plus one sentence and one button (e.g. empty cart → "Your cart is empty" + "Continue shopping").
* **Footer:** Deep Green background, cream text, 4 link columns (Shop, Help, Policies, Company), payment logos row (UPI, cards, net banking), "Made in Tamil Nadu" note.

## 5. Layout Principles
- Max content width 1280px, centred, 24px side padding (16px on mobile).
- Home hero: split screen — left side headline, one-line description and one green button; right side a large product photo. Not centred.
- Category rows: horizontal-scroll strips or 2-column zig-zag, not three equal boxes.
- Product grid: 4 columns desktop, 3 tablet, 2 mobile.
- Product page: images left (big main image + thumbnails), details right (name, price, "Inclusive of all taxes", size selector chips, quantity, Add to cart + Buy now, delivery pincode check, return policy note).
- Below 768px everything collapses to one column; bottom sticky bar on product page with price + Add to cart. Touch targets at least 44px. No sideways scrolling of the page.

## 6. Motion & Interaction
- Cards lift 2px with a slightly deeper shadow on hover.
- Product grids fade in one after another (40ms stagger).
- Add to cart: cart badge does a short bounce; small toast "Added to cart" bottom-centre.
- Only transform and opacity animate.

## 7. Anti-Patterns (Banned)
- No emojis
- No Inter font, no generic serif fonts
- No pure black (#000000)
- No neon or purple glows, no gradient headline text
- No three equal feature cards in a row
- No "Elevate", "Seamless", "Unleash", "Next-gen" or similar copy
- No "independent sellers", "real shops" or marketplace story copy — plain store voice
- No fake numbers ("10,000+ happy customers", "99% satisfaction")
- No "Scroll to explore" text or bouncing arrows
- No placeholder names like "John Doe" or "Acme"
