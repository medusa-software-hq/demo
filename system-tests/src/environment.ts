import { createDemoApiClient, type DemoApiClient } from 'demo-client';

/**
 * Somebody the tests call as.
 *
 * Described by what their requests carry rather than by who they are: what an environment can
 * prove about a caller differs from one environment to the next, and a test only ever needs to
 * send it.
 */
export interface TestCaller {
  /** How the test report refers to them. */
  readonly name: string;

  /** Sent with every request made as this caller. */
  readonly headers: Readonly<Record<string, string>>;
}

/**
 * What only an environment whose login is played by the tests can do.
 *
 * A real login decides who gets past it, so the tests cannot hand it a forged token or a request
 * carrying none and watch what happens behind it. Where the login is played here, they can — and
 * that is where the Worker's refusals are shown to work.
 */
export interface LocalAbilities {
  /** Somebody the login signed in as a person, rather than as a service. */
  person(subject: string, email: string): Promise<TestCaller>;

  /** A token of the right shape, signed with a key the login never published. */
  forged(): Promise<TestCaller>;

  /** A token the login signed, for some other application. */
  signedForAnotherApplication(): Promise<TestCaller>;
}

/** An app to test, reached the way a browser reaches it. */
export interface TestEnvironment {
  /** The origin a browser uses. The API answers under `/api`, and webhooks under `/webhooks`. */
  readonly origin: string;

  /** Two callers who are not each other, so that what is personal can be shown to be. */
  readonly callers: readonly [TestCaller, TestCaller];

  /** Present where the login is played by the tests, and absent wherever it is real. */
  readonly local: LocalAbilities | undefined;

  /** Stops whatever was started for the tests. Nothing, for an environment that was already there. */
  stop(): Promise<void>;
}

/** The API of [environment], called as [caller]. */
export const apiClientFor = (environment: TestEnvironment, caller: TestCaller): DemoApiClient =>
  createDemoApiClient(`${environment.origin}/api`, { headers: caller.headers });

/** What only a local environment can do, for a test that has already been skipped everywhere else. */
export const localAbilitiesOf = (environment: TestEnvironment): LocalAbilities => {
  if (environment.local === undefined) {
    throw new Error('This test needs an environment whose login is played by the tests');
  }

  return environment.local;
};
