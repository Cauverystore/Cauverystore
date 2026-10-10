// Vercel Routing Middleware (the "proxy" entrypoint - see vercel.json) — serves a
// prerendered (fully rendered) HTML snapshot to crawlers that do NOT execute
// JavaScript (WhatsApp, Facebook, Twitter/X, LinkedIn, Slack, Telegram, Googlebot's
// non-JS pass, etc).
//
// Why this exists: cauverystore.in is a client-side-only React app (Create React
// App, no SSR). react-helmet-async updates <title>/meta/OG tags AFTER React
// hydrates in a real browser. Link-preview bots fetch the raw HTML only and never
// run JS, so without this they always see the static generic tags baked into
// public/index.html - a product link shared on WhatsApp shows "Cauvery Store -
// Your one-stop shop" instead of the actual product name/price/image.
//
// How it works: if the request's User-Agent matches a known bot, this proxies the
// request to Prerender.io (https://prerender.io), which runs a real headless
// browser, waits for the page to render, and returns the final HTML (with the
// Helmet-injected tags baked in as static markup). Everyone else gets the normal
// SPA untouched via next() - this never affects real visitors or page performance.
//
// SETUP REQUIRED (one-time, in the Vercel dashboard):
//   1. Sign up at https://prerender.io (free tier covers small catalogs; paid
//      tiers scale with page count - check current pricing for your product count).
//   2. Copy your Prerender token from the Prerender.io dashboard.
//   3. In Vercel: Project Settings -> Environment Variables -> add
//        PRERENDER_TOKEN = <your token>
//      for the Production environment, then redeploy.
//   4. In Prerender.io's dashboard, add cauverystore.in as a recognized domain.
//
// Without PRERENDER_TOKEN set, this does nothing (calls next() immediately) so
// the site keeps working exactly as it does today until the token is added.
//
// Requires the "@vercel/functions" package - add it to frontend/package.json:
//   npm install @vercel/functions

import { next } from '@vercel/functions';

const BOT_USER_AGENT_PATTERN = new RegExp(
  [
    "facebookexternalhit",
    "Facebot",
    "Twitterbot",
    "LinkedInBot",
    "WhatsApp",
    "TelegramBot",
    "Slackbot",
    "Slack-ImgProxy",
    "Discordbot",
    "Googlebot",
    "bingbot",
    "Pinterest",
    "redditbot",
    "Applebot",
    "SkypeUriPreview",
    "vkShare",
    "W3C_Validator",
    // Owner's own SEO audit tool (RankForge): lets it see the prerendered page that
    // search engines get, instead of the empty JavaScript shell.
    "RankForgeAuditBot",
  ].join("|"),
  "i"
);

// The backend generates this live from the database (see SitemapController.java /
// SitemapService.java on the Railway service) - CRA has no backend of its own to put this
// logic in, so the frontend's own routing layer is what forwards the request there. Handled
// here directly (rather than via a vercel.json "rewrites" entry to an external URL) because
// mixing the newer "proxy" entrypoint with a static external-destination rewrite in the same
// vercel.json did not reliably take effect - this keeps all custom routing in one place.
const BACKEND_URL = "https://cauvery-store-backend-production.up.railway.app";

export default async function proxy(request) {
  const url = new URL(request.url);

  if (url.pathname === "/sitemap.xml") {
    try {
      const sitemap = await fetch(BACKEND_URL + "/api/sitemap.xml");
      if (sitemap.ok) {
        return new Response(sitemap.body, {
          status: sitemap.status,
          headers: sitemap.headers,
        });
      }
    } catch (err) {
      // Backend unreachable - fall through to the normal SPA rather than hard-failing;
      // a missing sitemap for a few minutes is far less harmful than an opaque 500.
    }
    return next();
  }

  const userAgent = request.headers.get("user-agent") || "";
  const token = process.env.PRERENDER_TOKEN;

  if (!token || !BOT_USER_AGENT_PATTERN.test(userAgent)) {
    // Not a known bot, or Prerender.io isn't configured yet - serve the normal SPA.
    return next();
  }

  const prerenderUrl = "https://service.prerender.io/" + url.toString();

  try {
    const prerendered = await fetch(prerenderUrl, {
      headers: {
        "X-Prerender-Token": token,
        "User-Agent": userAgent,
      },
    });

    if (prerendered.ok) {
      // Pass through Prerender.io's response, but NOT its raw headers verbatim: fetch()
      // already transparently decompresses a gzip-encoded body, yet the original
      // Content-Encoding/Content-Length headers stay attached to prerendered.headers and
      // would still claim the (now plain) body is gzip-compressed with the original
      // (now wrong) byte length. A lenient browser ignores that mismatch, but strict
      // scrapers (Facebook's Sharing Debugger among them) try to gzip-decode an
      // already-decoded body and fail with a curl/content-encoding error. Strip both so
      // the headers describe the body we're actually sending.
      const headers = new Headers(prerendered.headers);
      headers.delete("content-encoding");
      headers.delete("content-length");
      return new Response(prerendered.body, {
        status: prerendered.status,
        headers,
      });
    }
  } catch (err) {
    // Network error reaching Prerender.io - fail open to the normal SPA below.
  }

  return next();
}
