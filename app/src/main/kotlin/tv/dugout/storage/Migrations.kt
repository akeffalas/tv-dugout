package tv.dugout.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 2 adds each child's age range and level; children saved before then played kid pitch at 9 to 10. */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profile ADD COLUMN age_range TEXT NOT NULL DEFAULT 'NINE_TO_TEN'")
            db.execSQL("ALTER TABLE profile ADD COLUMN level TEXT NOT NULL DEFAULT 'KID_PITCH'")
        }
    }

/** Version 3 tracks the library version each child has been offered and adds the parent lock. */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profile ADD COLUMN offered_library_version INTEGER NOT NULL DEFAULT 1")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `parent_lock` (`id` INTEGER NOT NULL, `pin_hash` BLOB NOT NULL, " +
                    "`pin_salt` BLOB NOT NULL, `recovery_hash` BLOB NOT NULL, `recovery_salt` BLOB NOT NULL, " +
                    "`failed_attempts` INTEGER NOT NULL, `lock_wait_ms` INTEGER NOT NULL, " +
                    "`locked_until` INTEGER NOT NULL, `lock_boot` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
        }
    }

/** Version 4 adds each child's best round and mastered plays for What's the Play? (spec 0009). */
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `game_best` (`profile_id` TEXT NOT NULL, `best_runs` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`profile_id`), FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `mastered_play` (`profile_id` TEXT NOT NULL, `play_id` TEXT NOT NULL, " +
                    "PRIMARY KEY(`profile_id`, `play_id`), FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }
