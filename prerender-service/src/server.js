// Self-hosted Prerender for cauverystore.in.
//
// The website is a client-side React app, so crawlers that don't run JavaScript
// (WhatsApp, Facebook, ChatGPT and other AI crawlers, Bing...) would only see an
// empty page. The Vercel proxy (frontend/proxy.js) sends those crawlers here; this
// service opens the page in headless Chromium, waits for it to finish loading and
// returns the finished HTML. It replaces the paid Prerender.io service and speaks
// the same protocol: GET /<full page URL> with an X-Prerender-Token header.
//
// Settings (Railway service variables):
//   PRERENDER_TOKEN   required. Shared secret; must match PRERENDER_TOKEN in Vercel.
//   ALLOWED_DOMAINS   comma-separated hosts this service will render
//                     (default: cauverystore.in,www.cauverystore.in).
//   CACHE_HOURS       how long a rendered page is reused (default 24).
//   CACHE_MAX_PAGES   most pages kept in memory (default 500).
//   CHROME_LOCATION   Chromium path (set in the Dockerfile).
//   PORT              set by Railway.
const prerender = require("prerender");
const health = require("./plugins/health");
const tokenAuth = require("./plugins/tokenAuth");
const memoryCache = require("./plugins/memoryCache");

if (!process.env.PRERENDER_TOKEN) {
  console.error("PRERENDER_TOKEN is not set; refusing to start an open rendering service.");
  process.exit(1);
}
process.env.ALLOWED_DOMAINS = process.env.ALLOWED_DOMAINS || "cauverystore.in,www.cauverystore.in";

const server = prerender({
  chromeLocation: process.env.CHROME_LOCATION,
  // --no-sandbox and --disable-dev-shm-usage are needed inside a container.
  chromeFlags: [
    "--headless",
    "--disable-gpu",
    "--no-sandbox",
    "--disable-dev-shm-usage",
    "--hide-scrollbars",
    "--remote-debugging-port=9222",
  ],
  pageLoadTimeout: 20 * 1000,
  waitAfterLastRequest: 500,
  logRequests: process.env.LOG_REQUESTS === "true",
});

// Order matters: health answers before the password check; the cache answers
// before a browser tab is opened.
server.use(health);
server.use(tokenAuth);
server.use(prerender.whitelist());
server.use(memoryCache);
server.use(prerender.sendPrerenderHeader()); // tells the site "this is a render"
server.use(prerender.blockResources()); // skips analytics, fonts, images: faster renders
server.use(prerender.httpHeaders()); // honours <meta name="prerender-status-code" content="404">
server.use(prerender.removeScriptTags()); // crawlers get plain HTML

server.start();
