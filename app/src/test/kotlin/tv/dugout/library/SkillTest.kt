package tv.dugout.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SkillTest {
    @Test
    fun everySkillHasItsOwnIcon() {
        assertThat(Skill.entries.map { it.icon }.toSet()).hasSize(Skill.entries.size)
    }
}
