package com.lifelog.wear.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lifelog.wear.LifeLogConfig
import com.lifelog.wear.LifeLogApi
import com.lifelog.wear.TranscriptRequest
import com.lifelog.wear.data.local.AppDatabase
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * WorkManager CoroutineWorker that drains the offline transcript queue.
 *
 * Behavior:
 * - Reads up to 20 pending transcripts from Room (oldest first).
 * - Sends each to the backend via Retrofit (with API key auth).
 * - On success: deletes from Room.
 * - On failure: increments retry count; items with retryCount >= 10 are purged.
 * - Returns Result.success() if all sent, Result.retry() if any remain.
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "lifelog_transcript_sync"
        const val MAX_RETRIES = 10
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.transcriptDao()

        // Clean up transcripts that have exceeded max retries
        dao.deleteExpired(MAX_RETRIES)

        val pending = dao.getPending()
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending transcripts to sync.")
            return Result.success()
        }

        Log.d(TAG, "Syncing ${pending.size} pending transcript(s)...")

        val api = Retrofit.Builder()
            .baseUrl(LifeLogConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LifeLogApi::class.java)

        var hadFailure = false

        for (entity in pending) {
            try {
                val request = TranscriptRequest(
                    clientEventId = entity.clientEventId,
                    text = entity.text,
                    occurredAt = entity.occurredAt,
                    timezone = entity.timezone,
                    source = entity.source,
                    deviceId = entity.deviceId
                )

                val response = api.sendTranscript(LifeLogConfig.API_KEY, request)

                if (response.isSuccessful) {
                    Log.d(TAG, "Synced transcript ${entity.clientEventId}")
                    dao.delete(entity)
                } else {
                    Log.w(TAG, "Server rejected transcript ${entity.clientEventId}: HTTP ${response.code()}")
                    dao.incrementRetryCount(entity.clientEventId)
                    hadFailure = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network error syncing ${entity.clientEventId}: ${e.message}")
                dao.incrementRetryCount(entity.clientEventId)
                hadFailure = true
            }
        }

        return if (hadFailure) {
            Log.d(TAG, "Some transcripts failed — will retry later.")
            Result.retry()
        } else {
            Log.d(TAG, "All pending transcripts synced successfully.")
            Result.success()
        }
    }
}
