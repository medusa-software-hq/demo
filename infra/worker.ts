import * as cloudflare from '@pulumi/cloudflare';
import * as pulumi from '@pulumi/pulumi';
import { buildSync } from 'esbuild';
import { edgeKeyJson } from './edge-identity.ts';
import { buildFrontend } from './frontend.ts';
import { serviceUrl } from './service.ts';
import { execFileSync } from 'node:child_process';

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

/**
 * What checking a sign-in takes: who issues the tokens, where their keys are published,
 * and which of them are meant for this environment.
 *
 * Handed over by the platform, which puts the login in front of this hostname and so is
 * the one that knows what its tokens look like. Nothing here says which login that is.
 */
const auth = {
  issuer: config.require('authIssuer'),
  keysUrl: config.require('authKeysUrl'),
  audience: config.require('authAudience'),
};

/** Relative to the Pulumi project, which is `infra`. */
const PACKAGE = '../worker';
const ENTRY = `${PACKAGE}/src/index.ts`;

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
  // The same reason `frontend.ts` does this: the deployment runner installs the
  // dependencies of the Pulumi project and no others, so on a runner there is nothing
  // here for the bundler to resolve `jose` from. It resolves from the importing file's
  // directory, not from this one, so infra having its own copy would not help.
  execFileSync('npm', ['ci'], { cwd: PACKAGE, stdio: 'inherit' });

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

        /**
         * Except for the paths that are not the page's at all.
         *
         * Single-page handling answers a browser's navigation to a path no file matches with the
         * page, before the Worker is asked — so a browser opening a webhook or API address would
         * be shown the app, and never reach the thing the address names. Requests from anything
         * but a browser were never affected, which is how this went unnoticed. The Worker runs
         * first on these, whoever is asking.
         *
         * The same prefixes `routing.ts` gives to the API, each with and without what follows it:
         * a rule ending in a wildcard does not match the bare prefix.
         */
        runWorkerFirst: ['/api', '/api/*', '/webhooks', '/webhooks/*'],
      },
    },

    bindings: [
      // How the Worker reaches the files above, for the requests that got past them.
      { name: 'ASSETS', type: 'assets' },

      /**
       * And how it reaches the API, for the requests that are for the API.
       *
       * The URL is Cloud Run's to choose, so it is read from the service rather than
       * written down here — which also orders the two, since a Worker told to call an
       * address that does not exist yet would serve errors until the next deployment.
       *
       * The same value twice over, in effect: it is where the request goes, and it is
       * the audience of the token that goes with it. Cloud Run checks the second
       * against itself, so a token minted for anywhere else is refused.
       */
      { name: 'API_TARGET', type: 'plain_text', text: serviceUrl },

      /**
       * What it signs with.
       *
       * `secret_text` rather than `plain_text` so it is write-only at Cloudflare:
       * deployable, and not readable back out of the dashboard or the API.
       */
      { name: 'GCP_SA_KEY', type: 'secret_text', text: edgeKeyJson },

      /**
       * How it tells who signed in. None of these is a credential — each only describes
       * what a valid token looks like — so plain text, readable back like the rest.
       */
      { name: 'AUTH_ISSUER', type: 'plain_text', text: auth.issuer },
      { name: 'AUTH_KEYS_URL', type: 'plain_text', text: auth.keysUrl },
      { name: 'AUTH_AUDIENCE', type: 'plain_text', text: auth.audience },
    ],
  },
  // Adopted rather than created: the platform stack made it, so that a hostname could
  // point at something before this repository had ever deployed.
  { import: `${accountId}/${scriptName}` },
);
