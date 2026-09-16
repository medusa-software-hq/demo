/**
 * The client, and the answers its operations can give.
 *
 * One module, because the client is one thing: what a caller holds, the shapes it hands back, and
 * the error it throws when the service says something the contract never promised.
 */
export * from './DemoApiClient.ts';
