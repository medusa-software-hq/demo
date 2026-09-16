import { buildSync } from 'esbuild';
import { repositoryRoot } from './paths.ts';
import { serve, type RunningServer } from './servers.ts';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Readable } from 'node:stream';
import type { ReadableStream } from 'node:stream/web';
import { fileURLToPath, pathToFileURL } from 'node:url';

/**
 * The Worker, run here, in front of the local Service.
 *
 * Not a stand-in for it: the same source, bundled with the same options it is deployed with, and
 * handed the same kind of environment. What differs is only what that environment points at — an
 * issuer and a token endpoint played by the tests, and a Service on this machine.
 */

/** What the platform hands the deployed Worker, pointed at things started here. */
export interface EdgeSettings {
  readonly apiTarget: string;
  readonly serviceAccountKey: string;
  readonly issuer: string;
  readonly keysUrl: string;
  readonly audience: string;
}

/** The one thing a Worker module is asked for. */
interface WorkerModule {
  readonly default: {
    fetch(request: Request, env: unknown): Promise<Response>;
  };
}

/** Kept in step with `infra/worker.ts`, which builds the bundle that is deployed. */
const TARGET = 'es2022';

/** Where Node and the edge runtime disagree, settled for the Worker's bundle alone. */
const edgeRuntime = fileURLToPath(new URL('./edgeRuntime.ts', import.meta.url));

/**
 * The Worker, as one module with nothing left to resolve.
 *
 * Built the way the deployment builds it, rather than imported as source: what runs here should be
 * what runs at the edge, down to which build of `jose` ends up inside it. Two additions, neither of
 * which changes the Worker's own code: the edge runtime's leniencies, injected, and a source map, so
 * a failure inside it points at the Worker's sources rather than at the bundle.
 */
const bundleWorker = (): string => {
  const entry = join(repositoryRoot, 'worker/src/index.ts');

  const { outputFiles } = buildSync({
    entryPoints: [entry],
    bundle: true,
    format: 'esm',
    target: TARGET,
    platform: 'neutral',
    write: false,
    tsconfigRaw: { compilerOptions: { target: TARGET, useDefineForClassFields: true } },
    inject: [edgeRuntime],
    sourcemap: 'inline',
  });

  const [output] = outputFiles;

  if (output === undefined) {
    throw new Error(`Bundling ${entry} produced no output`);
  }

  return output.text;
};

/** The files the deployed Worker is uploaded with, which no test here asks for. */
const assets = {
  fetch: (): Promise<Response> => Promise.resolve(new Response('the app would be here')),
};

export const startEdge = async (settings: EdgeSettings): Promise<RunningServer> => {
  // A file rather than a `data:` URL, so a stack trace names a place instead of printing the
  // whole bundle.
  const directory = mkdtempSync(join(tmpdir(), 'demo-edge-'));
  const bundlePath = join(directory, 'worker.mjs');

  writeFileSync(bundlePath, bundleWorker());

  const worker = ((await import(pathToFileURL(bundlePath).href)) as WorkerModule).default;

  const env = {
    ASSETS: assets,
    API_TARGET: settings.apiTarget,
    GCP_SA_KEY: settings.serviceAccountKey,
    AUTH_ISSUER: settings.issuer,
    AUTH_KEYS_URL: settings.keysUrl,
    AUTH_AUDIENCE: settings.audience,
  };

  let origin = '';

  const server = await serve((incoming, outgoing) => {
    const headers = new Headers();

    for (const [name, value] of Object.entries(incoming.headers)) {
      if (value !== undefined) {
        headers.set(name, Array.isArray(value) ? value.join(', ') : value);
      }
    }

    const method = incoming.method ?? 'GET';
    const hasBody = method !== 'GET' && method !== 'HEAD';

    const request = new Request(new URL(incoming.url ?? '/', origin), {
      method,
      headers,
      ...(hasBody
        ? {
            body: Readable.toWeb(
              incoming,
            ) as ReadableStream<Uint8Array> as globalThis.ReadableStream,
            // A streamed body has to say so, and the DOM typings do not know the option.
            duplex: 'half',
          }
        : {}),
    } as RequestInit);

    worker
      .fetch(request, env)
      .then(async (response) => {
        outgoing.writeHead(response.status, Object.fromEntries(response.headers));

        if (response.body !== null) {
          for await (const chunk of response.body) {
            outgoing.write(chunk);
          }
        }

        outgoing.end();
      })
      .catch((failure: unknown) => {
        console.error('the Worker failed outright', failure);
        outgoing.writeHead(500).end();
      });
  });

  origin = server.origin;

  return {
    origin,
    close: async () => {
      await server.close();
      rmSync(directory, { recursive: true, force: true });
    },
  };
};
