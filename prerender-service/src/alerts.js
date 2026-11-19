// Sends a short message when something goes wrong (and when it recovers).
// Targets, any or all:
//   ALERT_WEBHOOK_URL   Slack, Discord, Google Chat, Microsoft Teams (workflow) or any
//                       URL that accepts a JSON POST. The body carries the message as
//                       "text" and "content", which those services understand.
//   TELEGRAM_BOT_TOKEN + TELEGRAM_CHAT_ID   a Telegram message.
// The same kind of alert is sent at most once per ALERT_QUIET_MINUTES (default 30).
const config = require("./config");

const lastSent = new Map(); // kind -> time
const history = []; // newest first, for the dashboard

function configured() {
  return Boolean(config.alertWebhookUrl || (config.telegramBotToken && config.telegramChatId));
}

async function post(url, body) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(10000),
  });
  if (!res.ok) throw new Error(`answered ${res.status}`);
}

async function send(kind, message, { force = false } = {}) {
  const now = Date.now();
  if (!force && now - (lastSent.get(kind) || 0) < config.alertQuietMinutes * 60000) return false;
  lastSent.set(kind, now);
  const text = `Cauvery Prerender (${config.domains.join(", ")}): ${message}`;
  history.unshift({ at: new Date(now).toISOString(), kind, message, sent: configured() });
  history.length = Math.min(history.length, 30);
  console.warn(`[alert] ${message}`);
  if (!configured()) return false;

  const jobs = [];
  if (config.alertWebhookUrl) jobs.push(post(config.alertWebhookUrl, { text, content: text }));
  if (config.telegramBotToken && config.telegramChatId) {
    jobs.push(post(`https://api.telegram.org/bot${config.telegramBotToken}/sendMessage`, {
      chat_id: config.telegramChatId,
      text,
    }));
  }
  const results = await Promise.allSettled(jobs);
  for (const r of results) if (r.status === "rejected") console.warn(`[alert] could not send: ${r.reason.message}`);
  return results.some((r) => r.status === "fulfilled");
}

module.exports = { send, configured, history };
