package io.github.gitclo.spectra.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * CARTO basemap tiles, optionally authenticated with a CARTO basemaps API key.
 *
 * Without a key CARTO serves tiles under its free fair-use terms; with a key (created at
 * https://carto.com/basemaps/apikey/) every tile URL carries `?key=<key>`, which CARTO
 * requires for commercial or high-volume use. The key is entered in the API keys dialog.
 */
object CartoTiles {
    const val PREF_KEY = "carto_key"
    const val KEY_HELP_URL = "https://carto.com/basemaps/apikey/"

    /** Base names of the CARTO tile sources the app uses (osmdroid caches tiles per name). */
    val SOURCE_NAMES = listOf("CartoVoyager", "CartoDarkNoLabels", "CartoDarkLabels2x")

    /**
     * Current key, or blank for anonymous access. Compose state, so map views that read it in
     * their update block switch tile sources (and reload) as soon as the key changes.
     */
    var apiKey: String by mutableStateOf("")

    /**
     * Short, stable tag identifying the current key. It is part of every tile-source name and
     * cache key, so tiles fetched with one key (or with none, e.g. CARTO's "API key required"
     * placeholder images) are never served after the key changes.
     */
    val cacheTag: String get() = tagFor(apiKey)

    private fun tagFor(key: String): String =
        if (key.isBlank()) "" else "k" + Integer.toHexString(key.trim().hashCode())

    /** Effective osmdroid tile-source name for [base] under the current key. */
    fun sourceName(base: String): String = cacheTag.let { if (it.isEmpty()) base else "$base-$it" }

    fun load(ctx: Context) {
        apiKey = ctx.getSharedPreferences("spectra", Context.MODE_PRIVATE).getString(PREF_KEY, "")?.trim().orEmpty()
    }

    /**
     * Store a new key and drop tiles cached under the previous one. osmdroid's SQLite tile cache
     * and the Android Auto renderer's file cache are purged on a background thread; open maps
     * reload via [apiKey] being observable state.
     */
    fun save(ctx: Context, key: String) {
        val old = apiKey
        val new = key.trim()
        ctx.getSharedPreferences("spectra", Context.MODE_PRIVATE).edit().putString(PREF_KEY, new).apply()
        if (new == old) return
        apiKey = new
        val app = ctx.applicationContext
        Thread({ purgeCaches(app, listOf(tagFor(old), "", tagFor(new)).distinct()) }, "carto-cache-purge").start()
    }

    private fun purgeCaches(ctx: Context, tags: List<String>) {
        runCatching {
            // Not detached afterwards: SqlTileWriter instances share one database connection
            // with the map views that are currently open.
            val writer = org.osmdroid.tileprovider.modules.SqlTileWriter()
            for (base in SOURCE_NAMES) for (t in tags) writer.purgeCache(if (t.isEmpty()) base else "$base-$t")
        }.onFailure { io.github.gitclo.spectra.usb.BeastDiag.log("CARTO cache purge failed: ${it.message}") }
        // Android Auto map renderer keeps its own file cache.
        runCatching { java.io.File(ctx.cacheDir, "cartotiles").listFiles()?.forEach { it.delete() } }
    }

    /** Append the key to a fully built tile URL. */
    fun withKey(url: String): String {
        val k = apiKey
        return if (k.isBlank()) url else url + (if ('?' in url) '&' else '?') + "key=" + java.net.URLEncoder.encode(k, "UTF-8")
    }

    private val HOSTS = arrayOf("a", "b", "c", "d")

    /**
     * Tile source for one CARTO style, e.g. `rastertiles/voyager` or `dark_only_labels`.
     * [suffix] is `.png` or `@2x.png`.
     */
    fun source(name: String, style: String, tileSize: Int, suffix: String): OnlineTileSourceBase =
        object : OnlineTileSourceBase(
            sourceName(name), 1, 20, tileSize, suffix,
            HOSTS.map { "https://$it.basemaps.cartocdn.com/$style/" }.toTypedArray(),
            "© OpenStreetMap contributors, © CARTO"
        ) {
            override fun getTileURLString(index: Long): String = withKey(
                baseUrl + MapTileIndex.getZoom(index) + "/" + MapTileIndex.getX(index) + "/" + MapTileIndex.getY(index) + mImageFilenameEnding
            )
        }
}
