package tv.dugout.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    /**
     * Builds a file at [version] from the exported schema, as that app left it. Room's own
     * MigrationTestHelper reads schemas from assets, which Robolectric unit tests do not merge.
     */
    private fun createAt(
        version: Int,
        fill: SQLiteDatabase.() -> Unit,
    ) {
        val schema =
            JSONObject(File("schemas/tv.dugout.storage.DugoutDatabase/$version.json").readText())
                .getJSONObject("database")
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).apply {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) execSQL(setup.getString(i))
            this.version = version
            fill()
            close()
        }
    }

    private fun SQLiteDatabase.add(
        table: String,
        values: ContentValues.() -> Unit,
    ) = insertOrThrow(table, null, ContentValues().apply(values))

    @Test
    fun upgradeKeepsChildrenListsAndProgress() =
        runBlocking<Unit> {
            createAt(1) {
                listOf("sam" to 0, "jack" to 1).forEach { (id, position) ->
                    add("profile") {
                        put("id", id)
                        put("name", id)
                        put("avatar_color", 0xFFE63946)
                        put("position", position)
                    }
                }
                add("profile_video") {
                    put("profile_id", "sam")
                    put("video_id", "v")
                    put("position", 0)
                }
                add("watch_progress") {
                    put("profile_id", "sam")
                    put("video_id", "v")
                    put("furthest_seconds", 20f)
                    put("last_seconds", 10f)
                    put("duration_seconds", 100f)
                    put("last_played_at", 5L)
                    put("watched", 1)
                }
            }

            // Opening runs the migration, and Room checks the result against the version 2 tables.
            val database = dugoutDatabase(context, name)
            val dao = database.dao()

            assertThat(dao.profiles().map { it.id }).containsExactly("sam", "jack").inOrder()
            assertThat(dao.profiles().map { it.ageRange }.toSet()).containsExactly("NINE_TO_TEN")
            assertThat(dao.profiles().map { it.level }.toSet()).containsExactly("KID_PITCH")
            assertThat(dao.profiles().map { it.offeredLibraryVersion }.toSet()).containsExactly(1)
            assertThat(dao.profileVideos()).containsExactly(ProfileVideoEntity("sam", "v", 0))
            assertThat(dao.progress())
                .containsExactly(WatchProgressEntity("sam", "v", 20f, 10f, 100f, 5L, watched = true))
            database.close()
        }

    @Test
    fun upgradeFromTwoKeepsEverythingAndOffersVersionOne() =
        runBlocking<Unit> {
            createAt(2) {
                listOf("a" to 0, "b" to 1).forEach { (id, position) ->
                    add("profile") {
                        put("id", id)
                        put("name", id)
                        put("avatar_color", 0xFFE63946)
                        put("position", position)
                        put("age_range", "ELEVEN_TO_TWELVE")
                        put("level", "COACH_PITCH")
                    }
                }
                add("profile_video") {
                    put("profile_id", "b")
                    put("video_id", "v")
                    put("position", 0)
                }
                add("watch_progress") {
                    put("profile_id", "b")
                    put("video_id", "v")
                    put("furthest_seconds", 20f)
                    put("last_seconds", 10f)
                    put("duration_seconds", 100f)
                    put("last_played_at", 5L)
                    put("watched", 0)
                }
            }

            val database = dugoutDatabase(context, name)
            val dao = database.dao()

            assertThat(dao.profiles().map { it.id }).containsExactly("a", "b").inOrder()
            assertThat(dao.profiles().map { it.ageRange }.toSet()).containsExactly("ELEVEN_TO_TWELVE")
            assertThat(dao.profiles().map { it.offeredLibraryVersion }.toSet()).containsExactly(1)
            assertThat(dao.profileVideos()).containsExactly(ProfileVideoEntity("b", "v", 0))
            assertThat(dao.progress())
                .containsExactly(WatchProgressEntity("b", "v", 20f, 10f, 100f, 5L, watched = false))
            assertThat(dao.parentLock()).isNull()
            database.close()
        }
}
