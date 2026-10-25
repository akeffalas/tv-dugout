package tv.dugout.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface DugoutDao {
    @Query("SELECT * FROM profile ORDER BY position")
    suspend fun profiles(): List<ProfileEntity>

    @Query("SELECT * FROM profile_video ORDER BY profile_id, position")
    suspend fun profileVideos(): List<ProfileVideoEntity>

    @Query("SELECT * FROM watch_progress")
    suspend fun progress(): List<WatchProgressEntity>

    @Upsert
    suspend fun saveProgress(row: WatchProgressEntity)

    @Insert
    suspend fun insertProfiles(rows: List<ProfileEntity>)

    @Insert
    suspend fun insertProfileVideos(rows: List<ProfileVideoEntity>)

    /** Saves a new child with their video list, all or nothing. */
    @Transaction
    suspend fun addProfile(
        profile: ProfileEntity,
        videos: List<ProfileVideoEntity>,
    ) {
        insertProfiles(listOf(profile))
        insertProfileVideos(videos)
    }

    @Query("DELETE FROM profile WHERE id = :id")
    suspend fun deleteProfile(id: String)
}
