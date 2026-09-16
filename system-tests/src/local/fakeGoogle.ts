import { exportPKCS8, generateKeyPair } from 'jose';
import { serve } from './servers.ts';

/**
 * Google's token endpoint, as far as the Worker can tell, and a service account key that sends the
 * Worker to it.
 *
 * The Worker exchanges a signed assertion for an ID token on every call it makes upstream, at the
 * endpoint its key names. Here the key names this, and this answers with a token nobody checks:
 * the local Service is not behind Cloud Run, which is the only thing that would. What is being
 * shown is that the Worker gets a token the way it does when deployed, and carries on with it.
 */
export interface FakeGoogle {
  /** A key in the JSON Google issues keys as, pointing at this endpoint. */
  readonly serviceAccountKey: string;

  close(): Promise<void>;
}

const JWT_BEARER_GRANT = 'urn:ietf:params:oauth:grant-type:jwt-bearer';

export const startFakeGoogle = async (): Promise<FakeGoogle> => {
  const server = await serve((request, response) => {
    if (request.method !== 'POST' || request.url !== '/token') {
      response.writeHead(404).end();

      return;
    }

    let body = '';

    request.setEncoding('utf8');
    request.on('data', (chunk: string) => {
      body += chunk;
    });

    request.on('end', () => {
      // The one thing worth refusing: a request that is not the exchange the Worker makes.
      if (new URLSearchParams(body).get('grant_type') !== JWT_BEARER_GRANT) {
        response.writeHead(400).end();

        return;
      }

      response
        .writeHead(200, { 'content-type': 'application/json' })
        .end(JSON.stringify({ id_token: 'a-token-nothing-local-checks' }));
    });
  });

  const { privateKey } = await generateKeyPair('RS256', { extractable: true });

  return {
    serviceAccountKey: JSON.stringify({
      type: 'service_account',
      client_email: 'edge@local.invalid',
      private_key: await exportPKCS8(privateKey),
      token_uri: `${server.origin}/token`,
    }),

    close: () => server.close(),
  };
};
