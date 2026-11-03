# Prerender service (self-hosted)

Renders cauverystore.in pages in headless Chromium for crawlers that don't run
JavaScript (WhatsApp/Facebook link previews, ChatGPT and other AI crawlers, Bing).
It replaces the paid Prerender.io service. The website's Vercel proxy
(`frontend/proxy.js`) sends crawler requests here; real visitors never touch it.

## What it does
- `GET /<page URL>` with header `X-Prerender-Token` returns the finished HTML.
- `GET /health` returns `ok` (Railway health check).
- Only renders `cauverystore.in` and `www.cauverystore.in` (or `ALLOWED_DOMAINS`); other sites get 404.
  Requests without the right token get 401.
- **Saved pages**: kept compressed in memory and on disk for 24 hours (`CACHE_HOURS`).
  Attach a Railway Volume at `/data` (and set `RAILWAY_RUN_UID=0` so the service can
  write to it) to keep them across deploys. Without a volume they last until the next deploy.
- **Pre-loading**: every sitemap page is rendered at start and every 20 hours
  (`WARM_EVERY_HOURS`), so crawlers are answered instantly.
- **Phone and desktop copies**: phone crawlers (Googlebot Smartphone, WhatsApp on phones)
  get a page rendered at phone width; `SEPARATE_MOBILE=false` turns this off.
- **Never slow, never broken**: an expired page is answered at once from the saved copy
  while a fresh one renders in the background; if a render fails, the last good copy is sent.
- **Queue**: at most 2 renders at once (`MAX_CONCURRENT_RENDERS`); extra crawlers wait up
  to 15 s, then get 503 and the website serves its normal page.
- **Blocking**: analytics, tag managers, ads, images and fonts are not loaded while
  rendering, so renders are fast and never count as visits.
- Pages with `<meta name="prerender-status-code" content="404">` return 404.
- Header `X-Prerender-Recache: 1` forces a fresh render.

## Dashboard
Open `https://<service address>/dashboard` and sign in with `DASHBOARD_PASSWORD`
(or, if that is not set, the `PRERENDER_TOKEN` value). It shows crawler visits, which
crawlers came, failures, the pre-loading progress and every saved page, with buttons to
render a page or the whole sitemap again (do this after changing page wording).
Scripts can use the same API with the `X-Prerender-Token` header:
`POST /api/recache {"url": "..."}`, `POST /api/recache-all`, `GET /api/status`.

## Alerts (optional)
Set `ALERT_WEBHOOK_URL` (Slack, Discord, Google Chat or any JSON webhook) and/or
`TELEGRAM_BOT_TOKEN` + `TELEGRAM_CHAT_ID`. You get a message when 5 renders in a row
fail (`ALERT_AFTER_FAILURES`), when it recovers, when the sitemap can't be read, or
when more than a fifth of a pre-loading run fails.

All settings are listed in `src/config.js`. The same code is shared as the
"Cauvery Prerender" kit (DocumentsCauvery-Prerender).

## Setup on Railway (one time)
1. In the Railway project, **New → GitHub Repo → Cauverystore/Cauverystore**.
2. In the new service's **Settings**: set **Root Directory** to `/prerender-service`
   (Railway then uses this folder's `Dockerfile` and `railway.json`), and
   optionally **Watch Paths** to `/prerender-service/**`.
3. **Variables**: add `PRERENDER_TOKEN` = a long random secret.
4. **Settings → Networking → Generate Domain** to get its https address.
5. Check `https://<that address>/health` shows `ok`.

## Switch the website over (Vercel)
In Vercel → Project → Settings → Environment Variables (Production):
- `PRERENDER_SERVICE_URL` = the Railway https address
- `PRERENDER_TOKEN` = the same secret as on Railway

Then redeploy. To go back to Prerender.io, delete `PRERENDER_SERVICE_URL` and put
the Prerender.io token back in `PRERENDER_TOKEN`.

## Run locally
```
npm install
PRERENDER_TOKEN=test CHROME_LOCATION="C:/Program Files/Google/Chrome/Application/chrome.exe" PORT=3555 npm start
curl -H "X-Prerender-Token: test" http://localhost:3555/https://cauverystore.in/ta
```
