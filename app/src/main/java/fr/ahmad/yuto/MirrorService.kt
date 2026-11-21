package fr.ahmad.yuto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import kotlin.math.roundToInt

/** Process-local state only. Consent data is never persisted or reused. Main thread only. */
object MirrorSession {
    var active = false
        private set
    var width = 1
        private set
    var height = 1
        private set
    internal var display: VirtualDisplay? = null
    private val listeners = mutableSetOf<() -> Unit>()
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    internal fun update(on: Boolean, w: Int = width, h: Int = height) {
        active = on
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
        listeners.toList().forEach { it() }
    }
    internal fun attach(surface: Surface?) { display?.surface = surface }
    fun stop(context: Context) { context.stopService(Intent(context, MirrorService::class.java)) }
}

/** One consent token, one virtual display. Detach/resize, never recreate with the same token. */
class MirrorService : Service() {
    private var projection: MediaProjection? = null
    private val main = Handler(Looper.getMainLooper())
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { stopSelf() }
        override fun onCapturedContentResize(width: Int, height: Int) { resize(width, height) }
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        // A second start must not leave the previous, now invisible, capture alive.
        if (projection != null) { stopSelf(); return START_NOT_STICKY }
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") (intent?.getParcelableExtra(EXTRA_DATA) as? Intent)
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.mirror_title), NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop = PendingIntent.getService(this, 1, Intent(this, MirrorService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_yuto)
                .setContentTitle(getString(R.string.mirror_title))
                .setContentText(getString(R.string.mirror_active))
                .setContentIntent(open).setOngoing(true)
                .addAction(Notification.Action.Builder(null, getString(R.string.mirror_stop), stop).build())
                .build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(42, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(42, notification)
            val manager = getSystemService(MediaProjectionManager::class.java)
            val p = manager.getMediaProjection(android.app.Activity.RESULT_OK, data)
            projection = p
            p.registerCallback(callback, main)
            val (w, h) = screenSize()
            MirrorSession.display = p.createVirtualDisplay("YutoPhoneMirror", w, h,
                resources.configuration.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                null, null, main)
            MirrorSession.update(true, w, h)
        } catch (e: Exception) {
            android.util.Log.w("YutoMirror", "Unable to start screen sharing", e)
            Toast.makeText(this, R.string.mirror_error, Toast.LENGTH_LONG).show()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 30) {
            val b = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            return b.width() to b.height()
        }
        val m = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(m)
        return m.widthPixels to m.heightPixels
    }
    private fun resize(w: Int, h: Int) {
        if (w <= 0 || h <= 0 || MirrorSession.display == null) return
        MirrorSession.display?.resize(w, h, resources.configuration.densityDpi)
        MirrorSession.update(true, w, h)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Android 14 reports the selected app dimensions via the projection callback.
        if (Build.VERSION.SDK_INT < 34) { val (w, h) = screenSize(); resize(w, h) }
    }
    override fun onDestroy() {
        MirrorSession.display?.release()
        MirrorSession.display = null
        projection?.unregisterCallback(callback)
        projection?.stop()
        projection = null
        MirrorSession.update(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        const val EXTRA_DATA = "projection_consent"
        const val ACTION_STOP = "fr.ahmad.yuto.STOP_MIRROR"
        private const val CHANNEL = "screen_sharing"
    }
}

/** TextureView is composited BELOW the parking curtain, never over it like a SurfaceView. */
class MirrorView(context: Context) : FrameLayout(context), TextureView.SurfaceTextureListener {
    private val texture = TextureView(context)
    private var output: Surface? = null
    private val changed: () -> Unit = { updateSize() }
    init {
        setBackgroundColor(Color.BLACK)
        addView(texture, LayoutParams(1, 1, Gravity.CENTER))
        texture.surfaceTextureListener = this
        // No remote input injection: operate the source app on the parked phone.
        isClickable = true
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        MirrorSession.addListener(changed)
        updateSize()
    }
    override fun onDetachedFromWindow() {
        MirrorSession.removeListener(changed)
        detach()
        super.onDetachedFromWindow()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { updateSize() }
    private fun updateSize() {
        if (width <= 0 || height <= 0) return
        val size = MirrorGeometry.fit(width, height, MirrorSession.width, MirrorSession.height)
        texture.layoutParams = LayoutParams(size.first, size.second, Gravity.CENTER)
        texture.surfaceTexture?.setDefaultBufferSize(MirrorSession.width, MirrorSession.height)
    }
    override fun onSurfaceTextureAvailable(t: SurfaceTexture, w: Int, h: Int) {
        t.setDefaultBufferSize(MirrorSession.width, MirrorSession.height)
        output = Surface(t)
        MirrorSession.attach(output)
        updateSize()
    }
    override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, w: Int, h: Int) { }
    override fun onSurfaceTextureUpdated(t: SurfaceTexture) { }
    override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean { detach(); return true }
    private fun detach() {
        MirrorSession.attach(null)
        output?.release()
        output = null
    }
}

object MirrorGeometry {
    fun fit(boxW: Int, boxH: Int, sourceW: Int, sourceH: Int): Pair<Int, Int> {
        if (boxW <= 0 || boxH <= 0 || sourceW <= 0 || sourceH <= 0) return 1 to 1
        val scale = minOf(boxW.toDouble() / sourceW, boxH.toDouble() / sourceH)
        return (sourceW * scale).roundToInt().coerceIn(1, boxW) to
            (sourceH * scale).roundToInt().coerceIn(1, boxH)
    }
}
