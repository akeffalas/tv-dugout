package tv.dugout.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.eventually
import tv.dugout.library.BundledVideoLibrary
import tv.dugout.player.FakePlayerSurface
import tv.dugout.profile.DEFAULT_PROFILES
import tv.dugout.ready

@RunWith(AndroidJUnit4::class)
class SavedStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val library = BundledVideoLibrary()
    private val seed = Seed(DEFAULT_PROFILES, library.videos().map { it.id })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    @After
    fun stop() = scope.cancel()

    private fun store(open: () -> DugoutDatabase = { dugoutDatabase(context, name = null, seed) }) =
        SavedStore(open, library, FakePlayerSurface(), scope, clock = { 0L })

    @Test
    fun startsLoading() {
        assertThat(store().state.value).isEqualTo(StoreState.Loading)
    }

    @Test
    fun firstLaunchServesSampleProfilesAndTheWholeLibrary() =
        runBlocking<Unit> {
            val graph = store().apply { start() }.ready()

            assertThat(graph.profiles.profiles().map { it.id }).containsExactly("sam", "jack").inOrder()
            assertThat(graph.profiles.profile("jack")).isEqualTo(DEFAULT_PROFILES[1])
            assertThat(graph.profileVideos.videoIds("sam")).isEqualTo(library.videos().map { it.id })
        }

    @Test
    fun openFailureReportsFailed() =
        runBlocking<Unit> {
            val store = store(open = { error("no disk") })
            store.start()

            val settled = eventually { store.state.value.takeIf { it != StoreState.Loading } }

            assertThat(settled).isEqualTo(StoreState.Failed)
        }

    @Test
    fun progressForVideosNotInLibraryIsDropped() =
        runBlocking<Unit> {
            val database = dugoutDatabase(context, name = null, seed)
            database.dao().saveProgress(WatchProgressEntity("sam", "gone", 100f, 100f, 100f, 1L, watched = true))
            database.dao().saveProgress(WatchProgressEntity("sam", "TjH37PpeX7k", 50f, 50f, 193f, 1L, watched = false))

            val graph = store(open = { database }).apply { start() }.ready()

            assertThat(
                graph.history
                    .progress("sam")
                    .value.keys,
            ).containsExactly("TjH37PpeX7k")
        }

    @Test
    fun retryAfterAFailureLoadsAgain() =
        runBlocking<Unit> {
            var attempts = 0
            val store =
                store(open = {
                    attempts++
                    if (attempts == 1) error("storage busy")
                    dugoutDatabase(context, name = null, seed)
                })
            store.start()
            eventually { store.state.value.takeIf { it == StoreState.Failed } }

            store.retryIfFailed()

            assertThat(store.ready().profiles.profiles()).hasSize(2)
        }

    @Test
    fun retryDoesNothingOnceLoaded() =
        runBlocking<Unit> {
            var attempts = 0
            val store =
                store(open = {
                    attempts++
                    dugoutDatabase(context, name = null, seed)
                })
            store.start()
            val graph = store.ready()

            store.retryIfFailed()

            assertThat(store.ready()).isSameInstanceAs(graph)
            assertThat(attempts).isEqualTo(1)
        }
}
