import { app } from './app.js';
import { config } from './config.js';

// Log a promise error that nothing handled instead of letting Node 20 exit on it.
process.on('unhandledRejection', (reason) => {
  console.error('[image-service] unhandled promise rejection:', reason);
});

app.listen(config.port, () => {
  console.log(`[image-service] listening on http://localhost:${config.port}`);
  console.log(`[image-service] uploads dir: ${config.uploadDir}`);
});
