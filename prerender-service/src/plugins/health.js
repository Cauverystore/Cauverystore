// GET /health -> 200 "ok", for Railway's health check. Needs no token.
module.exports = {
  requestReceived(req, res, next) {
    if (req.path === "/health" || req.url === "/health") return res.send(200, "ok");
    return next();
  },
};
