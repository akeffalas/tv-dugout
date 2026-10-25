package tv.dugout.storage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import tv.dugout.profile.Profile

/** What a brand-new database starts with: these profiles, each given every one of [videoIds]. */
data class Seed(
    val profiles: List<Profile>,
    val videoIds: List<String>,
) {
    companion object {
        /** The app's own start: no children until a parent adds one. */
        val NONE = Seed(emptyList(), emptyList())
    }
}

/** Fills the database once, when its file is first created, so deleted profiles never come back. */
internal class SeedCallback(
    private val seed: Seed,
) : RoomDatabase.Callback() {
    // Room calls this inside the transaction that creates the tables.
    override fun onCreate(db: SupportSQLiteDatabase) {
        seed.profiles.forEachIndexed { index, profile ->
            db.insert(
                "profile",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("avatar_color", profile.avatarColor)
                    put("position", index)
                    put("age_range", profile.ageRange.name)
                    put("level", profile.level.name)
                },
            )
            seed.videoIds.forEachIndexed { position, videoId ->
                db.insert(
                    "profile_video",
                    SQLiteDatabase.CONFLICT_ABORT,
                    ContentValues().apply {
                        put("profile_id", profile.id)
                        put("video_id", videoId)
                        put("position", position)
                    },
                )
            }
        }
    }
}
