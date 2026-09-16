import type { TestCaller, TestEnvironment } from '../environment.ts';
import { startEdge } from './edge.ts';
import { startFakeAccess } from './fakeAccess.ts';
import { startFakeGoogle } from './fakeGoogle.ts';
import { startLocalStack } from './localStack.ts';
import { randomBytes } from 'node:crypto';

/** Where Access puts the token for a request it let through, and so where the Worker reads it. */
const SIGN_IN_TOKEN_HEADER = 'cf-access-jwt-assertion';

/** A service token's client id, in the form Access gives them. */
const newClientId = (): string => `${randomBytes(16).toString('hex')}.access`;

/**
 * The app, started on this machine and reached through the real Worker.
 *
 * The callers are services, as they are on a deployed environment, so the suites exercise the same
 * way in wherever they run. People are what the local abilities add.
 */
export const startLocalEnvironment = async (): Promise<TestEnvironment> => {
  const stopping: (() => Promise<void>)[] = [];

  // In reverse, so nothing is stopped while something started after it still depends on it.
  const stop = async (): Promise<void> => {
    for (let next = stopping.pop(); next !== undefined; next = stopping.pop()) {
      await next();
    }
  };

  try {
    const stack = await startLocalStack();
    stopping.push(() => stack.stop());

    const access = await startFakeAccess();
    stopping.push(() => access.close());

    const google = await startFakeGoogle();
    stopping.push(() => google.close());

    const edge = await startEdge({
      apiTarget: `http://localhost:${stack.servicePort}`,
      serviceAccountKey: google.serviceAccountKey,
      issuer: access.issuer,
      keysUrl: access.keysUrl,
      audience: access.audience,
    });
    stopping.push(() => edge.close());

    const carrying = (name: string, token: string): TestCaller => ({
      name,
      headers: { [SIGN_IN_TOKEN_HEADER]: token },
    });

    return {
      origin: edge.origin,

      callers: [
        carrying('a service', await access.serviceTokenFor(newClientId())),
        carrying('another service', await access.serviceTokenFor(newClientId())),
      ],

      local: {
        person: async (subject, email) =>
          carrying(email, await access.personTokenFor(subject, email)),

        forged: async () => carrying('a forger', await access.forgedToken()),

        signedForAnotherApplication: async () =>
          carrying('somebody from elsewhere', await access.tokenForAnotherApplication()),
      },

      stop,
    };
  } catch (failure) {
    await stop();

    throw failure;
  }
};
