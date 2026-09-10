import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

/**
 * Nothing environment-specific here.
 *
 * One bundle is built and the same one is uploaded to every environment: the page
 * names no host and no port, so there is no value that would have to differ between
 * them and nothing to keep in step.
 */
export default defineConfig({
  plugins: [react()],
});
