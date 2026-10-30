// Keeps rendered pages in memory so each page is rendered at most once per
// CACHE_HOURS. Send the header "X-Prerender-Recache: 1" to force a fresh render.
// The cache empties when the service restarts (e.g. after a deploy), which also
// means a website update reaches crawlers on the next render.
const HOURS = Number(process.env.CACHE_HOURS || 24);
const MAX_PAGES = Number(process.env.CACHE_MAX_PAGES || 500);
const pages = new Map(); // url -> { statusCode, content, savedAt }

module.exports = {
  requestReceived(req, res, next) {
    const hit = pages.get(req.prerender.url);
    const fresh = hit && Date.now() - hit.savedAt < HOURS * 3600 * 1000;
    if (fresh && !req.headers["x-prerender-recache"]) {
      res.setHeader("X-Prerender-Cache", "hit");
      return res.send(hit.statusCode, hit.content);
    }
    return next();
  },

  beforeSend(req, res, next) {
    const status = Number(req.prerender.statusCode);
    const tab = req.prerender.tab; // only set when the page was really rendered just now
    // Keep fresh renders of real pages and genuine "not found" pages; never keep
    // errors, timeouts, or answers that came from this cache (so the 24h timer isn't reset).
    if (tab && !(tab.prerender && tab.prerender.timedout) && (status === 200 || status === 404) && req.prerender.content) {
      pages.delete(req.prerender.url); // re-insert so the newest is last
      pages.set(req.prerender.url, { statusCode: status, content: req.prerender.content, savedAt: Date.now() });
      while (pages.size > MAX_PAGES) pages.delete(pages.keys().next().value);
    }
    return next();
  },
};
