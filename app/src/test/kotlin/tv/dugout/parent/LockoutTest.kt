package tv.dugout.parent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LockoutTest {
    @Test
    fun scheduleDoublesToAnHour() {
        val minute = 60_000L
        val waits = mapOf(1 to 0L, 4 to 0L, 5 to minute, 6 to 0L, 10 to 2 * minute, 15 to 4 * minute, 30 to 32 * minute)

        waits.forEach { (failures, wait) -> assertThat(lockWaitMillis(failures)).isEqualTo(wait) }
        assertThat(lockWaitMillis(35)).isEqualTo(60 * minute)
        assertThat(lockWaitMillis(40)).isEqualTo(60 * minute)
        assertThat(lockWaitMillis(500)).isEqualTo(60 * minute)
    }
}
