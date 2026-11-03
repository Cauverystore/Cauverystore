// Pre-loads the cache from the sitemap, so crawlers get an instant answer even on
// their first visit, and refreshes every page before it expires.
//
// Reads WARM_SITEMAP_URL (sitemap indexes are followed too), keeps the pages on
// ALLOWED_DOMAINS, and renders each one fresh, one at a time, for desktop and (when
// SEPARATE_MOBILE is on) for phones.
//   WARM_EVERY_HOURS     re-render everything this often (default 20; 0 = only at start)
//   WARM_DELAY_SECONDS   wait after start before the first run (default 20)
//   WARM_MAX_PAGES       most sitemap pages to pre-load (default 1000)
//   WARM_DISABLED=true   switch pre-loading off
const config = require("./config");
const alerts = require("./alerts");
const selfRender = require("./selfRender");

const PAUSE_BETWEEN_PAGES_MS = 1000;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const status = { running: false, done: 0, total: 0, startedAt: null, last: null, nextAt: null };

const locs = (xml) => [...xml.matchAll(/<loc>\s*([^<\s]+)\s*<\/loc>/g)].map((m) => m[1].replace(/&amp;/g, "&"));

async function fetchText(url) {
  const res = await fetch(url, { signal: AbortSignal.timeout(30000), headers: { "User-Agent": "CauveryPrerender-sitemap" } });
  if (!res.ok) throw new Error(`${url} answered ${res.status}`);
  return res.text();
}

async function readSitemap() {
  const xml = await fetchText(config.warmSitemapUrl);
  let urls = [];
  if (/<sitemapindex/i.test(xml)) {
    for (const child of locs(xml).slice(0, 50)) {
      try { urls.push(...locs(await fetchText(child))); }
      catch (err) { console.warn(`[warmup] skipped sitemap ${child}: ${err.message}`); }
    }
  } else {
    // Only <loc> entries: hreflang <xhtml:link> lines repeat pages that have their own <loc>.
    urls = locs(xml);
  }
  const allowed = new Set(config.domains);
  urls = urls.filter((u) => { try { return allowed.has(new URL(u).hostname.toLowerCase()); } catch { return false; } });
  return [...new Set(urls)].slice(0, config.warmMaxPages);
}

async function warmAll(reason = "scheduled") {
  if (status.running) return false; // a slow run never overlaps the next one
  Object.assign(status, { running: true, done: 0, total: 0, startedAt: new Date().toISOString() });
  const started = Date.now();
  let ok = 0;
  let failed = 0;
  try {
    const urls = await readSitemap();
    const devices = config.splitDevices ? ["desktop", "mobile"] : ["desktop"];
    status.total = urls.length * devices.length;
    console.log(`[warmup] ${reason}: rendering ${urls.length} pages x ${devices.length} (${devices.join(" + ")}) from ${config.warmSitemapUrl}`);
    for (const url of urls) {
      for (const device of devices) {
        try {
          const code = await selfRender(url, { device, source: "warmup" });
          if ([200, 301, 302, 404, 410].includes(code)) ok++;
          else { failed++; console.warn(`[warmup] ${code} ${device} ${url}`); }
        } catch (err) {
          failed++;
          console.warn(`[warmup] failed ${device} ${url}: ${err.message}`);
        }
        status.done++;
        await sleep(PAUSE_BETWEEN_PAGES_MS);
      }
    }
    const mins = ((Date.now() - started) / 60000).toFixed(1);
    console.log(`[warmup] done in ${mins} min: ${ok} saved, ${failed} failed`);
    status.last = { finishedAt: new Date().toISOString(), minutes: Number(mins), ok, failed, reason };
    if (status.total && failed / status.total > 0.2) {
      alerts.send("warmup", `pre-loading finished with ${failed} of ${status.total} pages failing. Check the dashboard.`);
    }
  } catch (err) {
    console.warn(`[warmup] could not read the sitemap: ${err.message}`);
    status.last = { finishedAt: new Date().toISOString(), error: `could not read the sitemap: ${err.message}`, reason };
    alerts.send("sitemap", `could not read the sitemap ${config.warmSitemapUrl} (${err.message}).`);
  } finally {
    status.running = false;
  }
  return true;
}

function start() {
  if (config.warmDisabled || !config.warmSitemapUrl) {
    console.log("[warmup] switched off");
    return;
  }
  status.nextAt = new Date(Date.now() + config.warmDelaySeconds * 1000).toISOString();
  setTimeout(() => warmAll("start"), config.warmDelaySeconds * 1000);
  if (config.warmEveryHours > 0) {
    setInterval(() => {
      status.nextAt = new Date(Date.now() + config.warmEveryHours * 3600 * 1000).toISOString();
      warmAll("scheduled");
    }, config.warmEveryHours * 3600 * 1000);
  }
}

module.exports = {
  start,
  runNow: (reason) => warmAll(reason),
  status: () => ({ ...status, enabled: !config.warmDisabled && Boolean(config.warmSitemapUrl), sitemap: config.warmSitemapUrl }),
};
