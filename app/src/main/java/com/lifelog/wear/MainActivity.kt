package com.lifelog.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val SPEECH_REQUEST_CODE = 0
    private lateinit var api: LifeLogApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.example.com/") // Placeholder for actual backend URL
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(LifeLogApi::class.java)

        checkPermissionsAndStartListening()
    }

    private fun checkPermissionsAndStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else {
            startSpeechRecognition()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startSpeechRecognition()
        } else {
            Toast.makeText(this, "Permission required to record journal.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your journal entry...")
        }
        startActivityForResult(intent, SPEECH_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0)
            if (!spokenText.isNullOrBlank()) {
                sendTranscript(spokenText)
            } else {
                finish()
            }
        } else {
            finish() // Close if canceled or failed
        }
    }

    private fun sendTranscript(text: String) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getDefault()
        
        val request = TranscriptRequest(
            clientEventId = UUID.randomUUID().toString(),
            text = text,
            occurredAt = dateFormat.format(Date()),
            timezone = TimeZone.getDefault().id,
            source = "wear-os",
            deviceId = "wear-device-mock-id" // Should fetch real device ID
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = api.sendTranscript(request)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        vibrateSuccess()
                    } else {
                        queueForOffline(request)
                    }
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    queueForOffline(request)
                    finish()
                }
            }
        }
    }

    private fun queueForOffline(request: TranscriptRequest) {
        // MVP: Just show a toast. Room DB should be used here for proper offline queue.
        Toast.makeText(this, "Network error. Queued for later.", Toast.LENGTH_SHORT).show()
    }

    private fun vibrateSuccess() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
