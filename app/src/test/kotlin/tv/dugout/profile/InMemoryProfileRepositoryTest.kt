package tv.dugout.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InMemoryProfileRepositoryTest {
    @Test
    fun defaultProfilesAreSamThenJack() {
        val profiles = InMemoryProfileRepository().profiles()

        assertThat(profiles.map { it.name }).containsExactly("Sam", "Jack").inOrder()
    }

    @Test
    fun defaultProfilesUseTheMockupColors() {
        val colors = InMemoryProfileRepository().profiles().map { it.avatarColor }

        assertThat(colors).containsExactly(0xFFE63946, 0xFF2A9D8F).inOrder()
    }

    @Test
    fun profileLooksUpById() {
        assertThat(InMemoryProfileRepository().profile("jack")?.name).isEqualTo("Jack")
    }

    @Test
    fun unknownIdReturnsNull() {
        assertThat(InMemoryProfileRepository().profile("nobody")).isNull()
    }
}
