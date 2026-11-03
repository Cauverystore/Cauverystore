// All settings in one place. They come from environment variables, or from a
// settings.env file next to package.json (KEY=VALUE lines). Environment variables win.
const fs = require("fs");
const os = require("os");
const path = require("path");

loadSettingsFile(path.join(__dirname, "..", "settings.env"));

const env = process.env;
const num = (name, fallback) => {
  const v = Number(env[name]);
  return env[name] !== undefined && env[name] !== "" && Number.isFinite(v) ? v : fallback;
};
const bool = (name, fallback = false) =>
  env[name] === undefined || env[name] === "" ? fallback : /^(1|true|yes|on)$/i.test(env[name]);

// Cauvery Store default, so the live service keeps working without the variable.
const domains = String(env.ALLOWED_DOMAINS || "cauverystore.in,www.cauverystore.in")
  .split(",")
  .map((d) => d.trim().toLowerCase())
  .filter(Boolean);

// Saved pages go on disk so they survive restarts. On Railway, add a Volume
// mounted at /data and it is used automatically.
const defaultCacheDir = fs.existsSync("/data") ? "/data/cauvery-prerender" : path.join(__dirname, "..", "cache");

const config = {
  token: env.PRERENDER_TOKEN || "",
  domains,
  port: num("PORT", 3000),
  debugPort: num("BROWSER_DEBUGGING_PORT", 9222),
  chromeLocation: env.CHROME_LOCATION || "",
  logRequests: bool("LOG_REQUESTS"),

  // Rendering
  pageLoadTimeoutMs: num("PAGE_LOAD_TIMEOUT_SECONDS", 20) * 1000,
  waitAfterLastRequestMs: num("WAIT_AFTER_LAST_REQUEST_MS", 500),
  maxConcurrentRenders: Math.max(1, num("MAX_CONCURRENT_RENDERS", 2)),
  maxQueueWaitMs: num("MAX_QUEUE_WAIT_SECONDS", 15) * 1000,
  maxQueueLength: num("MAX_QUEUE_LENGTH", 50),
  splitDevices: bool("SEPARATE_MOBILE", true),

  // Saved pages
  cacheHours: num("CACHE_HOURS", 24),
  cacheMaxPages: num("CACHE_MAX_PAGES", 2000),
  cacheDir: env.CACHE_DIR || defaultCacheDir,
  cacheOnDisk: bool("CACHE_ON_DISK", true),

  // Pre-loading from the sitemap
  warmDisabled: bool("WARM_DISABLED"),
  warmSitemapUrl: env.WARM_SITEMAP_URL || (domains[0] ? `https://${domains[0]}/sitemap.xml` : ""),
  warmEveryHours: num("WARM_EVERY_HOURS", 20),
  warmDelaySeconds: num("WARM_DELAY_SECONDS", 20),
  warmMaxPages: num("WARM_MAX_PAGES", 1000),

  // Dashboard and alerts
  dashboardDisabled: bool("DASHBOARD_DISABLED"),
  dashboardPassword: env.DASHBOARD_PASSWORD || env.PRERENDER_TOKEN || "",
  alertWebhookUrl: env.ALERT_WEBHOOK_URL || "",
  telegramBotToken: env.TELEGRAM_BOT_TOKEN || "",
  telegramChatId: env.TELEGRAM_CHAT_ID || "",
  alertAfterFailures: Math.max(1, num("ALERT_AFTER_FAILURES", 5)),
  alertQuietMinutes: num("ALERT_QUIET_MINUTES", 30),

  tmpDir: os.tmpdir(),
};

// The rest of the code (and prerender's whitelist plugin) reads these from env.
env.ALLOWED_DOMAINS = domains.join(",");
env.BROWSER_DEBUGGING_PORT = String(config.debugPort);

function loadSettingsFile(file) {
  if (!fs.existsSync(file)) return;
  for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (m && process.env[m[1]] === undefined) process.env[m[1]] = m[2].replace(/^["']|["']$/g, "");
  }
}

module.exports = config;
