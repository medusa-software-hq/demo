import * as cloudflare from '@pulumi/cloudflare';
import * as pulumi from '@pulumi/pulumi';
import { buildSync } from 'esbuild';
import { buildFrontend } from './frontend.ts';

/**
 * What this app serves.
 *
 * The Worker itself, its hostname and its certificate belong to the platform stack,
 * which creates it holding a placeholder. This owns only what it returns — so the
 * account, the name and the credential all arrive through the environment, and nothing
 * here names any of them.
 */

const config = new pulumi.Config();
const accountId = config.require('cloudflareAccountId');
const scriptName = config.require('workerName');

/** Relative to the Pulumi project, which is `infra`. */
const ENTRY = '../worker/src/index.ts';

/** Kept in step with the worker's own `tsconfig.json`, which type-checking uses. */
const TARGET = 'es2022';

/**
 * Bundled here rather than by a step before this one.
 *
 * A build that runs separately can leave a stale artifact behind, and the upload would
 * describe it faithfully — the deployment succeeds and serves the previous version.
 * Producing the bundle inside the program that uploads it makes that impossible: what
 * is planned is what was just compiled from source.
 */
const bundle = (): string => {
  const { outputFiles } = buildSync({
    entryPoints: [ENTRY],
    bundle: true,
    format: 'esm',
    target: TARGET,
    platform: 'neutral',
    write: false,

    // Stated rather than read from the worker's tsconfig.json, which extends a preset
    // living in that package's node_modules — and the deployment runner installs this
    // package's dependencies, not that one's. esbuild would warn that it cannot find
    // the base config and carry on under different rules, so the bundle built in the
    // pipeline would not be the bundle built here.
    //
    // These are the options that change emitted code. Everything else in that file is
    // about type-checking, which `tsc` does, and which esbuild does not attempt.
    tsconfigRaw: {
      compilerOptions: {
        target: TARGET,
        // What ES2022 implies for `tsc`, said out loud so an esbuild default cannot
        // quietly disagree with it.
        useDefineForClassFields: true,
      },
    },
  });

  const [output] = outputFiles;
  if (output === undefined) {
    throw new Error(`Bundling ${ENTRY} produced no output`);
  }

  return output.text;
};

export const worker = new cloudflare.WorkersScript(
  'site',
  {
    accountId,
    scriptName,
    mainModule: 'index.js',
    compatibilityDate: '2026-09-01',
    content: bundle(),

    /**
     * The files the browser is served, uploaded alongside the code that serves
     * everything else.
     *
     * A request naming one of them is answered from here without the Worker running
     * at all, which is both faster and cheaper than answering it from code. Only what
     * is left over reaches the Worker.
     */
    assets: {
      directory: buildFrontend(),
      config: {
        // One page, many routes. A URL that names no file is a route the app knows
        // about, so the app is the right thing to answer with.
        notFoundHandling: 'single-page-application',
      },
    },

    // How the Worker reaches the files above, for the requests that got past them.
    bindings: [{ name: 'ASSETS', type: 'assets' }],
  },
  // Adopted rather than created: the platform stack made it, so that a hostname could
  // point at something before this repository had ever deployed.
  { import: `${accountId}/${scriptName}` },
);
