package tv.dugout.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.dugout.SAMPLE_PROFILES

class InMemoryProfileRepositoryTest {
    @Test
    fun defaultProfilesAreSamThenJack() {
        val profiles = InMemoryProfileRepository(SAMPLE_PROFILES).profiles()

        assertThat(profiles.map { it.name }).containsExactly("Sam", "Jack").inOrder()
    }

    @Test
    fun defaultProfilesUseTheMockupColors() {
        val colors = InMemoryProfileRepository(SAMPLE_PROFILES).profiles().map { it.avatarColor }

        assertThat(colors).containsExactly(0xFFE63946, 0xFF2A9D8F).inOrder()
    }

    @Test
    fun profileLooksUpById() {
        assertThat(InMemoryProfileRepository(SAMPLE_PROFILES).profile("jack")?.name).isEqualTo("Jack")
    }

    @Test
    fun unknownIdReturnsNull() {
        assertThat(InMemoryProfileRepository(SAMPLE_PROFILES).profile("nobody")).isNull()
    }
}
