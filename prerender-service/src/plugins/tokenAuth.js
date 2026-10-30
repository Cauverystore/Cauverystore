// Only callers that know PRERENDER_TOKEN (our Vercel proxy) may use the service,
// so nobody else can make it render pages on our bill.
const crypto = require("crypto");

const expected = Buffer.from(process.env.PRERENDER_TOKEN || "");

module.exports = {
  requestReceived(req, res, next) {
    const given = Buffer.from(String(req.headers["x-prerender-token"] || ""));
    const ok = expected.length > 0 && given.length === expected.length && crypto.timingSafeEqual(given, expected);
    if (!ok) return res.send(401, "Unauthorized");
    return next();
  },
};
