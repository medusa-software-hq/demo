/** What this Worker is handed when it runs. */
interface Env {
  /** The pages and their assets, uploaded alongside this code. */
  readonly ASSETS: Fetcher;
}

/**
 * Which commit is answering, in a form something automated can compare.
 *
 * The one path this Worker keeps for itself. Everything else it has is the page, and
 * the page cannot say this — it is served from the asset store, which knows nothing
 * about where it came from.
 *
 * It is also the only evidence that this code runs at all. Every other request either
 * matches a file or comes back as the app, and those two look identical from outside.
 */
const VERSION_PATH = '/version';

const version = (): Response =>
  new Response(`${__COMMIT__}\n`, {
    headers: {
      'content-type': 'text/plain; charset=utf-8',
      // Answering with a cached commit is answering the wrong question.
      'cache-control': 'no-store',
    },
  });

/**
 * The demo application.
 *
 * Only requests that named no file arrive here — anything that matched one was
 * answered from the assets without this running. Of what is left, this claims a single
 * path; the rest is either a route the app handles in the browser or a URL that means
 * nothing, and the two are indistinguishable from here, so both are answered with the
 * app.
 *
 * That is where the backend goes when there is one: alongside `/version`, on the paths
 * it owns, leaving everything else to fall through as it does now.
 */
export default {
  fetch(request: Request, env: Env): Response | Promise<Response> {
    const { pathname } = new URL(request.url);

    return pathname === VERSION_PATH ? version() : env.ASSETS.fetch(request);
  },
} satisfies ExportedHandler<Env>;
