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

/**
 * What a caller sends and this Worker does not pass on.
 *
 * Deliberately a rule rather than a list of things that have gone wrong. Everything
 * above the last two is the set HTTP defines as hop-by-hop (RFC 9110 §7.6.1): they
 * describe a conversation between a peer and its immediate neighbour, and a proxy
 * that relays them is misreporting one connection's terms as another's. Applying the
 * whole set is the only way to stop discovering it one header at a time.
 *
 * What remains open is a different question and not answerable here. `cookie`,
 * `x-forwarded-for` and the rest are forwarded, and are harmless only because the API
 * behind this reads none of them — no sessions, nothing keyed on an address. That is
 * a fact about today's backend rather than a property of this Worker, and the day it
 * stops being true, spoofing any of them through here becomes trivial.
 */
const NOT_FORWARDED = [
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',

  // Addressed to this origin. Cloud Run routes by the name in the URL, and `fetch`
  // derives that itself; leaving ours on would address the request to a service that
  // does not exist.
  'host',

  /**
   * Cloud Run accepts this in place of `authorization` for its own IAM check — that
   * is the header's purpose, so an application can receive an end user's
   * `authorization` untouched. A caller who sets it is taking part in a decision that
   * is not theirs. Nobody gets in that way today, since the decision still ends at IAM
   * and one account holds `run.invoker`; the worst they manage is to have their own
   * request refused. It matters because of what changes next: when there is an end
   * user to authenticate, this Worker's credential moves here.
   */
  'x-serverless-authorization',
] as const;

/** The request as the API should see it, carrying proof that this Worker sent it. */
const callApi = async (request: Request, pathname: string, env: Env): Promise<Response> => {
  const idToken = await minterFor(env.GCP_SA_KEY).idTokenFor(env.API_TARGET);

  const target = new URL(pathname + new URL(request.url).search, env.API_TARGET);

  const headers = new Headers(request.headers);

  for (const header of NOT_FORWARDED) {
    headers.delete(header);
  }

  // Whose name the upstream request travels under. `authorization` is free to use
  // while nothing authenticates the caller of this Worker; when something does, the
  // browser's own credential wants this header and ours moves to
  // `x-serverless-authorization` — which is stripped above, so a caller cannot get
  // there first.
  headers.set('authorization', `Bearer ${idToken}`);

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
