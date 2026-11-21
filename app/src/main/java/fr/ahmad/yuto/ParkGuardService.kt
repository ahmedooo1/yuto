package fr.ahmad.yuto

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Lit la vitesse GPS du téléphone tant que Yuto est ouvert dans la voiture,
 * pour le verrou de stationnement quand la voiture ne transmet pas sa vitesse.
 */
class ParkGuardService : Service() {

    private var lm: LocationManager? = null

    private val listener = LocationListener { loc: Location ->
        val kmh = if (loc.hasSpeed()) loc.speed * 3.6f else null
        ParkGuard.onGps(kmh, if (loc.hasAccuracy()) loc.accuracy else 999f)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = notification()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            Log.w(TAG, "foreground refused", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (lm == null && hasLocation(this)) {
            val m = getSystemService(LocationManager::class.java)
            lm = m
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                runCatching {
                    if (m.isProviderEnabled(p)) m.requestLocationUpdates(p, 1_000L, 0f, listener, Looper.getMainLooper())
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { lm?.removeUpdates(listener) }
        lm = null
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_yuto)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "ParkGuardService"
        private const val CHANNEL = "guard"
        private const val NOTIF_ID = 7

        fun hasLocation(c: Context) =
            ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun start(c: Context) {
            if (!hasLocation(c)) return
            runCatching { ContextCompat.startForegroundService(c, Intent(c, ParkGuardService::class.java)) }
                .onFailure { Log.w(TAG, "start failed", it) }
        }

        fun stop(c: Context) {
            runCatching { c.stopService(Intent(c, ParkGuardService::class.java)) }
        }
    }
}
