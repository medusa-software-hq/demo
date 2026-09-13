import { errors, jwtVerify, type JWTPayload, type JWTVerifyGetKey } from 'jose';

/**
 * The token that says who signed in, checked.
 *
 * Signing in happens in front of this Worker, and whatever does it puts a token on every
 * request it lets through. That it let a request through is not something this Worker can
 * see — a request can reach it by a way that never met the sign-in at all — so the token
 * is what gets believed, and only once its signature, issuer, audience and expiry are all
 * what they should be.
 *
 * Nothing here names what does the signing. It is described entirely by three values the
 * platform hands over — an issuer, where its keys are published, and an audience — so a
 * different login is three different values rather than different code.
 */

/** What the tokens are signed with. Named here, so a token's own header is no argument. */
const SIGNING_ALGORITHM = 'RS256';

/** How far apart this clock and the issuer's may be before a fresh token counts as expired. */
const CLOCK_TOLERANCE_SECONDS = 60;

/**
 * What a token can be refused for, in `jose`'s names — every one of them the token's own
 * doing.
 *
 * What is left off is left off on purpose. A key set the issuer would not serve, or served
 * too slowly, says nothing about the token, and refusing the request as though it did
 * would report an outage as an intrusion. Those are thrown instead.
 */
const TOKEN_FAULTS: ReadonlySet<string> = new Set([
  errors.JWSInvalid.code,
  errors.JWTInvalid.code,
  errors.JOSEAlgNotAllowed.code,
  errors.JWSSignatureVerificationFailed.code,
  errors.JWKSNoMatchingKey.code,
  errors.JWTExpired.code,
  errors.JWTClaimValidationFailed.code,
]);

/** Who signed in. Everything else the token says about them is not ours to keep. */
export interface SignedInUser {
  /**
   * The issuer's id for this person.
   *
   * Unique, and not permanent: Cloudflare Access issues a new one to somebody removed and
   * added back. Good for telling two people apart; not for storing anything against.
   */
  readonly subject: string;

  /** Their address, as the identity provider verified it. */
  readonly email: string;
}

/**
 * Accepts tokens from one issuer, meant for one audience.
 *
 * Both bounds matter and neither implies the other. Without the issuer, a token signed by
 * anyone whose keys happened to be fetched would pass; without the audience, a token the
 * same issuer made for another app — or for this app's other environment — would.
 */
export class SignInTokenVerifier {
  constructor(
    private readonly issuer: string,
    private readonly audience: string,

    /**
     * The issuer's keys.
     *
     * In the Worker, `jose`'s remote key set, which holds what it fetched for ten minutes
     * and looks again for an unknown key id at most every thirty seconds — so an invented
     * `kid` costs a request per isolate per half minute, not one per request. In the
     * tests, a key set built from a key made on the spot.
     */
    private readonly keySet: JWTVerifyGetKey,
  ) {}

  /**
   * Who [token] proves signed in, or `null` if it proves nothing.
   *
   * One `null` for every way of failing, on purpose: which check a token failed is worth
   * knowing to us and worth nothing to whoever presented it. That is what the logging is
   * for — the reason is written down at the point where it stops being carried.
   */
  async verify(token: string): Promise<SignedInUser | null> {
    const claims = await this.verifiedClaims(token);

    if (claims === null) {
      return null;
    }

    // A token that names nobody — one issued to a service rather than a person — says
    // that something was let in, and not who.
    if (typeof claims.sub !== 'string' || typeof claims['email'] !== 'string') {
      console.warn('rejecting a token: no subject or no address');

      return null;
    }

    return { subject: claims.sub, email: claims['email'] };
  }

  /** What [token] says, if the issuer said it, to this audience, and recently; `null` otherwise. */
  private async verifiedClaims(token: string): Promise<JWTPayload | null> {
    try {
      const { payload } = await jwtVerify(token, this.keySet, {
        algorithms: [SIGNING_ALGORITHM],
        issuer: this.issuer,
        audience: this.audience,
        clockTolerance: CLOCK_TOLERANCE_SECONDS,

        // `exp` is checked whenever it is present, which is not the same as requiring it.
        requiredClaims: ['exp'],
      });

      return payload;
    } catch (failure) {
      if (failure instanceof errors.JOSEError && TOKEN_FAULTS.has(failure.code)) {
        console.warn('rejecting a token', failure.code, failure.message);

        return null;
      }

      throw failure;
    }
  }
}
