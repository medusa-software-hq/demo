import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';

/**
 * [response], checked to be the [kind] of answer expected, and typed as that answer.
 *
 * The client answers with a union tagged by `kind`, and a test usually wants one member of it and
 * the fields only that member has.
 */
export const expectKind = <
  ResponseT extends { readonly kind: string },
  KindT extends ResponseT['kind'],
>(
  response: ResponseT,
  kind: KindT,
): Extract<ResponseT, { readonly kind: KindT }> => {
  assert.equal(response.kind, kind);

  return response as Extract<ResponseT, { readonly kind: KindT }>;
};

/** How often something being waited for is looked at again. */
const POLL_INTERVAL_MS = 200;

/**
 * What [check] answers, once it answers something, waiting at most [timeoutMs] for it to.
 *
 * For what happens after an answer rather than before it — work, above all, which is done by
 * something other than whatever was asked to start it.
 */
export const eventually = async <ResultT>(
  description: string,
  check: () => Promise<ResultT | undefined>,
  timeoutMs: number,
): Promise<ResultT> => {
  const deadline = Date.now() + timeoutMs;

  for (;;) {
    const result = await check();

    if (result !== undefined) {
      return result;
    }

    if (Date.now() > deadline) {
      throw new Error(`Gave up after ${timeoutMs} ms waiting for ${description}`);
    }

    await delay(POLL_INTERVAL_MS);
  }
};

/** A title nobody else will have chosen, so a test can find what it made among everything else. */
export const uniqueTitle = (): string => `system test ${crypto.randomUUID()}`;
