# Self-hosted Prerender: Node + headless Chromium.
FROM node:20-slim

RUN apt-get update \
 && apt-get install -y --no-install-recommends chromium ca-certificates fonts-liberation \
 && rm -rf /var/lib/apt/lists/*

ENV NODE_ENV=production \
    CHROME_LOCATION=/usr/bin/chromium

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY src ./src

# Run as the unprivileged "node" user.
USER node
EXPOSE 3000
CMD ["node", "src/server.js"]
