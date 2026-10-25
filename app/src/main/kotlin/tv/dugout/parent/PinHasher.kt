package tv.dugout.parent

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2 rounds per check. Tuned on a TV so one check takes roughly 100 to 300 ms. */
const val PIN_ITERATIONS = 12_000

private const val SALT_BYTES = 16
private const val HASH_BITS = 256

/**
 * Turns a PIN or recovery code into a hash that cannot be turned back: a slow, salted PBKDF2, then
 * an HMAC under [key], so a copy of the saved hash is useless away from this TV.
 */
class PinHasher(
    private val key: PinKey,
    private val iterations: Int = PIN_ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
) {
    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun hash(
        secret: String,
        salt: ByteArray,
    ): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, HASH_BITS)
        val stretched =
            try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        return key.mac(stretched)
    }

    /** Whether [secret] hashes to [expected], compared in constant time. */
    fun matches(
        secret: String,
        salt: ByteArray,
        expected: ByteArray,
    ): Boolean = MessageDigest.isEqual(hash(secret, salt), expected)
}
