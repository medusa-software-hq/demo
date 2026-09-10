/** What this Worker is handed when it runs. */
interface Env {
  /** The pages and their assets, uploaded alongside this code. */
  readonly ASSETS: Fetcher;
}

/**
 * The demo application.
 *
 * Only requests that named no file arrive here — anything that matched one was
 * answered from the assets without this running. What is left is either a route the
 * app handles in the browser or a URL that means nothing, and the two are
 * indistinguishable from here, so both are answered with the app.
 *
 * That is where the backend goes when there is one: in front of this, on the paths it
 * owns, leaving everything else to fall through as it does now.
 */
export default {
  fetch(request: Request, env: Env): Promise<Response> {
    return env.ASSETS.fetch(request);
  },
} satisfies ExportedHandler<Env>;
