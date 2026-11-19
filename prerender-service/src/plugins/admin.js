// The dashboard (/dashboard) and a small API for scripts.
//
// Sign in on the page with the password DASHBOARD_PASSWORD (or, if that is not set,
// PRERENDER_TOKEN); you stay signed in for 7 days. Scripts can send the
// X-Prerender-Token header (or HTTP Basic auth with the password) instead.
//   POST /api/login     {"password": "..."}      POST /api/logout
//   GET  /api/status                 everything the dashboard shows
//   GET  /api/cache?q=text           saved pages (newest first)
//   POST /api/recache   {"url": "https://example.com/page"}   render one page again now
//   POST /api/recache-all            render every sitemap page again now
//   POST /api/forget    {"url": "..."}   drop one saved page
//   POST /api/clear                  drop all saved pages
//   POST /api/test-alert             send a test alert
// DASHBOARD_DISABLED=true switches all of this off.
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const config = require("../config");
const alerts = require("../alerts");
const selfRender = require("../selfRender");
const warmup = require("../warmup");
const cache = require("./cache");
const queue = require("./queue");
const stats = require("./stats");
const { version } = require("../../package.json");

const page = fs.readFileSync(path.join(__dirname, "..", "dashboard.html"), "utf8");
const loginPage = fs.readFileSync(path.join(__dirname, "..", "login.html"), "utf8");
const sessions = new Map(); // id -> expires
const SESSION_MS = 7 * 24 * 3600 * 1000;
const COOKIE = "cauvery_prerender_session";
const failedLogins = new Map(); // ip -> { count, since }
const LOGIN_WINDOW_MS = 10 * 60000;
const LOGIN_LIMIT = 10;

const same = (a, b) => {
  const x = Buffer.from(String(a));
  const y = Buffer.from(String(b));
  return x.length > 0 && x.length === y.length && crypto.timingSafeEqual(x, y);
};

function sessionOf(req) {
  const pair = String(req.headers.cookie || "").split(";").map((c) => c.trim()).find((c) => c.startsWith(`${COOKIE}=`));
  const id = pair ? pair.slice(COOKIE.length + 1) : "";
  if (!/^[a-f0-9]{64}$/.test(id)) return null;
  const expires = sessions.get(id);
  if (!expires || expires < Date.now()) { sessions.delete(id); return null; }
  return id;
}

function cookie(req, value, maxAgeSeconds) {
  const secure = String(req.headers["x-forwarded-proto"] || "").includes("https") || req.secure;
  return `${COOKIE}=${value}; Path=/; HttpOnly; SameSite=Strict; Max-Age=${maxAgeSeconds}${secure ? "; Secure" : ""}`;
}

function signedIn(req) {
  if (sessionOf(req)) return true;
  if (same(req.headers["x-prerender-token"] || "", config.token)) return true;
  const m = String(req.headers.authorization || "").match(/^Basic\s+(.+)$/i);
  if (!m) return false;
  const decoded = Buffer.from(m[1], "base64").toString("utf8");
  return same(decoded.slice(decoded.indexOf(":") + 1), config.dashboardPassword);
}

function json(res, status, body) {
  res.setHeader("Cache-Control", "no-store");
  // Sent as text; "<" is escaped so page addresses can never become markup.
  return res.send(status, JSON.stringify(body).replace(/</g, "\\u003c"));
}

function allowedUrl(u) {
  try {
    const parsed = new URL(String(u));
    return /^https?:$/.test(parsed.protocol) && config.domains.includes(parsed.hostname.toLowerCase()) ? parsed.href : null;
  } catch {
    return null;
  }
}

function recacheOne(url) {
  const devices = config.splitDevices ? ["desktop", "mobile"] : ["desktop"];
  for (const device of devices) {
    selfRender(url, { device, source: "dashboard" })
      .catch((err) => console.warn(`[dashboard] recache failed for ${url}: ${err.message}`));
  }
}

module.exports = {
  requestReceived(req, res, next) {
    const p = req.path || "";
    const isAdmin = p === "/" || p === "/dashboard" || p.startsWith("/api/");
    if (!isAdmin) return next();
    req.prerender.internal = true;
    if (config.dashboardDisabled) return res.send(404, "Not found");

    const ip = req.ip || (req.socket && req.socket.remoteAddress) || "?";
    const tries = failedLogins.get(ip);
    if (tries && Date.now() - tries.since > LOGIN_WINDOW_MS) failedLogins.delete(ip);
    const tooMany = () => (failedLogins.get(ip) || { count: 0 }).count >= LOGIN_LIMIT;
    const countFailure = () => {
      const t = failedLogins.get(ip) || { count: 0, since: Date.now() };
      t.count++;
      failedLogins.set(ip, t);
    };
    const body = req.body && typeof req.body === "object" ? req.body : {};
    const post = req.method === "POST";

    if (p === "/api/login" && post) {
      if (tooMany()) return json(res, 429, { error: "Too many wrong passwords. Try again in 10 minutes." });
      if (!same(body.password || "", config.dashboardPassword)) {
        countFailure();
        return json(res, 401, { error: "Wrong password." });
      }
      failedLogins.delete(ip);
      const id = crypto.randomBytes(32).toString("hex");
      sessions.set(id, Date.now() + SESSION_MS);
      for (const [k, exp] of sessions) if (exp < Date.now()) sessions.delete(k);
      res.setHeader("Set-Cookie", cookie(req, id, SESSION_MS / 1000));
      return json(res, 200, { ok: true });
    }
    if (p === "/api/logout") {
      const id = sessionOf(req);
      if (id) sessions.delete(id);
      res.setHeader("Set-Cookie", cookie(req, "", 0));
      return json(res, 200, { ok: true });
    }

    if (!signedIn(req)) {
      if (req.headers.authorization || req.headers["x-prerender-token"]) {
        if (tooMany()) return json(res, 429, { error: "Too many wrong passwords. Try again in 10 minutes." });
        countFailure();
      }
      if (p === "/" || p === "/dashboard") {
        res.setHeader("Cache-Control", "no-store");
        res.setHeader("X-Frame-Options", "DENY");
        return res.send(200, loginPage);
      }
      return json(res, 401, { error: "Sign in first." });
    }
    failedLogins.delete(ip);

    if (p === "/" || p === "/dashboard") {
      res.setHeader("Cache-Control", "no-store");
      res.setHeader("X-Frame-Options", "DENY");
      res.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:");
      return res.send(200, page);
    }

    switch (p) {
      case "/api/status": {
        const mem = process.memoryUsage();
        return json(res, 200, {
          version,
          domains: config.domains,
          now: new Date().toISOString(),
          settings: {
            cacheHours: config.cacheHours, cacheMaxPages: config.cacheMaxPages, separateMobile: config.splitDevices,
            maxConcurrentRenders: config.maxConcurrentRenders, warmEveryHours: config.warmEveryHours,
            pageLoadTimeoutSeconds: config.pageLoadTimeoutMs / 1000,
          },
          memoryMB: Math.round(mem.rss / 1048576),
          stats: stats.snapshot(),
          cache: cache.summary(),
          queue: queue.status(),
          warmup: warmup.status(),
          alerts: { configured: alerts.configured(), history: alerts.history },
        });
      }
      case "/api/cache":
        return json(res, 200, cache.list(String(req.query.q || ""), 500));
      case "/api/recache": {
        if (!post) break;
        const url = allowedUrl(body.url);
        if (!url) return json(res, 400, { error: `Give a full address on ${config.domains.join(" or ")}` });
        recacheOne(url);
        return json(res, 202, { ok: true, message: `Rendering ${url} again now.` });
      }
      case "/api/recache-all": {
        if (!post) break;
        if (warmup.status().running) return json(res, 409, { error: "Pre-loading is already running." });
        warmup.runNow("dashboard");
        return json(res, 202, { ok: true, message: "Rendering every sitemap page again." });
      }
      case "/api/forget": {
        if (!post) break;
        const url = allowedUrl(body.url);
        if (!url) return json(res, 400, { error: "Give the full page address." });
        return json(res, 200, { ok: true, removed: cache.forget(url) });
      }
      case "/api/clear":
        if (!post) break;
        return json(res, 200, { ok: true, removed: cache.clear() });
      case "/api/test-alert": {
        if (!post) break;
        if (!alerts.configured()) return json(res, 400, { error: "No alert target is set (ALERT_WEBHOOK_URL or TELEGRAM_BOT_TOKEN + TELEGRAM_CHAT_ID)." });
        return alerts.send("test", "test alert from the dashboard. Alerts are working.", { force: true })
          .then((ok) => json(res, ok ? 200 : 502, ok ? { ok: true } : { error: "The alert could not be delivered; check the address." }));
      }
      default:
        return json(res, 404, { error: "Unknown address" });
    }
    return json(res, 405, { error: "Use POST" });
  },
};
