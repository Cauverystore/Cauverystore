// Counts every request for the dashboard: which crawlers came, how fast pages were
// answered, cache hits, failures. Kept for 30 days in stats.json next to the saved
// pages. Also raises an alert after ALERT_AFTER_FAILURES renders in a row fail.
const fs = require("fs");
const path = require("path");
const config = require("../config");
const alerts = require("../alerts");

const BOTS = [
  ["Googlebot", /Googlebot|Google-InspectionTool|GoogleOther|Storebot-Google/i],
  ["Google (other)", /Google-Extended|Google-CloudVertexBot|AdsBot-Google|Mediapartners-Google|APIs-Google/i],
  ["Bing", /bingbot|BingPreview|msnbot|adidxbot/i],
  ["ChatGPT / OpenAI", /GPTBot|OAI-SearchBot|ChatGPT-User/i],
  ["Claude / Anthropic", /ClaudeBot|Claude-SearchBot|Claude-User|anthropic-ai/i],
  ["Perplexity", /PerplexityBot|Perplexity-User/i],
  ["WhatsApp", /WhatsApp/i],
  ["Facebook / Meta", /facebookexternalhit|Facebot|meta-externalagent|meta-externalfetcher/i],
  ["X / Twitter", /Twitterbot/i],
  ["LinkedIn", /LinkedInBot/i],
  ["Telegram", /TelegramBot/i],
  ["Slack", /Slackbot/i],
  ["Discord", /Discordbot/i],
  ["Pinterest", /Pinterest/i],
  ["Apple", /Applebot/i],
  ["DuckDuckGo", /DuckDuckBot|DuckAssistBot/i],
  ["Yandex", /YandexBot/i],
  ["Baidu", /Baiduspider/i],
  ["Amazon", /Amazonbot/i],
  ["Common Crawl", /CCBot/i],
];
const SOURCES = { warmup: "Pre-loading", refresh: "Background refresh", dashboard: "Dashboard recache" };

function whoIs(req) {
  const source = String(req.headers["x-prerender-source"] || "");
  if (SOURCES[source]) return SOURCES[source];
  const ua = String(req.headers["user-agent"] || "");
  const bot = BOTS.find(([, re]) => re.test(ua));
  return bot ? bot[0] : "Other";
}

const today = () => new Date().toISOString().slice(0, 10);
const emptyDay = () => ({ requests: 0, rendered: 0, hits: 0, stale: 0, fallbacks: 0, failed: 0, renderMs: 0, bots: {} });

const state = {
  startedAt: new Date().toISOString(),
  totals: emptyDay(),
  days: {}, // "2026-11-03" -> counts
  recent: [], // newest first
  failures: [], // newest first
  consecutiveFailures: 0,
  failingAlertSent: false,
};
const statsFile = () => path.join(config.cacheDir, "stats.json");

function load() {
  if (!config.cacheOnDisk) return;
  try {
    const saved = JSON.parse(fs.readFileSync(statsFile(), "utf8"));
    state.days = saved.days || {};
    state.failures = saved.failures || [];
  } catch { /* first start */ }
}

function persist() {
  if (!config.cacheOnDisk) return;
  const keep = Object.keys(state.days).sort().slice(-30);
  state.days = Object.fromEntries(keep.map((d) => [d, state.days[d]]));
  fs.promises.writeFile(statsFile(), JSON.stringify({ days: state.days, failures: state.failures }))
    .catch(() => {});
}

function bump(counts, field, who, ms) {
  counts.requests++;
  if (field) counts[field]++;
  if (ms) counts.renderMs += ms;
  counts.bots[who] = (counts.bots[who] || 0) + 1;
}

const plugin = {
  init() {
    load();
    setInterval(persist, 60000).unref();
  },

  beforeSend(req, res, next) {
    const p = req.prerender;
    if (p.internal) return next();
    const status = parseInt(p.statusCode, 10) || 504;
    const who = whoIs(req);
    const ms = Date.now() - p.start.getTime();
    const rendered = p.cacheStatus === "miss" || p.cacheStatus === "recache" || p.cacheStatus === "fallback";
    const renderOk = rendered && p.cacheStatus !== "fallback" && [200, 301, 302, 404, 410].includes(status);
    let field = null;
    if (p.cacheStatus === "hit") field = "hits";
    else if (p.cacheStatus === "stale") field = "stale";
    else if (p.cacheStatus === "fallback") field = "fallbacks";
    else if (renderOk) field = "rendered";
    else if (rendered) field = "failed";

    const refused = !p.cacheStatus; // wrong token, other site, too busy: logged, not counted
    const day = (state.days[today()] = state.days[today()] || emptyDay());
    for (const counts of refused ? [] : [state.totals, day]) {
      bump(counts, field, who, renderOk ? ms : 0);
      if (field === "fallbacks") counts.failed++; // a fallback is still a failed render
    }

    const row = { at: new Date().toISOString(), url: p.url, device: p.device || "desktop", who, status, ms,
      cache: p.cacheStatus || (p.queueRejected ? "busy" : "rejected") };
    state.recent.unshift(row);
    state.recent.length = Math.min(state.recent.length, 200);

    if (rendered && !renderOk) {
      const why = p.renderFailed ? (p.renderFailed.timedOut ? "timed out" : `status ${p.renderFailed.status}`)
        : `status ${status}`;
      state.failures.unshift({ ...row, why });
      state.failures.length = Math.min(state.failures.length, 50);
      state.consecutiveFailures++;
      if (state.consecutiveFailures >= config.alertAfterFailures && !state.failingAlertSent) {
        state.failingAlertSent = true;
        alerts.send("failing", `${state.consecutiveFailures} page renders in a row have failed. Last: ${p.url} (${why}). ` +
          "Crawlers are getting saved copies where there are some, otherwise your normal page.");
      }
    } else if (renderOk) {
      if (state.failingAlertSent) alerts.send("recovered", "renders are working again.", { force: true });
      state.consecutiveFailures = 0;
      state.failingAlertSent = false;
    }
    return next();
  },

  snapshot() {
    return {
      startedAt: state.startedAt,
      totals: state.totals,
      days: state.days,
      recent: state.recent,
      failures: state.failures,
      consecutiveFailures: state.consecutiveFailures,
    };
  },
};

module.exports = plugin;
