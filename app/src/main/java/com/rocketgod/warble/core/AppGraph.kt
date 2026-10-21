package com.rocketgod.warble.core

import android.content.Context
import com.rocketgod.warble.data.WarbleDb

/**
 * Minimal manual dependency container for app-wide singletons.
 *
 * The app predates any DI framework and most legacy code reaches for `WarbleDb.get(ctx)`
 * directly. New code asks [AppGraph] instead, so dependencies are created in one place, are
 * easy to swap in tests, and ViewModels don't each build their own copies.
 */
class AppGraph private constructor(private val appContext: Context) {

    val db: WarbleDb by lazy { WarbleDb.get(appContext) }
    val intel: IntelRepository by lazy { IntelRepository(db) }
    val following: FollowingMonitor by lazy { FollowingMonitor(appContext, intel) }
    val settings: IntelSettings by lazy { IntelSettings(appContext) }

    companion object {
        @Volatile private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) { instance ?: AppGraph(context.applicationContext).also { instance = it } }
    }
}

/** Preferences for the device-intelligence features, backed by the shared "warble" prefs file. */
class IntelSettings(context: Context) {
    private val prefs = context.getSharedPreferences("warble", Context.MODE_PRIVATE)

    /** Run the following detector in the background and notify on new alerts. */
    var followingAlerts: Boolean
        get() = prefs.getBoolean(KEY_FOLLOWING, true)
        set(v) = prefs.edit().putBoolean(KEY_FOLLOWING, v).apply()

    /** Look-back window for following detection, in hours. */
    var followingWindowHours: Int
        get() = prefs.getInt(KEY_WINDOW, 24)
        set(v) = prefs.edit().putInt(KEY_WINDOW, v).apply()

    /** Location-history retention in days; 0 = keep forever. */
    var historyRetentionDays: Int
        get() = prefs.getInt(KEY_RETENTION, 60)
        set(v) = prefs.edit().putInt(KEY_RETENTION, v).apply()

    /** Theme: "system", "dark" or "light". */
    var themeMode: String
        get() = prefs.getString(KEY_THEME, "dark") ?: "dark"
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    /** Use Material You wallpaper colors on Android 12+. */
    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC, false)
        set(v) = prefs.edit().putBoolean(KEY_DYNAMIC, v).apply()

    /** Sightings newer than this still need transmitter estimates (see IntelRepository). */
    var estimatesCursor: Long
        get() = prefs.getLong(KEY_EST_CURSOR, 0L)
        set(v) = prefs.edit().putLong(KEY_EST_CURSOR, v).apply()

    companion object {
        const val KEY_EST_CURSOR = "estimates_cursor_ts"
        const val KEY_FOLLOWING = "following_alerts"
        const val KEY_WINDOW = "following_window_h"
        const val KEY_RETENTION = "history_retention_days"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
    }
}
