import { DemoTodoResponseKinds } from 'demo-client';
import { apiClientFor, type TestEnvironment } from '../environment.ts';
import { expectKind, uniqueTitle } from './expectations.ts';
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

/**
 * Todos, which belong to whoever made them.
 *
 * What makes them anybody's is decided before the Service sees a request — by the login and the
 * Worker, which name the caller — so this is as much a test of that as of the Service.
 */
export const todosSuite = (environment: TestEnvironment): void => {
  describe('todos', () => {
    it('are kept, updated and removed for whoever made them', async () => {
      const [caller] = environment.callers;
      const api = apiClientFor(environment, caller);
      const title = uniqueTitle();
      const { todoId } = expectKind(await api.createTodo(title), DemoTodoResponseKinds.created);

      try {
        assert.deepEqual(
          (await api.listTodos()).find((todo) => todo.todoId === todoId),
          { todoId, title, done: false },
        );

        expectKind(await api.setTodoDone(todoId, true), DemoTodoResponseKinds.updated);

        assert.equal((await api.listTodos()).find((todo) => todo.todoId === todoId)?.done, true);

        expectKind(await api.deleteTodo(todoId), DemoTodoResponseKinds.deleted);

        assert.ok(!(await api.listTodos()).some((todo) => todo.todoId === todoId));
      } finally {
        await api.deleteTodo(todoId);
      }
    });

    it('are nobody else’s to see, change or remove', async () => {
      const [owner, stranger] = environment.callers;
      const ownApi = apiClientFor(environment, owner);
      const strangerApi = apiClientFor(environment, stranger);
      const { todoId } = expectKind(
        await ownApi.createTodo(uniqueTitle()),
        DemoTodoResponseKinds.created,
      );

      try {
        assert.ok(!(await strangerApi.listTodos()).some((todo) => todo.todoId === todoId));

        // Somebody else's todo looks exactly like no todo at all.
        expectKind(await strangerApi.setTodoDone(todoId, true), DemoTodoResponseKinds.noSuchTodo);
        expectKind(await strangerApi.deleteTodo(todoId), DemoTodoResponseKinds.noSuchTodo);

        assert.equal(
          (await ownApi.listTodos()).find((todo) => todo.todoId === todoId)?.done,
          false,
        );
      } finally {
        await ownApi.deleteTodo(todoId);
      }
    });

    it('need a title', async () => {
      const [caller] = environment.callers;

      expectKind(
        await apiClientFor(environment, caller).createTodo('   '),
        DemoTodoResponseKinds.blankTitle,
      );
    });
  });
};
