/**
 * Which of the two things behind this Worker a request is for.
 *
 * One prefix, and everything else. The API is mounted under a path of this origin
 * rather than given an origin of its own, so a browser calling it is making a
 * same-origin request — no preflight, no CORS to configure, and nothing about the
 * arrangement visible to the page.
 */

const API_PREFIX = '/api';

export type Route =
  /** For the API, at [pathname] — which is what the API sees, not what was asked for. */
  | { readonly to: 'api'; readonly pathname: string }
  /** For the files this Worker was uploaded with, or the page that stands in for them. */
  | { readonly to: 'assets' };

export const routeFor = (pathname: string): Route => {
  if (pathname !== API_PREFIX && !pathname.startsWith(`${API_PREFIX}/`)) {
    return { to: 'assets' };
  }

  // The prefix belongs to this origin, not to the API: the service publishes
  // `/counters`, and a request for `/api/counters` is a request for that. `/api`
  // alone leaves nothing behind, which is the service's own root.
  return { to: 'api', pathname: pathname.slice(API_PREFIX.length) || '/' };
};
