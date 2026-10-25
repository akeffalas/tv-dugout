package tv.dugout.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.profile.DEFAULT_PROFILES

@RunWith(AndroidJUnit4::class)
class SeedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val seed = Seed(DEFAULT_PROFILES, listOf("a", "b", "c"))

    @Test
    fun newDatabaseHoldsTheSeedProfilesInOrder() =
        runTest {
            val database = dugoutDatabase(context, name = null, seed)

            val profiles = database.dao().profiles()

            assertThat(profiles.map { it.id }).containsExactly("sam", "jack").inOrder()
            assertThat(profiles.map { it.name }).containsExactly("Sam", "Jack").inOrder()
            assertThat(profiles.map { it.avatarColor })
                .containsExactly(DEFAULT_PROFILES[0].avatarColor, DEFAULT_PROFILES[1].avatarColor)
                .inOrder()
            database.close()
        }

    @Test
    fun everySeedProfileGetsEveryVideoInOrder() =
        runTest {
            val database = dugoutDatabase(context, name = null, seed)

            val lists = database.dao().profileVideos().groupBy({ it.profileId }, { it.videoId })

            assertThat(lists).containsExactly("sam", listOf("a", "b", "c"), "jack", listOf("a", "b", "c"))
            database.close()
        }

    @Test
    fun reopeningDoesNotReseedEvenWithNoProfiles() =
        runTest {
            val first = dugoutDatabase(context, "seed-test.db", seed)
            first.dao().deleteProfile("sam")
            first.dao().deleteProfile("jack")
            first.close()

            val second = dugoutDatabase(context, "seed-test.db", seed)

            assertThat(second.dao().profiles()).isEmpty()
            second.close()
        }
}
