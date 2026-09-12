import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

/**
 * Nothing environment-specific reaches the bundle.
 *
 * One bundle is built and the same one is uploaded to every environment: the page names no host
 * and no port, so there is no value that would have to differ between them and nothing to keep in
 * step.
 */

/**
 * Where the backend is, when one is running for local development.
 *
 * Read here and nowhere else, and deliberately not a `VITE_` name — nothing in the bundle should
 * be able to see it. `task gradleRoot:run` prints the port it chose; this is how to pass it:
 *
 *     API_URL=http://localhost:<port> task frontend:dev
 */
const backendUrl = process.env['API_URL'];

export default defineConfig({
  plugins: [react()],

  server: {
    /**
     * The dev server standing in for the Worker.
     *
     * In production the Worker answers `/api/*` by forwarding it to Cloud Run with the prefix
     * removed. Here the dev server does the same thing, so the page makes the same same-origin
     * request either way and learns nothing about where the service actually is.
     */
    proxy: backendUrl
      ? {
          '/api': {
            target: backendUrl,
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
          },
        }
      : undefined,
  },
});
