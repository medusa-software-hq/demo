import * as cloudflare from '@pulumi/cloudflare';
import * as pulumi from '@pulumi/pulumi';
import { buildSync } from 'esbuild';

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
    target: 'es2022',
    platform: 'neutral',
    write: false,
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
  },
  // Adopted rather than created: the platform stack made it, so that a hostname could
  // point at something before this repository had ever deployed.
  { import: `${accountId}/${scriptName}` },
);
