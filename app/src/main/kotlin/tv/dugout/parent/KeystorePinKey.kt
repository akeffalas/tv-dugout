package tv.dugout.parent

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

private const val KEYSTORE = "AndroidKeyStore"

/** A [PinKey] held in the Android Keystore, which can use the key but never hands it out. */
class KeystorePinKey(
    private val alias: String = "dugout_parent_pin",
) : PinKey {
    // Opened on first use, so building the app's data never touches the Keystore.
    private val store: KeyStore by lazy { KeyStore.getInstance(KEYSTORE).apply { load(null) } }

    override fun exists(): Boolean = store.containsAlias(alias)

    override fun create() {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE).run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).build())
            generateKey()
        }
    }

    override fun mac(data: ByteArray): ByteArray {
        val key = store.getKey(alias, null) as? SecretKey ?: throw IllegalStateException("No parent PIN key")
        return Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256).run {
            init(key)
            doFinal(data)
        }
    }
}
