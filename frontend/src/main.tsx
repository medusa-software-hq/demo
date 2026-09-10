import '@mantine/core/styles.css';
import { MantineProvider } from '@mantine/core';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.tsx';
import { panic } from './panic.ts';

const root = document.getElementById('root') ?? panic('there is no root element to render into');

createRoot(root).render(
  <StrictMode>
    <MantineProvider>
      <App />
    </MantineProvider>
  </StrictMode>,
);
