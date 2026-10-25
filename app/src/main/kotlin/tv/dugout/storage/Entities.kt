package tv.dugout.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** A saved child. [position] is their place on the picker. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "avatar_color") val avatarColor: Long,
    val position: Int,
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
