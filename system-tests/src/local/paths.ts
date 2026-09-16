import { fileURLToPath } from 'node:url';

/** The repository this package sits in, which holds the stack, the Worker and the client. */
export const repositoryRoot = fileURLToPath(new URL('../../../', import.meta.url));
