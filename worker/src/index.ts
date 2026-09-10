import { page } from './page.ts';

/**
 * The demo application.
 *
 * One source serves every environment. The hostname distinguishes them, so nothing
 * here needs telling which environment it is, and there is no configuration to get
 * out of step with reality.
 */
export default {
  fetch(request: Request): Response {
    const { hostname } = new URL(request.url);

    return new Response(page(hostname), {
      headers: { 'content-type': 'text/html; charset=utf-8' },
    });
  },
} satisfies ExportedHandler;
