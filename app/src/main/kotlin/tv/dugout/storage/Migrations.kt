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
