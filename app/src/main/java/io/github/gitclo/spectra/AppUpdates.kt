package io.github.gitclo.spectra

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Update checks against the project's GitHub releases. Spectra is distributed as APKs on
 * GitHub, so there is no Play Store path: the latest release tag is compared with the installed
 * version and the release page is opened if it is newer.
 */
object AppUpdates {
    const val REPO = "GitClo/spectra"
    const val REPO_URL = "https://github.com/$REPO"
    const val RELEASES_URL = "$REPO_URL/releases"

    fun checkForUpdate(ctx: Context, scope: CoroutineScope) {
        scope.launch {
            toast(ctx, "Checking for updates…")
            val latest = withContext(Dispatchers.IO) { fetchLatestRelease() }
            val cur = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
            when {
                latest == null -> {
                    toast(ctx, "No published release found. Opening the releases page.")
                    open(ctx, RELEASES_URL)
                }
                isNewer(latest.first, cur) -> {
                    toast(ctx, "Spectra ${latest.first} is available. Opening…")
                    open(ctx, latest.second)
                }
                else -> toast(ctx, "You're on the latest version ($cur).")
            }
        }
    }

    /** The watch app ships as an APK attached to each release; open the releases page. */
    fun openWatchReleases(ctx: Context) {
        toast(ctx, "Download the watch APK from the latest release.")
        open(ctx, RELEASES_URL)
    }

    /** Latest non-draft, non-prerelease release as (version, html url); null if none / offline. */
    private fun fetchLatestRelease(): Pair<String, String>? = runCatching {
        val c = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 8000; setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (c.responseCode != 200) return@runCatching null
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val ver = normalizeTag(o.optString("tag_name"))
            val url = o.optString("html_url")
            if (ver.isBlank() || url.isBlank()) null else ver to url
        } finally { c.disconnect() }
    }.getOrNull()

    /** Accepts tags like "v1.2.0", "1.2.0" or "spectra-v1.2.0". */
    fun normalizeTag(tag: String): String = tag.trim().substringAfterLast('-').removePrefix("v").removePrefix("V")

    fun isNewer(remote: String, current: String): Boolean {
        fun parts(s: String) = s.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val r = parts(remote); val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }; val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun open(ctx: Context, url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}
