package tv.dugout

import tv.dugout.library.VideoLibrary
import tv.dugout.parent.FixedPinKey
import tv.dugout.parent.PinKey
import tv.dugout.storage.Seed

/** The real app, started with the sample children each given the whole library. */
class SampleDugoutApplication : OfflineDugoutApplication() {
    override fun seed(library: VideoLibrary): Seed = Seed(SAMPLE_PROFILES, library.videos().map { it.id })

    override fun pinKey(): PinKey = FixedPinKey()
}
