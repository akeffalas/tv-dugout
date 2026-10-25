package tv.dugout.storage

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DugoutDaoTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DugoutDatabase::class.java)
            .build()
    private val dao = database.dao()

    @After
    fun close() = database.close()

    private fun profile(
        id: String,
        position: Int,
    ) = ProfileEntity(id = id, name = id.uppercase(), avatarColor = 0xFFE63946, position = position)

    private fun row(
        videoId: String = "v",
        last: Float = 10f,
    ) = WatchProgressEntity(
        profileId = "a",
        videoId = videoId,
        furthestSeconds = 20f,
        lastSeconds = last,
        durationSeconds = 100f,
        lastPlayedAt = 5L,
        watched = false,
    )

    @Test
    fun profilesComeBackInPositionOrder() =
        runTest {
            dao.insertProfiles(listOf(profile("b", position = 1), profile("a", position = 0)))

            assertThat(dao.profiles().map { it.id }).containsExactly("a", "b").inOrder()
            assertThat(dao.profiles().first()).isEqualTo(profile("a", position = 0))
        }

    @Test
    fun profileVideosComeBackInPositionOrder() =
        runTest {
            dao.insertProfiles(listOf(profile("a", position = 0)))
            dao.insertProfileVideos(
                listOf(ProfileVideoEntity("a", "second", 1), ProfileVideoEntity("a", "first", 0)),
            )

            assertThat(dao.profileVideos().map { it.videoId }).containsExactly("first", "second").inOrder()
        }

    @Test
    fun saveProgressReplacesTheRow() =
        runTest {
            dao.insertProfiles(listOf(profile("a", position = 0)))
            dao.saveProgress(row(last = 10f))
            dao.saveProgress(row(last = 30f))

            assertThat(dao.progress()).containsExactly(row(last = 30f))
        }

    @Test
    fun deletingAProfileDeletesItsVideosAndProgress() =
        runTest {
            dao.insertProfiles(listOf(profile("a", position = 0)))
            dao.insertProfileVideos(listOf(ProfileVideoEntity("a", "v", 0)))
            dao.saveProgress(row())

            dao.deleteProfile("a")

            assertThat(dao.profiles()).isEmpty()
            assertThat(dao.profileVideos()).isEmpty()
            assertThat(dao.progress()).isEmpty()
        }

    @Test
    fun progressNeedsAnExistingProfile() {
        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking { dao.saveProgress(row()) }
        }
    }
}
