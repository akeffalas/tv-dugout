package tv.dugout

import tv.dugout.library.VideoLibrary
import tv.dugout.storage.Seed

/** The real app, started with the sample children each given the whole library. */
class SampleDugoutApplication : DugoutApplication() {
    override fun seed(library: VideoLibrary): Seed = Seed(SAMPLE_PROFILES, library.videos().map { it.id })
}
