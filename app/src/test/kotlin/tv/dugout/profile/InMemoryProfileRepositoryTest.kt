package tv.dugout.profile

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import tv.dugout.SAMPLE_PROFILES
import tv.dugout.library.AgeRange
import tv.dugout.library.FixedProfileVideos
import tv.dugout.library.Level

class InMemoryProfileRepositoryTest {
    private val lists = FixedProfileVideos(emptyMap())
    private val saved = mutableListOf<Triple<Profile, Int, List<String>>>()
    private val maya = Profile("maya", "Maya", 0xFFF4A261, AgeRange.SEVEN_TO_EIGHT, Level.COACH_PITCH)

    private fun repository(
        saver: ProfileSaver =
            ProfileSaver { profile, position, ids ->
                saved +=
                    Triple(profile, position, ids)
            },
    ) = InMemoryProfileRepository(SAMPLE_PROFILES, lists, saver)

    @Test
    fun profilesKeepTheirOrder() {
        assertThat(repository().profiles.value.map { it.name }).containsExactly("Sam", "Jack").inOrder()
    }

    @Test
    fun profileLooksUpById() {
        assertThat(repository().profile("jack")?.name).isEqualTo("Jack")
    }

    @Test
    fun unknownIdReturnsNull() {
        assertThat(repository().profile("nobody")).isNull()
    }

    @Test
    fun addAppendsTheChildAndTheirList() =
        runTest {
            val repository = repository()

            repository.add(maya, listOf("a", "b"))

            assertThat(repository.profiles.value.last()).isEqualTo(maya)
            assertThat(repository.profile("maya")).isEqualTo(maya)
            assertThat(lists.videoIds("maya")).containsExactly("a", "b").inOrder()
        }

    @Test
    fun addPassesTheNextPositionToTheSaver() =
        runTest {
            repository().add(maya, listOf("a"))

            assertThat(saved).containsExactly(Triple(maya, 2, listOf("a")))
        }

    @Test
    fun failedSaveChangesNothing() {
        val repository = repository(saver = { _, _, _ -> error("disk full") })

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { repository.add(maya, listOf("a")) }
        }

        assertThat(repository.profiles.value).isEqualTo(SAMPLE_PROFILES)
        assertThat(lists.videoIds("maya")).isEmpty()
    }
}
