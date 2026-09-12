import * as gcp from '@pulumi/gcp';
import * as pulumi from '@pulumi/pulumi';
import { execFileSync } from 'node:child_process';

/**
 * What answers the API, and where it runs.
 *
 * Everything about running on Cloud Run is decided here, including the three steps
 * that make an image reachable: enabling the service, bringing its agent into
 * existence, and letting that agent read this app's registry. The platform stack
 * hands over a project and a registry the app administers; what runs in one and pulls
 * from the other is this program's business. An app that moved to a virtual machine
 * would rewrite this file and nothing above it.
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
/**
 * Where this app's images live, in the three parts a registry has.
 *
 * Handed over apart rather than joined, so each API can be given the shape it asks
 * for: IAM wants them separately, and a container reference wants them run together.
 * Taking one apart to get the other would be reading a string somebody else built and
 * hoping it was built the way this expects.
 */
const imageProject = config.require('imageProject');
const imageLocation = config.require('imageLocation');
const imageRepository = config.require('imageRepository');

/** Artifact Registry's published form, assembled where it is needed. */
const imageRegistry = `${imageLocation}-docker.pkg.dev/${imageProject}/${imageRepository}`;

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

/** See the annotation below. Any different value will do; the next one up is simplest. */
const REDEPLOY = '1';

const runService = new gcp.projects.Service('run', {
  project,
  service: 'run.googleapis.com',
  disableOnDestroy: false,
});

/**
 * The account that pulls the image, which is not the account that deploys it.
 *
 * Cloud Run pulls as the project's own agent. Asking for the identity returns the
 * name it will have; what brings the account into existence is the service being
 * enabled, so this waits for that — and the grant below waits for this, because
 * granting to a name nothing has created is refused.
 */
const runAgent = new gcp.projects.ServiceIdentity(
  'run-agent',
  { project, service: 'run.googleapis.com' },
  { dependsOn: runService },
);

/**
 * Read on this app's registry, granted by this app.
 *
 * The registry lives in a project this program cannot otherwise see, and the app is
 * its administrator — which is exactly so that this grant can be made here, by
 * whoever knows what needs to pull, rather than guessed at by a stack that should not
 * know what this app runs on.
 */
const registryReader = new gcp.artifactregistry.RepositoryIamMember(
  'registry-reader',
  {
    project: imageProject,
    location: imageLocation,
    repository: imageRepository,
    role: 'roles/artifactregistry.reader',
    member: runAgent.member,
  },
  { dependsOn: runAgent },
);

export const service = new gcp.cloudrunv2.Service(
  'service',
  {
    project,
    location: region,
    name: 'service',

    // Reachable from the Internet, because the Worker in front of it calls from
    // there — Cloudflare is not inside anything Google would call internal. What
    // stops anyone else is IAM, not this: the only member holding `run.invoker` is
    // the account the Worker signs as.
    ingress: 'INGRESS_TRAFFIC_ALL',

    template: {
      /**
       * Bumped to force a new revision.
       *
       * Cloud Run creates one only when the template changes, and abandons one that
       * failed to start — so when the fix for a broken revision is not a change to
       * this service, this is the change. It means nothing to Cloud Run and nothing
       * to the container; the reason for any particular bump belongs in the commit
       * that made it.
       */
      annotations: { 'medusa.software/redeploy': REDEPLOY },

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
  },
  {
    /**
     * Last of the four, because the three above it are what let a first revision start:
     * the API has to be on before a service can be created at all, and the agent has to
     * be allowed to read the image before it is asked to pull one. Pulumi creates
     * whatever nothing orders in parallel, and both halves of that went wrong in turn —
     * one deployment tried to create the service nineteen seconds before the API was
     * enabled, the next pulled the image six seconds before the grant to read it
     * existed. Waiting on the grant orders all four, because the grant already waits
     * for the agent and the agent for the API.
     */
    dependsOn: registryReader,
  },
);

/** Where the service answers. Cloud Run chooses this; nothing here can. */
export const serviceUrl = service.uri;
