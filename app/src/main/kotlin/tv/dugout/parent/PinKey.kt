package tv.dugout.parent

/** A secret key that never leaves the TV, mixed into every PIN and recovery code hash. */
interface PinKey {
    fun exists(): Boolean

    /** Makes the key, replacing any there was. */
    fun create()

    /** HMAC-SHA256 of [data] under the key. Throws [IllegalStateException] when there is no key. */
    fun mac(data: ByteArray): ByteArray
}
