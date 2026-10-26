package tv.dugout.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Update
    suspend fun updateProfile(row: ProfileEntity)

    @Query("DELETE FROM profile_video WHERE profile_id = :profileId")
    suspend fun deleteProfileVideos(profileId: String)

    /** Gives [profileId] the list [videos] in place of the one they had, all or nothing. */
    @Transaction
    suspend fun replaceProfileVideos(
        profileId: String,
        videos: List<ProfileVideoEntity>,
    ) {
        deleteProfileVideos(profileId)
        insertProfileVideos(videos)
    }

    @Query(
        "UPDATE profile SET position = (SELECT COUNT(*) FROM profile AS earlier WHERE earlier.position < profile.position)",
    )
    suspend fun renumberProfiles()

    /** Deletes a child with their list and progress (by cascade), closing the gap in picker positions. */
    @Transaction
    suspend fun removeProfile(id: String) {
        deleteProfile(id)
        renumberProfiles()
    }

    @Query("DELETE FROM watch_progress WHERE profile_id = :profileId AND video_id IN (:videoIds)")
    suspend fun deleteProgress(
        profileId: String,
        videoIds: List<String>,
    )

    @Query("SELECT * FROM parent_lock WHERE id = 1")
    suspend fun parentLock(): ParentLockEntity?

    @Upsert
    suspend fun saveParentLock(row: ParentLockEntity)

    @Query("SELECT * FROM game_best")
    suspend fun gameBests(): List<GameBestEntity>

    @Query("SELECT * FROM mastered_play")
    suspend fun masteredPlays(): List<MasteredPlayEntity>

    @Upsert
    suspend fun saveGameBest(row: GameBestEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun saveMasteredPlay(row: MasteredPlayEntity)
}
