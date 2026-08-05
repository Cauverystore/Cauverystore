import { app } from './app.js';
import { config } from './config.js';

app.listen(config.port, () => {
  console.log(`[image-service] listening on http://localhost:${config.port}`);
  console.log(`[image-service] uploads dir: ${config.uploadDir}`);
});
