package tv.dugout.parent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PinHasherTest {
    private val hasher = PinHasher(FixedPinKey(), iterations = 1)
    private val salt = hasher.newSalt()

    @Test
    fun saltsAreSixteenRandomBytes() {
        assertThat(salt).hasLength(16)
        assertThat(hasher.newSalt()).isNotEqualTo(salt)
    }

    @Test
    fun sameSecretAndSaltMatch() {
        assertThat(hasher.matches("1234", salt, hasher.hash("1234", salt))).isTrue()
    }

    @Test
    fun wrongSecretDoesNotMatch() {
        assertThat(hasher.matches("1235", salt, hasher.hash("1234", salt))).isFalse()
    }

    @Test
    fun differentSaltsDiffer() {
        assertThat(hasher.hash("1234", hasher.newSalt())).isNotEqualTo(hasher.hash("1234", hasher.newSalt()))
    }

    @Test
    fun differentKeysDiffer() {
        val other = PinHasher(FixedPinKey(ByteArray(32) { 7 }), iterations = 1)

        assertThat(other.hash("1234", salt)).isNotEqualTo(hasher.hash("1234", salt))
    }

    @Test
    fun hashDoesNotContainThePin() {
        val hash = hasher.hash("1234", salt)

        assertThat(String(hash, Charsets.ISO_8859_1)).doesNotContain("1234")
        assertThat(hash).hasLength(32)
    }

    @Test
    fun missingKeyCannotHash() {
        val hasher = PinHasher(FixedPinKey(present = false), iterations = 1)

        val failure = runCatching { hasher.hash("1234", salt) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
    }
}
