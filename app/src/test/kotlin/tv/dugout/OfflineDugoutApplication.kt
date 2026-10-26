package tv.dugout

import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.serviceLoaderEnabled

/** The real app for Robolectric tests, except that thumbnails never reach the network. */
open class OfflineDugoutApplication :
    DugoutApplication(),
    SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context as Context)
            .serviceLoaderEnabled(false)
            .build()
}
