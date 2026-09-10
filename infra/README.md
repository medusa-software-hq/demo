# Demo infrastructure

One stack per environment. Each opens the ESC environment the platform stack created
for it, which supplies both the Google Cloud credentials and the project to build in —
so nothing here names either, and adding an environment is a change to the platform
stack's model rather than to this program.

## What this builds

Both halves of what the site is: the Worker's bundle, and the frontend's `dist`. Both
are built by this program rather than before it, so that the artifact being uploaded is
the one that was just compiled from the sources in this checkout. A build that runs as
a separate step can leave a stale artifact behind, and the upload would describe it
perfectly — a deployment that succeeds and serves the previous version.

It also puts the compiler on the path a pull request has to get past. A preview builds
both, so a change that does not compile fails its deployment check instead of reaching
the merge that would break the site.

## Why npm

Every other TypeScript package in the organization uses yarn 4 through corepack. This
one uses npm because it runs in Pulumi Deployments, whose runner installs dependencies
before any pre-run command can enable corepack. The `frontend` and `worker` packages
follow, for a quieter reason: this program installs and builds them, and one package
manager to reach for is simpler than a rule about which lives where.
