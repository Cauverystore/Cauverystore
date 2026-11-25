// The server answers in English. This turns the messages a customer can see (sign-in, sign-up,
// OTP, cart, coupons, checkout, orders, returns, profile, contact and feedback forms) into the
// language they picked. The wording lives in the "server" section of each language file.
// Anything not listed here is shown as the server sent it.
//
// When you change one of these messages on the server, change it here too, or it will show in
// English again (nothing breaks).

const EXACT = {
  // Signing in, signing up, passwords, OTP
  "Logged out successfully": "loggedOut",
  "Logged out from all devices": "loggedOutAll",
  "Password changed successfully": "passwordChanged",
  "If an account exists for that email, a reset code has been sent to it.": "resetCodeSent",
  "Password reset successfully": "passwordReset",
  "If an account exists for that email, a reset link has been sent to it.": "resetLinkSent",
  "OTP sent to email": "otpSent",
  "Session revoked": "sessionRevoked",
  "User registered successfully": "registered",
  "Password is required": "passwordRequired",
  "Email already registered": "emailRegistered",
  "Invalid username or password": "invalidLogin",
  "Account is not active": "accountInactive",
  "Access denied. This account does not have the required role.": "wrongAccountType",
  "Invalid request": "invalidRequest",
  "Current password is incorrect": "currentPasswordWrong",
  "Google sign-in is not configured": "googleUnavailable",
  "Invalid Google sign-in token": "googleFailed",
  "Google account email is not verified": "googleNotVerified",
  "Please wait a minute before requesting another code.": "waitCode",
  "Invalid OTP": "invalidOtp",
  "OTP has already been used": "otpUsed",
  "OTP has expired": "otpExpired",
  "Too many incorrect attempts. Please request a new code.": "tooManyOtp",
  "No OTP has been requested for this email": "noOtp",
  "Please wait a minute before requesting another link.": "waitLink",
  "Invalid or expired reset link": "resetLinkInvalid",
  "This reset link has already been used": "resetLinkUsed",
  "This reset link has expired": "resetLinkExpired",
  "Two-factor authentication is already enabled": "twoFactorOn",
  "Two-factor authentication is not enabled for this account": "twoFactorOff",
  "Invalid authenticator code": "invalidAuthCode",
  "Your sign-in has expired. Please enter your password again.": "signInExpired",
  "Email is required": "emailRequired",
  "Full name is required": "nameRequired",
  "New password is required": "newPasswordRequired",
  "OTP is required": "otpRequired",
  "Password must be at least 8 characters": "passwordMin",
  "Please provide a valid email address": "invalidEmail",
  "must be a phone number": "invalidPhone",

  // General
  "An unexpected error occurred": "unexpected",
  "Access denied": "accessDenied",
  "Token expired": "sessionExpired",
  "Product not found": "productNotFound",

  // Contact Us and Feedback forms
  "Please fill in your name, email and message.": "contactFill",
  "Please enter a valid email address.": "invalidEmail",
  "Your message is too long. Please shorten it.": "messageTooLong",
  "Too many messages. Please try again later.": "tooManyMessages",
  "Thanks! We have received your message and will reply by email.": "contactThanks",
  "Please enter your name.": "enterName",
  "Please enter your email address.": "enterEmail",
  "Please write your message.": "writeMessage",
  "Your name is too long. Please shorten it.": "nameTooLong",
  "The photo is too large. Please choose a smaller one.": "photoTooBig",
  "Please attach a JPG, PNG or WebP photo.": "photoType",
  "Thank you! We have received your message.": "feedbackThanks",

  // Newsletter, reviews
  "Already subscribed": "alreadySubscribed",
  "Subscribed successfully": "subscribed",
  "Rating must be 1-5": "ratingRange",
  "Already reviewed": "alreadyReviewed",
  "Review deleted": "reviewDeleted",

  // Profile, addresses, payment methods
  "Your account has been deleted.": "accountDeleted",
  "Address deleted": "addressDeleted",
  "Address not found": "addressNotFound",
  "Access denied: this address does not belong to you": "addressNotYours",
  "Payment method removed": "paymentMethodRemoved",
  "Payment method not found": "paymentMethodNotFound",

  // Cart, wishlist, coupons
  "Quantity must be positive": "quantityMin",
  "Quantity must be at least 1": "quantityMin",
  "That option does not belong to this product": "optionMismatch",
  "Not enough stock available": "notEnoughStock",
  "Not enough stock for updated quantity": "notEnoughStock",
  "Cart item not found": "cartItemNotFound",
  "Product already in wishlist": "alreadyInWishlist",
  "Product not found in wishlist": "notInWishlist",
  "Coupon code is required": "couponRequired",
  "Invalid or expired coupon": "couponInvalid",
  "This coupon is no longer active": "couponInactive",
  "This coupon is not yet valid": "couponNotYet",
  "This coupon has expired": "couponExpired",
  "This coupon has reached its usage limit": "couponLimit",
  "Coupon not found": "couponInvalid",

  // Checkout, payment, orders, returns
  "Cart has no active items": "cartEmpty",
  "Order not found": "orderNotFound",
  "This order has already been shipped and can no longer be cancelled": "alreadyShipped",
  "Cash on delivery is currently unavailable. Please pay online for this order.": "codUnavailable",
  "Cash on delivery is currently unavailable. Please pay online.": "codUnavailable",
  "Online payment is currently unavailable. Please choose cash on delivery.": "onlineUnavailable",
  "Payment has not been verified for this order, so it cannot be placed.": "paymentNotVerified",
  "Paid amount does not match the order total": "paidMismatch",
  "This payment was refunded because no order was placed in time": "paymentRefundedLate",
  "Payment verified successfully": "paymentVerified",
  "Payment verification failed": "paymentFailed",
  "Failed to create Razorpay order": "paymentStartFailed",
  "Payment has not been captured by Razorpay": "paymentNotCaptured",
  "Unable to verify payment with Razorpay": "paymentVerifyUnavailable",
  "This order has already been paid for": "alreadyPaid",
  "A reason is required to request a return": "returnReasonRequired",
  "Return not found": "returnNotFound",
  "This return has no credited amount yet. It has to pass its quality check first.": "returnNotCredited",
};

// Messages with a number or name inside. Each pattern's groups fill the named values in order.
const PATTERNS = [
  [/^Invalid username or password\. (\d+) attempt\(s\) remaining\.$/, "invalidLoginAttempts", ["count"]],
  [/^Account locked due to too many failed attempts\. Try again in (\d+) minute\(s\), or reset your password to unlock it now\.$/, "accountLocked", ["minutes"]],
  [/^Account has been suspended by /, "accountSuspended", []],
  [/^Invalid authenticator code\. (\d+) attempt\(s\) remaining\.$/, "invalidAuthCodeAttempts", ["count"]],
  [/^This address is attached to (\d+) active order\(s\) and cannot be removed\./, "addressInUse", ["count"]],
  [/^Please choose an option \(such as a size\) for (.+)$/, "chooseOption", ["name"]],
  [/^Minimum order amount of ₹(\S+) required for this coupon$/, "couponMin", ["amount"]],
  [/^Insufficient stock for (.+)$/, "insufficientStock", ["name"]],
  [/^Order cannot be cancelled - current status is (\S+)$/, "cannotCancel", ["status"]],
  [/^Cash on delivery is available for orders from Rs (\d+) to Rs (\d+)\. Please pay online for this order\.$/, "codRange", ["min", "max"]],
  [/^Cash on delivery is available for orders from Rs (\d+) upwards\. Please pay online for this order\.$/, "codFrom", ["min"]],
  [/^A bill-to \/ ship-to order needs the buyer's GSTIN\./, "gstinNeeded", []],
  [/^Tell us who is receiving the goods\./, "consigneeNeeded", []],
];

/** Returns the message in the current language, or the original when there is no translation. */
export function translateServerMessage(msg, i18n) {
  if (typeof msg !== "string" || !msg || !i18n || (i18n.language || "en").startsWith("en")) return msg;
  const key = EXACT[msg];
  if (key && i18n.exists(`server.${key}`)) return i18n.t(`server.${key}`);
  for (const [re, k, names] of PATTERNS) {
    const m = msg.match(re);
    if (m && i18n.exists(`server.${k}`)) {
      const values = {};
      names.forEach((n, i) => { values[n] = m[i + 1]; });
      return i18n.t(`server.${k}`, values);
    }
  }
  return msg;
}

// Staff and seller tools stay in English.
const STAFF_AREA = /^\/(admin|super-admin|executive|seller)(\/|$)/;

/**
 * Translates `error` and `message` in a server reply, in place. The English original is kept as
 * `errorEn` / `messageEn` for code that reads the wording (e.g. the attempts-left count on sign-in).
 */
export function translateResponseData(data, i18n) {
  if (!data || typeof data !== "object" || Array.isArray(data)) return;
  if (typeof window !== "undefined" && STAFF_AREA.test(window.location.pathname)) return;
  for (const field of ["error", "message"]) {
    const original = data[field];
    if (typeof original !== "string") continue;
    const translated = translateServerMessage(original, i18n);
    if (translated !== original) {
      data[`${field}En`] = original;
      data[field] = translated;
    }
  }
}
