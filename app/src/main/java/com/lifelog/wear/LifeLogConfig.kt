package com.lifelog.wear

/**
 * Central configuration for the LifeLog backend connection.
 *
 * BASE_URL: Update this to your machine's local IP when testing on a real device/emulator.
 *           Use 10.0.2.2 for Android emulator → host loopback.
 *           Use your LAN IP (e.g. 192.168.1.x) for a real Wear OS watch.
 *
 * API_KEY: Must match the key in LifeLogBackend/src/LifeLog.Api/appsettings.json → "ApiKeys"
 */
object LifeLogConfig {
    // Replace with your actual backend URL when deploying
    const val BASE_URL = "http://192.168.1.100:5000/"

    // API key — must match appsettings.json ApiKeys array
    const val API_KEY = "a719b76eadd9548474501cc3a7644434c2b567d17abb102c813d5ee541b5e358"
}
