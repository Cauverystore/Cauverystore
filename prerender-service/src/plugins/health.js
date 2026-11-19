// GET /health -> 200 "ok", for hosting health checks. Needs no token.
module.exports = {
  requestReceived(req, res, next) {
    if (req.path === "/health" || req.url === "/health") {
      req.prerender.internal = true;
      return res.send(200, "ok");
    }
    return next();
  },
};
