package tv.dugout

import tv.dugout.parent.FixedPinKey
import tv.dugout.parent.PinKey

/** The real app on an empty database, with a PIN key that needs no Android Keystore. */
class FreshDugoutApplication : DugoutApplication() {
    override fun pinKey(): PinKey = FixedPinKey()
}
