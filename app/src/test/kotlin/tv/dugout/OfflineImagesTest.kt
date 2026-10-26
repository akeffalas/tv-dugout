package tv.dugout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineImagesTest {
    @Test
    fun thumbnailsNeverReachTheNetworkInTests() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val request = ImageRequest.Builder(context).data("https://i.ytimg.com/vi/test/mqdefault.jpg").build()

            val result = SingletonImageLoader.get(context).execute(request)

            assertThat(result).isInstanceOf(ErrorResult::class.java)
            assertThat((result as ErrorResult).throwable).hasMessageThat().contains("Unable to create a fetcher")
        }
}
