package com.lifelog.wear

/**
 * Central configuration for the LifeLog backend connection. Values come from
 * Gradle properties or local.properties so deploy secrets are not hardcoded.
 */
object LifeLogConfig {
    val BASE_URL: String = BuildConfig.LIFELOG_BASE_URL
    val API_KEY: String = BuildConfig.LIFELOG_API_KEY
}
