package com.lifelog.wear.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete

/**
 * Data Access Object for the offline transcript queue.
 * SyncWorker reads pending items, sends them, then deletes them on success.
 */
@Dao
interface TranscriptDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transcript: TranscriptEntity)

    @Query("SELECT * FROM pending_transcripts ORDER BY createdAt ASC LIMIT 20")
    suspend fun getPending(): List<TranscriptEntity>

    @Query("SELECT COUNT(*) FROM pending_transcripts")
    suspend fun getPendingCount(): Int

    @Delete
    suspend fun delete(transcript: TranscriptEntity)

    @Query("UPDATE pending_transcripts SET retryCount = retryCount + 1 WHERE clientEventId = :id")
    suspend fun incrementRetryCount(id: String)

    @Query("DELETE FROM pending_transcripts WHERE retryCount >= :maxRetries")
    suspend fun deleteExpired(maxRetries: Int = 10)
}
