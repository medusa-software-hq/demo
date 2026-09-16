import { importPKCS8, SignJWT } from 'jose';

/**
 * Google ID tokens, from a service account key.
 *
 * A Worker has no metadata server and no identity Workload Identity Federation will
 * accept, so calling an IAM-locked Cloud Run service means holding a key and doing by
 * hand what a Google client library would do: sign an assertion, exchange it for an ID
 * token whose audience is the service being called.
 *
 * The exchange is the part written out here. The signing is not: `jose` knows what a
 * PKCS#8 key looks like and what a JWT looks like, and both are things it is easy to
 * be quietly wrong about — a key in the other PEM format, a base64url alphabet with
 * padding left on. Those produce a signature Google rejects, and no clue as to why.
 */

/**
 * Where assertions are exchanged, for a key that does not say.
 *
 * Every key Google issues does say, as `token_uri`, and Google's own client libraries go where it
 * points rather than where they would have guessed. So does this — which is also what lets
 * something other than Google play the token endpoint's part, with nothing here knowing.
 */
const DEFAULT_TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';
const JWT_BEARER_GRANT = 'urn:ietf:params:oauth:grant-type:jwt-bearer';

/** What Google issues service account keys for, and so what the assertion is signed with. */
const SIGNING_ALGORITHM = 'RS256';

/** How long an assertion is valid. Google rejects anything longer than an hour. */
const ASSERTION_LIFETIME_SECONDS = 3600;

/** Refresh this long before expiry, so a token is never handed over about to die. */
const REFRESH_MARGIN_SECONDS = 300;

/** The parts of a service account key this needs. The key has more; none of it is wanted. */
export interface ServiceAccountKey {
  readonly client_email: string;
  readonly private_key: string;
  readonly token_uri?: string;
}

/** A minted token, and when it stops being worth reusing. */
interface CachedToken {
  readonly idToken: string;
  readonly reusableUntil: number;
}

/**
 * Mints ID tokens, and holds each one until it is nearly expired.
 *
 * Keyed by audience: an ID token names the service it may be presented to, so a token
 * for the API is not a token for anything else.
 */
export class GoogleIdTokenMinter {
  private readonly cache = new Map<string, CachedToken>();
  private readonly signingKey: Promise<CryptoKey>;
  private readonly tokenEndpoint: string;

  constructor(private readonly key: ServiceAccountKey) {
    this.tokenEndpoint = key.token_uri ?? DEFAULT_TOKEN_ENDPOINT;

    // Imported once and awaited per call: parsing the key on every request would be
    // work repeated for nothing, and one isolate may serve very many.
    this.signingKey = importPKCS8(key.private_key, SIGNING_ALGORITHM);
  }

  /** An ID token for [audience], minted or reused. */
  async idTokenFor(audience: string): Promise<string> {
    const nowSeconds = Math.floor(Date.now() / 1000);
    const cached = this.cache.get(audience);

    if (cached !== undefined && cached.reusableUntil > nowSeconds) {
      return cached.idToken;
    }

    const idToken = await this.mint(audience, nowSeconds);

    this.cache.set(audience, {
      idToken,
      reusableUntil: nowSeconds + ASSERTION_LIFETIME_SECONDS - REFRESH_MARGIN_SECONDS,
    });

    return idToken;
  }

  private async mint(audience: string, nowSeconds: number): Promise<string> {
    const assertion = await this.signAssertion(audience, nowSeconds);

    const response = await fetch(this.tokenEndpoint, {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: JWT_BEARER_GRANT, assertion }),
    });

    if (!response.ok) {
      // The body says which of several things is wrong — a key that has expired, a
      // clock too far out, an account that no longer exists — and none of it belongs
      // in a reply to whoever happened to be asking.
      console.error('minting an ID token failed', response.status, await response.text());

      throw new Error('could not mint an ID token');
    }

    const body = (await response.json()) as { readonly id_token?: string };

    if (body.id_token === undefined) {
      console.error('the token endpoint answered without an id_token');

      throw new Error('could not mint an ID token');
    }

    return body.id_token;
  }

  /**
   * The signed JWT that is exchanged for an ID token.
   *
   * Two audiences, which is the part worth reading twice: `aud` is who the assertion
   * is presented to — the token endpoint — while `target_audience` is what the
   * token it buys will be for. Cloud Run checks the second against itself.
   */
  private async signAssertion(audience: string, nowSeconds: number): Promise<string> {
    return new SignJWT({ target_audience: audience })
      .setProtectedHeader({ alg: SIGNING_ALGORITHM, typ: 'JWT' })
      .setIssuer(this.key.client_email)
      .setSubject(this.key.client_email)
      .setAudience(this.tokenEndpoint)
      .setIssuedAt(nowSeconds)
      .setExpirationTime(nowSeconds + ASSERTION_LIFETIME_SECONDS)
      .sign(await this.signingKey);
  }
}
