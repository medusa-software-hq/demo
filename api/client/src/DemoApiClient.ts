import { StatusCodes } from 'http-status-codes';
import { createClient, createConfig } from './gen/client/index.ts';
import * as sdk from './gen/sdk.gen.ts';

/**
 * The API, in the answers each operation can actually give.
 *
 * Counters, which everyone shares, and todos, which are the caller's own — though nothing here
 * says whose, since the Worker in front of the service names the caller and this client never does.
 *
 * The same shape as `todo-board`'s client, minus the parts that exist for signing people in:
 * nothing authenticates the caller of this page yet, so there is no token to present and no
 * refusal to act on. What the two still share is the important bit — an outcome the contract
 * describes is a value, and a status the contract never promised is an error rather than a guess.
 */

/** A counter, and the last count the service reported for it. */
export interface DemoCounter {
  readonly counterId: string;
  readonly count: number;
}

export const DemoCounterResponseKinds = {
  received: 'received',
  adjusted: 'adjusted',
  deleted: 'deleted',
  noSuchCounter: 'noSuchCounter',
} as const;

export type DemoCounterNoSuchCounterResponse = {
  readonly kind: typeof DemoCounterResponseKinds.noSuchCounter;
};

const noSuchCounterResponse: DemoCounterNoSuchCounterResponse = {
  kind: DemoCounterResponseKinds.noSuchCounter,
};

export type DemoCounterReceivedResponse =
  | { readonly kind: typeof DemoCounterResponseKinds.received; readonly currentCount: number }
  | DemoCounterNoSuchCounterResponse;

export type DemoCounterAdjustmentResponse =
  | { readonly kind: typeof DemoCounterResponseKinds.adjusted; readonly newCount: number }
  | DemoCounterNoSuchCounterResponse;

export type DemoCounterDeletedResponse =
  | { readonly kind: typeof DemoCounterResponseKinds.deleted }
  | DemoCounterNoSuchCounterResponse;

/** One of the caller's todos. */
export interface DemoTodo {
  readonly todoId: string;
  readonly title: string;
  readonly done: boolean;
}

export const DemoTodoResponseKinds = {
  created: 'created',
  blankTitle: 'blankTitle',
  updated: 'updated',
  deleted: 'deleted',
  noSuchTodo: 'noSuchTodo',
} as const;

/** The caller has no such todo — which is also what somebody else's todo looks like. */
export type DemoTodoNoSuchTodoResponse = {
  readonly kind: typeof DemoTodoResponseKinds.noSuchTodo;
};

const noSuchTodoResponse: DemoTodoNoSuchTodoResponse = {
  kind: DemoTodoResponseKinds.noSuchTodo,
};

export type DemoTodoCreatedResponse =
  | { readonly kind: typeof DemoTodoResponseKinds.created; readonly todoId: string }
  | { readonly kind: typeof DemoTodoResponseKinds.blankTitle };

export type DemoTodoUpdatedResponse =
  | { readonly kind: typeof DemoTodoResponseKinds.updated }
  | DemoTodoNoSuchTodoResponse;

/** A run of work the caller started, and how far it has got. Finished once it has a result. */
export interface DemoWorkRun {
  readonly runId: string;
  readonly stepsDone: number;
  readonly stepsTotal: number;
  readonly result: string | null;
}

/** Whether work can be started here, and the caller's runs, oldest first. */
export interface DemoWorkOverview {
  readonly enabled: boolean;
  readonly runs: readonly DemoWorkRun[];
}

export const DemoWorkResponseKinds = {
  started: 'started',
  disabled: 'disabled',
} as const;

export type DemoWorkStartResponse =
  | { readonly kind: typeof DemoWorkResponseKinds.started; readonly runId: string }
  | { readonly kind: typeof DemoWorkResponseKinds.disabled };

export type DemoTodoDeletedResponse =
  | { readonly kind: typeof DemoTodoResponseKinds.deleted }
  | DemoTodoNoSuchTodoResponse;

export const DemoApiErrorKinds = {
  network: 'network',
  incompatibility: 'incompatibility',
  internalServerError: 'internalServerError',
} as const;

export type DemoApiErrorKind = keyof typeof DemoApiErrorKinds;

export class DemoApiError extends Error {
  constructor(readonly kind: DemoApiErrorKind) {
    super(kind);
    this.name = 'DemoApiError';
  }
}

export type DemoApiClient = {
  listCounters(): Promise<readonly DemoCounter[]>;
  createCounter(): Promise<string>;
  deleteCounter(counterId: string): Promise<DemoCounterDeletedResponse>;
  getCount(counterId: string): Promise<DemoCounterReceivedResponse>;
  incrementCount(counterId: string): Promise<DemoCounterAdjustmentResponse>;
  decrementCount(counterId: string): Promise<DemoCounterAdjustmentResponse>;
  listTodos(): Promise<readonly DemoTodo[]>;
  createTodo(title: string): Promise<DemoTodoCreatedResponse>;
  setTodoDone(todoId: string, done: boolean): Promise<DemoTodoUpdatedResponse>;
  deleteTodo(todoId: string): Promise<DemoTodoDeletedResponse>;
  getWork(): Promise<DemoWorkOverview>;
  startWork(): Promise<DemoWorkStartResponse>;
};

type RawResult<DataT> = {
  data?: DataT | undefined;
  // Optional, because the generated client resolves this way when there was no exchange at all.
  response?: Response | undefined;
};

/** How a client is set up, beyond where the API is. */
export interface DemoApiClientOptions {
  /**
   * Sent with every request, besides whatever an operation sends of its own.
   *
   * For whatever stands between the client and the API and needs telling who is calling. A page
   * needs none: its browser carries the sign-in by itself.
   */
  readonly headers?: Readonly<Record<string, string>>;
}

/** A client for the API at [baseUrl]. */
export function createDemoApiClient(
  baseUrl: string,
  options: DemoApiClientOptions = {},
): DemoApiClient {
  const httpClient = createClient(
    createConfig(
      options.headers === undefined ? { baseUrl } : { baseUrl, headers: { ...options.headers } },
    ),
  );

  /**
   * Wraps a call to the generated client.
   *
   * @param operation - the name of the operation being called
   * @param callRaw - calls the generated method
   * @param processRawResponse - answers the result for a response the operation was promised, and
   *   `undefined` for any other — a 2xx included
   */
  async function wrapCall<DataT, ResponseT>(
    operation: string,
    callRaw: () => Promise<RawResult<DataT>>,
    processRawResponse: (response: Response, data: DataT | undefined) => ResponseT | undefined,
  ): Promise<ResponseT> {
    const tryCallRaw = async () => {
      try {
        return await callRaw();
      } catch (cause) {
        // The generated client rejects only when the exchange did not happen.
        console.warn(`${operation}: the call did not complete over the network`, cause);

        throw new DemoApiError(DemoApiErrorKinds.network);
      }
    };

    const rawResult = await tryCallRaw();
    const rawResponse = rawResult.response;

    if (rawResponse === undefined) {
      console.warn(`${operation}: the call produced no response`);

      throw new DemoApiError(DemoApiErrorKinds.network);
    }

    const { status } = rawResponse;

    if (status >= StatusCodes.INTERNAL_SERVER_ERROR) {
      console.error(`${operation}: the server failed the request`, status);

      throw new DemoApiError(DemoApiErrorKinds.internalServerError);
    }

    const response = processRawResponse(rawResponse, rawResult.data);

    if (response === undefined) {
      console.error(
        `${operation}: the server answered ${status}, which this operation was not promised`,
      );

      throw new DemoApiError(DemoApiErrorKinds.incompatibility);
    }

    return response;
  }

  function processRawReceivedResponse(
    response: Response,
    data: { readonly count: number } | undefined,
  ): DemoCounterReceivedResponse | undefined {
    if (response.status === StatusCodes.OK && data) {
      return { kind: DemoCounterResponseKinds.received, currentCount: data.count };
    }

    if (response.status === StatusCodes.NOT_FOUND) {
      return noSuchCounterResponse;
    }

    return undefined;
  }

  function processRawAdjustmentResponse(
    response: Response,
    data: { readonly count: number } | undefined,
  ): DemoCounterAdjustmentResponse | undefined {
    if (response.status === StatusCodes.OK && data) {
      return { kind: DemoCounterResponseKinds.adjusted, newCount: data.count };
    }

    if (response.status === StatusCodes.NOT_FOUND) {
      return noSuchCounterResponse;
    }

    return undefined;
  }

  return {
    listCounters: () =>
      wrapCall(
        'listCounters',
        () => sdk.listCounters({ client: httpClient }),
        (response, data) =>
          response.status === StatusCodes.OK && data ? data.counters : undefined,
      ),

    createCounter: () =>
      wrapCall(
        'createCounter',
        () => sdk.createCounter({ client: httpClient }),
        (response, data) =>
          response.status === StatusCodes.OK && data ? data.counterId : undefined,
      ),

    deleteCounter: (counterId) =>
      wrapCall(
        'deleteCounter',
        () => sdk.deleteCounter({ client: httpClient, path: { counterId } }),
        (response) => {
          if (response.status === StatusCodes.NO_CONTENT) {
            return { kind: DemoCounterResponseKinds.deleted } as const;
          }

          if (response.status === StatusCodes.NOT_FOUND) {
            return noSuchCounterResponse;
          }

          return undefined;
        },
      ),

    getCount: (counterId) =>
      wrapCall(
        'getCount',
        () => sdk.getCount({ client: httpClient, path: { counterId } }),
        processRawReceivedResponse,
      ),

    incrementCount: (counterId) =>
      wrapCall(
        'incrementCount',
        () => sdk.incrementCount({ client: httpClient, path: { counterId } }),
        processRawAdjustmentResponse,
      ),

    decrementCount: (counterId) =>
      wrapCall(
        'decrementCount',
        () => sdk.decrementCount({ client: httpClient, path: { counterId } }),
        processRawAdjustmentResponse,
      ),

    getWork: () =>
      wrapCall(
        'getWork',
        () => sdk.getWork({ client: httpClient }),
        (response, data) =>
          response.status === StatusCodes.OK && data
            ? {
                enabled: data.enabled,
                runs: data.runs.map((run) => ({ ...run, result: run.result ?? null })),
              }
            : undefined,
      ),

    startWork: () =>
      wrapCall(
        'startWork',
        () => sdk.startWork({ client: httpClient }),
        (response, data) => {
          if (response.status === StatusCodes.OK && data) {
            return { kind: DemoWorkResponseKinds.started, runId: data.runId } as const;
          }

          // This environment runs no work. Nothing was started.
          if (response.status === StatusCodes.CONFLICT) {
            return { kind: DemoWorkResponseKinds.disabled } as const;
          }

          return undefined;
        },
      ),

    listTodos: () =>
      wrapCall(
        'listTodos',
        () => sdk.listTodos({ client: httpClient }),
        (response, data) => (response.status === StatusCodes.OK && data ? data.todos : undefined),
      ),

    createTodo: (title) =>
      wrapCall(
        'createTodo',
        () => sdk.createTodo({ client: httpClient, body: { title } }),
        (response, data) => {
          if (response.status === StatusCodes.OK && data) {
            return { kind: DemoTodoResponseKinds.created, todoId: data.todoId } as const;
          }

          if (response.status === StatusCodes.BAD_REQUEST) {
            return { kind: DemoTodoResponseKinds.blankTitle } as const;
          }

          return undefined;
        },
      ),

    setTodoDone: (todoId, done) =>
      wrapCall(
        'setTodoDone',
        () => sdk.setTodoDone({ client: httpClient, path: { todoId }, body: { done } }),
        (response) => {
          if (response.status === StatusCodes.NO_CONTENT) {
            return { kind: DemoTodoResponseKinds.updated } as const;
          }

          if (response.status === StatusCodes.NOT_FOUND) {
            return noSuchTodoResponse;
          }

          return undefined;
        },
      ),

    deleteTodo: (todoId) =>
      wrapCall(
        'deleteTodo',
        () => sdk.deleteTodo({ client: httpClient, path: { todoId } }),
        (response) => {
          if (response.status === StatusCodes.NO_CONTENT) {
            return { kind: DemoTodoResponseKinds.deleted } as const;
          }

          if (response.status === StatusCodes.NOT_FOUND) {
            return noSuchTodoResponse;
          }

          return undefined;
        },
      ),
  };
}
