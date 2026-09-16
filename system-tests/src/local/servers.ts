import { createServer, type RequestListener } from 'node:http';

/** An HTTP server this process started, and how to stop it. */
export interface RunningServer {
  /** Where it answers, as `http://host:port`. */
  readonly origin: string;

  close(): Promise<void>;
}

/**
 * Serves [listener] on a port the system chooses.
 *
 * Bound to the loopback address and nothing else: whatever is served here stands in for something
 * trusted, and has no business being reachable from anywhere but this machine.
 */
export const serve = async (listener: RequestListener): Promise<RunningServer> => {
  const server = createServer(listener);

  await new Promise<void>((resolve) => {
    server.listen(0, '127.0.0.1', resolve);
  });

  const address = server.address();

  if (address === null || typeof address === 'string') {
    throw new Error('A server listening on a port has no port to report');
  }

  return {
    origin: `http://127.0.0.1:${address.port}`,

    close: () =>
      new Promise((resolve, reject) => {
        // Kept-alive connections would otherwise hold the server open until they time out.
        server.closeAllConnections();
        server.close((failure) => (failure === undefined ? resolve() : reject(failure)));
      }),
  };
};
