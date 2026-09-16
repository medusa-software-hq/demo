import { repositoryRoot } from './paths.ts';
import { spawn } from 'node:child_process';
import { readdirSync } from 'node:fs';
import { join } from 'node:path';

/**
 * The backend as it runs on a developer's machine: a database, a Temporal server, the Service and a
 * worker, in one process started from the distribution Gradle builds.
 *
 * Started as a process rather than through Gradle, because Gradle is slow to start and would stand
 * between this and the signal that stops it. `task systemTests:test` and CI build the distribution
 * first.
 */
export interface LocalStack {
  /** Where the Service answers, with nothing in front of it. */
  readonly servicePort: number;

  stop(): Promise<void>;
}

/** Long enough for a cold machine to bring up a database, a Temporal server and the Service. */
const START_TIMEOUT_MS = 180_000;

/** Long enough for everything the stack started to be stopped in turn. */
const STOP_TIMEOUT_MS = 30_000;

/** What the stack prints once the Service answers, and so what is waited for. */
const SERVICE_PORT_LINE = /^Demo service port: (\d+)$/m;

/** How much of what the stack printed is kept, for an error to show why it did not start. */
const KEPT_OUTPUT_CHARS = 20_000;

const launcher = join(
  repositoryRoot,
  'backend/local-stack/build/install/local-stack/bin/local-stack',
);

/** The Temporal CLI the build unpacked, which is the only thing in its directory. */
const temporalCli = (): string => {
  const directory = join(repositoryRoot, 'build/temporal-cli');
  const versions = readdirSync(directory, { withFileTypes: true }).filter((entry) =>
    entry.isDirectory(),
  );
  const [version] = versions;

  if (version === undefined || versions.length > 1) {
    throw new Error(`Expected exactly one Temporal CLI under ${directory}`);
  }

  return join(directory, version.name, 'temporal');
};

export const startLocalStack = async (): Promise<LocalStack> => {
  const child = spawn(launcher, ['--work-plan=brief'], {
    env: { ...process.env, LOCAL_STACK_OPTS: `-Ddemo.temporal.cli=${temporalCli()}` },
    stdio: ['ignore', 'pipe', 'pipe'],
  });

  // If this process ends first, the stack must not outlive it. A signal rather than a kill, so the
  // stack stops what it started on its way out — the database above all.
  const stopWithThisProcess = (): void => {
    child.kill('SIGTERM');
  };

  process.once('exit', stopWithThisProcess);

  let output = '';

  // Read for as long as the stack runs, not only until it starts: a pipe nobody drains fills up,
  // and the stack would then block on its next line.
  const keep = (chunk: Buffer): void => {
    output = (output + chunk.toString()).slice(-KEPT_OUTPUT_CHARS);
  };

  child.stdout.on('data', keep);
  child.stderr.on('data', keep);

  const stop = async (): Promise<void> => {
    process.removeListener('exit', stopWithThisProcess);

    if (child.exitCode !== null || child.signalCode !== null) {
      return;
    }

    const exited = new Promise<void>((resolve) => {
      child.once('exit', () => {
        resolve();
      });
    });

    child.kill('SIGTERM');

    const escalation = setTimeout(() => child.kill('SIGKILL'), STOP_TIMEOUT_MS);

    await exited;
    clearTimeout(escalation);
  };

  try {
    const servicePort = await new Promise<number>((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(
          new Error(`The local stack did not start within ${START_TIMEOUT_MS} ms:\n${output}`),
        );
      }, START_TIMEOUT_MS);

      child.stdout.on('data', () => {
        const port = SERVICE_PORT_LINE.exec(output)?.[1];

        if (port !== undefined) {
          clearTimeout(timeout);
          resolve(Number(port));
        }
      });

      child.once('exit', (code, signal) => {
        clearTimeout(timeout);
        reject(
          new Error(`The local stack ended (${code ?? signal}) before it started:\n${output}`),
        );
      });
    });

    return { servicePort, stop };
  } catch (failure) {
    await stop();

    throw failure;
  }
};
