package com.lifelog.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lifelog.wear.data.local.AppDatabase
import com.lifelog.wear.data.local.TranscriptEntity
import com.lifelog.wear.workers.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Main (and only) Activity for the LifeLog Wear OS app.
 *
 * Flow:
 * 1. Launch → check RECORD_AUDIO permission → open speech recognizer.
 * 2. Speech result → save transcript to local Room database (always succeeds).
 * 3. Trigger SyncWorker via WorkManager to upload pending transcripts.
 * 4. Haptic feedback → close.
 *
 * This "offline-first" approach guarantees no transcript is ever lost,
 * regardless of network connectivity.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LifeLogMain"
        private const val SPEECH_REQUEST_CODE = 0
        private const val PERMISSION_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissionsAndStartListening()
    }

    // ─── Permissions ───────────────────────────────────────────

    private fun checkPermissionsAndStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        } else {
            startSpeechRecognition()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE
            && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startSpeechRecognition()
        } else {
            Toast.makeText(this, "Permission required to record journal.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // ─── Speech Recognition ────────────────────────────────────

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your journal entry...")
        }
        startActivityForResult(intent, SPEECH_REQUEST_CODE)
    }

    @Deprecated("Using onActivityResult for simplicity on Wear OS")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0)
            if (!spokenText.isNullOrBlank()) {
                enqueueTranscript(spokenText)
            } else {
                finish()
            }
        } else {
            finish()
        }
    }

    // ─── Offline-First Transcript Handling ──────────────────────

    /**
     * Saves the transcript to the local Room database, then triggers a
     * background sync worker. This ensures transcripts are never lost.
     */
    private fun enqueueTranscript(text: String) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getDefault()

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-wear-device"

        val entity = TranscriptEntity(
            clientEventId = UUID.randomUUID().toString(),
            text = text,
            occurredAt = dateFormat.format(Date()),
            timezone = TimeZone.getDefault().id,
            source = "wear-os",
            deviceId = deviceId
        )

        val db = AppDatabase.getInstance(applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            // 1. Persist locally (offline-safe)
            db.transcriptDao().insert(entity)
            Log.d(TAG, "Transcript saved locally: ${entity.clientEventId}")

            // 2. Trigger background sync
            scheduleSyncWorker()

            // 3. Haptic feedback on the main thread, then close
            CoroutineScope(Dispatchers.Main).launch {
                vibrateSuccess()
                Toast.makeText(applicationContext, "✓ Journal entry saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // ─── WorkManager Sync ──────────────────────────────────────

    /**
     * Enqueues a one-time SyncWorker that drains the pending transcript queue.
     * Uses KEEP policy so multiple rapid entries don't spawn redundant workers.
     * Requires network connectivity to run.
     */
    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                SyncWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                syncRequest
            )

        Log.d(TAG, "SyncWorker scheduled.")
    }

    // ─── Haptics ───────────────────────────────────────────────

    private fun vibrateSuccess() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
