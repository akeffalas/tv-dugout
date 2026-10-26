package tv.dugout.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A saved child. [position] is their place on the picker; [ageRange] and [level] hold enum names;
 * [offeredLibraryVersion] is the newest library version a parent has been shown for them.
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "avatar_color") val avatarColor: Long,
    val position: Int,
    @ColumnInfo(name = "age_range", defaultValue = "'NINE_TO_TEN'") val ageRange: String = "NINE_TO_TEN",
    @ColumnInfo(defaultValue = "'KID_PITCH'") val level: String = "KID_PITCH",
    @ColumnInfo(name = "offered_library_version", defaultValue = "1") val offeredLibraryVersion: Int = 1,
)

/** One video on a child's list. [videoId] refers to the bundled library. */
@Entity(
    tableName = "profile_video",
    primaryKeys = ["profile_id", "video_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProfileVideoEntity(
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "video_id") val videoId: String,
    val position: Int,
)

/** How far one child has gotten in one video. */
@Entity(
    tableName = "watch_progress",
    primaryKeys = ["profile_id", "video_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WatchProgressEntity(
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "furthest_seconds") val furthestSeconds: Float,
    @ColumnInfo(name = "last_seconds") val lastSeconds: Float,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Float,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long,
    val watched: Boolean,
)

/** One child's best round of What's the Play?, in runs out of five. */
@Entity(
    tableName = "game_best",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GameBestEntity(
    @PrimaryKey @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "best_runs") val bestRuns: Int,
)

/** A play one child has gotten right. [playId] refers to the bundled plays. */
@Entity(
    tableName = "mastered_play",
    primaryKeys = ["profile_id", "play_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MasteredPlayEntity(
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "play_id") val playId: String,
)

/**
 * The parent PIN, kept as one row when a PIN is set. The hashes are never the PIN or code
 * themselves (see `PinHasher`); [lockWaitMs] is the wait in force after too many wrong tries,
 * ending at [lockedUntil] on the monotonic clock of boot number [lockBoot].
 */
@Entity(tableName = "parent_lock")
data class ParentLockEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "pin_hash") val pinHash: ByteArray,
    @ColumnInfo(name = "pin_salt") val pinSalt: ByteArray,
    @ColumnInfo(name = "recovery_hash") val recoveryHash: ByteArray,
    @ColumnInfo(name = "recovery_salt") val recoverySalt: ByteArray,
    @ColumnInfo(name = "failed_attempts") val failedAttempts: Int,
    @ColumnInfo(name = "lock_wait_ms") val lockWaitMs: Long,
    @ColumnInfo(name = "locked_until") val lockedUntil: Long = 0,
    @ColumnInfo(name = "lock_boot") val lockBoot: Int = 0,
) {
    override fun equals(other: Any?): Boolean =
        other is ParentLockEntity &&
            id == other.id &&
            pinHash.contentEquals(other.pinHash) &&
            pinSalt.contentEquals(other.pinSalt) &&
            recoveryHash.contentEquals(other.recoveryHash) &&
            recoverySalt.contentEquals(other.recoverySalt) &&
            failedAttempts == other.failedAttempts &&
            lockWaitMs == other.lockWaitMs &&
            lockedUntil == other.lockedUntil &&
            lockBoot == other.lockBoot

    override fun hashCode(): Int =
        listOf(
            id,
            pinHash.contentHashCode(),
            pinSalt.contentHashCode(),
            recoveryHash.contentHashCode(),
            recoverySalt.contentHashCode(),
            failedAttempts,
            lockWaitMs,
            lockedUntil,
            lockBoot,
        ).hashCode()
}
