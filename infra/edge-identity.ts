import * as gcp from '@pulumi/gcp';
import * as pulumi from '@pulumi/pulumi';
import { service } from './service.ts';

/**
 * How the edge gets in, and the one credential in this system that is a file.
 *
 * The Worker in front of this app is the only thing meant to call its API, and Cloud
 * Run will believe that only if the caller proves it. Everything else in this
 * organization proves itself by federation — a pipeline exchanges a token it was
 * issued, a Cloud Run service pulls as an agent Google hands it. A Cloudflare Worker
 * can do neither: it has no metadata server, and no identity Workload Identity
 * Federation will accept. So it holds a key.
 *
 * The organization forbids keys and means to; zygote lifts that for this folder only,
 * and bounds what it lifted by capping a key's life at a week. Which makes rotation
 * not a good practice but a requirement: a key nobody replaces stops working, and
 * takes the API with it.
 */

const config = new pulumi.Config('gcp');
const project = config.require('project');

/**
 * Who the Worker is, as far as Google is concerned.
 *
 * Its own account rather than the one this program deploys as. The deployer can
 * rewrite this project; the edge should be able to do exactly one thing, and holding
 * a copyable credential is precisely the case where that distinction earns its keep.
 */
const edge = new gcp.serviceaccount.Account('edge', {
  project,
  accountId: 'edge',
  displayName: 'The Worker in front of this app',
});

/**
 * And the one thing it may do.
 *
 * This is what replaced `allUsers`. The service is no less reachable than it was —
 * anyone can still send it a request — but now only one caller is answered, and the
 * Worker is the only thing holding what it takes to be that caller.
 */
new gcp.cloudrunv2.ServiceIamMember('edge-invoker', {
  project,
  location: service.location,
  name: service.name,
  role: 'roles/run.invoker',
  member: edge.member,
});

/**
 * Replaced whenever the day has changed since the last deployment.
 *
 * This is the whole rotation: there is no separate job, because a deployment is the
 * only thing in this design holding a Cloudflare credential, and a key the Worker
 * cannot be handed is no use. A scheduled deployment therefore rotates, and the
 * daily cadence against a weekly expiry means six consecutive failures are survivable
 * and the seventh is not — which is the point.
 *
 * A pull request opened the day after a deployment will show this being replaced.
 * That is not drift; it is the plan saying, accurately, that merging will rotate the
 * key.
 */
const key = new gcp.serviceaccount.Key('edge', {
  serviceAccountId: edge.name,
  keepers: { day: new Date().toISOString().slice(0, 10) },
});

/**
 * The key itself, as the Worker needs to read it.
 *
 * Google returns it base64-encoded; what is bound to the Worker is the JSON inside.
 * Secret because it is one — it goes into Pulumi state, encrypted, which is the cost
 * of having the deployment be the thing that rotates.
 */
export const edgeKeyJson = pulumi.secret(
  key.privateKey.apply((encoded) => Buffer.from(encoded, 'base64').toString('utf8')),
);

/** Who the Worker signs as, for anyone wondering who is calling the API. */
export const edgeServiceAccount = edge.email;
