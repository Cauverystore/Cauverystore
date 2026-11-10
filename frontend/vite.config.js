import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Keeps the REACT_APP_ prefix so the variable names already set on Vercel,
// Railway and in the Dockerfile keep working unchanged.
export default defineConfig({
  plugins: [react()],
  envPrefix: 'REACT_APP_',
  server: { port: 3000, open: false },
  preview: { port: 3000 },
  build: {
    outDir: 'build',          // Vercel, Capacitor (webDir) and the Dockerfile all read ./build
    assetsDir: 'static',      // vercel.json cache headers and proxy.js matcher expect /static/
    sourcemap: false,
  },
});
