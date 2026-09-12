import { StatusCodes } from 'http-status-codes';
import { createClient, createConfig } from './gen/client/index.ts';
import * as sdk from './gen/sdk.gen.ts';

/**
 * The API, in the answers each operation can actually give.
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
};

type RawResult<DataT> = {
  data?: DataT | undefined;
  // Optional, because the generated client resolves this way when there was no exchange at all.
  response?: Response | undefined;
};

/** A client for the API at [baseUrl]. */
export function createDemoApiClient(baseUrl: string): DemoApiClient {
  const httpClient = createClient(createConfig({ baseUrl }));

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
  };
}
