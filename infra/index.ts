import * as pulumi from '@pulumi/pulumi';
import { databaseTarget } from './database.ts';
import { edgeServiceAccount } from './edge-identity.ts';
import { serviceUrl } from './service.ts';
import { worker } from './worker.ts';

/**
 * The demo application's own infrastructure.
 *
 * Credentials and the target project arrive from the ESC environment the platform
 * stack created for this pair, so nothing here names either. Adding an environment is
 * a change to the platform stack's model, not to this program.
 */

/** Where this environment's Google Cloud resources belong. */
export const project = new pulumi.Config('gcp').require('project');

/** Which environment this stack is, under the name the platform stack gave it. */
export const environment = pulumi.getStack();

/** The Worker this app's contents are uploaded to. */
export const workerName = worker.scriptName;

/** Where this environment's API answers, behind the Worker that routes to it. */
export const apiUrl = serviceUrl;

/** Who the Worker calls the API as. The only member holding `run.invoker` on it. */
export const edgeIdentity = edgeServiceAccount;

/** Where this environment's database is, without the part that gets you in. */
export const database = databaseTarget;
