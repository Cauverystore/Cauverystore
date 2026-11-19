// Renders a phone-sized page for mobile crawlers (Googlebot Smartphone, WhatsApp on
// phones...) and a desktop-sized page for the rest, and keeps them apart in the cache.
// Websites that show different content on phones then give each crawler the right one.
// SEPARATE_MOBILE=false renders everything at desktop size.
const config = require("../config");

const MOBILE = /Mobile|Android|iPhone|iPad|iPod|Opera Mini|IEMobile|WhatsApp/i;
const MOBILE_UA =
  "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
  "Chrome/130.0.0.0 Mobile Safari/537.36 Prerender (CauveryPrerender)";

function pickDevice(req) {
  if (!config.splitDevices) return "desktop";
  const asked = String(req.headers["x-prerender-device"] || "").toLowerCase();
  if (asked === "mobile" || asked === "desktop") return asked;
  return MOBILE.test(String(req.headers["user-agent"] || "")) ? "mobile" : "desktop";
}

module.exports = {
  requestReceived(req, res, next) {
    const device = pickDevice(req);
    req.prerender.device = device;
    if (device === "mobile") {
      req.prerender.width = 412;
      req.prerender.height = 915;
      // Keeps the word "Prerender" so the website's connector never loops back here.
      req.prerender.userAgent = MOBILE_UA;
    }
    return next();
  },

  tabCreated(req, res, next) {
    if (req.prerender.device !== "mobile") return next();
    const tab = req.prerender.tab;
    Promise.resolve(tab.Emulation && tab.Emulation.setTouchEmulationEnabled({ enabled: true, maxTouchPoints: 5 }))
      .catch(() => {})
      .finally(next);
  },
};
