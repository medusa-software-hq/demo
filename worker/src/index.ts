import { createRemoteJWKSet } from 'jose';
import { GoogleIdTokenMinter, type ServiceAccountKey } from './googleIdToken.ts';
import { routeFor } from './routing.ts';
import { SignInTokenVerifier, type Caller } from './signInToken.ts';

/**
 * What this origin answers with: the app's files, its API, and its webhooks.
 *
 * Signing in happens in front of this Worker rather than in it: the platform puts a login
 * before every app hostname, and a request that got past it carries a token saying who
 * signed in. This Worker checks that token and hands the API what it says, in headers the
 * API takes at its word — which it can, because nothing but this Worker can call the API.
 *
 * Webhooks are the exception, on purpose. Their senders cannot sign in, the login lets
 * them through unasked, and so they reach the API with nobody named. What vouches for a
 * webhook is the signature its sender puts on it, and checking that is the API's job.
 *
 * What this Worker holds besides is a credential for calling *upstream*. That says the
 * request came through here, which is a different thing from saying who sent it.
 */

interface Env {
  /** The files this Worker was uploaded with. */
  readonly ASSETS: Fetcher;
  /** Where the API answers. Also the audience of the token presented to it. */
  readonly API_TARGET: string;
  /** A service account key, in the JSON Google issues it as. */
  readonly GCP_SA_KEY: string;
  /** Who signs the tokens saying who signed in. */
  readonly AUTH_ISSUER: string;
  /** Where the keys those tokens are signed with are published. */
  readonly AUTH_KEYS_URL: string;
  /** Which of that issuer's tokens are meant for this app, in this environment. */
  readonly AUTH_AUDIENCE: string;
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

/** Kept the same way, and holding the same kind of thing: the issuer's keys, once fetched. */
let verifier: { readonly builtFrom: string; readonly value: SignInTokenVerifier } | undefined;

const verifierFor = (env: Env): SignInTokenVerifier => {
  const builtFrom = `${env.AUTH_ISSUER} ${env.AUTH_KEYS_URL} ${env.AUTH_AUDIENCE}`;

  if (verifier?.builtFrom !== builtFrom) {
    verifier = {
      builtFrom,
      value: new SignInTokenVerifier(
        env.AUTH_ISSUER,
        env.AUTH_AUDIENCE,
        createRemoteJWKSet(new URL(env.AUTH_KEYS_URL)),
      ),
    };
  }

  return verifier.value;
};

/**
 * Where the login in front of this Worker puts its token.
 *
 * The one place this file knows which login that is. Cloudflare Access sends the token in
 * a cookie too, and recommends this header over it: the cookie is not guaranteed to arrive.
 */
const SIGN_IN_TOKEN_HEADER = 'cf-access-jwt-assertion';

/**
 * The headers the API reads as who is calling, all under one prefix.
 *
 * Taken off every request, whatever its route, and set again only from a token checked
 * here. A prefix rather than a list, so a header added under it later is covered without
 * anyone remembering to add it: a caller able to set one would be choosing who they are.
 */
const IDENTITY_HEADER_PREFIX = 'x-medusa-user-';

const IDENTITY_HEADERS = {
  subject: `${IDENTITY_HEADER_PREFIX}subject`,
  email: `${IDENTITY_HEADER_PREFIX}email`,
} as const;

/**
 * Credentials Google Cloud will authenticate a request with, and which are therefore
 * ours to send and never a caller's.
 *
 * Two products, one design. Cloud Run reads `x-serverless-authorization`, and IAP
 * reads `proxy-authorization` — both exist so that infrastructure can be given a
 * token without spending the `authorization` header an application may want for
 * itself. IAP is explicit about the consequence: a valid ID token there authorizes
 * the request, and `authorization` is then passed through "without processing the
 * content".
 *
 * So these are not headers, they are entrances. This Worker's own credential travels
 * in the first, which is why a caller must not be able to set it: one who could would
 * be arguing with the credential that says we are us. No IAP stands in front of
 * anything here, but one that did would honour the second in the same way.
 *
 * https://cloud.google.com/iap/docs/authentication-howto
 */
const UPSTREAM_CREDENTIALS = ['proxy-authorization', 'x-serverless-authorization'] as const;

/**
 * What belongs to one hop and must not reach the next.
 *
 * The set HTTP defines (RFC 9110 §7.6.1), applied as a rule rather than discovered a
 * header at a time. They describe a conversation between a peer and its immediate
 * neighbour, so relaying them reports one connection's terms as another's.
 *
 * `proxy-authorization` belongs here too, and is listed above instead, under the
 * stronger of its two reasons.
 */
const HOP_BY_HOP = [
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
] as const;

/**
 * Everything this Worker takes off a request before passing it on.
 *
 * What stays is a different question, and not answerable here. `x-forwarded-for` and
 * the rest still travel, and are harmless only because the API behind this reads none
 * of them — nothing is keyed on an address. That is a fact about today's backend rather
 * than a property of this Worker, and on the day it stops being true, spoofing any of
 * them through here becomes trivial.
 */
const NOT_FORWARDED = [
  ...HOP_BY_HOP,
  ...UPSTREAM_CREDENTIALS,

  // The sign-in, as a credential for this hostname — in the header and in the cookie
  // alike. The API is handed what the token says rather than the token, and has no
  // sessions of its own for a cookie to belong to.
  SIGN_IN_TOKEN_HEADER,
  'cookie',

  // Addressed to this origin. Cloud Run routes by the name in the URL, and `fetch`
  // derives that itself; leaving ours on would address the request to a service that
  // does not exist.
  'host',
] as const;

/**
 * The request as the API should see it: carrying proof that this Worker sent it, and
 * naming [caller] — or nobody, for a sender that did not sign in.
 */
const callApi = async (
  request: Request,
  pathname: string,
  env: Env,
  caller: Caller | null,
): Promise<Response> => {
  const idToken = await minterFor(env.GCP_SA_KEY).idTokenFor(env.API_TARGET);

  const target = new URL(pathname + new URL(request.url).search, env.API_TARGET);

  const headers = new Headers(request.headers);

  for (const header of NOT_FORWARDED) {
    headers.delete(header);
  }

  // Collected before deleting, so the deletion is not done to the thing being walked.
  for (const header of [...headers.keys()]) {
    if (header.startsWith(IDENTITY_HEADER_PREFIX)) {
      headers.delete(header);
    }
  }

  if (caller !== null) {
    headers.set(IDENTITY_HEADERS.subject, caller.subject);
    headers.set(IDENTITY_HEADERS.email, caller.email);
  }

  // Whose name the upstream request travels under. Cloud Run reads this header ahead of
  // `authorization` and leaves that one to the application — which a webhook sender may
  // well use for its own credential. Stripped from what the caller sent, so nobody gets
  // here first.
  headers.set('x-serverless-authorization', `Bearer ${idToken}`);

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

/**
 * What a request for the API is told when it carries no sign-in this Worker believes.
 *
 * Forbidden rather than unauthorized: the login stands in front of this hostname, so a
 * request reaching here without a good token did not come the way a signed-in browser
 * does, and there is no challenge to answer it with that it could meet here. Why it was
 * refused is in the log.
 */
const notSignedIn = (): Response => new Response(null, { status: 403 });

/**
 * Runs [call], which reaches upstream, and answers for it when it fails.
 *
 * A key that will not import, keys or a token endpoint that will not answer, an API that
 * cannot be reached. The details are logged where an operator can read them; the browser
 * gets to know only that the API did not answer.
 */
const answeringFailures = async (call: () => Promise<Response>): Promise<Response> => {
  try {
    return await call();
  } catch (failure) {
    console.error('the API could not be called', failure);

    return new Response('the API is unavailable', { status: 502 });
  }
};

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const route = routeFor(new URL(request.url).pathname);

    switch (route.to) {
      case 'assets':
        return env.ASSETS.fetch(request);

      case 'webhooks':
        return answeringFailures(() => callApi(request, route.pathname, env, null));

      case 'api': {
        const token = request.headers.get(SIGN_IN_TOKEN_HEADER);

        if (token === null) {
          console.warn('refusing an API request: it carries no sign-in token');

          return notSignedIn();
        }

        return answeringFailures(async () => {
          const caller = await verifierFor(env).verify(token);

          // Nothing has been spent upstream yet, and if the token is no good nothing will be.
          if (caller === null) {
            return notSignedIn();
          }

          return callApi(request, route.pathname, env, caller);
        });
      }
    }
  },
} satisfies ExportedHandler<Env>;
