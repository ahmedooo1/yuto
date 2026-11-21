package fr.ahmad.yuto

import android.content.Context

/** Réglages partagés entre le téléphone et l'écran de la voiture. */
object Settings {
    private const val FILE = "yuto"
    private const val KEY_STRICT = "strict_lock"
    private const val KEY_ALLOW_DRIVING = "allow_driving"
    private const val KEY_NIGHT = "night_mode"

    /** Coché par défaut : la vidéo reste cachée tant que l'arrêt n'est pas confirmé. */
    fun strictLock(c: Context): Boolean =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_STRICT, true)

    fun setStrictLock(c: Context, on: Boolean) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_STRICT, on).apply()
    }

    /** Décoché par défaut : choix explicite de garder la vidéo visible même en roulant. */
    fun allowDriving(c: Context): Boolean =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ALLOW_DRIVING, false)

    /** Mode nuit : image assombrie et thème sombre des pages, pour conduire de nuit. */
    fun night(c: Context): Boolean =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_NIGHT, false)

    fun setNight(c: Context, on: Boolean) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_NIGHT, on).apply()
    }

    fun setAllowDriving(c: Context, on: Boolean) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_ALLOW_DRIVING, on).apply()
    }
}
