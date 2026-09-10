import { Button, Card, Container, Group, Stack, Text, Title } from '@mantine/core';
import { useCallback, useState } from 'react';

/** A counter and what it currently reads. */
interface Counter {
  readonly id: string;
  readonly count: number;
}

/**
 * Where a counter's identity comes from, until something else hands them out.
 *
 * Generated here so that the shape is already the one a service would impose — a
 * counter is referred to by an id rather than by where it sits in an array — and so
 * that adopting real ids later changes this line rather than everything that reads one.
 */
const newCounterId = (): string => crypto.randomUUID();

/**
 * The counters this page is showing.
 *
 * Held in this tab and nowhere else. There is no service behind them yet, so a reload
 * starts over and a second tab shows its own set — which is why nothing here has
 * anything to say about loading, failing, or being out of date. Those appear when
 * there is somewhere for a counter to be kept, and not before.
 */
export default function App() {
  const [counters, setCounters] = useState<readonly Counter[]>([]);

  const add = useCallback(() => {
    setCounters((current) => [...current, { id: newCounterId(), count: 0 }]);
  }, []);

  const adjust = useCallback((id: string, by: number) => {
    setCounters((current) =>
      current.map((counter) =>
        counter.id === id ? { ...counter, count: counter.count + by } : counter,
      ),
    );
  }, []);

  const remove = useCallback((id: string) => {
    setCounters((current) => current.filter((counter) => counter.id !== id));
  }, []);

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
          <Button onClick={add}>Add a counter</Button>
        </Group>

        {counters.length === 0 && <Text c="dimmed">No counters yet.</Text>}

        {counters.map((counter) => (
          <Card key={counter.id} withBorder padding="md">
            <Group justify="space-between">
              <Stack gap={0}>
                <Text size="xl" fw={700}>
                  {counter.count}
                </Text>
                <Text size="xs" c="dimmed">
                  {counter.id}
                </Text>
              </Stack>

              <Group>
                <Button
                  variant="default"
                  onClick={() => adjust(counter.id, -1)}
                  aria-label="Decrement"
                >
                  −
                </Button>
                <Button
                  variant="default"
                  onClick={() => adjust(counter.id, 1)}
                  aria-label="Increment"
                >
                  +
                </Button>
                <Button color="red" variant="subtle" onClick={() => remove(counter.id)}>
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
