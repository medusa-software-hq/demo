import { DemoWorkResponseKinds } from 'demo-client';
import { apiClientFor, type TestEnvironment } from '../environment.ts';
import { eventually, expectKind } from './expectations.ts';
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

/**
 * Work: runs started by a caller, done elsewhere, and reported back as they go.
 *
 * Not every environment runs work, and each says whether it does. These tests ask rather than
 * assume, so the same suite shows both that work gets done where it is enabled and that nothing
 * pretends to start where it is not.
 */

/** Long enough for a run to finish on a stack whose runs are made brief, and on a slow runner. */
const RUN_TIMEOUT_MS = 60_000;

export const workSuite = (environment: TestEnvironment): void => {
  describe('work', () => {
    it('runs to the end where it is enabled, and does not start where it is not', async () => {
      const [caller] = environment.callers;
      const api = apiClientFor(environment, caller);
      const { enabled } = await api.getWork();
      const started = await api.startWork();

      if (!enabled) {
        expectKind(started, DemoWorkResponseKinds.disabled);

        return;
      }

      const { runId } = expectKind(started, DemoWorkResponseKinds.started);

      const finished = await eventually(
        `run ${runId} to finish`,
        async () => {
          const run = (await api.getWork()).runs.find((candidate) => candidate.runId === runId);

          return run?.result === null ? undefined : run;
        },
        RUN_TIMEOUT_MS,
      );

      assert.equal(finished.stepsDone, finished.stepsTotal);
      assert.notEqual(finished.result, null);
    });

    it('shows nobody else the runs somebody started', async (context) => {
      const [starter, stranger] = environment.callers;
      const starterApi = apiClientFor(environment, starter);

      if (!(await starterApi.getWork()).enabled) {
        context.skip('work is not enabled here');

        return;
      }

      const { runId } = expectKind(await starterApi.startWork(), DemoWorkResponseKinds.started);
      const { runs } = await apiClientFor(environment, stranger).getWork();

      assert.ok(!runs.some((run) => run.runId === runId));
    });
  });
};
