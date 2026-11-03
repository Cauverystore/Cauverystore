// Limits how many pages render at the same time (MAX_CONCURRENT_RENDERS, default 2),
// so a burst of crawler visits can't exhaust memory. Others wait in line; anyone who
// would wait longer than MAX_QUEUE_WAIT_SECONDS, or finds more than MAX_QUEUE_LENGTH
// ahead, gets 503 straight away and the website's connector serves the normal page.
const config = require("../config");

let active = 0;
const waiting = []; // { grant, timer }
const counters = { rejected: 0, timedOutInQueue: 0 };

function release() {
  active--;
  const nextInLine = waiting.shift();
  if (nextInLine) { clearTimeout(nextInLine.timer); active++; nextInLine.grant(); }
}

module.exports = {
  requestReceived(req, res, next) {
    const start = () => {
      req.prerender.queuedMs = Date.now() - queuedAt;
      let released = false;
      const done = () => { if (!released) { released = true; release(); } };
      // Free the slot when the answer has gone out, whatever happened.
      req.res.once("finish", done);
      req.res.once("close", done);
      next();
    };
    const queuedAt = Date.now();
    if (active < config.maxConcurrentRenders) { active++; return start(); }
    if (waiting.length >= config.maxQueueLength) {
      counters.rejected++;
      req.prerender.queueRejected = true;
      return res.send(503, "Busy");
    }
    const ticket = { grant: start, timer: null };
    ticket.timer = setTimeout(() => {
      const i = waiting.indexOf(ticket);
      if (i >= 0) waiting.splice(i, 1);
      counters.timedOutInQueue++;
      req.prerender.queueRejected = true;
      res.send(503, "Busy");
    }, req.headers["x-prerender-source"] ? 120000 : config.maxQueueWaitMs); // own refreshes may wait longer
    waiting.push(ticket);
  },

  status() {
    return { rendering: active, waiting: waiting.length, limit: config.maxConcurrentRenders, ...counters };
  },
};
