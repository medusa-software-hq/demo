package software.medusa.demo.core

/**
 * Whose something is: the key personal data is kept against.
 *
 * Today the address the organization's identity provider verified. Not the sign-in's own subject
 * id, which changes when somebody is removed and added back, and which would take their data with
 * it. An address stays for as long as the account keeps it; renaming an account is the one thing
 * this does not survive.
 */
@JvmInline value class UserId(val value: String)
