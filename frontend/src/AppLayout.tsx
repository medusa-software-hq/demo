import { Container, Stack, Tabs, Text, Title } from '@mantine/core';
import { Outlet, useLocation, useNavigate } from '@tanstack/react-router';

/**
 * The tabs, as the addresses they open.
 *
 * Each tab is a page with an address of its own, so it can be linked to, reloaded into, and gone
 * back to — rather than a piece of state that a reload forgets.
 */
const TABS = [
  { to: '/counters', label: 'Counters' },
  { to: '/todos', label: 'My todos' },
  { to: '/work', label: 'Work' },
] as const;

/** What every page is shown inside: the app's name, its environment, and the tabs between pages. */
export function AppLayout() {
  const pathname = useLocation({ select: (location) => location.pathname });
  const navigate = useNavigate();

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

        {/* The address decides which tab is open, and choosing a tab changes the address — so
            the two cannot disagree, and going back in the browser goes back a tab. */}
        <Tabs
          value={pathname}
          onChange={(value) => {
            const tab = TABS.find((candidate) => candidate.to === value);

            if (tab !== undefined) {
              void navigate({ to: tab.to });
            }
          }}
        >
          <Tabs.List>
            {TABS.map((tab) => (
              <Tabs.Tab key={tab.to} value={tab.to}>
                {tab.label}
              </Tabs.Tab>
            ))}
          </Tabs.List>
        </Tabs>

        <Outlet />
      </Stack>
    </Container>
  );
}
