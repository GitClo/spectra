package io.github.gitclo.spectra.ui

import androidx.compose.material.icons.filled.KeyboardArrowRight

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Watch
import io.github.gitclo.spectra.model.Skin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun HelpScreen(
    accent: Color, deep: Color,
    skin: Skin = Skin.TEAL, onSkin: (Skin) -> Unit = {},
    fontScale: Float = 1f, onFontScale: (Float) -> Unit = {},
    wifiPriority: Boolean = true, onWifiPriority: (Boolean) -> Unit = {},
    backgroundScan: Boolean = true, onBackgroundScan: (Boolean) -> Unit = {},
    startOnBoot: Boolean = false, onStartOnBoot: (Boolean) -> Unit = {},
    maxWifi: Boolean = false, onMaxWifi: (Boolean) -> Unit = {},
    captureClients: Boolean = true, onCaptureClients: (Boolean) -> Unit = {},
    campBusy: Boolean = false, onCampBusy: (Boolean) -> Unit = {},
    keepScreenOn: Boolean = true, onKeepScreenOn: (Boolean) -> Unit = {},
    toolsOnTop: Boolean = false, onToolsOnTop: (Boolean) -> Unit = {},
    buzzOnSpot: Boolean = true, onBuzzOnSpot: (Boolean) -> Unit = {},
    notificationsOn: Boolean = true, onNotificationsOn: (Boolean) -> Unit = {},
    offensiveBanners: Boolean = true, onOffensiveBanners: (Boolean) -> Unit = {},
    captureBanners: Boolean = true, onCaptureBanners: (Boolean) -> Unit = {},
    achievementBanners: Boolean = true, onAchievementBanners: (Boolean) -> Unit = {},
    wifiGoal: Int = 0, onWifiGoal: (Int) -> Unit = {},
    gnssFullTracking: Boolean = false, onGnssFullTracking: (Boolean) -> Unit = {},
    allowUpsideDown: Boolean = false, onAllowUpsideDown: (Boolean) -> Unit = {},
    useMetric: Boolean = false, onUseMetric: (Boolean) -> Unit = {},
    darkMap: Boolean = false, onDarkMap: (Boolean) -> Unit = {},
    reduceMotion: Boolean = false, onReduceMotion: (Boolean) -> Unit = {},
    onExitApp: () -> Unit = {},
    onBatterySettings: () -> Unit = {},
    onApiSettings: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    onDiag: () -> Unit = {},
    onCheckUpdates: () -> Unit = {},
    onUpdateWatch: () -> Unit = {},
    onReplayOnboarding: () -> Unit, onBack: () -> Unit
) {
    val uri = LocalUriHandler.current
    val ctx = LocalContext.current
    var showCredits by remember { mutableStateOf(false) }

    val openGroup = rememberSaveable { mutableStateOf<String?>(null) }

    var hasWatch by remember {
        mutableStateOf(ctx.getSharedPreferences("spectra", android.content.Context.MODE_PRIVATE).getBoolean("has_watch", false))
    }
    LaunchedEffect(Unit) {
        runCatching {
            com.google.android.gms.wearable.Wearable.getNodeClient(ctx).connectedNodes
                .addOnSuccessListener { nodes ->
                    if (nodes.isNotEmpty() && !hasWatch) {
                        hasWatch = true
                        ctx.getSharedPreferences("spectra", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("has_watch", true).apply()
                    }
                }
        }
    }
    io.github.gitclo.spectra.ui.components.ScrollScreen(title = "Settings & help", subtitle = "Wardrive Go", onBack = onBack) {
        Spacer(Modifier.height(4.dp))

        SettingsGroup(Icons.Filled.Palette, "Appearance", accent, openGroup) {
            SubHeading("Accent")
            Text("Theme mode and dynamic color are under More › Appearance.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Skin.values().forEach { sk ->
                    androidx.compose.material3.FilterChip(
                        selected = sk == skin, onClick = { onSkin(sk) }, label = { Text(sk.display) },
                        leadingIcon = { Box(Modifier.size(16.dp).background(Color(sk.accentHex), CircleShape)) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SubHeading("Text size")
            Text("Scales all text in the app, on top of your phone's system font size.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                listOf("Small" to 0.9f, "Default" to 1.0f, "Large" to 1.15f, "Larger" to 1.3f, "Max" to 1.5f).forEach { (label, mult) ->
                    androidx.compose.material3.FilterChip(
                        selected = kotlin.math.abs(fontScale - mult) < 0.01f, onClick = { onFontScale(mult) }, label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingSwitch("Dark map", "Use a dark basemap on every map in the app — field report, finds map, category maps and privacy zones.", darkMap, false, accent, onDarkMap)
            Spacer(Modifier.height(8.dp))
            SettingSwitch("Reduce animations", "Turn off the score counter's roll/pop and alert-banner pop-ins (including offensive-device alerts). Numbers and banners update instantly instead.", reduceMotion, false, accent, onReduceMotion)
        }

        SettingsGroup(Icons.Filled.Sensors, "Scanning", accent, openGroup) {
            SettingSwitch("Wi-Fi priority", "Wi-Fi is king — scan Bluetooth calmly so it never crowds Wi-Fi. Bluetooth is still counted.", wifiPriority, true, accent, onWifiPriority)
            Spacer(Modifier.height(8.dp))
            SettingSwitch("Max Wi-Fi mode", "Go all-in on Wi-Fi — turn off Bluetooth and cell scanning so the phone spends everything on Wi-Fi.", maxWifi, false, accent, onMaxWifi)
            Spacer(Modifier.height(8.dp))
            SettingSwitch("Keep scanning in background", "Keep the radar running (with a notification) when the app is minimized.", backgroundScan, true, accent, onBackgroundScan)
            Spacer(Modifier.height(8.dp))
            SettingSwitch("External: capture clients", "External adapter logs client stations too. Turn off to run APs-only — pure promiscuous, no cycles spent on clients.", captureClients, true, accent, onCaptureClients)
            Spacer(Modifier.height(8.dp))
            SettingSwitch("External: camp busy channels", "Skip empty channels on the external adapter's hop. Your phone scans every channel and tells the adapter which ones actually have Wi-Fi, so it spends its dwell time only where there's traffic — better handshake/PMKID odds and more time on the busy channels where distant APs also live. Works best with phone Wi-Fi on; falls back to a full sweep if nothing's in range.", campBusy, true, accent, onCampBusy)
            Spacer(Modifier.height(8.dp))
            SettingSwitch("GNSS full tracking", "Decode raw satellite navigation messages for extra detail (health/anti-spoof/leap-second). Large battery drain — the satellite list works fine without it.", gnssFullTracking, false, accent, onGnssFullTracking)
        }

        SettingsGroup(Icons.Filled.Notifications, "Notifications", accent, openGroup) {
            SettingSwitch("System notifications", "Master switch for the app's system notifications — a notable device (camera, drone, tracker, Flipper) first seen nearby posts to your notification shade. Off = none. The scanning notification Android requires while running in the background is separate.", notificationsOn, true, accent, onNotificationsOn)
            Spacer(Modifier.height(8.dp))
            SettingSwitch("Buzz on spot", "Vibrate on those spot alerts. A paired Wear OS watch mirrors it, so your wrist taps. (Needs System notifications on.)", buzzOnSpot, true, accent, onBuzzOnSpot)
            Spacer(Modifier.height(14.dp))
            SubHeading("In-app banners")
            Text("The banner at the top of the Scan screen. Alerts you turn off leave it on its rotating info cards.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(8.dp))
            SettingSwitch("Offensive device alerts", "Banner drops down when an offensive/notable device (Flock, camera, drone, tracker, Flipper) is spotted. Turn off if you watch the Offensive Devices tool instead.", offensiveBanners, true, accent, onOffensiveBanners)
            Spacer(Modifier.height(8.dp))
            SettingSwitch("Hash capture alerts", "Banner drops down when a PMKID or 4-way handshake is captured — a crackable hash to add to your haul.", captureBanners, true, accent, onCaptureBanners)
            Spacer(Modifier.height(8.dp))
            SettingSwitch("Achievement alerts", "Banner drops down on score achievements — milestones, unlocks, new best run and leaderboard rank changes.", achievementBanners, true, accent, onAchievementBanners)
            if (hasWatch) {
                Spacer(Modifier.height(14.dp))
                WifiGoalSetting(wifiGoal, accent, onWifiGoal)
            }
        }

        SettingsGroup(Icons.Filled.Tune, "Behavior & display", accent, openGroup) {
            SettingSwitch("Keep screen on", "Hold the display awake while the app is open. Turn off to let the screen sleep and save battery — scanning keeps running in the background.", keepScreenOn, true, accent, onKeepScreenOn)
            Spacer(Modifier.height(8.dp))
            SettingSwitch("Tool console on top", "Put the Tool Center (radar / device) above the stats on the dashboard. Off keeps stats up top and tools below.", toolsOnTop, false, accent, onToolsOnTop)
            Spacer(Modifier.height(8.dp))
            SettingSwitch("Start on boot", "Relaunch Wardrive Go automatically after the phone restarts, so wardriving resumes on its own (great for a car-mounted phone). On Samsung/OnePlus/Xiaomi you may also need to allow the app's Auto-start in the phone's settings.", startOnBoot, false, accent, onStartOnBoot)
            Spacer(Modifier.height(8.dp))
            SettingSwitch("Allow upside down", "Add upside-down (180°) portrait to the rotation. Off = regular portrait + landscape only. Respects your phone's auto-rotate lock either way.", allowUpsideDown, false, accent, onAllowUpsideDown)
            Spacer(Modifier.height(8.dp))
            SettingSwitch("Use metric units", "Show speed and distance in km/h and kilometres instead of mph and miles.", useMetric,
                java.util.Locale.getDefault().country !in setOf("US", "GB", "LR", "MM"), accent, onUseMetric)
        }

        SettingsGroup(Icons.Filled.Key, "Keys & privacy", accent, openGroup) {
            LinkButton(Icons.Filled.Key, "API keys", "WiGLE, WDGWars and CARTO map keys", accent, deep, filled = true) { onApiSettings() }
            Spacer(Modifier.height(10.dp))
            LinkButton(Icons.Filled.Shield, "Privacy & uploads", "Exclusion zones + blocked devices", accent, deep, filled = true) { onPrivacy() }
        }

        SettingsGroup(Icons.Filled.SystemUpdate, "Updates & maintenance", accent, openGroup) {
            LinkButton(Icons.Filled.SystemUpdate, "Check for updates", "Compare with the latest release on GitHub", accent, deep, filled = true) { onCheckUpdates() }
            Spacer(Modifier.height(10.dp))
            LinkButton(Icons.Filled.Watch, "Install / update watch app", "Get the Wear OS APK from the latest GitHub release", accent, deep, filled = true) { onUpdateWatch() }
            Spacer(Modifier.height(10.dp))
            LinkButton(Icons.Filled.BatteryFull, "Allow background (battery)", "Stop aggressive phones (OnePlus, etc.) killing background scans", accent, deep, filled = true) { onBatterySettings() }
            Spacer(Modifier.height(10.dp))
            LinkButton(Icons.Filled.BugReport, "Diagnostic logs", "Adapter bring-up + capture trace", accent, deep, filled = true) { onDiag() }
            Spacer(Modifier.height(10.dp))
            LinkButton(Icons.Filled.Refresh, "Replay onboarding", "Re-run the setup walkthrough", accent, deep, filled = true) { onReplayOnboarding() }
        }

        SettingsGroup(Icons.Filled.Info, "How it works", accent, openGroup) {
            InfoCard(Icons.Filled.Radar, "The radar", accent,
                "Every device broadcasting near you shows up as a glowing blip, placed by signal strength — the closer to the centre, the stronger the signal. Its icon is our best guess at what it is. Tap any blip for the full detail card.")
            InfoCard(Icons.Filled.EmojiEvents, "Your score", accent,
                "Each contact is a distinct identifier. Devices rotate their IDs for privacy, so this is a hunt score, not a device census — bigger numbers mean more air explored. WiFi is the mission, so achievements score on Wi-Fi. A \"run\" is everything you find since your last WiGLE upload: sending to WiGLE banks the run, resets the run counters to zero and starts a fresh one (WDGWars sends don't). THIS RUN achievements track the current run; LIFETIME and BEST RUN are all-time.")
            InfoCard(Icons.Filled.Sensors, "Seen now vs. logged", accent,
                "Currently Seen lists what's in range this second. After 30 seconds of silence a device drops into the Field Report — your permanent, categorised log of everything you've ever found.")
            InfoCard(Icons.Filled.Category, "Field report & maps", accent,
                "The Field Report folds every device into collapsible categories under Bluetooth, WiFi and Cell. Each section has a map, and every contact is geotagged when a GPS fix is available — so you can see exactly where you found it.")
            ExternalAdaptersCard(accent)
        }

        SettingsGroup(Icons.Filled.Favorite, "About & credits", accent, openGroup) {
            LinkButton(Icons.Filled.Language, "Source code & releases", "github.com/GitClo/spectra", accent, deep, filled = true) {
                uri.openUri(io.github.gitclo.spectra.AppUpdates.REPO_URL)
            }
            Spacer(Modifier.height(10.dp))
            LinkButton(Icons.Filled.Favorite, "Credits", if (showCredits) "Tap to hide" else "WiGLE, WDGWars, Wardrive Go & special thanks", accent, deep, filled = true) { showCredits = !showCredits }
            if (showCredits) {
                Spacer(Modifier.height(10.dp))
                CreditsCard(accent, deep, uri)
            }
        }

        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = onExitApp, modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Filled.PowerSettingsNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Stop scanning & close app")
        }
    }
}

@Composable
private fun SettingsGroup(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, @Suppress("UNUSED_PARAMETER") accent: Color, openGroup: MutableState<String?>, content: @Composable () -> Unit) {
    val open = openGroup.value == title
    io.github.gitclo.spectra.ui.components.KitExpandableGroup(icon, title, open, onToggle = { openGroup.value = if (open) null else title }) { content() }
}

/** Small heading inside a settings group. */
@Composable
private fun SubHeading(text: String) {
    Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp))
}

/** A switch row; shows the factory default only when the current value differs from it. */
@Composable
private fun SettingSwitch(title: String, sub: String, checked: Boolean, defaultOn: Boolean, @Suppress("UNUSED_PARAMETER") accent: Color, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.ListItem(
        modifier = Modifier.clip(androidx.compose.material3.MaterialTheme.shapes.medium).clickable { onChange(!checked) },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(sub)
                if (checked != defaultOn) Text("Default: ${if (defaultOn) "on" else "off"}", style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
            }
        },
        trailingContent = { androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange) }
    )
}

@Composable
private fun WifiGoalSetting(goal: Int, accent: Color, onGoal: (Int) -> Unit) {
    var custom by remember(goal) { mutableStateOf(if (goal > 0) goal.toString() else "") }
    val best = goal == 0
    Column(
        Modifier.fillMaxWidth()
            .background(Palette.surface, RoundedCornerShape(12.dp)).padding(14.dp)
    ) {
        Text("Watch Wi-Fi gauge goal", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
        Text("The target the watch complication's Wi-Fi gauge fills toward. BEST RUN scales it to your best-ever Wi-Fi run so the arc always races your record — or type any fixed number.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.border(if (best) 2.dp else 1.dp, if (best) accent else Palette.line, RoundedCornerShape(20.dp))
                    .background(if (best) accent.copy(alpha = 0.15f) else Palette.paper, RoundedCornerShape(20.dp))
                    .clickable { custom = ""; onGoal(0) }
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) { Text("BEST RUN", style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = if (best) accent else Palette.ink) }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.weight(1f).border(if (!best) 2.dp else 1.dp, if (!best) accent else Palette.line, RoundedCornerShape(20.dp))
                    .background(Palette.paper, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                BasicTextField(
                    value = custom,
                    onValueChange = { s -> val d = s.filter { it.isDigit() }.take(7); custom = d; onGoal(d.toIntOrNull() ?: 0) },
                    textStyle = TextStyle(color = if (!best) accent else Palette.ink, fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    singleLine = true,
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    decorationBox = { inner ->
                        Box {
                            if (custom.isEmpty()) Text("Type a goal #", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CreditsCard(accent: Color, deep: Color, uri: androidx.compose.ui.platform.UriHandler) {
    Column(
        Modifier.fillMaxWidth().background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer, androidx.compose.material3.MaterialTheme.shapes.medium).padding(16.dp)
    ) {
        Text("Credits", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Special thanks to Arkasha, co-founder of WiGLE, who invented this whole beautiful sickness and has personally cost every one of us a small fortune in gas. Worth every drop. -RocketGod",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        LinkButton(Icons.Filled.Language, "WiGLE.net", "The database we all feed", accent, deep, filled = false) { uri.openUri("https://wigle.net") }
        Spacer(Modifier.height(10.dp))
        LinkButton(Icons.Filled.Language, "WDGWars", "wdgwars.pl — Watch Dogs Go wardriving arena", accent, deep, filled = false) { uri.openUri("https://wdgwars.pl") }
        Spacer(Modifier.height(12.dp))
        Text("Special thanks to RocketGod for making the \"Wardrive Go\" app. He made most of the stuff, I've just added some improvements I wanted. -sHAM4an",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        LinkButton(Icons.Filled.Language, "Wardrive Go", "github.com/RocketGod-git/wardrive-go", accent, deep, filled = false) {
            uri.openUri("https://github.com/RocketGod-git/wardrive-go")
        }
    }
}

@Composable
private fun ExternalAdaptersCard(accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer, androidx.compose.material3.MaterialTheme.shapes.medium).padding(14.dp)
    ) {
        Icon(Icons.Filled.Bolt, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text("External Wi-Fi capabilities", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text("Stock Android can't put its Wi-Fi radio into monitor mode, so client devices stay hidden. Plug in a supported USB adapter over OTG and Wardrive Go taps true monitor mode — no root — running in parallel with the phone's own radios. Its promiscuous 802.11 capture (APs + hidden client stations) merges live into the same radar and field report.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            Text("Single-band · 2.4 GHz", style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            DeviceBullet("Atheros AR9271", "ath9k_htc", "Alfa AWUS036NHA · TP-Link TL-WN722N v1", accent)
            Spacer(Modifier.height(6.dp))
            DeviceBullet("Ralink RT5370 / RT3070", "rt2800usb", "Superwang · TurboTenna Yagi (high-gain)", accent)

            Spacer(Modifier.height(10.dp))
            Text("Dual-band · 2.4 + 5 GHz", style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            DeviceBullet("MediaTek MT7612U", "mt76x2u", "Alfa AWUS036ACM · Panda PAU0D AC1200 · full 5 GHz incl. DFS", accent)
            Spacer(Modifier.height(6.dp))
            DeviceBullet("Realtek RTL8814AU", "88xxau", "Alfa AWUS1900 · 4-antenna AC1900 · 5 GHz: ch 36–48 and 149–165 only (no DFS channels yet)", accent)
            Spacer(Modifier.height(10.dp))
            Text("Single-band in this app", style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            DeviceBullet("Realtek RTL8821AU", "88xxau", "Alfa AWUS036ACS · dual-band chip, but the driver currently captures 2.4 GHz only", accent)

            Spacer(Modifier.height(10.dp))
            Text("ESP32 scanners · serial", style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            DeviceBullet("ESP32 Marauder", "serial · AP scan", "JustCallMeKoko wardriver + other Marauder boards", accent)
            Text("Access-point discovery over USB-serial (not monitor mode). On a dual-band board (ESP32-C5) it sweeps 2.4 + 5 GHz. APs only — no client-station or handshake capture on serial.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, top = 2.dp))

            Spacer(Modifier.height(8.dp))
            DeviceBullet("Ghost ESP", "serial · AP scan", "Any Ghost ESP board — Poltergeist, GhostESP Revival, etc.", accent)
            Text("Passive AP discovery over USB-serial with channel, band and vendor per network; dual-band on ESP32-C5. Ghost ESP runs on many ESP32 boards. APs only.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, top = 2.dp))

            Spacer(Modifier.height(8.dp))
            DeviceBullet("Cerberus", "serial · AP scan", "ESP32 rig with Wardrive-Go-compatible CLI firmware", accent)
            Text("Custom wardriving rig whose firmware speaks the same serial CLI we drive — plug it in and its scan feeds the same pipeline. APs only.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, top = 2.dp))

            Spacer(Modifier.height(6.dp))
            Text("The firmware (Marauder or Ghost ESP) is detected automatically over any bridge — native-USB or a CP210x — so just plug in a supported board.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))

            Spacer(Modifier.height(14.dp))
            Text("Hash capture", style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text("While the adapter runs, Wardrive Go passively harvests WPA key material from the frames it already sees — no deauth, no injection. It grabs PMKIDs (WPA*01) and full 4-way handshakes (WPA*02) and files each in the Field Report under \"# HASH CAPTURES\". Tap one for its card — SSID, BSSID, channel, signal, location and the ready-to-crack hashcat 22000 line — with copy / save / share and a map link to where you caught it.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(12.dp))
            Text("Crack it · hashcat mode 22000", style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            CmdBox("Linux / macOS", "hashcat -m 22000 capture.hc22000 wordlist.txt", accent)
            Spacer(Modifier.height(6.dp))
            CmdBox("Windows · PowerShell", ".\\hashcat.exe -m 22000 capture.hc22000 wordlist.txt", accent)

            Spacer(Modifier.height(12.dp))
            Text("Crack online", style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            AutoShrinkLink("https://handshakecrack.com/", "handshakecrack.com", accent, maxSize = 20.sp)

            Spacer(Modifier.height(8.dp))
            Text("Only against networks you own or are authorized to test.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeviceBullet(chipset: String, driver: String, examples: String, @Suppress("UNUSED_PARAMETER") accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text("•", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.primary, modifier = Modifier.width(14.dp))
        Column {
            Text("$chipset  ·  $driver", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            Text(examples, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, @Suppress("UNUSED_PARAMETER") accent: Color, body: String) {
    androidx.compose.material3.Surface(shape = androidx.compose.material3.MaterialTheme.shapes.medium, color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(body, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LinkButton(icon: ImageVector, title: String, subtitle: String, @Suppress("UNUSED_PARAMETER") accent: Color, @Suppress("UNUSED_PARAMETER") deep: Color, @Suppress("UNUSED_PARAMETER") filled: Boolean, onClick: () -> Unit) {
    io.github.gitclo.spectra.ui.components.KitNavRow(icon, title, subtitle, onClick,
        trailing = { Icon(Icons.Filled.KeyboardArrowRight, null) })
}
