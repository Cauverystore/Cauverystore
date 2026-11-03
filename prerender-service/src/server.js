// Cauvery Prerender - serves fully rendered HTML of JavaScript websites to search
// engines, link-preview bots and AI crawlers. Your website's connector sends crawler
// requests here (GET /<full page URL> with an X-Prerender-Token header); this
// service opens the page in headless Chrome, waits for it to finish and returns
// the finished HTML. It speaks the same protocol as Prerender.io.
//
// Features: saved pages kept on disk, separate phone and desktop copies, sitemap
// pre-loading with scheduled refresh, instant answers while refreshing, last good
// copy when a render fails, a render queue, a dashboard (/dashboard) with an API,
// alerts by webhook or Telegram, and blocking of analytics, ads, images and fonts.
// All settings are listed in config.js and in SETUP-GUIDE.html.
const fs = require("fs");
const path = require("path");
const config = require("./config");
const { version } = require("../package.json");

if (!config.token) {
  console.error("PRERENDER_TOKEN is not set; refusing to start an open rendering service.");
  process.exit(1);
}
if (!config.domains.length) {
  console.error("ALLOWED_DOMAINS is not set (e.g. example.com,www.example.com); refusing to render any site.");
  process.exit(1);
}
const chromeLocation = config.chromeLocation || findBrowser();
if (!chromeLocation) {
  console.error("No Chrome, Chromium or Edge found. Install one, or set CHROME_LOCATION to its full path.");
  process.exit(1);
}

const prerender = require("prerender");
const health = require("./plugins/health");
const admin = require("./plugins/admin");
const tokenAuth = require("./plugins/tokenAuth");
const device = require("./plugins/device");
const cache = require("./plugins/cache");
const queue = require("./plugins/queue");
const stats = require("./plugins/stats");
const blockResources = require("./plugins/blockResources");
const warmup = require("./warmup");

console.log(`Cauvery Prerender ${version} - rendering ${config.domains.join(",")} on port ${config.port}`);
console.log(`Browser: ${chromeLocation}`);

const server = prerender({
  port: config.port,
  chromeLocation,
  // --no-sandbox and --disable-dev-shm-usage are needed inside containers.
  chromeFlags: [
    "--headless",
    "--disable-gpu",
    "--no-sandbox",
    "--disable-dev-shm-usage",
    "--hide-scrollbars",
    `--remote-debugging-port=${config.debugPort}`,
    `--user-data-dir=${path.join(config.tmpDir, `cauvery-prerender-${config.debugPort}`)}`,
  ],
  pageLoadTimeout: config.pageLoadTimeoutMs,
  waitAfterLastRequest: config.waitAfterLastRequestMs,
  logRequests: config.logRequests,
});

// Order matters.
server.use(health); // /health, no password
server.use(admin); // /dashboard and /api/*, own password
server.use(tokenAuth); // everything else needs the token
server.use(prerender.whitelist()); // only ALLOWED_DOMAINS
server.use(device); // phone or desktop
server.use(cache); // saved copy answers before a browser tab is opened
server.use(queue); // limit renders running at once
server.use(stats); // counts every answer
server.use(prerender.sendPrerenderHeader()); // tells the site "this is a render"
server.use(blockResources); // skips analytics, ads, fonts, images: faster, no fake visits
server.use(prerender.httpHeaders()); // honours <meta name="prerender-status-code" content="404">
server.use(prerender.removeScriptTags()); // crawlers get plain HTML

server.start();
warmup.start();
if (!config.dashboardDisabled) console.log(`Dashboard: http://localhost:${config.port}/dashboard`);

function findBrowser() {
  const local = process.env.LOCALAPPDATA || "";
  const candidates = process.platform === "win32"
    ? [
        "C:/Program Files/Google/Chrome/Application/chrome.exe",
        "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
        path.join(local, "Google/Chrome/Application/chrome.exe"),
        "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
        "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
      ]
    : process.platform === "darwin"
      ? ["/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", "/Applications/Chromium.app/Contents/MacOS/Chromium"]
      : ["/usr/bin/chromium", "/usr/bin/chromium-browser", "/usr/bin/google-chrome", "/usr/bin/google-chrome-stable"];
  return candidates.find((p) => fs.existsSync(p));
}
