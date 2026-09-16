import { Alert, Badge, Button, Card, Group, Loader, Progress, Stack, Text } from '@mantine/core';
import { DemoWorkResponseKinds, type DemoApiErrorKind, type DemoWorkOverview } from 'demo-client';
import { useCallback, useEffect, useState } from 'react';
import { catchingApiError, demoApiClient, describeApiError } from './apiCalls.ts';
import { assertNever } from './assertNever.ts';

/** How often a page with a run still going asks how far it has got. */
const pollIntervalMillis = 1000;

/**
 * Work the signed-in person has set going: start a run, and watch it get done.
 *
 * The work happens elsewhere — a worker, on Temporal — and writes down how far it has got as it
 * goes. This page only reads that back, and keeps reading while anything is still going. Where the
 * environment runs no work, it says so, and still shows whatever runs there already are.
 */
export function WorkPage() {
  const [overview, setOverview] = useState<DemoWorkOverview | null>(null);
  const [apiErrorKind, setApiErrorKind] = useState<DemoApiErrorKind | null>(null);

  /** Replaces what is shown with what the service has. */
  const reload = useCallback(
    async () =>
      catchingApiError(
        () => demoApiClient.getWork(),
        (fetched) => {
          setApiErrorKind(null);
          setOverview(fetched);
        },
        setApiErrorKind,
      ),
    [],
  );

  useEffect(() => {
    void reload();
  }, [reload]);

  const anyRunGoing = overview?.runs.some((run) => run.result === null) ?? false;

  // Asking again only while there is something to see change, so an idle page asks for nothing.
  useEffect(() => {
    if (!anyRunGoing) {
      return;
    }

    const interval = setInterval(() => {
      void reload();
    }, pollIntervalMillis);

    return () => {
      clearInterval(interval);
    };
  }, [anyRunGoing, reload]);

  const start = useCallback(
    async () =>
      catchingApiError(
        () => demoApiClient.startWork(),
        (response) => {
          switch (response.kind) {
            case DemoWorkResponseKinds.started:
            case DemoWorkResponseKinds.disabled:
              // Either way, what the service now says is what to show: the new run, or that work
              // is not enabled here after all.
              void reload();
              break;
            default:
              assertNever(response);
          }
        },
        setApiErrorKind,
      ),
    [reload],
  );

  return (
    <Stack>
      {overview !== null && !overview.enabled && (
        <Alert color="gray" title="Work is not enabled here">
          This environment has nowhere to run work yet, so a new run cannot be started. Runs started
          earlier are still listed below.
        </Alert>
      )}

      <Group>
        <Button onClick={() => void start()} disabled={overview?.enabled !== true}>
          Start work
        </Button>
      </Group>

      {apiErrorKind !== null && (
        <Alert color="red" title="That did not work">
          {describeApiError(apiErrorKind)}
        </Alert>
      )}

      {overview === null && apiErrorKind === null && <Loader size="sm" />}

      {overview?.runs.length === 0 && <Text c="dimmed">No work started yet.</Text>}

      {overview?.runs.map((run) => {
        const finished = run.result !== null;

        return (
          <Card key={run.runId} withBorder padding="md">
            <Stack gap="xs">
              <Group justify="space-between">
                <Text size="xs" c="dimmed">
                  {run.runId}
                </Text>
                <Badge color={finished ? 'green' : 'blue'} variant="light">
                  {finished ? 'Finished' : 'Working'}
                </Badge>
              </Group>

              <Progress
                value={(100 * run.stepsDone) / run.stepsTotal}
                animated={!finished}
                aria-label="Progress"
              />

              <Text size="sm">
                {run.result ?? `${run.stepsDone} of ${run.stepsTotal} steps done`}
              </Text>
            </Stack>
          </Card>
        );
      })}
    </Stack>
  );
}
