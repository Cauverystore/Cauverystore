// Skips analytics, ads, fonts, images and video while a page renders. Crawlers only
// need the HTML, so this makes renders faster and stops each render from being
// counted as a visit in Google Analytics.
//
// Replaces prerender's built-in blockResources plugin, which uses
// Network.setRequestInterception - removed from current Chrome versions, so it
// silently stopped blocking anything. This uses the Fetch domain instead.
const BLOCKED = [
  // analytics, tag managers, ads
  "google-analytics.com",
  "googletagmanager.com",
  "stats.g.doubleclick.net",
  "googleads.g.doubleclick.net",
  "pagead2.googlesyndication.com",
  "tpc.googlesyndication.com",
  "partner.googleadservices.com",
  "connect.facebook.net",
  "api.mixpanel.com",
  "api.segment.io",
  "cdn.heapanalytics.com",
  "js-agent.newrelic.com",
  "mc.yandex.ru",
  "static.hotjar.com",
  "clarity.ms",
  "fullstory.com/rec",
  "log.optimizely.com/event",
  // embeds and fonts
  "youtube.com/embed",
  "fonts.googleapis.com",
  "fonts.gstatic.com",
  "use.typekit.net",
  "fast.fonts.com",
];
// File types that never change the HTML a crawler receives.
const BLOCKED_EXT = /\.(png|jpe?g|gif|webp|avif|svg|ico|bmp|tiff?|woff2?|ttf|otf|eot|mp4|webm|mp3|pdf)(\?|#|$)/i;

function shouldBlock(url) {
  if (BLOCKED_EXT.test(url)) return true;
  return BLOCKED.some((s) => url.includes(s));
}

module.exports = {
  tabCreated(req, res, next) {
    const tab = req.prerender.tab;
    if (!tab || !tab.Fetch) return next(); // very old browser: render without blocking

    tab.Fetch.requestPaused(({ requestId, request }) => {
      const answer = shouldBlock(request.url)
        ? tab.Fetch.failRequest({ requestId, errorReason: "BlockedByClient" })
        : tab.Fetch.continueRequest({ requestId });
      answer.catch(() => {}); // the tab may already be closing
    });

    tab.Fetch.enable({ patterns: [{ urlPattern: "*", requestStage: "Request" }] })
      .then(() => next(), () => next());
  },
};

module.exports.shouldBlock = shouldBlock;
