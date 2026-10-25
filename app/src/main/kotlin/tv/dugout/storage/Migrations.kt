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
                    "`failed_attempts` INTEGER NOT NULL, `lock_wait_ms` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
        }
    }
