import { DemoCounterResponseKinds } from 'demo-client';
import { apiClientFor, type TestEnvironment } from '../environment.ts';
import { expectKind } from './expectations.ts';
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

/**
 * Counters, which everyone shares.
 *
 * Shared means other people's counters may be there as well — on a deployed environment, certainly
 * — so these tests only ever look at a counter they made, and remove it afterwards.
 */
export const countersSuite = (environment: TestEnvironment): void => {
  describe('counters', () => {
    it('start at zero, count up and down, and are gone once deleted', async () => {
      const [caller] = environment.callers;
      const api = apiClientFor(environment, caller);
      const counterId = await api.createCounter();

      try {
        assert.deepEqual(await api.getCount(counterId), {
          kind: DemoCounterResponseKinds.received,
          currentCount: 0,
        });

        assert.equal(
          expectKind(await api.incrementCount(counterId), DemoCounterResponseKinds.adjusted)
            .newCount,
          1,
        );

        assert.equal(
          expectKind(await api.decrementCount(counterId), DemoCounterResponseKinds.adjusted)
            .newCount,
          0,
        );

        assert.ok((await api.listCounters()).some((counter) => counter.counterId === counterId));

        expectKind(await api.deleteCounter(counterId), DemoCounterResponseKinds.deleted);

        expectKind(await api.getCount(counterId), DemoCounterResponseKinds.noSuchCounter);
        expectKind(await api.incrementCount(counterId), DemoCounterResponseKinds.noSuchCounter);
        assert.ok(!(await api.listCounters()).some((counter) => counter.counterId === counterId));
      } finally {
        // Already gone when the test got that far, which answers `noSuchCounter` and does no harm.
        await api.deleteCounter(counterId);
      }
    });

    it('are the same counter for everybody', async () => {
      const [first, second] = environment.callers;
      const counting = apiClientFor(environment, first);
      const watching = apiClientFor(environment, second);
      const counterId = await counting.createCounter();

      try {
        await counting.incrementCount(counterId);

        assert.deepEqual(await watching.getCount(counterId), {
          kind: DemoCounterResponseKinds.received,
          currentCount: 1,
        });
      } finally {
        await counting.deleteCounter(counterId);
      }
    });
  });
};
