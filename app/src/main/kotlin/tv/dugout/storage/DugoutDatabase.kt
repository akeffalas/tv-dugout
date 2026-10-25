package tv.dugout.storage

import androidx.room.Database
import androidx.room.RoomDatabase

const val DATABASE_NAME = "dugout.db"

/** Everything the app saves on the TV (ADR-0007). */
@Database(
    entities = [ProfileEntity::class, ProfileVideoEntity::class, WatchProgressEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DugoutDatabase : RoomDatabase() {
    abstract fun dao(): DugoutDao
}
