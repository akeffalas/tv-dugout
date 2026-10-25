package tv.dugout.parent

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A [PinKey] with a known key, for tests that cannot reach the Android Keystore. */
class FixedPinKey(
    private val key: ByteArray = ByteArray(32) { it.toByte() },
    var present: Boolean = true,
) : PinKey {
    override fun exists(): Boolean = present

    override fun create() {
        present = true
    }

    override fun mac(data: ByteArray): ByteArray {
        check(present) { "No parent PIN key" }
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }
    }
}
