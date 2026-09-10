# Demo infrastructure

One stack per environment. Each opens the ESC environment the platform stack created
for it, which supplies both the Google Cloud credentials and the project to build in —
so nothing here names either, and adding an environment is a change to the platform
stack's model rather than to this program.

## Why npm

Every other TypeScript package in the organization uses yarn 4 through corepack. This
one uses npm because it runs in Pulumi Deployments, whose runner installs dependencies
before any pre-run command can enable corepack.
