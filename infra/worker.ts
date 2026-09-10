import * as cloudflare from '@pulumi/cloudflare';
import * as pulumi from '@pulumi/pulumi';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';

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
const SOURCE = '../worker/index.js';

const source = readFileSync(new URL(SOURCE, import.meta.url));

export const worker = new cloudflare.WorkersScript(
  'site',
  {
    accountId,
    scriptName,
    mainModule: 'index.js',
    compatibilityDate: '2026-09-01',
    contentFile: SOURCE,
    // Changing the file changes this, which is what tells Pulumi to upload again.
    contentSha256: createHash('sha256').update(source).digest('hex'),
  },
  // Adopted rather than created: the platform stack made it, so that a hostname could
  // point at something before this repository had ever deployed.
  { import: `${accountId}/${scriptName}` },
);
