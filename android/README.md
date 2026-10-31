# Cauvery Store — Android app

A native Android shopping app for [cauverystore.in](https://cauverystore.in), written in
Kotlin with Jetpack Compose. It talks to the same Spring Boot backend as the website
(`https://cauvery-store-backend-production.up.railway.app/api/...`), so products, accounts,
carts and orders are shared with the site.

It replaces the earlier website wrapper in `frontend/android`: it uses the same app ID,
`in.cauverystore.app`, with a higher version code, so phones that have the wrapper get this
app as an update. (For a Play Store update it must be signed with the same key as the
wrapper was.)

## What v1 does

- Home (search box, categories, featured and all products), categories, search and
  product lists, product details with photos
- Sign in with email and password (with the authenticator-code step for accounts that use
  2-step sign-in), create account, sign out
- Cart: change quantity, remove, totals with free delivery from ₹500
- Checkout: saved or new address, GST worked out by the server, payment with the Razorpay
  Android SDK (UPI, cards, net banking, wallets; cash on delivery is hidden)
- My orders and order details
- English, Tamil, Telugu, Hindi, Kannada and Malayalam, chosen in Account → Language
  (or follows the phone's language)
- Empty states for when the store has no products yet

Not in v1: Google sign-in, wishlist, reviews, returns, coupons, B2B/GSTIN checkout,
push notifications.

## Building

You need Android Studio (for the SDK) and JDK 17. The JDK bundled with current Android Studio
is Java 25, which this Gradle version does not run on, so point `JAVA_HOME` at a JDK 17
(this PC has one at `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`).

`android/local.properties` (not committed) must say where the SDK is:

```
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
```

Then, from the `android` folder:

```bash
./gradlew assembleDebug
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.
Install it on a running emulator or a phone with USB debugging on:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Opening in Android Studio

File → Open → choose the `android` folder (not the repo root). In Settings → Build, Execution,
Deployment → Build Tools → Gradle, set *Gradle JDK* to JDK 17. Then pick an emulator and press Run.

### Release builds

Release signing reads `android/keystore.properties`, which is never committed:

```
storeFile=../path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Then `./gradlew bundleRelease` makes the `.aab` for the Play Store.

## How it fits together

| Folder | What's in it |
| --- | --- |
| `data/Api.kt` | Every backend endpoint the app calls |
| `data/Models.kt` | The JSON shapes it reads and sends |
| `data/Session.kt` | Who is signed in; keeps the refresh-token cookie |
| `data/Network.kt` | HTTP client, adds the sign-in token, renews it when it expires |
| `ui/*Screens.kt` | The screens |
| `res/values-*/strings.xml` | Text in each language |

Sign-in works like the website: the backend returns a short-lived access token and sets the
refresh token as a cookie. The app keeps that cookie in its private storage and swaps it for a
new access token when a request comes back 401.

Payment follows the website's checkout exactly: `POST /api/payment/create` gives the Razorpay
order and key → Razorpay checkout → `POST /api/payment/verify` → `POST /api/orders/place`.
The app never holds any Razorpay secret; the key comes from the backend, so whether payments
are test or live depends on the backend's Razorpay settings.

## Known limits

- If Android closes the app while the Razorpay screen is open (rare, low-memory phones), the
  app can't finish placing the order; the backend's existing job refunds payments that never
  became orders.
- Images and prices come straight from the backend; nothing is cached for offline use.
