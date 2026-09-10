import * as gcp from '@pulumi/gcp';
import * as pulumi from '@pulumi/pulumi';
import { execFileSync } from 'node:child_process';

/**
 * What answers the API, and where it runs.
 *
 * Cloud Run is enabled by the platform stack rather than here, which looks like the
 * wrong side of the boundary and is not: a service pulls its image as the project's
 * Cloud Run agent, and that account does not exist until the service is enabled. The
 * grant that lets it read this app's registry is the platform stack's to make, so the
 * enablement it depends on has to be as well. Two owners of one fact would take turns
 * undoing each other.
 *
 * The image is built and pushed before this ever runs — by a workflow, on merge —
 * because a deployment that also built its own container would be free to deploy
 * something no pull request ever saw. This only says which image, and the answer is
 * whichever one this commit's sources produced.
 */

const config = new pulumi.Config();
const gcpConfig = new pulumi.Config('gcp');

const project = gcpConfig.require('project');

/** Both handed down: the registry is in a project this app cannot see. */
const region = gcpConfig.require('region');
const imageRegistry = config.require('imageRegistry');

/**
 * Every plan carries one warning from the provider, and it is expected:
 *
 *   failed to get regions list: … constraints/gcp.restrictServiceUsage … for
 *   'compute.googleapis.com'
 *
 * The provider lists Compute regions when it starts, and the organization denies
 * Compute to keep apps away from things that bill by the hour. Nothing here needs it
 * — Cloud Run is serverless and plans correctly without it — and the warning appears
 * whether or not a region is configured, so there is nothing to configure away.
 * Silencing it would mean allowing an app to run virtual machines, which is a worse
 * trade than a warning somebody has written down.
 */

/**
 * The name the image was pushed under, worked out the same way the workflow that
 * pushed it worked it out — by running the same script.
 *
 * Repeating the rule here instead would be two definitions of one name, and the
 * failure would be a deployment asking for an image nobody built.
 */
const imageTag = (): string => execFileSync('../backend/image-tag.sh', { encoding: 'utf8' }).trim();

export const service = new gcp.cloudrunv2.Service('service', {
  project,
  location: region,
  name: 'service',

  // Reached from the Internet, for now. The Worker in front of it is the only thing
  // that should be talking to it, and making that true is a later change — until
  // then this is deliberately open, and there is nothing behind it but counters
  // that vanish on restart.
  ingress: 'INGRESS_TRAFFIC_ALL',

  template: {
    containers: [
      {
        image: pulumi.interpolate`${imageRegistry}/service:${imageTag()}`,

        // The JVM wants more than the 512Mi default before it will start promptly.
        resources: { limits: { cpu: '1', memory: '1Gi' } },
      },
    ],

    /**
     * One instance, at most.
     *
     * The counters live in the process, so a second instance would hold a second
     * set of them and which one a request reached would decide what it saw. That
     * is not a scaling limit to be raised later — it is what an in-memory store
     * means, and raising it without moving the store somewhere shared would produce
     * a bug that looks like the service forgetting things at random.
     *
     * Down to zero when nothing is asking, which costs nothing and forgets
     * everything. Both are fine for what this is.
     */
    scaling: { minInstanceCount: 0, maxInstanceCount: 1 },
  },

  // Exploration phase: `destroy` should actually destroy.
  deletionProtection: false,
});

/**
 * Answerable by anyone, which is the point for now: there is no sign-in yet, and the
 * page in front of it is public too.
 */
new gcp.cloudrunv2.ServiceIamMember('public', {
  project,
  location: service.location,
  name: service.name,
  role: 'roles/run.invoker',
  member: 'allUsers',
});

/** Where the service answers. Cloud Run chooses this; nothing here can. */
export const serviceUrl = service.uri;
