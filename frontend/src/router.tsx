import { Text } from '@mantine/core';
import { createRootRoute, createRoute, createRouter, redirect } from '@tanstack/react-router';
import { AppLayout } from './AppLayout.tsx';
import { CountersPage } from './CountersPage.tsx';
import { TodosPage } from './TodosPage.tsx';
import { WorkPage } from './WorkPage.tsx';

/**
 * Where each page lives.
 *
 * Written out in code rather than as files a generator turns into a route tree. With this few
 * pages, the tree is shorter than the build plugin that would write it, and there is no generated
 * file to keep in step with the pages.
 */

const rootRoute = createRootRoute({ component: AppLayout });

/** The bare address opens the counters: the page this app was before it had any others. */
const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  beforeLoad: () => {
    throw redirect({ to: '/counters', replace: true });
  },
});

const countersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/counters',
  component: CountersPage,
});

const todosRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/todos',
  component: TodosPage,
});

const workRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/work',
  component: WorkPage,
});

export const router = createRouter({
  routeTree: rootRoute.addChildren([indexRoute, countersRoute, todosRoute, workRoute]),
  defaultNotFoundComponent: () => <Text c="dimmed">There is no page at this address.</Text>,
});

// What makes every `to` above and in the pages a checked path rather than any string.
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
