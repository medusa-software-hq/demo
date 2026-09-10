import * as pulumi from '@pulumi/pulumi';

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
