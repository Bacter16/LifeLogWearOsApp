package com.lifelog.wear.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a pending transcript queued for upload.
 * Records stay in the database until successfully sent to the backend,
 * then are deleted by the SyncWorker.
 */
@Entity(tableName = "pending_transcripts")
data class TranscriptEntity(
    @PrimaryKey
    val clientEventId: String,
    val text: String,
    val occurredAt: String,
    val timezone: String,
    val source: String,
    val deviceId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)
