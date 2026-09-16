import { deploy } from './deploy.ts';
import { requireEnvironment } from './environment.ts';
import { smokeTest } from './smokeTest.ts';

/**
 * Deploys this app's environments, in the order they have to happen in.
 *
 * Every environment is deployed from the same commit and then asked whether it serves. A failure
 * stops the sequence where it happened, so an environment is only reached once the one before it
 * was observed working — which is the entire content of the word "staging".
 *
 * This lives in the app's own repository, which is a deliberate change from where it used to be.
 * The sequence, the smoke test and everything around them are the app's to shape; what it costs
 * is that a repository able to run this is a repository able to use the Pulumi credential for
 * whatever that credential permits. The identity is issued to this file at this path and no
 * other, so the boundary that remains is which repositories may ask, not what they do once asked.
 *
 * Which environments exist, in which order, and where each answers are not this repository's to
 * decide: they arrive as variables from the stack that creates the stacks and the hostnames.
 * Restating them here would be a second copy of a fact that is decided elsewhere.
 *
 * Nothing is undone on failure. There is no rollback here and none in Pulumi: a deployment that
 * fails partway has already made some of its changes, and the next commit is what fixes it. What
 * this prevents is not a broken environment, but a broken environment being copied to the next.
 */

/** `owner/name`, as GitHub gives it. The name is the app, and the Pulumi project. */
const [, app] = requireEnvironment('GITHUB_REPOSITORY').split('/');

if (app === undefined) {
  throw new Error('GITHUB_REPOSITORY is not `owner/name`');
}

const commit = requireEnvironment('GITHUB_SHA');

/** In order. A set would not do: staging is only staging because production comes after it. */
const environments = requireEnvironment('DEPLOY_ENVIRONMENTS').split(',');

/** Where each environment answers once it has been deployed. */
const hostnames = JSON.parse(requireEnvironment('DEPLOY_HOSTNAMES')) as Readonly<
  Record<string, string>
>;

/**
 * What gets the smoke test past the sign-in in front of every app: a service token, which the
 * login admits to the page and its files, and which names nobody, so nothing behind them that
 * needs a person will answer it.
 */
const smokeTestToken = JSON.parse(requireEnvironment('SMOKE_TEST_ACCESS_TOKEN')) as {
  readonly clientId: string;
  readonly clientSecret: string;
};

const smokeTestHeaders = {
  'cf-access-client-id': smokeTestToken.clientId,
  'cf-access-client-secret': smokeTestToken.clientSecret,
};

for (const environment of environments) {
  const hostname = hostnames[environment];

  if (hostname === undefined) {
    throw new Error(`No hostname was given for ${environment}`);
  }

  await deploy(app, environment, commit);
  await smokeTest(`https://${hostname}`, smokeTestHeaders);
}
