import { execFileSync } from 'node:child_process';

/**
 * Which commit this deployment is of.
 *
 * Read from the checkout rather than from the deployment's environment, so that it
 * means the same thing on a workstation as it does on a runner — and so that there is
 * no variable to be absent in one of the two places.
 *
 * It is compiled into what gets deployed, which means every commit produces a new
 * bundle even when nothing else about it changed. That is the intent: a deployment
 * that reports a commit it was not built from would be worse than one that reports
 * nothing at all.
 */
export const commit = ((): string => {
  const revision = execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();

  if (!/^[0-9a-f]{40}$/.test(revision)) {
    throw new Error(`Expected a commit hash, got ${JSON.stringify(revision)}`);
  }

  return revision;
})();
