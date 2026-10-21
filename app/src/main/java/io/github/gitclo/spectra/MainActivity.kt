package io.github.gitclo.spectra

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gitclo.spectra.core.Leaderboard
import io.github.gitclo.spectra.data.ObservationEntity
import io.github.gitclo.spectra.model.Contact
import io.github.gitclo.spectra.model.Skin
import io.github.gitclo.spectra.model.SortMode
import io.github.gitclo.spectra.model.TypeFilter
import io.github.gitclo.spectra.ui.Dashboard
import io.github.gitclo.spectra.ui.FieldReportScreen
import io.github.gitclo.spectra.ui.HelpScreen
import io.github.gitclo.spectra.ui.LeaderboardScreen
import io.github.gitclo.spectra.ui.MakerDetail
import io.github.gitclo.spectra.ui.MapPoint
import io.github.gitclo.spectra.ui.MapScreen
import io.github.gitclo.spectra.ui.AppFont
import io.github.gitclo.spectra.ui.inkOn
import io.github.gitclo.spectra.ui.ObservationDetail
import io.github.gitclo.spectra.ui.Onboarding
import io.github.gitclo.spectra.ui.PmkidScreen
import io.github.gitclo.spectra.ui.PmkidDetailScreen
import android.content.IntentFilter
import io.github.gitclo.spectra.ui.BeastOverlay
import io.github.gitclo.spectra.ui.Palette
import io.github.gitclo.spectra.usb.BeastState
import io.github.gitclo.spectra.usb.UsbBeast
import io.github.gitclo.spectra.ui.SignalDetail
import io.github.gitclo.spectra.ui.navigation.AppNavigator
import io.github.gitclo.spectra.ui.navigation.AppShell
import io.github.gitclo.spectra.ui.navigation.Screen
import io.github.gitclo.spectra.ui.theme.LocalIsDarkTheme
import io.github.gitclo.spectra.ui.theme.ThemeMode
import io.github.gitclo.spectra.ui.theme.SpectraTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.statusBarsPadding
import kotlinx.coroutines.launch


private val usbAttachFlow = kotlinx.coroutines.flow.MutableStateFlow<android.hardware.usb.UsbDevice?>(null)

/** Device profile to open, set when the user taps a following-alert notification. */
private val openDeviceFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

class MainActivity : ComponentActivity() {

    companion object {
        /** Intent extra: profile id of a device to open in the inspector. */
        const val EXTRA_OPEN_DEVICE = "open_device"
    }

    fun applyOrientation(allowUpsideDown: Boolean) {
        requestedOrientation = if (allowUpsideDown) ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                               else ActivityInfo.SCREEN_ORIENTATION_USER
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {

            val pi = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
            val vc = pi?.let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() } ?: -1L
            io.github.gitclo.spectra.usb.BeastDiag.begin(pi?.versionName, vc)
        }

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        applyOrientation(getSharedPreferences("spectra", Context.MODE_PRIVATE).getBoolean("allow_upside_down", false))

        getSharedPreferences("spectra", Context.MODE_PRIVATE).let {
            io.github.gitclo.spectra.ui.UiFlags.seed(it.getBoolean("dark_map", false), it.getBoolean("reduce_motion", false),
                it.getBoolean("offensive_banners", true), it.getBoolean("capture_banners", true), it.getBoolean("achievement_banners", true))
            io.github.gitclo.spectra.ui.MapLayerFilter.seed(this)
            io.github.gitclo.spectra.ui.MapLayerState.seed(this)

        }
        handleUsbAttach(intent)
        intent?.getStringExtra(EXTRA_OPEN_DEVICE)?.let { openDeviceFlow.value = it }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val crash = runCatching {
            openFileInput(SpectraApp.CRASH_FILE).bufferedReader().use { it.readText() }
        }.getOrNull()
        setContent {

            val ctx = LocalContext.current
            val fsPrefs = remember { ctx.getSharedPreferences("spectra", Context.MODE_PRIVATE) }
            var uiFontScale by remember { mutableStateOf(fsPrefs.getFloat("ui_font_scale", 1f)) }
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * uiFontScale)) {
                if (!crash.isNullOrBlank()) {
                    SpectraTheme(seed = Color(0xFF37ECCB)) { CrashScreen(crash) { deleteFile(SpectraApp.CRASH_FILE); recreate() } }
                } else {
                    App(
                        uiFontScale = uiFontScale,
                        onUiFontScale = { uiFontScale = it; fsPrefs.edit().putFloat("ui_font_scale", it).apply() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbAttach(intent)
        intent.getStringExtra(EXTRA_OPEN_DEVICE)?.let { openDeviceFlow.value = it }
    }

    private fun handleUsbAttach(intent: Intent?) {
        if (intent?.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            @Suppress("DEPRECATION")
            val d = intent.getParcelableExtra<android.hardware.usb.UsbDevice>(android.hardware.usb.UsbManager.EXTRA_DEVICE)
            if (d != null) usbAttachFlow.value = d
        }
    }
}

private fun scanPermissions(): Array<String> {
    val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        p.add(Manifest.permission.BLUETOOTH_SCAN)
        p.add(Manifest.permission.BLUETOOTH_CONNECT)
    }

    return p.toTypedArray()
}

const val DEV_EMAIL = "rocketgod666@gmail.com"

fun emailCrashToDev(ctx: android.content.Context, text: String) {
    val subject = "Wardrive Go crash report"
    val sendto = Intent(Intent.ACTION_SENDTO).apply {
        data = android.net.Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(DEV_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    if (runCatching { ctx.startActivity(sendto) }.isSuccess) return
    runCatching {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(DEV_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Send to dev"))
    }
}

@Composable
private fun CrashScreen(text: String, onDismiss: () -> Unit) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(Palette.paper).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("WarBLE hit an error", color = Palette.danger, fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text("Send it to the dev, or copy it, so it can be fixed.", color = Palette.muted, fontFamily = AppFont, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.border(1.dp, Palette.glow, RoundedCornerShape(10.dp))
                .clickable { emailCrashToDev(ctx, text) }.padding(horizontal = 16.dp, vertical = 10.dp)
        ) { Text("Send to dev", color = Palette.ink, fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        Spacer(Modifier.height(10.dp))
        Row {
            Box(
                Modifier.border(1.dp, Palette.glow, RoundedCornerShape(10.dp))
                    .clickable { clip.setText(AnnotatedString(text)) }.padding(horizontal = 16.dp, vertical = 10.dp)
            ) { Text("Copy", color = Palette.ink, fontFamily = AppFont, fontSize = 14.sp) }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.border(1.dp, Palette.line, RoundedCornerShape(10.dp))
                    .clickable { onDismiss() }.padding(horizontal = 16.dp, vertical = 10.dp)
            ) { Text("Dismiss & retry", color = Palette.ink, fontFamily = AppFont, fontSize = 14.sp) }
        }
        Spacer(Modifier.height(14.dp))
        Text(text, color = Palette.ink, fontFamily = AppFont, fontSize = 11.sp)
    }
}

@Composable
private fun App(uiFontScale: Float = 1f, onUiFontScale: (Float) -> Unit = {}) {
    val vm: SpectraViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("spectra", Context.MODE_PRIVATE) }

    val stats by vm.stats.collectAsState()
    val live by vm.live.collectAsState()
    val gnss by vm.gnss.collectAsState()
    val skin by vm.skin.collectAsState()
    val filter by vm.listingFilter.collectAsState()
    val sortMode by vm.sortMode.collectAsState()
    val typeFilter by vm.typeFilter.collectAsState()
    val feed by vm.feedEvent.collectAsState()
    val makers by vm.makerBreakdown.collectAsState()
    val typeCatCounts by vm.typeCatCounts.collectAsState()
    val monitorCats by vm.monitorCatCounts.collectAsState()
    val categoryTally by vm.categoryTally.collectAsState()
    val scanning by vm.scanningState.collectAsState()
    val exportSessions by vm.exportSessions.collectAsState()
    val newForWigle by vm.newForWigle.collectAsState()
    val newForWdgw by vm.newForWdgw.collectAsState()
    val newUnsentWifi by vm.newUnsentWifi.collectAsState()
    val newUnsentBle by vm.newUnsentBle.collectAsState()
    val newUnsentCell by vm.newUnsentCell.collectAsState()
    val wifiPriorityUi by vm.wifiPriority.collectAsState()
    val bgScanUi by vm.backgroundScan.collectAsState()
    val maxWifiUi by vm.maxWifi.collectAsState()
    val captureClientsUi by vm.captureClients.collectAsState()
    val gnssFullUi by vm.gnssFullTracking.collectAsState()
    val keepScreenOnUi by vm.keepScreenOn.collectAsState()
    val speedMps by vm.speedMps.collectAsState()
    val bearingDeg by vm.bearingDeg.collectAsState()
    val distanceM by vm.distanceM.collectAsState()
    val discoveryRate by vm.discoveryRate.collectAsState()
    val gnssSats by vm.gnssSats.collectAsState()
    val cellTowers by vm.cellTowers.collectAsState()
    val pmkidsAll by vm.pmkids.collectAsState()
    val wigleImportingUi by vm.wigleImporting.collectAsState()
    val wigleImportCount by vm.wigleImportCount.collectAsState()
    val wiglePhaseUi by vm.wiglePhase.collectAsState()
    val wdgwPhaseUi by vm.wdgwPhase.collectAsState()
    val uploadLineUi by vm.uploadLine.collectAsState()
    val hashesThisRunUi by vm.hashesThisRun.collectAsState()
    val graph = remember { io.github.gitclo.spectra.core.AppGraph.get(context) }
    var themeMode by remember { mutableStateOf(ThemeMode.of(graph.settings.themeMode)) }
    var dynamicColor by remember { mutableStateOf(graph.settings.dynamicColor) }
    var retentionDays by remember { mutableStateOf(graph.settings.historyRetentionDays) }
    val followAlerts by graph.following.alerts.collectAsState()
    val sightingCount by remember { graph.intel.sightingCount() }.collectAsState(initial = 0L)
    val runId by vm.repo.runIdFlow.collectAsState()
    val devicesVm: io.github.gitclo.spectra.ui.devices.DevicesViewModel = viewModel()
    val insightsVm: io.github.gitclo.spectra.ui.insights.InsightsViewModel = viewModel()
    val liveKeys = remember(live) { live.mapTo(HashSet(live.size)) { it.key } }

    var onboarded by remember { mutableStateOf(prefs.getBoolean("onboarded", false)) }

    var radarCollapsed by remember { mutableStateOf(prefs.getBoolean("radar_collapsed", false)) }

    var bannerMode by remember { mutableStateOf(prefs.getInt("banner_mode", 0)) }

    var toolsOnTop by remember { mutableStateOf(prefs.getBoolean("tools_on_top", false)) }
    var startOnBoot by remember { mutableStateOf(prefs.getBoolean("start_on_boot", false)) }

    var wifiGoal by remember { mutableStateOf(prefs.getInt("wifi_goal", 0)) }

    var campBusy by remember { mutableStateOf(prefs.getBoolean("camp_busy", true)) }

    var seenCollapsed by remember { mutableStateOf(prefs.getBoolean("seen_collapsed", false)) }

    var toolOrd by remember { mutableStateOf(prefs.getInt("tool_ord", 0)) }

    var buzzOnSpot by remember { mutableStateOf(prefs.getBoolean("buzz_on_spot", true)) }
    LaunchedEffect(buzzOnSpot) { vm.buzzOnSpot = buzzOnSpot }

    var notificationsOn by remember { mutableStateOf(prefs.getBoolean("notifications_enabled", true)) }
    LaunchedEffect(notificationsOn) { vm.notificationsEnabled = notificationsOn }

    var allowUpsideDown by remember { mutableStateOf(prefs.getBoolean("allow_upside_down", false)) }

    var useMetric by remember {
        mutableStateOf(prefs.getBoolean("use_metric", java.util.Locale.getDefault().country !in setOf("US", "GB", "LR", "MM")))
    }

    val blockedList by vm.blockedDevices.collectAsState()
    val blockedKeys = remember(blockedList) { blockedList.mapTo(HashSet()) { it.bssid.lowercase() } }
    val nav = remember { AppNavigator() }
    val screen = nav.current

    LaunchedEffect(screen) {
        vm.heavyDataActive.value = screen is Screen.Field || screen is Screen.ObsDetail ||
            screen is Screen.MakerDetail || screen is Screen.MapView
    }

    LaunchedEffect(keepScreenOnUi) {
        val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        (context as? android.app.Activity)?.window?.let { if (keepScreenOnUi) it.addFlags(flag) else it.clearFlags(flag) }
    }
    val stateHolder = rememberSaveableStateHolder()
    fun go(next: Screen) = nav.go(next)
    fun back() = nav.back()
    androidx.activity.compose.BackHandler(enabled = nav.canGoBack) { back() }

    val openDevice by openDeviceFlow.collectAsState()
    LaunchedEffect(openDevice) {
        val id = openDevice ?: return@LaunchedEffect
        openDeviceFlow.value = null
        go(Screen.DeviceDetail(id))
    }
    /** Open the new device inspector for a stored observation (cell towers keep their own screen). */
    fun openObservation(o: ObservationEntity) {
        val cid = if (o.type == "CELL") cellTowers.firstOrNull { "${it.mcc}_${it.mnc}_${it.cid}" == o.key }?.id else null
        if (cid != null) go(Screen.CellTowerDetail(cid)) else go(Screen.DeviceDetail(o.profileId ?: o.key))
    }
    val usb = remember { io.github.gitclo.spectra.usb.AdapterSessionController(context, scope) { vm.repo.feedMonitor(it) } }
    var showDiag by remember { mutableStateOf(false) }
    var showWigle by remember { mutableStateOf(false) }
    var locateRequest by remember { mutableStateOf<String?>(null) }
    /** Hand a MAC to the Scan screen's locator tool and switch to it. */
    fun locate(key: String) {
        locateRequest = key
        nav.selectTab(io.github.gitclo.spectra.ui.navigation.TopLevel.SCAN)
    }

    var wigleLifeWifi by remember { mutableStateOf(prefs.getLong("wigle_life_wifi", -1L)) }
    var wigleLifeBt by remember { mutableStateOf(prefs.getLong("wigle_life_bt", -1L)) }
    var wigleLifeCell by remember { mutableStateOf(prefs.getLong("wigle_life_cell", -1L)) }
    fun refreshWigleLife() {
        val n = (prefs.getString("wigle_name", "") ?: "").trim()
        val t = (prefs.getString("wigle_token", "") ?: "").trim()
        if (n.isBlank() || t.isBlank()) return
        scope.launch {
            val p = withContext(kotlinx.coroutines.Dispatchers.IO) { io.github.gitclo.spectra.net.WigleStats.user(n, t) } ?: return@launch
            wigleLifeWifi = p.wifiGps; wigleLifeBt = p.bt; wigleLifeCell = p.cell
            prefs.edit().putLong("wigle_life_wifi", p.wifiGps).putLong("wigle_life_bt", p.bt).putLong("wigle_life_cell", p.cell)
                .putLong("wigle_life_ts", System.currentTimeMillis()).apply()
        }
    }
    var wigleSendAfterSave by remember { mutableStateOf(false) }

    fun sendToWigleNow() {
        val n = (prefs.getString("wigle_name", "") ?: "").trim()
        val t = (prefs.getString("wigle_token", "") ?: "").trim()
        if (n.isBlank() || t.isBlank()) { wigleSendAfterSave = true; showWigle = true; return }
        vm.uploadTargets.value = "WiGLE"
        vm.sendToWigle(n, t) { ok, _ -> if (ok) refreshWigleLife() }
    }
    var wdgwSendAfterSave by remember { mutableStateOf(false) }

    fun sendToWdgwNow() {
        val k = (prefs.getString("wdgw_key", "") ?: "").trim()
        if (k.isBlank()) { wdgwSendAfterSave = true; showWigle = true; return }
        vm.uploadTargets.value = "WDGWars"
        vm.sendToWdgw(k) { _, _ -> }
    }

    fun quickUpload() {
        val n = (prefs.getString("wigle_name", "") ?: "").trim()
        val t = (prefs.getString("wigle_token", "") ?: "").trim()
        val k = (prefs.getString("wdgw_key", "") ?: "").trim()
        val hasWigle = n.isNotBlank() && t.isNotBlank()
        val hasWdgw = k.isNotBlank()
        if (!hasWigle && !hasWdgw) { wigleSendAfterSave = false; showWigle = true; return }
        vm.uploadTargets.value = when {
            hasWigle && hasWdgw -> "WiGLE + WDGWars"
            hasWigle -> "WiGLE"
            else -> "WDGWars"
        }

        fun toast(msg: String) = android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        if (hasWigle) vm.sendToWigle(n, t) { ok, msg -> if (ok) refreshWigleLife(); toast(msg) }
        if (hasWdgw) vm.sendToWdgw(k) { _, msg -> toast(msg) }
    }

    fun batteryExempt(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun promptBatteryExemption(force: Boolean = false) {
        if (!force && batteryExempt()) return
        val pkg = context.packageName
        val direct = android.content.Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            android.net.Uri.parse("package:$pkg")
        )
        val fallback = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { context.startActivity(direct) }
            .onFailure { runCatching { context.startActivity(fallback) } }
    }
    val wigleImporting = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    fun importFromWigleNow() {
        if (wigleImporting.get()) {
            vm.cancelWigleSync()
            android.widget.Toast.makeText(context, "Stopping WiGLE sync…", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val n = (prefs.getString("wigle_name", "") ?: "").trim()
        val t = (prefs.getString("wigle_token", "") ?: "").trim()
        if (n.isBlank() || t.isBlank()) {
            wigleSendAfterSave = false; showWigle = true
            android.widget.Toast.makeText(context, "Add your WiGLE API key first", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (!wigleImporting.compareAndSet(false, true)) return
        val wasScanning = vm.scanningState.value
        vm.stopScanning()
        android.widget.Toast.makeText(context, "Syncing WiGLE networks (radar paused)…", android.widget.Toast.LENGTH_SHORT).show()
        vm.wigleImportCount.value = 0
        vm.wigleImporting.value = true
        scope.launch {
            try {
                val indexed = vm.syncWigleMine(n, t) { c ->
                    vm.wigleImportCount.value = c
                    vm.banner.value = "WiGLE sync — ${Leaderboard.fmt(c.toLong())} networks…"
                }
                vm.banner.value = "WiGLE sync complete"
                refreshWigleLife()
                val capped = indexed >= 3_000_000
                val msg = if (capped)
                    "Indexed WiGLE's max of 3,000,000. Your account is larger, so the rest is deduped by WiGLE automatically when you upload — your score stays accurate."
                else
                    "Synced ${Leaderboard.fmt(indexed.toLong())} WiGLE networks — new counts updated"
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "WiGLE sync stopped: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                vm.wigleImporting.value = false
                wigleImporting.set(false)
                if (wasScanning) vm.startScanning(true)
            }
        }
    }
    val hotAttach by usbAttachFlow.collectAsState()
    LaunchedEffect(hotAttach) {
        val d0 = hotAttach ?: return@LaunchedEffect
        usbAttachFlow.value = null
        usb.onHotAttach(d0)
    }
    LaunchedEffect(usb.pendingReengage) { usb.consumeReengage() }
    DisposableEffect(Unit) {
        val unregister = usb.register()
        onDispose { unregister() }
    }
    fun granted(): Boolean = scanPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var hasPerms by remember { mutableStateOf(granted()) }

    var exportOnlyNew by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasPerms = granted() }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(onboarded, hasPerms) {
        if (onboarded && hasPerms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(onboarded, hasPerms) {
        if (onboarded && hasPerms && bgScanUi && !batteryExempt() && !prefs.getBoolean("batt_opt_asked", false)) {
            prefs.edit().putBoolean("batt_opt_asked", true).apply()
            promptBatteryExemption()
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var wasScanning = false
        var wasEngaged = false
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->

            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> vm.appInForeground = true
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> vm.appInForeground = false
                else -> {}
            }
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> if (!vm.backgroundScan.value) {
                    wasScanning = vm.scanningState.value
                    wasEngaged = usb.beast is BeastState.Engaged
                    usb.userStopped.set(true)
                    vm.stopScanning()
                    io.github.gitclo.spectra.usb.MonitorCapture.abort()
                    io.github.gitclo.spectra.usb.Mt7612uMonitor.abort()
                    io.github.gitclo.spectra.usb.Rt3070Monitor.abort(); io.github.gitclo.spectra.usb.Rtl8821auMonitor.abort(); io.github.gitclo.spectra.usb.Rtl8814auMonitor.abort()
                    io.github.gitclo.spectra.usb.BeastDiag.log("background: 'keep scanning' is off — stopped radios + location")
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    if (wasScanning && hasPerms && onboarded) vm.startScanning(true)
                    if (wasEngaged) usb.lastEngagedDevice?.let { d ->
                        if (UsbBeast.hasPermission(context, d)) { usb.lastEngageAt = 0L; usb.engage(d) }
                    } else if (usb.beast is BeastState.Internal) {

                        io.github.gitclo.spectra.usb.BeastDiag.log(UsbBeast.scanLog(context))
                        UsbBeast.findAdapter(context)?.let { d ->
                            if (UsbBeast.hasPermission(context, d)) usb.engage(d)
                            else { usb.beast = BeastState.Awaiting(UsbBeast.describe(d)); UsbBeast.requestPermission(context, d) }
                        }
                    }
                    wasScanning = false; wasEngaged = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val pcapLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) scope.launch {
            val fr = io.github.gitclo.spectra.usb.MonitorCapture.frames.toList()
            context.contentResolver.openOutputStream(uri)?.use { io.github.gitclo.spectra.usb.PcapWriter.write(it, fr) }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) scope.launch {
            val onlyNew = exportOnlyNew

            val counts = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { vm.exportToStream(it, onlyNew) }
            }.getOrNull()
            val msg = when {
                counts == null -> "Export failed — couldn't write the file"
                counts.total == 0 -> "Nothing ${if (onlyNew) "new " else ""}to export"
                else -> "Saved ${counts.total} networks (${if (onlyNew) "new" else "all"}) to the file you chose"
            }
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val updateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            context.contentResolver.openInputStream(uri)?.use { vm.importWigle(it) }
        }
    }

    LaunchedEffect(hasPerms, onboarded) {
        if (hasPerms && onboarded) vm.startScanning(true)
    }
    LaunchedEffect(usb.beast) {
        if (usb.beast !is BeastState.Engaged) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(3000)
            val hits = io.github.gitclo.spectra.usb.PmkidCapture.drainNew()
            for (h in hits) vm.savePmkid(h)
        }
    }
    LaunchedEffect(Unit) {
        runCatching { vm.skin.value = Skin.valueOf(prefs.getString("skin", "TEAL") ?: "TEAL") }
        runCatching { vm.sortMode.value = SortMode.valueOf(prefs.getString("sortMode", "SIGNAL") ?: "SIGNAL") }
        runCatching { vm.typeFilter.value = TypeFilter.valueOf(prefs.getString("typeFilter", "ALL") ?: "ALL") }
        vm.wifiPriority.value = prefs.getBoolean("wifi_priority", true)
        vm.repo.wifiPriority = vm.wifiPriority.value
        vm.backgroundScan.value = prefs.getBoolean("bg_scan", true)
        vm.setMaxWifi(prefs.getBoolean("max_wifi", false))
        vm.setCaptureClients(prefs.getBoolean("capture_clients", true))
        io.github.gitclo.spectra.usb.UsbBeast.campBusy = prefs.getBoolean("camp_busy", true)
        vm.setGnssFullTracking(prefs.getBoolean("gnss_full", false))
        vm.setKeepScreenOn(prefs.getBoolean("keep_screen_on", true))
        if (onboarded && granted()) hasPerms = true
        refreshWigleLife()
    }

    SpectraTheme(seed = Color(skin.accentHex), mode = themeMode, dynamicColor = dynamicColor) {
    val accent = MaterialTheme.colorScheme.primary
    val deep = MaterialTheme.colorScheme.primaryContainer
    val darkTheme = LocalIsDarkTheme.current
    val hostView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (context as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, hostView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Palette.paper)) {
    if (!onboarded || !hasPerms) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Onboarding(
            onRequestLocation = { permLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            onRequestBluetooth = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    permLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
                else hasPerms = granted()
            },
            onOpenDeveloperSettings = {
                try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                catch (_: Exception) { try { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {} }
            },
            onSetupExclusion = {

                hasPerms = granted()
                if (!hasPerms) permLauncher.launch(scanPermissions())
                prefs.edit().putBoolean("onboarded", true).apply()
                onboarded = true
                nav.reset(Screen.Privacy)
            },
            onFinish = { wardrive ->
                vm.setWardrive(wardrive)
                hasPerms = granted()
                if (!hasPerms) permLauncher.launch(scanPermissions())
                prefs.edit().putBoolean("onboarded", true).apply()
                onboarded = true
                nav.reset(Screen.Dash)
            }
        )
        }
    } else {
        AppShell(nav, insightsBadge = followAlerts.count { it.level != io.github.gitclo.spectra.domain.tracking.AlertLevel.LOW }) { pad ->
        // Screens built on Scaffold / full-bleed maps handle insets themselves.
        val selfInsets = screen is Screen.MapView || screen is Screen.DeviceMap || screen is Screen.DeviceDetail ||
            screen is Screen.DeviceHistory || screen is Screen.SecurityType || screen is Screen.NewFinds
        Box(when {
            selfInsets -> Modifier.fillMaxSize()
            nav.isTopLevel -> Modifier.fillMaxSize().padding(pad)
            else -> Modifier.fillMaxSize().systemBarsPadding()
        }) {
    when (val s = screen) {
        is Screen.Dash -> stateHolder.SaveableStateProvider("dash") {

            val engagedKey = (usb.beast as? BeastState.Engaged)?.adapter?.idHex
            remember(engagedKey) {
                if (engagedKey != null) {
                    io.github.gitclo.spectra.usb.UsbBeast.currentAdapterKey = engagedKey
                    io.github.gitclo.spectra.usb.UsbBeast.dwellMs = prefs.getInt("dwell_$engagedKey", 0)
                    val l = prefs.getInt("lock_$engagedKey", -1)
                    io.github.gitclo.spectra.usb.UsbBeast.lockChannel = if (l >= 0) l else null
                    io.github.gitclo.spectra.usb.UsbBeast.settingsPersister = { d, lk ->
                        prefs.edit().putInt("dwell_$engagedKey", d).putInt("lock_$engagedKey", lk).apply()
                    }
                } else {
                    io.github.gitclo.spectra.usb.UsbBeast.currentAdapterKey = null
                    io.github.gitclo.spectra.usb.UsbBeast.settingsPersister = null
                }
                engagedKey
            }

            val cfg = androidx.compose.ui.platform.LocalConfiguration.current
            if (maxOf(cfg.screenWidthDp, cfg.screenHeightDp) < 520)
                io.github.gitclo.spectra.ui.CoverGlance(live, scanning, gnss.usedInFix > 0, gnss.usedInFix, speedMps ?: 0f, useMetric, androidx.compose.ui.graphics.Color(skin.accentHex))
            else
            Dashboard(
                stats = stats, liveCount = live.size, contacts = live,
                categoryTally = categoryTally, feed = feed, skin = skin, scanning = scanning, beast = usb.beast, fwStatus = usb.fwStatus,
                wigleLifeWifi = wigleLifeWifi, wigleLifeBt = wigleLifeBt, wigleLifeCell = wigleLifeCell,
                gnss = gnss,
                newWifi = newUnsentWifi, newBle = newUnsentBle, newCell = newUnsentCell,
                onSatellites = { go(Screen.Satellites) },
                sortMode = sortMode,
                onSort = { vm.sortMode.value = it; prefs.edit().putString("sortMode", it.name).apply() },
                typeFilter = typeFilter,
                onFilter = { vm.typeFilter.value = it; prefs.edit().putString("typeFilter", it.name).apply() },
                maxWifi = maxWifiUi,
                useMetric = useMetric,
                blockedKeys = blockedKeys,
                onSelect = { c ->

                    val cid = if (c.type == io.github.gitclo.spectra.model.SignalType.CELL)
                        cellTowers.firstOrNull { "${it.mcc}_${it.mnc}_${it.cid}" == c.key }?.id else null
                    if (cid != null) go(Screen.CellTowerDetail(cid)) else go(Screen.Detail(c))
                },
                onLeaderboards = { go(Screen.Board) },
                onFieldReport = { go(Screen.Field) },
                onQuickUpload = { quickUpload() },
                wiglePhase = wiglePhaseUi,
                wdgwPhase = wdgwPhaseUi,
                uploadStatus = uploadLineUi,
                hashesThisRun = hashesThisRunUi,
                onSettings = { wigleSendAfterSave = false; showWigle = true },
                onHelp = { go(Screen.Help) },
                speedMps = speedMps,
                bearingDeg = bearingDeg,
                distanceM = distanceM,
                discoveryRate = discoveryRate,
                bannerMode = bannerMode,
                onCycleBannerMode = {
                    bannerMode = (bannerMode + 1) % 8
                    prefs.edit().putInt("banner_mode", bannerMode).apply()
                },
                radarCollapsed = radarCollapsed,
                onRadarCollapsed = { collapsed ->

                    radarCollapsed = collapsed
                    prefs.edit().putBoolean("radar_collapsed", collapsed).apply()
                },
                locateRequest = locateRequest,
                onLocateHandled = { locateRequest = null },
                toolsOnTop = toolsOnTop,
                seenCollapsed = seenCollapsed,
                onSeenCollapsed = { seenCollapsed = it; prefs.edit().putBoolean("seen_collapsed", it).apply() },
                toolOrd = toolOrd,
                onToolOrd = { toolOrd = it; prefs.edit().putInt("tool_ord", it).apply() },
                currentPose = { vm.lastPose() },
                loadMapPointsIn = { s, n, w, e -> vm.findMapPointsIn(s, n, w, e) },
                onOpenFullMap = { go(Screen.FullMap) },
                loadMapMarkersIn = { s, n, w, e -> vm.mapMarkersIn(s, n, w, e) },
                onOpenMapMarker = { m ->

                    scope.launch {
                        if (m.kind == io.github.gitclo.spectra.ui.MapMarkerKind.OFFENSIVE)
                            vm.obsByKey(m.key)?.let { go(Screen.ObsDetail(it)) }
                        else go(Screen.PmkidDetail(m.key))
                    }
                },
                onOpenMapPoint = { key -> go(Screen.DeviceDetail(key)) },
                mapSeedCenter = { vm.recentLocatedLatLng() },
                onCategory = { go(Screen.Devices(it)) },
                onNewFinds = { go(Screen.NewFinds(it)) }
            )
        }
        is Screen.Field -> stateHolder.SaveableStateProvider("field") {
            FieldReportScreen(
                stats = stats, typeCatCounts = typeCatCounts, monitorCats = monitorCats, makers = makers, accent = accent,
                loadCat = { t, c -> vm.obsByCat(t, c) },
                loadMonitorCat = { c -> vm.obsMonitorByCat(c) },
                loadNotable = { brands -> vm.obsByMakers(brands) },
                exportSessions = exportSessions,
                onSelectObs = { o -> openObservation(o) },
                onSelectMaker = { go(Screen.MakerDetail(it)) },
                onMasterMap = { m ->
                    scope.launch { go(Screen.MapView("${m.label} map", vm.mapPointsByType(m.name))) }
                },
                onCategoryMap = { title, brands ->
                    scope.launch { go(Screen.MapView("$title map", vm.mapPointsByMakers(brands))) }
                },
                onExportNew = { exportOnlyNew = true; exportLauncher.launch("WardriveGo-new-${System.currentTimeMillis()}.csv") },
                onExportAll = { exportOnlyNew = false; exportLauncher.launch("WardriveGo-all.csv") },
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onSendWigle = { sendToWigleNow() },
                onSendWdgw = { sendToWdgwNow() },
                onImportWigle = { importFromWigleNow() },
                onSettings = { wigleSendAfterSave = false; wdgwSendAfterSave = false; showWigle = true },
                newForWigle = newForWigle,
                newForWdgw = newForWdgw,
                wigleImporting = wigleImportingUi,
                wigleImportCount = wigleImportCount,
                wiglePhase = wiglePhaseUi,
                wdgwPhase = wdgwPhaseUi,
                satellites = gnssSats,
                onSelectSat = { go(Screen.SatelliteDetail(it)) },
                cellTowers = cellTowers,
                onSelectCell = { go(Screen.CellTowerDetail(it)) },
                pmkids = pmkidsAll,
                onPmkids = { go(Screen.Pmkids) },
                onSelectPmkid = { go(Screen.PmkidDetail(it)) },
                onPmkidMap = {
                    val pts = pmkidsAll.filter { it.lat != null && it.lng != null }
                        .map { MapPoint(it.lat!!, it.lng!!, it.ssid ?: it.bssid, it.pmkid) }
                    go(Screen.MapView("PMKID captures", pts))
                },
                onBack = { back() }
            )
        }
        is Screen.Board -> stateHolder.SaveableStateProvider("board") {
            LeaderboardScreen(
                stats = stats, accent = accent,
                wigleName = (prefs.getString("wigle_name", "") ?: "").trim(),
                wigleToken = (prefs.getString("wigle_token", "") ?: "").trim(),
                wdgwKey = (prefs.getString("wdgw_key", "") ?: "").trim(),
                onSettings = { wigleSendAfterSave = false; wdgwSendAfterSave = false; showWigle = true },
                onBack = { back() },
                onWdgwUsername = { vm.cacheWdgwUsername(it) }
            )
        }
        is Screen.Detail -> stateHolder.SaveableStateProvider("detail:${s.contact.key}") {
            val blockFlow = remember(s.contact.key) { vm.isBlockedFlow(s.contact.key) }
            val blocked by blockFlow.collectAsState()
            SignalDetail(c = s.contact, accent = accent,
                onLocate = { locateRequest = s.contact.key; back() },
                canBlock = s.contact.type != io.github.gitclo.spectra.model.SignalType.CELL,
                blocked = blocked,
                onBlockToggle = { if (blocked) vm.unblockDevice(s.contact.key) else vm.blockDevice(s.contact.key, s.contact.name) },
                onProfile = {
                    // Make sure the row exists before the inspector queries it.
                    scope.launch { vm.repo.flushPending(); go(Screen.DeviceDetail(s.contact.profileId)) }
                },
                onBack = { back() })
        }
        is Screen.ObsDetail -> stateHolder.SaveableStateProvider("obs:${s.obs.key}") {
            val blockFlow = remember(s.obs.key) { vm.isBlockedFlow(s.obs.key) }
            val blocked by blockFlow.collectAsState()
            ObservationDetail(
                o = s.obs, accent = accent,
                canBlock = s.obs.type != "CELL",
                blocked = blocked,
                onBlockToggle = { if (blocked) vm.unblockDevice(s.obs.key) else vm.blockDevice(s.obs.key, s.obs.name) },
                onMap = { o ->
                    val label = io.github.gitclo.spectra.classify.NotableDevices.displayName(o.name, o.key, o.companyId, o.category, o.maker) ?: o.category
                    go(Screen.MapView(label, listOf(MapPoint(o.lat!!, o.lng!!, label, o.key))))
                },
                onExportPcap = {
                    if (io.github.gitclo.spectra.usb.MonitorCapture.frames.isEmpty())
                        android.widget.Toast.makeText(context, "No monitor frames yet — radio capture is Phase 2", android.widget.Toast.LENGTH_SHORT).show()
                    else pcapLauncher.launch("wardrive_monitor.pcap")
                },
                onBack = { back() }
            )
        }
        is Screen.MakerDetail -> stateHolder.SaveableStateProvider("maker:${s.maker}") {
            MakerDetail(maker = s.maker, loadRows = { m -> vm.obsByMaker(m) }, accent = accent, onSelectObs = { openObservation(it) }, onBack = { back() })
        }
        is Screen.FullMap -> stateHolder.SaveableStateProvider("fullmap") {
            io.github.gitclo.spectra.ui.FindsMapTool(
                accent = accent,
                currentPose = { vm.lastPose() },
                loadPointsIn = { s2, n2, w2, e2 -> vm.findMapPointsIn(s2, n2, w2, e2) },
                onOpenFull = {},
                loadMarkersIn = { s2, n2, w2, e2 -> vm.mapMarkersIn(s2, n2, w2, e2) },
                onMarkerTap = { m ->
                    scope.launch {
                        if (m.kind == io.github.gitclo.spectra.ui.MapMarkerKind.OFFENSIVE)
                            vm.obsByKey(m.key)?.let { go(Screen.ObsDetail(it)) }
                        else go(Screen.PmkidDetail(m.key))
                    }
                },
                onOpenPoint = { key -> go(Screen.DeviceDetail(key)) },
                seedCenter = { vm.recentLocatedLatLng() },
                onBack = { back() },
                wigleName = (prefs.getString("wigle_name", "") ?: "").trim(),
                wigleToken = (prefs.getString("wigle_token", "") ?: "").trim()
            )
        }
        is Screen.MapView -> stateHolder.SaveableStateProvider("map:${s.title}") {
            MapScreen(
                title = s.title, points = s.points, markers = s.markers, accent = accent,
                currentPose = { vm.lastPose() },
                onSelectKey = { k ->

                    scope.launch {
                        val obs = vm.obsByKey(k)
                        if (obs != null) openObservation(obs)
                        else if (pmkidsAll.any { it.pmkid == k }) go(Screen.PmkidDetail(k))
                    }
                },
                onBack = { back() }
            )
        }
        is Screen.Help -> stateHolder.SaveableStateProvider("help") {
            HelpScreen(
                accent = accent, deep = deep,
                skin = skin,
                onSkin = { vm.skin.value = it; prefs.edit().putString("skin", it.name).apply() },
                fontScale = uiFontScale, onFontScale = onUiFontScale,
                onDiag = { showDiag = true },
                wifiPriority = wifiPriorityUi,
                onWifiPriority = { vm.setWifiPriority(it); prefs.edit().putBoolean("wifi_priority", it).apply() },
                backgroundScan = bgScanUi,
                onBackgroundScan = {
                    vm.setBackgroundScan(it); prefs.edit().putBoolean("bg_scan", it).apply()
                    if (it) promptBatteryExemption()
                },
                startOnBoot = startOnBoot,
                onStartOnBoot = { startOnBoot = it; prefs.edit().putBoolean("start_on_boot", it).apply() },
                wifiGoal = wifiGoal,
                onWifiGoal = { wifiGoal = it; prefs.edit().putInt("wifi_goal", it).apply() },
                onBatterySettings = { promptBatteryExemption(force = true) },
                maxWifi = maxWifiUi,
                onMaxWifi = { vm.setMaxWifi(it); prefs.edit().putBoolean("max_wifi", it).apply() },
                captureClients = captureClientsUi,
                onCaptureClients = { vm.setCaptureClients(it); prefs.edit().putBoolean("capture_clients", it).apply() },
                campBusy = campBusy,
                onCampBusy = {
                    campBusy = it
                    io.github.gitclo.spectra.usb.UsbBeast.campBusy = it
                    prefs.edit().putBoolean("camp_busy", it).apply()
                },
                keepScreenOn = keepScreenOnUi,
                onKeepScreenOn = { vm.setKeepScreenOn(it); prefs.edit().putBoolean("keep_screen_on", it).apply() },
                toolsOnTop = toolsOnTop,
                onToolsOnTop = { toolsOnTop = it; prefs.edit().putBoolean("tools_on_top", it).apply() },
                buzzOnSpot = buzzOnSpot,
                onBuzzOnSpot = { buzzOnSpot = it; prefs.edit().putBoolean("buzz_on_spot", it).apply() },
                notificationsOn = notificationsOn,
                onNotificationsOn = { notificationsOn = it; prefs.edit().putBoolean("notifications_enabled", it).apply() },
                offensiveBanners = io.github.gitclo.spectra.ui.UiFlags.offensiveBanners,
                onOffensiveBanners = { io.github.gitclo.spectra.ui.UiFlags.offensiveBanners = it; prefs.edit().putBoolean("offensive_banners", it).apply() },
                captureBanners = io.github.gitclo.spectra.ui.UiFlags.captureBanners,
                onCaptureBanners = { io.github.gitclo.spectra.ui.UiFlags.captureBanners = it; prefs.edit().putBoolean("capture_banners", it).apply() },
                achievementBanners = io.github.gitclo.spectra.ui.UiFlags.achievementBanners,
                onAchievementBanners = { io.github.gitclo.spectra.ui.UiFlags.achievementBanners = it; prefs.edit().putBoolean("achievement_banners", it).apply() },
                gnssFullTracking = gnssFullUi,
                onGnssFullTracking = { vm.setGnssFullTracking(it); prefs.edit().putBoolean("gnss_full", it).apply() },
                allowUpsideDown = allowUpsideDown,
                onAllowUpsideDown = {
                    allowUpsideDown = it
                    prefs.edit().putBoolean("allow_upside_down", it).apply()
                    (context as? MainActivity)?.applyOrientation(it)
                },
                useMetric = useMetric,
                onUseMetric = { useMetric = it; prefs.edit().putBoolean("use_metric", it).apply() },
                darkMap = io.github.gitclo.spectra.ui.UiFlags.darkMap,
                onDarkMap = { io.github.gitclo.spectra.ui.UiFlags.darkMap = it; prefs.edit().putBoolean("dark_map", it).apply() },
                reduceMotion = io.github.gitclo.spectra.ui.UiFlags.reduceMotion,
                onReduceMotion = { io.github.gitclo.spectra.ui.UiFlags.reduceMotion = it; prefs.edit().putBoolean("reduce_motion", it).apply() },
                onExitApp = {
                    vm.stopScanning()
                    (context as? android.app.Activity)?.finishAndRemoveTask()
                },
                onApiSettings = { wigleSendAfterSave = false; wdgwSendAfterSave = false; showWigle = true },
                onPrivacy = { go(Screen.Privacy) },
                onCheckUpdates = { AppUpdates.checkForUpdate(context, scope, updateLauncher) },
                onUpdateWatch = { AppUpdates.openWatchPlayStore(context, scope) },
                onReplayOnboarding = { onboarded = false }, onBack = { back() }
            )
        }
        is Screen.Privacy -> stateHolder.SaveableStateProvider("privacy") {
            val here = vm.lastLatLng()
            io.github.gitclo.spectra.ui.PrivacyScreen(
                vm = vm, accent = accent, startLat = here?.first, startLng = here?.second,
                onBack = { back() }
            )
        }
        is Screen.Satellites -> stateHolder.SaveableStateProvider("sats") {
            io.github.gitclo.spectra.ui.SatelliteListScreen(
                gnss = gnss, logged = gnssSats, accent = accent,
                onSelect = { go(Screen.SatelliteDetail(it)) }, onBack = { back() }
            )
        }
        is Screen.SatelliteDetail -> stateHolder.SaveableStateProvider("sat:${s.id}") {
            io.github.gitclo.spectra.ui.SatelliteDetailScreen(
                id = s.id, gnss = gnss, logged = gnssSats, accent = accent, onBack = { back() }
            )
        }
        is Screen.CellTowerDetail -> stateHolder.SaveableStateProvider("cell:${s.id}") {
            io.github.gitclo.spectra.ui.CellTowerDetailScreen(
                id = s.id, towers = cellTowers, accent = accent, onBack = { back() }
            )
        }
        is Screen.Pmkids -> stateHolder.SaveableStateProvider("pmkids") {
            PmkidScreen(
                pmkids = pmkidsAll, accent = accent,
                onSelect = { id -> go(Screen.PmkidDetail(id)) },
                onMap = {
                    val pts = pmkidsAll.filter { it.lat != null && it.lng != null }
                        .map { MapPoint(it.lat!!, it.lng!!, it.ssid ?: it.bssid, it.pmkid) }
                    go(Screen.MapView("PMKID captures", pts))
                },
                onBack = { back() }
            )
        }
        is Screen.PmkidDetail -> stateHolder.SaveableStateProvider("pmkid:${s.id}") {
            val p = pmkidsAll.firstOrNull { it.pmkid == s.id }
            if (p != null) PmkidDetailScreen(
                p = p, accent = accent, onBack = { back() },
                onMap = if (p.lat != null && p.lng != null) {
                    { go(Screen.MapView(p.ssid?.ifBlank { null } ?: p.bssid,
                        listOf(MapPoint(p.lat!!, p.lng!!, p.ssid?.ifBlank { null } ?: p.bssid, p.pmkid)))) }
                } else null
            )
            else back()
        }
        is Screen.Devices -> stateHolder.SaveableStateProvider("devices") {
            io.github.gitclo.spectra.ui.devices.DevicesScreen(
                vm = devicesVm, liveKeys = liveKeys, initialCategory = s.category,
                onOpen = { go(Screen.DeviceDetail(it)) },
                onLocate = { locate(it) }
            )
        }
        is Screen.Insights -> stateHolder.SaveableStateProvider("insights") {
            io.github.gitclo.spectra.ui.insights.InsightsScreen(
                vm = insightsVm, runId = runId, live = live,
                onOpenDevice = { go(Screen.DeviceDetail(it)) },
                onOpenSecurityType = { go(Screen.SecurityType(it)) },
                onLocate = { locate(it) }
            )
        }
        is Screen.More -> stateHolder.SaveableStateProvider("more") {
            io.github.gitclo.spectra.ui.more.MoreScreen(
                onOpen = { d ->
                    when (d) {
                        io.github.gitclo.spectra.ui.more.MoreDestination.FIELD_REPORT -> go(Screen.Field)
                        io.github.gitclo.spectra.ui.more.MoreDestination.LEADERBOARDS -> go(Screen.Board)
                        io.github.gitclo.spectra.ui.more.MoreDestination.SATELLITES -> go(Screen.Satellites)
                        io.github.gitclo.spectra.ui.more.MoreDestination.PMKIDS -> go(Screen.Pmkids)
                        io.github.gitclo.spectra.ui.more.MoreDestination.PRIVACY -> go(Screen.Privacy)
                        io.github.gitclo.spectra.ui.more.MoreDestination.API_KEYS -> { wigleSendAfterSave = false; wdgwSendAfterSave = false; showWigle = true }
                        io.github.gitclo.spectra.ui.more.MoreDestination.SETTINGS -> go(Screen.Help)
                    }
                },
                themeMode = themeMode, onThemeMode = { themeMode = it; graph.settings.themeMode = it.key },
                dynamicColor = dynamicColor, onDynamicColor = { dynamicColor = it; graph.settings.dynamicColor = it },
                accent = skin, onAccent = { vm.skin.value = it; prefs.edit().putString("skin", it.name).apply() },
                retentionDays = retentionDays,
                onRetentionDays = {
                    retentionDays = it; graph.settings.historyRetentionDays = it
                    scope.launch { graph.intel.pruneHistory(it) }
                },
                sightingCount = sightingCount
            )
        }
        is Screen.DeviceDetail -> stateHolder.SaveableStateProvider("device:${s.profileId}") {
            val dvm = io.github.gitclo.spectra.ui.devices.rememberDeviceDetailViewModel(s.profileId)
            io.github.gitclo.spectra.ui.devices.DeviceDetailScreen(
                vm = dvm, blockedKeys = blockedKeys,
                onBlockToggle = { key, label, isBlocked -> if (isBlocked) vm.unblockDevice(key) else vm.blockDevice(key, label) },
                onShowOnMap = { pid, enc -> go(Screen.DeviceMap(pid, enc)) },
                onHistory = { go(Screen.DeviceHistory(it)) },
                onLegacyDetail = { key -> scope.launch { vm.obsByKey(key)?.let { go(Screen.ObsDetail(it)) } } },
                onBack = { back() },
                liveKeys = liveKeys,
                onLocate = { locate(it) }
            )
        }
        is Screen.DeviceHistory -> stateHolder.SaveableStateProvider("history:${s.profileId}") {
            io.github.gitclo.spectra.ui.devices.EncounterHistoryScreen(
                vm = io.github.gitclo.spectra.ui.devices.rememberDeviceDetailViewModel(s.profileId),
                onOpenEncounter = { pid, idx -> go(Screen.DeviceMap(pid, idx)) },
                onShowAll = { go(Screen.DeviceMap(it, null)) },
                onBack = { back() }
            )
        }
        is Screen.DeviceMap -> stateHolder.SaveableStateProvider("devmap:${s.profileId}:${s.encounter}") {
            io.github.gitclo.spectra.ui.devices.DeviceMapScreen(
                vm = io.github.gitclo.spectra.ui.devices.rememberDeviceDetailViewModel(s.profileId),
                initialEncounter = s.encounter, currentPose = { vm.lastPose() }, onBack = { back() }
            )
        }
        is Screen.NewFinds -> stateHolder.SaveableStateProvider("newfinds") {
            io.github.gitclo.spectra.ui.scan.NewFindsScreen(
                initialType = s.type,
                counts = mapOf(
                    io.github.gitclo.spectra.model.SignalType.WIFI to newUnsentWifi,
                    io.github.gitclo.spectra.model.SignalType.BLE to newUnsentBle,
                    io.github.gitclo.spectra.model.SignalType.CELL to newUnsentCell,
                ),
                load = { t -> graph.intel.newFinds(t.name) },
                cellTowers = cellTowers,
                onOpen = { o -> openObservation(o) },
                onBack = { back() }
            )
        }
        is Screen.SecurityType -> stateHolder.SaveableStateProvider("sec:${s.type.key}") {
            io.github.gitclo.spectra.ui.insights.SecurityTypeScreen(
                type = s.type, vm = insightsVm, onOpenDevice = { go(Screen.DeviceDetail(it)) }, onBack = { back() }
            )
        }
    }
        }
        }
    }
        BeastOverlay(usb.beast, accent)
        if (showDiag) io.github.gitclo.spectra.ui.BeastDiagDialog(
            accent,
            { io.github.gitclo.spectra.usb.BeastDiag.dump() },
            { showDiag = false },
            clipProvider = { io.github.gitclo.spectra.usb.BeastDiag.dumpForClipboard() })
        usb.koNotice?.let { (koTitle, koBody) ->
            AlertDialog(
                onDismissRequest = { usb.koNotice = null },
                containerColor = Palette.surface,
                title = { Text("⚡ Marauder · $koTitle", color = Palette.ink,
                    fontFamily = AppFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 15.sp) },
                text = { Text(koBody, color = Palette.muted, fontFamily = AppFont, fontSize = 13.sp) },
                confirmButton = {
                    TextButton(onClick = { usb.koNotice = null }) {
                        Text("Got it", color = accent, fontFamily = AppFont,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                })
        }
        if (showWigle) {
            io.github.gitclo.spectra.ui.ApiKeysDialog(
                initial = io.github.gitclo.spectra.ui.ApiKeys(
                    wigleName = prefs.getString("wigle_name", "") ?: "",
                    wigleToken = prefs.getString("wigle_token", "") ?: "",
                    wdgwKey = prefs.getString("wdgw_key", "") ?: "",
                    cartoKey = io.github.gitclo.spectra.ui.CartoTiles.apiKey,
                ),
                confirmLabel = if (wigleSendAfterSave || wdgwSendAfterSave) "Save & send" else "Save",
                onSave = { k ->
                    prefs.edit()
                        .putString("wigle_name", k.wigleName)
                        .putString("wigle_token", k.wigleToken)
                        .putString("wdgw_key", k.wdgwKey)
                        .apply()
                    io.github.gitclo.spectra.ui.CartoTiles.save(context, k.cartoKey)
                    val sendW = wigleSendAfterSave
                    val sendD = wdgwSendAfterSave
                    wigleSendAfterSave = false
                    wdgwSendAfterSave = false
                    showWigle = false
                    when {
                        sendW -> sendToWigleNow()
                        sendD -> sendToWdgwNow()
                        else -> android.widget.Toast.makeText(context, "API keys saved", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { wigleSendAfterSave = false; wdgwSendAfterSave = false; showWigle = false }
            )
        }
    }
    }
}
