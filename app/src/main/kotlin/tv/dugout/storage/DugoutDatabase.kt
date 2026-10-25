package tv.dugout.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

const val DATABASE_NAME = "dugout.db"

/** Everything the app saves on the TV (ADR-0007). */
@Database(
    entities = [ProfileEntity::class, ProfileVideoEntity::class, WatchProgressEntity::class, ParentLockEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class DugoutDatabase : RoomDatabase() {
    abstract fun dao(): DugoutDao
}

/** Opens the database file [name] (in memory when null), seeding it with [seed] if it is new. */
fun dugoutDatabase(
    context: Context,
    name: String?,
    seed: Seed = Seed.NONE,
): DugoutDatabase {
    val builder =
        if (name == null) {
            Room.inMemoryDatabaseBuilder(context, DugoutDatabase::class.java)
        } else {
            Room.databaseBuilder(context, DugoutDatabase::class.java, name)
        }
    return builder.addMigrations(MIGRATION_1_2, MIGRATION_2_3).addCallback(SeedCallback(seed)).build()
}
