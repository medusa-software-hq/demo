/**
 * Which of the things behind this Worker a request is for.
 *
 * Two prefixes, and everything else. The API is mounted under a path of this origin
 * rather than given an origin of its own, so a browser calling it is making a
 * same-origin request — no preflight, no CORS to configure, and nothing about the
 * arrangement visible to the page.
 */

const API_PREFIX = '/api';

/**
 * Where webhooks arrive: the one path of this hostname the platform lets through without
 * anybody signing in, because the senders of webhooks cannot.
 */
const WEBHOOKS_PREFIX = '/webhooks';

/** Whether [pathname] is [prefix] itself, or something beneath it — not merely spelled alike. */
const isUnder = (pathname: string, prefix: string): boolean =>
  pathname === prefix || pathname.startsWith(`${prefix}/`);

export type Route =
  /** For the API, at [pathname] — which is what the API sees, not what was asked for. */
  | { readonly to: 'api'; readonly pathname: string }
  /** For the API too, at [pathname] unchanged, from a sender nobody signed in as. */
  | { readonly to: 'webhooks'; readonly pathname: string }
  /** For the files this Worker was uploaded with, or the page that stands in for them. */
  | { readonly to: 'assets' };

export const routeFor = (pathname: string): Route => {
  // Kept whole. The path a sender is given is the path the service publishes, so there is
  // one name for a webhook endpoint rather than a public one and a private one.
  if (isUnder(pathname, WEBHOOKS_PREFIX)) {
    return { to: 'webhooks', pathname };
  }

  if (!isUnder(pathname, API_PREFIX)) {
    return { to: 'assets' };
  }

  // The prefix belongs to this origin, not to the API: the service publishes
  // `/counters`, and a request for `/api/counters` is a request for that. `/api`
  // alone leaves nothing behind, which is the service's own root.
  return { to: 'api', pathname: pathname.slice(API_PREFIX.length) || '/' };
};
