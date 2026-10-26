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
    fun addProfileSavesTheChildAndList() =
        runTest {
            dao.addProfile(profile("a", position = 0), listOf(ProfileVideoEntity("a", "v", 0)))

            assertThat(dao.profiles()).containsExactly(profile("a", position = 0))
            assertThat(dao.profileVideos()).containsExactly(ProfileVideoEntity("a", "v", 0))
        }

    @Test
    fun failedAddLeavesNothingBehind() {
        val duplicate = listOf(ProfileVideoEntity("a", "v", 0), ProfileVideoEntity("a", "v", 1))

        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking { dao.addProfile(profile("a", position = 0), duplicate) }
        }

        kotlinx.coroutines.runBlocking {
            assertThat(dao.profiles()).isEmpty()
            assertThat(dao.profileVideos()).isEmpty()
        }
    }

    @Test
    fun ageRangeAndLevelAreSaved() =
        runTest {
            val child = profile("a", position = 0).copy(ageRange = "SEVEN_TO_EIGHT", level = "COACH_PITCH")
            dao.insertProfiles(listOf(child))

            assertThat(dao.profiles()).containsExactly(child)
        }

    @Test
    fun progressNeedsAnExistingProfile() {
        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking { dao.saveProgress(row()) }
        }
    }

    @Test
    fun replaceProfileVideosSwapsOnlyThatChild() =
        runTest {
            dao.insertProfiles(listOf(profile("a", 0), profile("b", 1)))
            dao.insertProfileVideos(
                listOf(
                    ProfileVideoEntity("a", "x", 0),
                    ProfileVideoEntity("a", "y", 1),
                    ProfileVideoEntity("b", "x", 0),
                ),
            )

            dao.replaceProfileVideos("a", listOf(ProfileVideoEntity("a", "z", 0), ProfileVideoEntity("a", "x", 1)))

            assertThat(dao.profileVideos())
                .containsExactly(
                    ProfileVideoEntity("a", "z", 0),
                    ProfileVideoEntity("a", "x", 1),
                    ProfileVideoEntity("b", "x", 0),
                )
        }

    @Test
    fun removeProfileCascadesAndRenumbers() =
        runTest {
            dao.insertProfiles(listOf(profile("a", 0), profile("b", 1), profile("c", 2)))
            dao.insertProfileVideos(listOf(ProfileVideoEntity("b", "x", 0), ProfileVideoEntity("c", "x", 0)))
            dao.saveProgress(row().copy(profileId = "b"))

            dao.removeProfile("b")

            assertThat(dao.profiles()).containsExactly(profile("a", 0), profile("c", 1)).inOrder()
            assertThat(dao.profileVideos()).containsExactly(ProfileVideoEntity("c", "x", 0))
            assertThat(dao.progress()).isEmpty()
        }

    @Test
    fun deleteProgressLeavesOtherVideos() =
        runTest {
            dao.insertProfiles(listOf(profile("a", 0)))
            listOf("x", "y", "z").forEach { dao.saveProgress(row(videoId = it)) }

            dao.deleteProgress("a", listOf("x", "z"))

            assertThat(dao.progress().map { it.videoId }).containsExactly("y")
        }

    @Test
    fun updateProfileChangesItsFields() =
        runTest {
            dao.insertProfiles(listOf(profile("a", 0)))

            dao.updateProfile(profile("a", 0).copy(name = "Al", level = "TEE_BALL", offeredLibraryVersion = 2))

            assertThat(dao.profiles().single())
                .isEqualTo(profile("a", 0).copy(name = "Al", level = "TEE_BALL", offeredLibraryVersion = 2))
        }

    @Test
    fun parentLockRoundTrips() =
        runTest {
            assertThat(dao.parentLock()).isNull()
            val lock = ParentLockEntity(1, byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), byteArrayOf(4), 5, 60_000L)

            dao.saveParentLock(lock)
            dao.saveParentLock(lock.copy(failedAttempts = 6))

            assertThat(dao.parentLock()).isEqualTo(lock.copy(failedAttempts = 6))
        }

    @Test
    fun gameProgressRoundTrips() =
        runTest {
            dao.insertProfiles(listOf(profile("a", position = 0)))
            dao.saveGameBest(GameBestEntity("a", 2))
            dao.saveGameBest(GameBestEntity("a", 5))
            dao.saveMasteredPlay(MasteredPlayEntity("a", "p01"))
            dao.saveMasteredPlay(MasteredPlayEntity("a", "p01"))
            dao.saveMasteredPlay(MasteredPlayEntity("a", "p02"))

            assertThat(dao.gameBests()).containsExactly(GameBestEntity("a", 5))
            assertThat(dao.masteredPlays())
                .containsExactly(MasteredPlayEntity("a", "p01"), MasteredPlayEntity("a", "p02"))
        }

    @Test
    fun removingAChildDeletesGameProgress() =
        runTest {
            dao.insertProfiles(listOf(profile("a", position = 0), profile("b", position = 1)))
            dao.saveGameBest(GameBestEntity("a", 3))
            dao.saveMasteredPlay(MasteredPlayEntity("a", "p01"))
            dao.saveMasteredPlay(MasteredPlayEntity("b", "p01"))

            dao.removeProfile("a")

            assertThat(dao.gameBests()).isEmpty()
            assertThat(dao.masteredPlays()).containsExactly(MasteredPlayEntity("b", "p01"))
        }
}
