import {
  createDemoApiClient,
  DemoApiError,
  DemoApiErrorKinds,
  type DemoApiClient,
  type DemoApiErrorKind,
} from 'demo-client';
import { assertNever } from './assertNever.ts';

/**
 * The service, on this page's own origin.
 *
 * The Worker in front of both splits this prefix off before anything else sees it, so the page
 * crosses no origin, needs no CORS, and names nothing that differs between environments.
 */
const apiUrl = '/api';

/** The client every page calls the service through. It holds nothing of any one page's. */
export const demoApiClient: DemoApiClient = createDemoApiClient(apiUrl);

/**
 * Runs [call], handing the result to [onSuccess] and a failed call's kind to [onError].
 *
 * Only a `DemoApiError` is caught. Anything else is a bug in this page rather than an answer from
 * the service, and swallowing it here is how it would go unnoticed.
 */
export async function catchingApiError<ResultT>(
  call: () => Promise<ResultT>,
  onSuccess: (result: ResultT) => void,
  onError: (errorKind: DemoApiErrorKind) => void,
): Promise<void> {
  let result: ResultT;

  try {
    result = await call();
  } catch (cause) {
    if (!(cause instanceof DemoApiError)) {
      throw cause;
    }

    onError(cause.kind);

    return;
  }

  onSuccess(result);
}

/** What to say about a call that produced no answer. Chosen here, not carried by the error. */
export function describeApiError(errorKind: DemoApiErrorKind): string {
  switch (errorKind) {
    case DemoApiErrorKinds.network:
      return 'The service could not be reached.';
    case DemoApiErrorKinds.internalServerError:
      return 'The service failed to handle that.';
    case DemoApiErrorKinds.incompatibility:
      return 'The service answered something this page cannot read.';
    default:
      return assertNever(errorKind);
  }
}
