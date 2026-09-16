import { execFileSync } from 'node:child_process';

/**
 * The files the browser is served, built by the program that uploads them.
 *
 * The same argument as the Worker's own bundle: a build that ran separately can leave
 * a stale `dist` behind, and the upload would describe it faithfully — a deployment
 * that succeeds and serves the previous version. Building here makes what is planned
 * and what was just compiled the same thing by construction.
 *
 * It also means a preview compiles the app, so a pull request that breaks the build
 * fails its deployment check rather than reaching the merge that would break the site.
 */

/** Relative to the Pulumi project, which is `infra`. */
const PACKAGE = '../frontend';

/**
 * The client the app calls the service through, which is a package of its own next door.
 *
 * Half of it is generated from the contract and is not committed, and installing the app links
 * the package without filling that half in — so it is generated here, before the app that
 * imports it is built.
 */
const CLIENT_PACKAGE = '../api/client';

const npm = (packageDirectory: string, ...args: readonly string[]): void => {
  execFileSync('npm', args, { cwd: packageDirectory, stdio: 'inherit' });
};

/** Builds the app and says where it landed. */
export const buildFrontend = (): string => {
  // The deployment runner installs the dependencies of the Pulumi project and no
  // others, so on a runner there is nothing in either of these packages to build
  // with. `ci` rather
  // than `install` for the usual reason — it takes the lockfile as the answer instead
  // of as a starting point — which on a workstation costs a reinstall of a directory
  // that was probably already correct. That is the cheaper of the two mistakes.
  npm(CLIENT_PACKAGE, 'ci');
  npm(CLIENT_PACKAGE, 'run', 'generate');

  npm(PACKAGE, 'ci');
  npm(PACKAGE, 'run', 'build');

  return `${PACKAGE}/dist`;
};
