package com.rocketgod.warble.usb

import android.content.Context
import android.content.IntentFilter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rocketgod.warble.scan.RawObservation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the lifecycle of an external USB radio ("beast" adapter): permission, firmware upload,
 * chipset-specific monitor-mode drivers, auto-reset on early session failure, a no-RX
 * watchdog, and hot-plug / detach handling. Captured frames are delivered through [feed].
 *
 * Extracted verbatim from MainActivity, where this logic used to live inside the root
 * composable; behaviour is unchanged. [beast], [fwStatus] and [koNotice] are Compose state so
 * the UI recomposes when they change.
 */
class AdapterSessionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val feed: (List<RawObservation>) -> Unit,
) {
    var beast by mutableStateOf<BeastState>(BeastState.Internal)
    /** Human-readable driver / firmware status line for the dashboard. */
    var fwStatus by mutableStateOf<String?>(null)
    /** One-off notice from the Marauder firmware (title to body), shown as a dialog. */
    var koNotice by mutableStateOf<Pair<String, String>?>(null)

    @Volatile private var lastUpload = 0L
    @Volatile private var lastEngageId = -1
    @Volatile var lastEngageAt = 0L
    @Volatile var lastEngagedDevice: android.hardware.usb.UsbDevice? = null
        private set

    @Volatile private var autoResetCount = 0
    /** Set when a session ended abnormally and should be reopened; consumed by [consumeReengage]. */
    var pendingReengage by mutableStateOf<android.hardware.usb.UsbDevice?>(null)
        private set
    /** True once the user (or app lifecycle) stopped the adapter; suppresses auto-recovery. */
    val userStopped = java.util.concurrent.atomic.AtomicBoolean(false)
    private val feeding = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun startMonitorFeed() {
        if (!feeding.compareAndSet(false, true)) return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var idle = 0
                while (idle < 15 && !com.rocketgod.warble.usb.MonitorCapture.isRunning()) {
                    kotlinx.coroutines.delay(200); idle++
                }
                var lastLogged = -1
                while (com.rocketgod.warble.usb.MonitorCapture.isRunning()) {
                    val snap = com.rocketgod.warble.usb.MonitorCapture.snapshotSightings()
                    if (snap.isNotEmpty()) {
                        if (snap.size != lastLogged) {
                            lastLogged = snap.size
                            com.rocketgod.warble.usb.BeastDiag.log(
                                "monitor feed -> app: ${snap.size} device(s) pushed to live list (${snap.count { it.isAp }} AP, ${snap.count { !it.isAp }} client)")
                        }
                        feed(snap.map { s ->
                            com.rocketgod.warble.scan.RawObservation(
                                key = s.mac,
                                type = com.rocketgod.warble.model.SignalType.WIFI,

                                name = s.ssid ?: s.probedSsids.firstOrNull(),
                                rssi = (s.rssi - 95).coerceIn(-120, -20),
                                channel = s.channel, frequency = 2407 + s.channel * 5,
                                viaMonitor = true,
                                wifiClient = !s.isAp,
                                flockProbe = s.flock
                            )
                        })
                    }
                    kotlinx.coroutines.delay(2000)
                }
            } finally { feeding.set(false) }
        }
    }

    private fun launchAdapterSession(d: android.hardware.usb.UsbDevice, block: suspend () -> Unit) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val start = android.os.SystemClock.elapsedRealtime()
            try { block() } finally {
                val ranMs = android.os.SystemClock.elapsedRealtime() - start
                val dev = com.rocketgod.warble.usb.UsbBeast.findAdapter(context)
                val recoverable = dev != null && com.rocketgod.warble.usb.UsbBeast.hasPermission(context, dev)
                when {
                    ranMs >= 15000L -> autoResetCount = 0
                    userStopped.get() || !recoverable -> { }
                    autoResetCount < 2 -> {
                        autoResetCount++
                        com.rocketgod.warble.usb.BeastDiag.log("adapter auto-reset $autoResetCount/2 — session ended in ${ranMs}ms; reopening")
                        fwStatus = "Adapter hiccup — resetting ($autoResetCount/2)…"
                        kotlinx.coroutines.delay(700L * autoResetCount)
                        pendingReengage = dev
                    }
                    else -> fwStatus = "Adapter isn't recovering — unplug it and plug it back in"
                }
            }
        }
    }

    private fun armRxWatchdog() {
        val myEngageAt = lastEngageAt
        val startedAt = android.os.SystemClock.elapsedRealtime()
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(15000)
            if (lastEngageAt != myEngageAt || userStopped.get() || beast !is BeastState.Engaged) return@launch
            if (com.rocketgod.warble.usb.UsbBeast.lastFrameMs >= startedAt) return@launch
            val d2 = com.rocketgod.warble.usb.UsbBeast.findAdapter(context) ?: return@launch
            if (!com.rocketgod.warble.usb.UsbBeast.hasPermission(context, d2)) return@launch
            if (autoResetCount < 2) {
                autoResetCount++
                com.rocketgod.warble.usb.BeastDiag.log("no-RX watchdog: engaged but 0 frames after 15s — auto-reset $autoResetCount/2 (the old 'unplug/replug to fix' case)")
                fwStatus = "Adapter came up but isn't receiving — resetting ($autoResetCount/2)…"
                com.rocketgod.warble.usb.MonitorCapture.abort()
                com.rocketgod.warble.usb.Mt7612uMonitor.abort()
                com.rocketgod.warble.usb.Rt3070Monitor.abort()
                com.rocketgod.warble.usb.Rtl8821auMonitor.abort(); com.rocketgod.warble.usb.Rtl8814auMonitor.abort()
                kotlinx.coroutines.delay(1400)
                pendingReengage = d2
            } else {
                fwStatus = "Adapter isn't receiving — unplug it and plug it back in"
            }
        }
    }
    /** Bring up the driver for [d] according to its chipset. Debounced per device. */
    fun engage(d: android.hardware.usb.UsbDevice) {

        val nowE = System.currentTimeMillis()
        if (d.deviceId == lastEngageId && nowE - lastEngageAt < 4000) return
        lastEngageId = d.deviceId; lastEngageAt = nowE; lastEngagedDevice = d
        userStopped.set(false)
        beast = BeastState.Engaged(UsbBeast.describe(d))
        val cs0 = com.rocketgod.warble.usb.UsbBeast.chipset(d)

        if (cs0 == com.rocketgod.warble.usb.UsbBeast.Chipset.RTL8821AU ||
            cs0 == com.rocketgod.warble.usb.UsbBeast.Chipset.RTL8814AU ||
            cs0 == com.rocketgod.warble.usb.UsbBeast.Chipset.MT7612U ||
            cs0 == com.rocketgod.warble.usb.UsbBeast.Chipset.RT3070) armRxWatchdog()
        when (cs0) {
            com.rocketgod.warble.usb.UsbBeast.Chipset.RTL8187 -> {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    com.rocketgod.warble.usb.Rtl8187.probe(context, d) { st -> fwStatus = st }
                }
                return
            }
            com.rocketgod.warble.usb.UsbBeast.Chipset.RTL8821AU -> {

                launchAdapterSession(d) {
                    com.rocketgod.warble.usb.Rtl8821auMonitor.run(context, d, { st -> fwStatus = st }) { batch ->
                        feed(batch)
                    }
                }
                return
            }
            com.rocketgod.warble.usb.UsbBeast.Chipset.RTL8814AU -> {

                launchAdapterSession(d) {
                    com.rocketgod.warble.usb.Rtl8814auMonitor.run(context, d, { st -> fwStatus = st }) { batch ->
                        feed(batch)
                    }
                }
                return
            }
            com.rocketgod.warble.usb.UsbBeast.Chipset.MT7612U -> {

                launchAdapterSession(d) {
                    if (com.rocketgod.warble.usb.Mt7612uLoader.upload(context, d) { st -> fwStatus = st }) {
                        kotlinx.coroutines.delay(300)
                        com.rocketgod.warble.usb.Mt7612uMonitor.run(context, d, { st -> fwStatus = st }) { batch ->
                            feed(batch)
                        }
                    }
                }
                return
            }
            com.rocketgod.warble.usb.UsbBeast.Chipset.RT3070 -> {

                launchAdapterSession(d) {
                    if (com.rocketgod.warble.usb.Rt3070Loader.upload(context, d) { st -> fwStatus = st }) {
                        kotlinx.coroutines.delay(300)
                        com.rocketgod.warble.usb.Rt3070Monitor.run(context, d, { st -> fwStatus = st }) { batch ->
                            feed(batch)
                        }
                    }
                }
                return
            }
            com.rocketgod.warble.usb.UsbBeast.Chipset.KOKO -> {

                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    com.rocketgod.warble.usb.KokoMarauder.run(
                        context, d, { st -> fwStatus = st }, { t, b -> koNotice = t to b }
                    ) { batch -> feed(batch) }
                }
                return
            }
            com.rocketgod.warble.usb.UsbBeast.Chipset.GHOST -> {

                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    com.rocketgod.warble.usb.GhostEsp.run(context, d, { st -> fwStatus = st }) { batch ->
                        feed(batch)
                    }
                }
                return
            }
            com.rocketgod.warble.usb.UsbBeast.Chipset.FREEWILI -> {

                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    com.rocketgod.warble.usb.FreeWili.run(context, d, { st -> fwStatus = st }) { batch ->
                        feed(batch)
                    }
                }
                return
            }
            com.rocketgod.warble.usb.UsbBeast.Chipset.OTHER -> {
                fwStatus = "Adapter ${String.format("%04x:%04x", d.vendorId, d.productId)} · detected, no driver yet"
                return
            }
            else -> {}
        }
        val now = System.currentTimeMillis()
        val needFw = !com.rocketgod.warble.usb.MonitorCapture.lastHandshakeOk
        com.rocketgod.warble.usb.BeastDiag.log(
            "engage: dev=%04x:%04x sinceUpload=%dms needFw=%b".format(d.vendorId, d.productId, now - lastUpload, needFw))
        if (needFw || now - lastUpload > 8000L) {
            lastUpload = now
            launchAdapterSession(d) {
                com.rocketgod.warble.usb.Ar9271Loader.upload(context, d) { st -> fwStatus = st }
                kotlinx.coroutines.delay(3500)
                val booted = com.rocketgod.warble.usb.UsbBeast.findAdapter(context) ?: d
                com.rocketgod.warble.usb.BeastDiag.log(
                    "post-boot handle: %04x:%04x (was %04x:%04x)".format(booted.vendorId, booted.productId, d.vendorId, d.productId))
                beast = BeastState.Engaged(UsbBeast.describe(booted))
                if (com.rocketgod.warble.usb.UsbBeast.hasPermission(context, booted)) {
                    startMonitorFeed()
                    com.rocketgod.warble.usb.MonitorCapture.probe(context, booted) { st -> fwStatus = st }
                } else {
                    fwStatus = "Adapter re-enumerated · re-authorize to probe"
                    com.rocketgod.warble.usb.UsbBeast.requestPermission(context, booted)
                }
            }
        } else {
            startMonitorFeed()
            launchAdapterSession(d) {
                com.rocketgod.warble.usb.BeastDiag.log("skipping fw upload (recent + last handshake OK)")
                com.rocketgod.warble.usb.MonitorCapture.probe(context, d) { st -> fwStatus = st }
            }
        }
    }

    /** A supported adapter was just plugged in (USB_DEVICE_ATTACHED intent). */
    suspend fun onHotAttach(d0: android.hardware.usb.UsbDevice) {
        if (!UsbBeast.isSupported(d0)) return

        val d = UsbBeast.findAdapter(context) ?: d0
        autoResetCount = 0

        com.rocketgod.warble.usb.MonitorCapture.lastHandshakeOk = false

        if (com.rocketgod.warble.usb.MonitorCapture.isRunning()) {
            com.rocketgod.warble.usb.MonitorCapture.abort()
            var w = 0
            while (com.rocketgod.warble.usb.MonitorCapture.isRunning() && w < 3000) {
                kotlinx.coroutines.delay(100); w += 100
            }
        }
        engage(d)
    }

    /** Re-open a session scheduled by the auto-reset / watchdog paths. */
    fun consumeReengage() {
        val d = pendingReengage ?: return
        pendingReengage = null
        if (!UsbBeast.hasPermission(context, d)) return
        com.rocketgod.warble.usb.MonitorCapture.lastHandshakeOk = false
        lastEngageAt = 0L
        engage(d)
    }

    /**
     * Listen for detach and permission broadcasts and engage an adapter that is already
     * attached. Returns a function that unregisters the receiver.
     */
    fun register(): () -> Unit {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                when (i.action) {
                    android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val d = i.getParcelableExtra<android.hardware.usb.UsbDevice>(android.hardware.usb.UsbManager.EXTRA_DEVICE)
                        if (d == null || UsbBeast.isSupported(d)) {

                            userStopped.set(true)
                            com.rocketgod.warble.usb.MonitorCapture.abort()
                            com.rocketgod.warble.usb.Mt7612uMonitor.abort()
                            com.rocketgod.warble.usb.Rt3070Monitor.abort(); com.rocketgod.warble.usb.Rtl8821auMonitor.abort(); com.rocketgod.warble.usb.Rtl8814auMonitor.abort()
                            com.rocketgod.warble.usb.KokoMarauder.abort()
                            com.rocketgod.warble.usb.GhostEsp.abort()
                            com.rocketgod.warble.usb.FreeWili.abort()

                            com.rocketgod.warble.usb.MonitorCapture.lastHandshakeOk = false
                            beast = BeastState.Internal
                            fwStatus = "Adapter unplugged · back to on-board scanning"
                        }
                    }
                    UsbBeast.ACTION_PERMISSION -> {
                        val d = i.getParcelableExtra<android.hardware.usb.UsbDevice>(android.hardware.usb.UsbManager.EXTRA_DEVICE)
                        val ok = i.getBooleanExtra(android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (d != null && ok) engage(d) else beast = BeastState.Internal
                    }
                }
            }
        }

        val f = IntentFilter().apply {
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbBeast.ACTION_PERMISSION)
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, f, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)

        com.rocketgod.warble.usb.BeastDiag.log(UsbBeast.scanLog(context))
        UsbBeast.findAdapter(context)?.let { d ->
            if (UsbBeast.hasPermission(context, d)) engage(d)
            else { beast = BeastState.Awaiting(UsbBeast.describe(d)); UsbBeast.requestPermission(context, d) }
        }
        return { runCatching { context.unregisterReceiver(receiver) } }
    }
}
