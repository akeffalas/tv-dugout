package tv.dugout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherArtTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val info = context.packageManager.getApplicationInfo(context.packageName, 0)

    @Test
    fun theIconIsSquareAndNotTheBanner() {
        assertThat(info.icon).isNotEqualTo(info.banner)

        val icon = context.getDrawable(info.icon)!!

        assertThat(icon.intrinsicWidth).isEqualTo(icon.intrinsicHeight)
    }

    @Test
    fun theBannerIsSixteenByNine() {
        val banner = context.getDrawable(info.banner)!!

        assertThat(banner.intrinsicWidth * 9).isEqualTo(banner.intrinsicHeight * 16)
    }
}
