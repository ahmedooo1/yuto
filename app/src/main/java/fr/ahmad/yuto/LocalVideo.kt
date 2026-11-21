package fr.ahmad.yuto

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Vidéos du téléphone choisies par l'utilisateur (file de lecture) et position de reprise. */
object LocalVideo {
    private const val FILE = "yuto"
    private const val KEY_LIST = "local_videos"
    private const val KEY_POS = "local_pos_"

    class Item(val uri: Uri, val name: String)

    private fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun list(c: Context): List<Item> =
        prefs(c).getString(KEY_LIST, "").orEmpty().lines().mapNotNull { line ->
            val i = line.indexOf('\t')
            if (i <= 0) null else Item(Uri.parse(line.substring(0, i)), line.substring(i + 1))
        }

    fun add(c: Context, uris: List<Uri>) {
        val have = list(c).toMutableList()
        for (u in uris) {
            if (have.none { it.uri == u }) have.add(Item(u, displayName(c, u)))
        }
        save(c, have)
    }

    fun clear(c: Context) = save(c, emptyList())

    private fun save(c: Context, items: List<Item>) {
        prefs(c).edit().putString(KEY_LIST, items.joinToString("\n") { it.uri.toString() + "\t" + it.name.replace('\n', ' ') }).apply()
    }

    private fun displayName(c: Context, uri: Uri): String = runCatching {
        c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "video"

    /** Position où la lecture s'est arrêtée (ms), 0 si inconnue. */
    fun position(c: Context, uri: Uri): Int = prefs(c).getInt(KEY_POS + uri.toString().hashCode(), 0)

    fun savePosition(c: Context, uri: Uri, ms: Int) {
        prefs(c).edit().putInt(KEY_POS + uri.toString().hashCode(), ms.coerceAtLeast(0)).apply()
    }
}
