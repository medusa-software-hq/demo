/**
 * Where Node and the runtime the Worker is deployed to disagree, settled the edge's way.
 *
 * Injected into the Worker's bundle and nowhere else: esbuild replaces the bundle's references to
 * the globals exported here with these, so the rest of this process keeps Node's own.
 */

/**
 * A request that may carry a streamed body without saying `duplex`.
 *
 * The Fetch standard asks for `duplex: 'half'` whenever a body is a stream, and Node enforces it.
 * Cloudflare's runtime does not, and its typings do not even name the option — so the Worker,
 * which forwards a request by handing its body stream on, never says it. Here it is said for it.
 */
class EdgeRequest extends globalThis.Request {
  constructor(input: RequestInfo | URL, init?: RequestInit) {
    super(
      input,
      init?.body instanceof ReadableStream ? ({ ...init, duplex: 'half' } as RequestInit) : init,
    );
  }
}

export { EdgeRequest as Request };
