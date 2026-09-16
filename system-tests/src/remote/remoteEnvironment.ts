import type { TestCaller, TestEnvironment } from '../environment.ts';

/** A Cloudflare Access service token, as the platform stores it. */
interface ServiceToken {
  readonly clientId: string;
  readonly clientSecret: string;
}

/**
 * An app that is really deployed, reached through the login really in front of it.
 *
 * The tests get past that login the way any service does, by presenting a service token, and the
 * login decides — by its own configuration — whether that token may reach this app at all. Two
 * tokens, because a deployed environment has to show that personal data is personal too.
 *
 * [serviceTokensJson] is a list of `{ clientId, clientSecret }`, as the platform stores it.
 */
export const remoteEnvironment = (origin: string, serviceTokensJson: string): TestEnvironment => {
  if (origin.endsWith('/')) {
    throw new Error(`An origin has no trailing slash: ${origin}`);
  }

  const [first, second] = JSON.parse(serviceTokensJson || '[]') as readonly ServiceToken[];

  if (first === undefined || second === undefined) {
    throw new Error('Testing a deployed environment needs two service tokens');
  }

  const presenting = (name: string, token: ServiceToken): TestCaller => ({
    name,
    headers: {
      'cf-access-client-id': token.clientId,
      'cf-access-client-secret': token.clientSecret,
    },
  });

  return {
    origin,
    callers: [presenting('a service', first), presenting('another service', second)],
    local: undefined,
    stop: () => Promise.resolve(),
  };
};
