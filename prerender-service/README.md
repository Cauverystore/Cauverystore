# Prerender service (self-hosted)

Renders cauverystore.in pages in headless Chromium for crawlers that don't run
JavaScript (WhatsApp/Facebook link previews, ChatGPT and other AI crawlers, Bing).
It replaces the paid Prerender.io service. The website's Vercel proxy
(`frontend/proxy.js`) sends crawler requests here; real visitors never touch it.

## What it does
- `GET /<page URL>` with header `X-Prerender-Token` returns the finished HTML.
- `GET /health` returns `ok` (Railway health check).
- Only renders `cauverystore.in` and `www.cauverystore.in`; other sites get 404.
- Requests without the right token get 401.
- Keeps rendered pages in memory for 24 hours (`CACHE_HOURS`); a deploy or restart
  clears it. Header `X-Prerender-Recache: 1` forces a fresh render.
- Pages with `<meta name="prerender-status-code" content="404">` return 404.

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
