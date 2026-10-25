package tv.dugout.parent

import java.security.SecureRandom

private const val CODE_LENGTH = 8

/** The one-time code that resets a forgotten PIN. */
object RecoveryCode {
    /** Digits and capitals without look-alikes: no 0, O, 1, I, or L. */
    const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    fun generate(random: SecureRandom): String =
        String(
            CharArray(CODE_LENGTH) {
                ALPHABET[random.nextInt(ALPHABET.length)]
            },
        )

    /** Shows [code] as `XXXX-XXXX`. */
    fun format(code: String): String = code.take(CODE_LENGTH / 2) + "-" + code.drop(CODE_LENGTH / 2)

    /** What a parent typed, as it is checked: capitals, with dashes and spaces dropped. */
    fun normalize(input: String): String = input.uppercase().filterNot { it == '-' || it.isWhitespace() }
}
