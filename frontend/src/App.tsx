import { Alert, Button, Card, Container, Group, Loader, Stack, Text, Title } from '@mantine/core';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { assertNever } from './assertNever.ts';
import {
  createDemoApiClient,
  DemoApiError,
  DemoApiErrorKinds,
  DemoCounterResponseKinds,
  type DemoApiClient,
  type DemoApiErrorKind,
  type DemoCounter,
} from './DemoApiClient.ts';

/**
 * The service, on this page's own origin.
 *
 * The Worker in front of both splits this prefix off before anything else sees it, so the page
 * crosses no origin, needs no CORS, and names nothing that differs between environments.
 */
const apiUrl = '/api';

/**
 * Runs [call], handing the result to [onSuccess] and a failed call's kind to [onError].
 *
 * Only a `DemoApiError` is caught. Anything else is a bug in this page rather than an answer from
 * the service, and swallowing it here is how it would go unnoticed.
 */
async function catchingApiError<ResultT>(
  call: () => Promise<ResultT>,
  onSuccess: (result: ResultT) => void,
  onError: (errorKind: DemoApiErrorKind) => void,
): Promise<void> {
  let result: ResultT;

  try {
    result = await call();
  } catch (cause) {
    if (!(cause instanceof DemoApiError)) {
      throw cause;
    }

    onError(cause.kind);

    return;
  }

  onSuccess(result);
}

/** What to say about a call that produced no answer. Chosen here, not carried by the error. */
function describeApiError(errorKind: DemoApiErrorKind): string {
  switch (errorKind) {
    case DemoApiErrorKinds.network:
      return 'The service could not be reached.';
    case DemoApiErrorKinds.internalServerError:
      return 'The service failed to handle that.';
    case DemoApiErrorKinds.incompatibility:
      return 'The service answered something this page cannot read.';
    default:
      return assertNever(errorKind);
  }
}

/**
 * The counters, which live in the service rather than in this tab.
 *
 * So a reload shows what is there, and a second tab shows the same thing. What it does not show is
 * anything that outlives the service: the store is in memory and the instance goes away when
 * nobody is asking, which is why an empty list is the ordinary state and not a sign of trouble.
 */
export default function App() {
  const apiClient: DemoApiClient = useMemo(() => createDemoApiClient(apiUrl), []);

  const [counters, setCounters] = useState<readonly DemoCounter[] | null>(null);
  const [apiErrorKind, setApiErrorKind] = useState<DemoApiErrorKind | null>(null);

  const succeeded = useCallback(() => {
    setApiErrorKind(null);
  }, []);

  /** Replaces what is shown with what the service has. */
  const reload = useCallback(
    async () =>
      catchingApiError(
        () => apiClient.listCounters(),
        (listed) => {
          succeeded();
          setCounters(listed);
        },
        setApiErrorKind,
      ),
    [apiClient, succeeded],
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
   * Reached when somebody clicks on a row that was drawn before the instance went away. Reloading
   * rather than only dropping the row, because if one counter is gone the rest probably are too.
   */
  const forgetMissing = useCallback(async () => {
    await reload();
  }, [reload]);

  const add = useCallback(
    async () =>
      catchingApiError(
        () => apiClient.createCounter(),
        (counterId) => {
          succeeded();
          setCounters((current) => [...(current ?? []), { counterId, count: 0 }]);
        },
        setApiErrorKind,
      ),
    [apiClient, succeeded],
  );

  const adjust = useCallback(
    async (counterId: string, by: 1 | -1) =>
      catchingApiError(
        () =>
          by === 1 ? apiClient.incrementCount(counterId) : apiClient.decrementCount(counterId),
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
    [apiClient, record, forgetMissing],
  );

  const remove = useCallback(
    async (counterId: string) =>
      catchingApiError(
        () => apiClient.deleteCounter(counterId),
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
    [apiClient, succeeded, forgetMissing],
  );

  return (
    <Container size="sm" py="xl">
      <Stack>
        <Stack gap={0}>
          <Title order={1}>Demo</Title>
          {/* Which of the deployed environments this is. The hostname is the only thing
              that distinguishes them, and it is worth being able to see. */}
          <Text size="sm" c="dimmed">
            {window.location.hostname}
          </Text>
        </Stack>

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
    </Container>
  );
}
