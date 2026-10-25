package tv.dugout.parent

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.SecureRandom

class RecoveryCodeTest {
    @Test
    fun codesAreEightCharactersFromTheAlphabet() {
        val random = SecureRandom()

        repeat(1_000) {
            val code = RecoveryCode.generate(random)
            assertThat(code).hasLength(8)
            assertThat(code.all { it in RecoveryCode.ALPHABET }).isTrue()
        }
    }

    @Test
    fun alphabetLeavesOutLookAlikes() {
        assertThat(RecoveryCode.ALPHABET).isEqualTo("23456789ABCDEFGHJKMNPQRSTUVWXYZ")
    }

    @Test
    fun formatAddsOneDash() {
        assertThat(RecoveryCode.format("ABCD2345")).isEqualTo("ABCD-2345")
    }

    @Test
    fun normalizeIgnoresCaseDashAndSpaces() {
        assertThat(RecoveryCode.normalize(" abcd-2345 ")).isEqualTo("ABCD2345")
        assertThat(RecoveryCode.normalize("ab cd 23 45")).isEqualTo("ABCD2345")
    }
}
