import { deploy } from './deploy.ts';
import { requireEnvironment } from './environment.ts';
import { smokeTest } from './smokeTest.ts';

/**
 * Deploys this app to one environment, from the commit being built, and asks whether it serves.
 *
 * One environment, named as the only argument. The order environments go in — and what has to
 * pass between one and the next — is the deploy workflow's to say, where each is a job of its own
 * and a failure is visible as the job it happened in. This does what each of those jobs needs.
 *
 * This lives in the app's own repository, which is a deliberate change from where it used to be.
 * The sequence, the checks and everything around them are the app's to shape; what it costs is
 * that a repository able to run this is a repository able to use the Pulumi credential for
 * whatever that credential permits. The identity is issued to the deploy workflow at its path and
 * no other, so the boundary that remains is which repositories may ask, not what they do once
 * asked.
 *
 * Which environments exist and where each answers are not this repository's to decide: they
 * arrive as variables from the stack that creates the stacks and the hostnames. An environment
 * named here that is not among them is refused, rather than deployed on this repository's word.
 *
 * Nothing is undone on failure. There is no rollback here and none in Pulumi: a deployment that
 * fails partway has already made some of its changes, and the next commit is what fixes it. What
 * the workflow prevents is not a broken environment, but a broken one being copied to the next.
 */

/** `owner/name`, as GitHub gives it. The name is the app, and the Pulumi project. */
const [, app] = requireEnvironment('GITHUB_REPOSITORY').split('/');

if (app === undefined) {
  throw new Error('GITHUB_REPOSITORY is not `owner/name`');
}

const commit = requireEnvironment('GITHUB_SHA');

const [environment, ...unexpected] = process.argv.slice(2);

if (environment === undefined || unexpected.length > 0) {
  throw new Error('Name exactly one environment to deploy');
}

/** The environments this app has, as the platform lists them. */
const environments = requireEnvironment('DEPLOY_ENVIRONMENTS').split(',');

if (!environments.includes(environment)) {
  throw new Error(
    `${environment} is not one of this app's environments: ${environments.join(', ')}`,
  );
}

/** Where each environment answers once it has been deployed. */
const hostname = (
  JSON.parse(requireEnvironment('DEPLOY_HOSTNAMES')) as Readonly<Record<string, string>>
)[environment];

if (hostname === undefined) {
  throw new Error(`No hostname was given for ${environment}`);
}

/**
 * What gets the smoke test past the sign-in in front of every app: a service token, which the
 * login admits as a service rather than as a person. The page and its files are all it asks for.
 */
const smokeTestToken = JSON.parse(requireEnvironment('SMOKE_TEST_ACCESS_TOKEN')) as {
  readonly clientId: string;
  readonly clientSecret: string;
};

await deploy(app, environment, commit);

await smokeTest(`https://${hostname}`, {
  'cf-access-client-id': smokeTestToken.clientId,
  'cf-access-client-secret': smokeTestToken.clientSecret,
});
