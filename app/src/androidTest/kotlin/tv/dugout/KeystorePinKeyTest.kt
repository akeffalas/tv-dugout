package tv.dugout

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.parent.KeystorePinKey
import tv.dugout.parent.PinHasher
import java.security.KeyStore

/** The real Keystore key, which Robolectric cannot provide. Uses its own alias, deleted afterward. */
@RunWith(AndroidJUnit4::class)
class KeystorePinKeyTest {
    private val alias = "dugout_test_${System.nanoTime()}"
    private val key = KeystorePinKey(alias)

    @After
    fun delete() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)

    @Test
    fun createThenMacIsStable() {
        assertThat(key.exists()).isFalse()

        key.create()

        assertThat(key.exists()).isTrue()
        assertThat(key.mac(byteArrayOf(1, 2, 3))).isEqualTo(key.mac(byteArrayOf(1, 2, 3)))
        assertThat(key.mac(byteArrayOf(1, 2, 3))).hasLength(32)
    }

    @Test
    fun macWithoutKeyThrows() {
        val failure = runCatching { key.mac(byteArrayOf(1)) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun pinHashMatchesWithTheRealKey() {
        key.create()
        val hasher = PinHasher(key)
        val salt = hasher.newSalt()

        assertThat(hasher.matches("1234", salt, hasher.hash("1234", salt))).isTrue()
        assertThat(hasher.matches("4321", salt, hasher.hash("1234", salt))).isFalse()
    }
}
