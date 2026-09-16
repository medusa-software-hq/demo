import { createDemoApiClient, DemoTodoResponseKinds } from 'demo-client';
import { apiClientFor, localAbilitiesOf, type TestEnvironment } from '../environment.ts';
import { expectKind, uniqueTitle } from './expectations.ts';
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

/**
 * The way in: who gets past the front of the app, and who the Service is told they are.
 *
 * Half of this can only be shown where the login is played by the tests, because a real one never
 * lets the requests through that would show it. The other half holds anywhere, and is the half a
 * misconfigured deployment would break.
 */
export const wayInSuite = (environment: TestEnvironment): void => {
  const loginIsReal = environment.local === undefined;

  /** Status of a request for the todos, carrying [headers]. */
  const todosStatusWith = async (headers: Readonly<Record<string, string>>): Promise<number> =>
    (await fetch(`${environment.origin}/api/todos`, { headers, redirect: 'manual' })).status;

  describe('the way in', () => {
    it('lets webhooks reach the Service without anybody signing in', async () => {
      const response = await fetch(`${environment.origin}/webhooks/system-tests`, {
        method: 'POST',
        redirect: 'manual',
      });

      // Past the login and the Worker, and answered by the Service — which has no such webhook.
      // Anything in front of it refusing would have answered with something else.
      assert.equal(response.status, 404);
    });

    it('believes the sign-in over whoever a caller claims to be', async () => {
      const [caller] = environment.callers;
      const honest = apiClientFor(environment, caller);
      const { todoId } = expectKind(
        await honest.createTodo(uniqueTitle()),
        DemoTodoResponseKinds.created,
      );

      try {
        const claiming = createDemoApiClient(`${environment.origin}/api`, {
          headers: {
            ...caller.headers,
            'x-medusa-user-subject': 'somebody-else',
            'x-medusa-user-email': 'somebody-else@medusa.software',
          },
        });

        // Still the caller's own todos, so the claim went nowhere.
        assert.ok((await claiming.listTodos()).some((todo) => todo.todoId === todoId));
      } finally {
        await honest.deleteTodo(todoId);
      }
    });

    it('lets in a person the login signed in', { skip: loginIsReal }, async () => {
      const person = await localAbilitiesOf(environment).person(
        '7335d417-61da-459d-899c-0a01c76a3a47',
        'someone@medusa.software',
      );
      const api = apiClientFor(environment, person);
      const { todoId } = expectKind(
        await api.createTodo(uniqueTitle()),
        DemoTodoResponseKinds.created,
      );

      try {
        assert.ok((await api.listTodos()).some((todo) => todo.todoId === todoId));
      } finally {
        await api.deleteTodo(todoId);
      }
    });

    it('refuses a request carrying no sign-in', { skip: loginIsReal }, async () => {
      assert.equal(await todosStatusWith({}), 403);
    });

    it(
      'refuses a token signed with a key the login never published',
      { skip: loginIsReal },
      async () => {
        assert.equal(
          await todosStatusWith((await localAbilitiesOf(environment).forged()).headers),
          403,
        );
      },
    );

    it(
      'refuses a token the login signed for another application',
      { skip: loginIsReal },
      async () => {
        const elsewhere = await localAbilitiesOf(environment).signedForAnotherApplication();

        assert.equal(await todosStatusWith(elsewhere.headers), 403);
      },
    );
  });
};
