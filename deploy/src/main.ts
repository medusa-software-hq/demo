import { deploy } from './deploy.ts';
import { requireEnvironment } from './environment.ts';
import { smokeTest } from './smokeTest.ts';

/**
 * Deploys this repository, in the order the environments have to happen in.
 *
 * Every environment is deployed from the same commit and then asked whether it
 * serves. A failure stops the sequence where it happened, so an environment is only
 * reached once the one before it was observed working — which is the entire content
 * of the word "staging".
 *
 * Nothing is undone on failure. There is no rollback here and none in Pulumi: a
 * deployment that fails partway has already made some of its changes, and the next
 * commit is what fixes it. What this prevents is not a broken environment, but a
 * broken environment being copied to the next one.
 */

/** In order. Each environment's stack is this repository's; its hostname is not. */
const SEQUENCE = [
  { stack: 'staging', url: 'STAGING_URL' },
  { stack: 'production', url: 'PRODUCTION_URL' },
] as const;

const commit = requireEnvironment('GITHUB_SHA');

for (const { stack, url } of SEQUENCE) {
  await deploy(stack, commit);
  await smokeTest(requireEnvironment(url));
}
