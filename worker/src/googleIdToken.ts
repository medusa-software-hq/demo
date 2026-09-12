/**
 * Google ID tokens, from a service account key.
 *
 * A Worker has no metadata server and no identity Workload Identity Federation will
 * accept, so calling an IAM-locked Cloud Run service means holding a key and doing by
 * hand what a Google client library would do: sign an assertion, exchange it for an ID
 * token whose audience is the service being called.
 *
 * The same mechanism, and deliberately the same shape, as the one in `todo-board`. Two
 * spellings of one protocol would be two places to be wrong about it.
 */

const TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';
const JWT_BEARER_GRANT = 'urn:ietf:params:oauth:grant-type:jwt-bearer';

/** How long an assertion is valid. Google rejects anything longer than an hour. */
const ASSERTION_LIFETIME_SECONDS = 3600;

/** Refresh this long before expiry, so a token is never handed over about to die. */
const REFRESH_MARGIN_SECONDS = 300;

/** The parts of a service account key this needs. The key has more; none of it is wanted. */
export interface ServiceAccountKey {
  readonly client_email: string;
  readonly private_key: string;
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

  constructor(private readonly key: ServiceAccountKey) {
    // Imported once and awaited per call: parsing the key on every request would be
    // work repeated for nothing, and one isolate may serve very many.
    this.signingKey = importPrivateKey(key.private_key);
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

    const response = await fetch(TOKEN_ENDPOINT, {
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

  /** The signed JWT that is exchanged for an ID token. */
  private async signAssertion(audience: string, nowSeconds: number): Promise<string> {
    const header = { alg: 'RS256', typ: 'JWT' };

    const claims = {
      iss: this.key.client_email,
      sub: this.key.client_email,
      // What the assertion is *for*: Google's token endpoint, not the service called.
      aud: TOKEN_ENDPOINT,
      iat: nowSeconds,
      exp: nowSeconds + ASSERTION_LIFETIME_SECONDS,
      // What the minted token will be for. Cloud Run checks this against itself.
      target_audience: audience,
    };

    const signingInput = `${base64Url(JSON.stringify(header))}.${base64Url(JSON.stringify(claims))}`;

    const signature = await crypto.subtle.sign(
      'RSASSA-PKCS1-v1_5',
      await this.signingKey,
      new TextEncoder().encode(signingInput),
    );

    return `${signingInput}.${base64UrlBytes(new Uint8Array(signature))}`;
  }
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const body = pem
    .replace('-----BEGIN PRIVATE KEY-----', '')
    .replace('-----END PRIVATE KEY-----', '')
    .replace(/\s+/g, '');

  const der = Uint8Array.from(atob(body), (character) => character.charCodeAt(0));

  return crypto.subtle.importKey(
    'pkcs8',
    der,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  );
}

function base64Url(text: string): string {
  return base64UrlBytes(new TextEncoder().encode(text));
}

function base64UrlBytes(bytes: Uint8Array): string {
  let binary = '';

  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }

  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
