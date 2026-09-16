import { exportJWK, generateKeyPair, SignJWT, type JWTPayload } from 'jose';
import { serve } from './servers.ts';
import { randomBytes } from 'node:crypto';

/**
 * Cloudflare Access, as far as the Worker can tell.
 *
 * Which is not very far: the Worker knows Access as an issuer, a URL its keys are published at, and
 * an audience. So this is an issuer with a key pair of its own, publishing the public half where
 * Access would, and signing tokens with the private half that carry the claims Access puts in them.
 */
export interface FakeAccess {
  readonly issuer: string;
  readonly keysUrl: string;
  readonly audience: string;

  /** What Access issues for a service token with [clientId]. */
  serviceTokenFor(clientId: string): Promise<string>;

  /** What Access issues for a person it signed in. */
  personTokenFor(subject: string, email: string): Promise<string>;

  /** The right claims, signed with a key Access never published. */
  forgedToken(): Promise<string>;

  /** A token Access signed, for an application that is not this one. */
  tokenForAnotherApplication(): Promise<string>;

  close(): Promise<void>;
}

/** Where Access publishes its keys, beneath its issuer. */
const KEYS_PATH = '/cdn-cgi/access/certs';

const KEY_ID = 'local';

/** Longer than any test run, so no token has to be minted twice. */
const TOKEN_LIFETIME_SECONDS = 3600;

export const startFakeAccess = async (): Promise<FakeAccess> => {
  const signer = await generateKeyPair('RS256');
  const impostor = await generateKeyPair('RS256');

  const keySet = JSON.stringify({
    keys: [{ ...(await exportJWK(signer.publicKey)), kid: KEY_ID, alg: 'RS256', use: 'sig' }],
  });

  const server = await serve((request, response) => {
    if (request.method === 'GET' && request.url === KEYS_PATH) {
      response.writeHead(200, { 'content-type': 'application/json' }).end(keySet);

      return;
    }

    response.writeHead(404).end();
  });

  const issuer = server.origin;
  const audience = randomBytes(32).toString('hex');

  /** A token carrying [claims] and everything Access puts beside them. */
  const sign = async (
    claims: JWTPayload,
    { key = signer.privateKey, forAudience = audience } = {},
  ): Promise<string> => {
    const nowSeconds = Math.floor(Date.now() / 1000);

    return new SignJWT({
      type: 'app',
      ...claims,
      iss: issuer,
      // A list, as Access sends it.
      aud: [forAudience],
      iat: nowSeconds,
      nbf: nowSeconds,
      exp: nowSeconds + TOKEN_LIFETIME_SECONDS,
    })
      .setProtectedHeader({ alg: 'RS256', kid: KEY_ID })
      .sign(key);
  };

  return {
    issuer,
    keysUrl: `${issuer}${KEYS_PATH}`,
    audience,

    // An empty subject and the client id in `common_name`, which is how Access marks a service.
    serviceTokenFor: (clientId) => sign({ sub: '', common_name: clientId }),

    personTokenFor: (subject, email) => sign({ sub: subject, email }),

    forgedToken: () =>
      sign({ sub: '', common_name: 'forged.access' }, { key: impostor.privateKey }),

    tokenForAnotherApplication: () =>
      sign({ sub: '', common_name: 'elsewhere.access' }, { forAudience: 'another-application' }),

    close: () => server.close(),
  };
};
