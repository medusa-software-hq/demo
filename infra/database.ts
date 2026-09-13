import * as gcp from '@pulumi/gcp';
import * as neon from '@pulumi/neon';
import * as pulumi from '@pulumi/pulumi';

/**
 * Where this environment's counters actually live.
 *
 * The platform stack created a Neon project for this environment and handed over a key
 * to it — not a connection string. What a connection string should look like depends on
 * what is connecting, and the thing connecting is here.
 */

const config = new pulumi.Config();
const project = new pulumi.Config('gcp').require('project');

/** Named by the platform stack, which created it. */
const neonProjectId = config.require('neonProjectId');

const database = neon.getProjectOutput({ id: neonProjectId });

/**
 * The database this app talks to, in the only spelling pgjdbc accepts.
 *
 * Not `connectionUri`, which the same data source offers. That is a libpq URL, with
 * credentials as `user:password@host` userinfo — a form pgjdbc rejects outright in
 * `acceptsURL`, so a driver handed one reports no suitable driver rather than a bad
 * password. The parts go in as query parameters instead, url-encoded because a
 * generated password is not guaranteed to be free of characters that mean something
 * here. `sslmode=require` because Neon accepts nothing else.
 *
 * The direct host rather than `databaseHostPooler`. The pooler is PgBouncer in
 * transaction mode, where a session-level advisory lock is not reliably held for the
 * length of a transaction — and that lock is exactly what a migration tool takes at
 * startup to stop two instances migrating at once. One instance with a small pool is
 * well inside what Neon allows directly.
 */
export const databaseUrl = pulumi.secret(
  pulumi
    .all([
      database.databaseHost,
      database.databaseName,
      database.databaseUser,
      database.databasePassword,
    ])
    .apply(
      ([host, name, user, password]) =>
        `jdbc:postgresql://${host}/${name}?sslmode=require` +
        `&user=${encodeURIComponent(user)}` +
        `&password=${encodeURIComponent(password)}`,
    ),
);

const secretManager = new gcp.projects.Service('secret-manager', {
  project,
  service: 'secretmanager.googleapis.com',
  disableOnDestroy: false,
});

/**
 * The connection string, kept where a password belongs.
 *
 * Not a plain environment variable on the service: those are readable by anyone who can
 * describe it, and this one carries a password. A secret is read by the running
 * container and by nothing else that has not been granted it by name.
 */
export const databaseUrlSecret = new gcp.secretmanager.Secret(
  'database-url',
  {
    project,
    secretId: 'database-url',
    replication: { auto: {} },
  },
  { dependsOn: secretManager },
);

new gcp.secretmanager.SecretVersion('database-url', {
  secret: databaseUrlSecret.id,
  secretData: databaseUrl,
});

/**
 * Where the database answers, without the part that gets you in.
 *
 * An output so that a deployment can be read back and checked against what Neon shows,
 * which is otherwise impossible for a value that exists only inside a secret.
 */
export const databaseTarget = pulumi.interpolate`${database.databaseUser}@${database.databaseHost}/${database.databaseName}`;
