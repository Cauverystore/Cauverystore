// Saved pages: each page is rendered once and reused, so crawlers get an instant answer.
//
// - Pages are kept compressed in memory and written to disk (CACHE_DIR), so they
//   survive restarts and redeploys (on Railway, attach a Volume at /data).
// - A page older than CACHE_HOURS is still answered at once from the saved copy,
//   and a fresh render starts in the background ("stale while refreshing").
// - If a fresh render fails or times out, the last good copy is sent instead.
// - "X-Prerender-Recache: 1" on a request forces a fresh render.
// Phone and desktop copies are kept apart (see device.js).
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");
const config = require("../config");
const selfRender = require("../selfRender");

const KEEP_STATUSES = new Set([200, 301, 302, 404, 410]);
const pages = new Map(); // key -> { key, url, device, statusCode, headers, gz, bytes, savedAt }
const refreshing = new Set();

const keyOf = (device, url) => `${device}|${url}`;
const fileOf = (key) => path.join(config.cacheDir, crypto.createHash("sha1").update(key).digest("hex") + ".json");
const isFresh = (entry) => Date.now() - entry.savedAt < config.cacheHours * 3600 * 1000;

function loadFromDisk() {
  if (!config.cacheOnDisk) return;
  try {
    fs.mkdirSync(config.cacheDir, { recursive: true });
  } catch (err) {
    console.warn(`[cache] cannot use ${config.cacheDir} (${err.message}); pages are kept in memory only`);
    config.cacheOnDisk = false;
    return;
  }
  const loaded = [];
  for (const name of fs.readdirSync(config.cacheDir)) {
    if (!name.endsWith(".json") || name === "stats.json") continue;
    try {
      const d = JSON.parse(fs.readFileSync(path.join(config.cacheDir, name), "utf8"));
      const gz = Buffer.from(d.gz, "base64");
      loaded.push({ key: keyOf(d.device, d.url), url: d.url, device: d.device, statusCode: d.statusCode,
        headers: d.headers || {}, gz, bytes: d.bytes || 0, savedAt: d.savedAt });
    } catch {
      fs.rm(path.join(config.cacheDir, name), { force: true }, () => {});
    }
  }
  loaded.sort((a, b) => a.savedAt - b.savedAt).forEach((e) => pages.set(e.key, e));
  trim();
  console.log(`[cache] ${pages.size} saved pages loaded from ${config.cacheDir}`);
}

function writeToDisk(entry) {
  if (!config.cacheOnDisk) return;
  const file = fileOf(entry.key);
  const tmp = `${file}.${process.pid}.tmp`;
  const body = JSON.stringify({ url: entry.url, device: entry.device, statusCode: entry.statusCode,
    headers: entry.headers, bytes: entry.bytes, savedAt: entry.savedAt, gz: entry.gz.toString("base64") });
  fs.promises.writeFile(tmp, body).then(() => fs.promises.rename(tmp, file))
    .catch((err) => console.warn(`[cache] could not save ${entry.url} to disk: ${err.message}`));
}

function removeEntry(key) {
  pages.delete(key);
  if (config.cacheOnDisk) fs.rm(fileOf(key), { force: true }, () => {});
}

function trim() {
  while (pages.size > config.cacheMaxPages) removeEntry(pages.keys().next().value);
}

function save(url, device, statusCode, content, headers) {
  const text = Buffer.isBuffer(content) ? content : Buffer.from(String(content), "utf8");
  const key = keyOf(device, url);
  const entry = { key, url, device, statusCode, headers: headers || {}, gz: zlib.gzipSync(text), bytes: text.length, savedAt: Date.now() };
  pages.delete(key); // re-insert so the newest is last
  pages.set(key, entry);
  trim();
  writeToDisk(entry);
}

function answerFrom(entry, req, res, status) {
  req.prerender.cacheStatus = status;
  req.prerender.headers = entry.headers;
  res.setHeader("X-Prerender-Cache", status);
  res.setHeader("X-Prerender-Saved-At", new Date(entry.savedAt).toISOString());
  return res.send(entry.statusCode, zlib.gunzipSync(entry.gz).toString("utf8"));
}

function refreshInBackground(entry) {
  if (refreshing.has(entry.key)) return;
  refreshing.add(entry.key);
  selfRender(entry.url, { device: entry.device, source: "refresh" })
    .catch((err) => console.warn(`[cache] background refresh failed for ${entry.url}: ${err.message}`))
    .finally(() => refreshing.delete(entry.key));
}

const plugin = {
  init() {
    loadFromDisk();
  },

  requestReceived(req, res, next) {
    const key = keyOf(req.prerender.device || "desktop", req.prerender.url);
    req.prerender.cacheKey = key;
    if (req.headers["x-prerender-recache"]) {
      req.prerender.cacheStatus = "recache";
      return next();
    }
    const entry = pages.get(key);
    if (entry && isFresh(entry)) return answerFrom(entry, req, res, "hit");
    if (entry) {
      refreshInBackground(entry);
      return answerFrom(entry, req, res, "stale");
    }
    req.prerender.cacheStatus = "miss";
    return next();
  },

  beforeSend(req, res, next) {
    const p = req.prerender;
    if (!p.cacheKey || p.cacheStatus === "hit" || p.cacheStatus === "stale") return next();
    const status = Number(p.statusCode);
    const timedOut = Boolean(p.tab && p.tab.prerender && p.tab.prerender.timedout);
    const good = Boolean(p.tab) && !timedOut && KEEP_STATUSES.has(status) && p.content;
    if (good) {
      save(p.url, p.device || "desktop", status, p.content, p.headers);
      return next();
    }
    const old = pages.get(p.cacheKey);
    if (old) {
      // Fresh render went wrong: send the last good copy instead.
      p.renderFailed = { status, timedOut };
      p.statusCode = old.statusCode;
      p.content = zlib.gunzipSync(old.gz).toString("utf8");
      p.headers = old.headers;
      p.cacheStatus = "fallback";
      res.setHeader("X-Prerender-Cache", "fallback");
      res.setHeader("X-Prerender-Saved-At", new Date(old.savedAt).toISOString());
    }
    return next();
  },

  // For the dashboard and the API.
  list(filter = "", limit = 500) {
    const q = filter.toLowerCase();
    const out = [];
    for (const e of [...pages.values()].reverse()) {
      if (q && !e.url.toLowerCase().includes(q)) continue;
      out.push({ url: e.url, device: e.device, statusCode: e.statusCode, bytes: e.bytes,
        savedAt: new Date(e.savedAt).toISOString(), fresh: isFresh(e) });
      if (out.length >= limit) break;
    }
    return out;
  },
  summary() {
    let fresh = 0;
    let bytes = 0;
    for (const e of pages.values()) { if (isFresh(e)) fresh++; bytes += e.gz.length; }
    return { pages: pages.size, fresh, stale: pages.size - fresh, compressedBytes: bytes,
      onDisk: config.cacheOnDisk, dir: config.cacheOnDisk ? config.cacheDir : null, maxPages: config.cacheMaxPages };
  },
  forget(url) {
    let removed = 0;
    for (const e of [...pages.values()]) if (e.url === url) { removeEntry(e.key); removed++; }
    return removed;
  },
  clear() {
    const n = pages.size;
    for (const key of [...pages.keys()]) removeEntry(key);
    return n;
  },
};

module.exports = plugin;
