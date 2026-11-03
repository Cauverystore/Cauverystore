// Asks this same service to render a page fresh and save it, the way a crawler
// would, so it goes through the queue, the cache and the statistics like any other
// request. Used by the sitemap pre-loading, background refreshes and the dashboard.
const config = require("./config");

async function selfRender(url, { device = "desktop", source = "refresh", timeoutMs = 90000 } = {}) {
  const res = await fetch(`http://127.0.0.1:${config.port}/${url}`, {
    headers: {
      "X-Prerender-Token": config.token,
      "X-Prerender-Recache": "1",
      "X-Prerender-Device": device,
      "X-Prerender-Source": source,
      "User-Agent": `CauveryPrerender-${source}`,
    },
    signal: AbortSignal.timeout(timeoutMs),
  });
  await res.arrayBuffer(); // finish reading so the connection is released
  return res.status;
}

module.exports = selfRender;
