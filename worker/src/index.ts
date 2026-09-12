import { GoogleIdTokenMinter, type ServiceAccountKey } from './googleIdToken.ts';
import { routeFor } from './routing.ts';

/**
 * What this origin answers with: the app's files, and the app's API.
 *
 * Nobody is asked who they are. The page is public and so, through here, is the API
 * behind it — there is no sign-in yet, and nothing behind the API but counters that
 * vanish when the process does. What this Worker holds is a credential for calling
 * *upstream*, which is a different thing from authenticating whoever called *it*.
 */

interface Env {
  /** The files this Worker was uploaded with. */
  readonly ASSETS: Fetcher;
  /** Where the API answers. Also the audience of the token presented to it. */
  readonly API_TARGET: string;
  /** A service account key, in the JSON Google issues it as. */
  readonly GCP_SA_KEY: string;
}

/**
 * The minter built from an earlier request's environment, and what it was built from.
 *
 * Built on first use rather than at the top of the file, because `env` arrives with a
 * request and exists nowhere else. Kept afterwards because one isolate serves many
 * requests and re-importing a key for each of them is work done for nothing.
 *
 * Kept *with what it was made of*, though, rather than merely kept. An isolate is only
 * ever handed one environment today — changing a secret deploys a new version, whose
 * requests reach isolates that start empty — so a plain `??=` would be right by
 * circumstance. It would also be a function that ignores its argument after the first
 * call: wrong the moment the circumstance changes, and wrong silently, signing with a
 * key that has since been rotated away.
 */
let minter: { readonly builtFrom: string; readonly value: GoogleIdTokenMinter } | undefined;

const minterFor = (serviceAccountKeyJson: string): GoogleIdTokenMinter => {
  if (minter?.builtFrom !== serviceAccountKeyJson) {
    minter = {
      builtFrom: serviceAccountKeyJson,
      value: new GoogleIdTokenMinter(JSON.parse(serviceAccountKeyJson) as ServiceAccountKey),
    };
  }

  return minter.value;
};

/** The request as the API should see it, carrying proof that this Worker sent it. */
const callApi = async (request: Request, pathname: string, env: Env): Promise<Response> => {
  const idToken = await minterFor(env.GCP_SA_KEY).idTokenFor(env.API_TARGET);

  const target = new URL(pathname + new URL(request.url).search, env.API_TARGET);

  const headers = new Headers(request.headers);

  // Whose name the upstream request travels under. Free to use while nothing
  // authenticates the caller of this Worker; when something does, the browser's own
  // credential wants this header and Cloud Run's moves to `X-Serverless-Authorization`.
  headers.set('authorization', `Bearer ${idToken}`);

  // The API is reached at a name this origin does not have, and Cloud Run routes by
  // the one in the URL. Leaving ours behind would address the request to a service
  // that does not exist.
  headers.delete('host');

  return fetch(
    new Request(target, {
      method: request.method,
      headers,
      body: request.body,

      // A redirect the API answers with is the browser's to follow, not ours to
      // resolve against an origin the browser cannot see.
      redirect: 'manual',
    }),
  );
};

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const route = routeFor(new URL(request.url).pathname);

    if (route.to === 'assets') {
      return env.ASSETS.fetch(request);
    }

    try {
      return await callApi(request, route.pathname, env);
    } catch (failure) {
      // A key that will not import, a token endpoint that refuses, an API that cannot
      // be reached. The details are logged where an operator can read them; the
      // browser gets to know only that the API did not answer.
      console.error('the API could not be called', failure);

      return new Response('the API is unavailable', { status: 502 });
    }
  },
} satisfies ExportedHandler<Env>;
