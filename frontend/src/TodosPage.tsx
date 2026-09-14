import {
  Alert,
  Button,
  Card,
  Checkbox,
  Group,
  Loader,
  Stack,
  Text,
  TextInput,
} from '@mantine/core';
import { useCallback, useEffect, useState } from 'react';
import { catchingApiError, demoApiClient, describeApiError } from './apiCalls.ts';
import { assertNever } from './assertNever.ts';
import { DemoTodoResponseKinds, type DemoApiErrorKind, type DemoTodo } from './DemoApiClient.ts';

/**
 * The todos of whoever is signed in.
 *
 * Nothing here says whose they are, and nothing here knows. The page asks for "mine": the Worker in
 * front of the service names the caller, and the service answers for them alone — so the same page
 * shows every person their own list, and cannot be talked into showing anybody else's.
 */
export function TodosPage() {
  const [todos, setTodos] = useState<readonly DemoTodo[] | null>(null);
  const [apiErrorKind, setApiErrorKind] = useState<DemoApiErrorKind | null>(null);
  const [title, setTitle] = useState('');

  const succeeded = useCallback(() => {
    setApiErrorKind(null);
  }, []);

  /** Replaces what is shown with what the service has. */
  const reload = useCallback(
    async () =>
      catchingApiError(
        () => demoApiClient.listTodos(),
        (listed) => {
          succeeded();
          setTodos(listed);
        },
        setApiErrorKind,
      ),
    [succeeded],
  );

  useEffect(() => {
    void reload();
  }, [reload]);

  /**
   * A todo the service does not have is one this page should stop showing — deleted in another tab,
   * most likely. Reloading, because whatever removed it may have changed others too.
   */
  const forgetMissing = useCallback(async () => {
    await reload();
  }, [reload]);

  const add = useCallback(async () => {
    const trimmed = title.trim();

    if (trimmed === '') {
      return;
    }

    await catchingApiError(
      () => demoApiClient.createTodo(trimmed),
      (response) => {
        switch (response.kind) {
          case DemoTodoResponseKinds.created:
            succeeded();
            setTodos((current) => [
              ...(current ?? []),
              { todoId: response.todoId, title: trimmed, done: false },
            ]);
            setTitle('');
            break;
          case DemoTodoResponseKinds.blankTitle:
            // Never sent from here, since a blank title is stopped above. The contract allows the
            // answer, so it has a branch; there is nothing to add and nothing to say.
            break;
          default:
            assertNever(response);
        }
      },
      setApiErrorKind,
    );
  }, [title, succeeded]);

  const setDone = useCallback(
    async (todoId: string, done: boolean) =>
      catchingApiError(
        () => demoApiClient.setTodoDone(todoId, done),
        (response) => {
          switch (response.kind) {
            case DemoTodoResponseKinds.updated:
              succeeded();
              setTodos((current) =>
                (current ?? []).map((todo) => (todo.todoId === todoId ? { ...todo, done } : todo)),
              );
              break;
            case DemoTodoResponseKinds.noSuchTodo:
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

  const remove = useCallback(
    async (todoId: string) =>
      catchingApiError(
        () => demoApiClient.deleteTodo(todoId),
        (response) => {
          switch (response.kind) {
            case DemoTodoResponseKinds.deleted:
              succeeded();
              setTodos((current) => (current ?? []).filter((todo) => todo.todoId !== todoId));
              break;
            case DemoTodoResponseKinds.noSuchTodo:
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
      {/* A form, so that Enter adds. Left to itself the browser would submit it by reloading the
          page, which is what `preventDefault` is for. */}
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void add();
        }}
      >
        <Group align="flex-end">
          <TextInput
            flex={1}
            aria-label="New todo"
            placeholder="What needs doing?"
            value={title}
            onChange={(event) => {
              setTitle(event.currentTarget.value);
            }}
          />
          <Button type="submit" disabled={title.trim() === ''}>
            Add
          </Button>
        </Group>
      </form>

      {apiErrorKind !== null && (
        <Alert color="red" title="That did not work">
          {describeApiError(apiErrorKind)}
        </Alert>
      )}

      {todos === null && apiErrorKind === null && <Loader size="sm" />}

      {todos?.length === 0 && <Text c="dimmed">Nothing to do.</Text>}

      {todos?.map((todo) => (
        <Card key={todo.todoId} withBorder padding="sm">
          <Group justify="space-between" wrap="nowrap">
            <Checkbox
              checked={todo.done}
              onChange={(event) => void setDone(todo.todoId, event.currentTarget.checked)}
              label={
                <Text {...(todo.done ? { td: 'line-through', c: 'dimmed' } : {})}>
                  {todo.title}
                </Text>
              }
            />
            <Button color="red" variant="subtle" onClick={() => void remove(todo.todoId)}>
              Delete
            </Button>
          </Group>
        </Card>
      ))}
    </Stack>
  );
}
