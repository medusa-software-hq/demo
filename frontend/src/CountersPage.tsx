import { Alert, Button, Card, Group, Loader, Stack, Text } from '@mantine/core';
import { DemoCounterResponseKinds, type DemoApiErrorKind, type DemoCounter } from 'demo-client';
import { useCallback, useEffect, useState } from 'react';
import { catchingApiError, demoApiClient, describeApiError } from './apiCalls.ts';
import { assertNever } from './assertNever.ts';

/**
 * The counters, which live in the service rather than in this tab — and which everyone shares.
 *
 * So a reload shows what is there, a second tab shows the same thing, and neither this page closing
 * nor the service stopping loses any of it: the service keeps them in Postgres.
 */
export function CountersPage() {
  const [counters, setCounters] = useState<readonly DemoCounter[] | null>(null);
  const [apiErrorKind, setApiErrorKind] = useState<DemoApiErrorKind | null>(null);

  const succeeded = useCallback(() => {
    setApiErrorKind(null);
  }, []);

  /** Replaces what is shown with what the service has. */
  const reload = useCallback(
    async () =>
      catchingApiError(
        () => demoApiClient.listCounters(),
        (listed) => {
          succeeded();
          setCounters(listed);
        },
        setApiErrorKind,
      ),
    [succeeded],
  );

  // Whatever the service already holds, before anything is clicked.
  useEffect(() => {
    void reload();
  }, [reload]);

  /** A count the service has just told us. */
  const record = useCallback(
    (counterId: string, count: number) => {
      succeeded();
      setCounters((current) =>
        (current ?? []).map((counter) =>
          counter.counterId === counterId ? { counterId, count } : counter,
        ),
      );
    },
    [succeeded],
  );

  /**
   * A counter the service does not have is one this page should stop showing.
   *
   * Reached when a row was drawn before somebody deleted its counter somewhere else — another tab,
   * another person. Reloading rather than only dropping the row, because whatever removed that one
   * may have changed others too.
   */
  const forgetMissing = useCallback(async () => {
    await reload();
  }, [reload]);

  const add = useCallback(
    async () =>
      catchingApiError(
        () => demoApiClient.createCounter(),
        (counterId) => {
          succeeded();
          setCounters((current) => [...(current ?? []), { counterId, count: 0 }]);
        },
        setApiErrorKind,
      ),
    [succeeded],
  );

  const adjust = useCallback(
    async (counterId: string, by: 1 | -1) =>
      catchingApiError(
        () =>
          by === 1
            ? demoApiClient.incrementCount(counterId)
            : demoApiClient.decrementCount(counterId),
        (response) => {
          switch (response.kind) {
            case DemoCounterResponseKinds.adjusted:
              record(counterId, response.newCount);
              break;
            case DemoCounterResponseKinds.noSuchCounter:
              void forgetMissing();
              break;
            default:
              assertNever(response);
          }
        },
        setApiErrorKind,
      ),
    [record, forgetMissing],
  );

  const remove = useCallback(
    async (counterId: string) =>
      catchingApiError(
        () => demoApiClient.deleteCounter(counterId),
        (response) => {
          switch (response.kind) {
            case DemoCounterResponseKinds.deleted:
              succeeded();
              setCounters((current) =>
                (current ?? []).filter((counter) => counter.counterId !== counterId),
              );
              break;
            case DemoCounterResponseKinds.noSuchCounter:
              void forgetMissing();
              break;
            default:
              assertNever(response);
          }
        },
        setApiErrorKind,
      ),
    [succeeded, forgetMissing],
  );

  return (
    <Stack>
      <Group>
        <Button onClick={() => void add()}>Add a counter</Button>
        <Button variant="default" onClick={() => void reload()}>
          Reload
        </Button>
      </Group>

      {apiErrorKind !== null && (
        <Alert color="red" title="That did not work">
          {describeApiError(apiErrorKind)}
        </Alert>
      )}

      {/* Null until the first listing answers — which is not the same as knowing there are
          none, and should not be drawn as though it were. */}
      {counters === null && apiErrorKind === null && <Loader size="sm" />}

      {counters?.length === 0 && <Text c="dimmed">No counters yet.</Text>}

      {counters?.map((counter) => (
        <Card key={counter.counterId} withBorder padding="md">
          <Group justify="space-between">
            <Stack gap={0}>
              <Text size="xl" fw={700}>
                {counter.count}
              </Text>
              <Text size="xs" c="dimmed">
                {counter.counterId}
              </Text>
            </Stack>

            <Group>
              <Button
                variant="default"
                onClick={() => void adjust(counter.counterId, -1)}
                aria-label="Decrement"
              >
                −
              </Button>
              <Button
                variant="default"
                onClick={() => void adjust(counter.counterId, 1)}
                aria-label="Increment"
              >
                +
              </Button>
              <Button color="red" variant="subtle" onClick={() => void remove(counter.counterId)}>
                Delete
              </Button>
            </Group>
          </Group>
        </Card>
      ))}
    </Stack>
  );
}
