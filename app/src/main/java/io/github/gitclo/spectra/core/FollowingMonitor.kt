package io.github.gitclo.spectra.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.github.gitclo.spectra.MainActivity
import io.github.gitclo.spectra.R
import io.github.gitclo.spectra.domain.tracking.AlertLevel
import io.github.gitclo.spectra.domain.tracking.FollowingAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs the following detector periodically while the app is alive and posts a notification
 * the first time a device reaches [AlertLevel.MEDIUM] or above. The latest results are exposed
 * as [alerts] for the Insights screen, which can also force a [refresh].
 */
class FollowingMonitor(private val context: Context, private val intel: IntelRepository) {

    private val _alerts = MutableStateFlow<List<FollowingAlert>>(emptyList())
    val alerts: StateFlow<List<FollowingAlert>> = _alerts.asStateFlow()

    private val _lastRun = MutableStateFlow(0L)
    val lastRun: StateFlow<Long> = _lastRun.asStateFlow()

    private val settings = AppGraph.get(context).settings
    private val runLock = Mutex()
    private var job: Job? = null
    /** profileId -> time we last notified, so one device doesn't buzz every cycle. */
    private val notified = HashMap<String, Long>()

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            delay(STARTUP_DELAY_MS)
            runCatching { intel.backfillSecurity() }
            runCatching { intel.pruneHistory(settings.historyRetentionDays) }
            while (isActive) {
                if (settings.followingAlerts) runCatching { refresh(notify = true) }
                // Transmitter position / coverage estimates for newly sighted APs and cells.
                runCatching { settings.estimatesCursor = intel.refreshEstimates(settings.estimatesCursor) }
                delay(INTERVAL_MS)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    suspend fun refresh(notify: Boolean = false): List<FollowingAlert> = runLock.withLock {
        val window = settings.followingWindowHours.coerceIn(1, 24 * 14) * 3_600_000L
        val result = intel.detectFollowing(window, bondedAddresses())
        _alerts.value = result
        _lastRun.value = System.currentTimeMillis()
        if (notify) notifyNew(result)
        result
    }

    @SuppressLint("MissingPermission")
    private fun bondedAddresses(): Set<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return emptySet()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return emptySet()
        return runCatching { adapter.bondedDevices.mapTo(HashSet()) { it.address.lowercase() } }.getOrDefault(emptySet())
    }

    private fun notifyNew(alerts: List<FollowingAlert>) {
        val now = System.currentTimeMillis()
        notified.entries.removeIf { now - it.value > RENOTIFY_MS }
        val fresh = alerts.filter { it.level != AlertLevel.LOW && it.profileId !in notified }
        if (fresh.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Following alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Warns when the same device has been near you at several different places."
                enableVibration(true)
            })
        }
        for (a in fresh) {
            notified[a.profileId] = now
            val tap = PendingIntent.getActivity(
                context, a.profileId.hashCode(),
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_DEVICE, a.profileId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_wardrive)
                .setContentTitle("Possible tracking: ${a.label}")
                .setContentText("Seen at ${a.places.size} places over %.1f km · ${a.level.label} risk".format(a.spanKm))
                .setStyle(Notification.BigTextStyle().bigText(a.reasons.joinToString("\n")))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            nm.notify(CHANNEL, a.profileId.hashCode(), n)
        }
    }

    private companion object {
        const val CHANNEL = "following_alerts"
        const val STARTUP_DELAY_MS = 20_000L
        const val INTERVAL_MS = 3 * 60_000L
        const val RENOTIFY_MS = 6 * 3_600_000L
    }
}
