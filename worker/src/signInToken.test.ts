import {
  createLocalJWKSet,
  errors,
  exportJWK,
  generateKeyPair,
  SignJWT,
  type JWTPayload,
} from 'jose';
import { SignInTokenVerifier } from './signInToken.ts';
import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';

/**
 * What the verifier does with tokens, including the ones it should not believe.
 *
 * The issuer's part is played by a key generated here: tokens are signed the way Cloudflare
 * Access signs them, carrying the claims it puts in them, and a key set holding the public
 * half is handed to the verifier in place of the published one.
 */

const issuer = 'https://medusa-software.cloudflareaccess.com';
const audience = '4714c1358e65fe4b408ad6d432a5f878f08194bdb4752441fd56faefa9b2b6f2';
const keyId = 'test-key';

const signer = await generateKeyPair('RS256');

/** Somebody else's key, which the issuer never published. */
const impostor = await generateKeyPair('RS256');

const keySet = createLocalJWKSet({
  keys: [{ ...(await exportJWK(signer.publicKey)), kid: keyId, alg: 'RS256', use: 'sig' }],
});

/** The claims Access puts in a token for somebody it let in. `aud` is a list, as Access sends it. */
function validClaims(): JWTPayload {
  const nowSeconds = Math.floor(Date.now() / 1000);

  return {
    iss: issuer,
    aud: [audience],
    sub: '7335d417-61da-459d-899c-0a01c76a3a47',
    email: 'someone@medusa.software',
    type: 'app',
    identity_nonce: '6ei69kawdKzMIAPF',
    country: 'PL',
    iat: nowSeconds,
    nbf: nowSeconds,
    exp: nowSeconds + 3600,
  };
}

/** A token signed the way the issuer signs one, unless told to sign it some other way. */
async function mintToken(
  claims: JWTPayload,
  { kid = keyId, key = signer.privateKey }: { kid?: string; key?: CryptoKey } = {},
): Promise<string> {
  return new SignJWT(claims).setProtectedHeader({ alg: 'RS256', kid }).sign(key);
}

function base64Url(value: unknown): string {
  return Buffer.from(JSON.stringify(value)).toString('base64url');
}

describe('SignInTokenVerifier', () => {
  let verifier: SignInTokenVerifier;

  beforeEach(() => {
    verifier = new SignInTokenVerifier(issuer, audience, keySet);
  });

  it('accepts a token the issuer signed for this audience', async () => {
    assert.deepEqual(await verifier.verify(await mintToken(validClaims())), {
      subject: '7335d417-61da-459d-899c-0a01c76a3a47',
      email: 'someone@medusa.software',
    });
  });

  // The case the audience exists for: the same sign-in, the same issuer, another app — or
  // this app's other environment.
  it('rejects a token meant for another audience', async () => {
    assert.equal(
      await verifier.verify(await mintToken({ ...validClaims(), aud: ['another-application'] })),
      null,
    );
  });

  it('rejects a token from another issuer', async () => {
    assert.equal(
      await verifier.verify(
        await mintToken({ ...validClaims(), iss: 'https://somebody-else.cloudflareaccess.com' }),
      ),
      null,
    );
  });

  it('rejects an expired token', async () => {
    const nowSeconds = Math.floor(Date.now() / 1000);

    assert.equal(
      await verifier.verify(
        await mintToken({ ...validClaims(), iat: nowSeconds - 7200, exp: nowSeconds - 3600 }),
      ),
      null,
    );
  });

  it('rejects a token that never expires', async () => {
    const { exp: _, ...claims } = validClaims();

    assert.equal(await verifier.verify(await mintToken(claims)), null);
  });

  it('rejects a token that names nobody, as a service token does', async () => {
    const { email: _, ...claims } = validClaims();

    assert.equal(await verifier.verify(await mintToken({ ...claims, sub: '' })), null);
  });

  // The one that matters most: everything above could be checked by reading the token, and
  // none of it means anything unless the signature says the issuer wrote it at all.
  it('rejects claims edited after signing', async () => {
    const [header, , signature] = (await mintToken(validClaims())).split('.');
    const forged = base64Url({ ...validClaims(), email: 'someone-else@medusa.software' });

    assert.equal(await verifier.verify(`${header}.${forged}.${signature}`), null);
  });

  it('rejects a token signed with a key the issuer does not hold, under a key id it does', async () => {
    assert.equal(
      await verifier.verify(await mintToken(validClaims(), { key: impostor.privateKey })),
      null,
    );
  });

  it('rejects a key id the issuer never published', async () => {
    assert.equal(await verifier.verify(await mintToken(validClaims(), { kid: 'made-up' })), null);
  });

  it('rejects a token that says it needs no signature', async () => {
    assert.equal(
      await verifier.verify(`${base64Url({ alg: 'none' })}.${base64Url(validClaims())}.`),
      null,
    );
  });

  it('rejects a token that is not a token', async () => {
    assert.equal(await verifier.verify('not-a-token'), null);
  });

  // Not a `null`: nothing is known to be wrong with the token, and refusing the request as
  // though something were would report an outage as an intrusion.
  it('fails, rather than refusing the token, when the keys cannot be had', async () => {
    const unreachable = new SignInTokenVerifier(issuer, audience, () =>
      Promise.reject(new errors.JWKSTimeout()),
    );

    await assert.rejects(unreachable.verify(await mintToken(validClaims())), errors.JWKSTimeout);
  });
});
