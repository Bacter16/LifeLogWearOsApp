package com.lifelog.wear

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class TranscriptRequest(
    val clientEventId: String,
    val text: String,
    val occurredAt: String,
    val timezone: String,
    val source: String,
    val deviceId: String
)

data class TranscriptResponse(
    val status: String,
    val transcriptId: String?
)

interface LifeLogApi {
    @POST("api/transcripts")
    suspend fun sendTranscript(
        @Header("X-Api-Key") apiKey: String,
        @Body request: TranscriptRequest
    ): Response<TranscriptResponse>
}
