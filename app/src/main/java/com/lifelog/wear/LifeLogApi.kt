package com.lifelog.wear

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Response

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
    suspend fun sendTranscript(@Body request: TranscriptRequest): Response<TranscriptResponse>
}
