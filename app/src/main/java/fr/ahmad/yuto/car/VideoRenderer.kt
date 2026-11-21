package fr.ahmad.yuto.car

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import fr.ahmad.yuto.R
import fr.ahmad.yuto.Web

/**
 * YouTube sur l'écran Android Auto.
 *
 * Même principe que la carte de Mapo : un écran virtuel (VirtualDisplay) branché sur la
 * Surface de la voiture, avec une Presentation qui contient une vraie WebView.
 * Les touchers de l'écran de la voiture arrivent par [SurfaceCallback] et sont rejoués
 * sur la page.
 *
 * Quand Android Auto retire la Surface (on passe à Mapo, à la radio…), l'écran virtuel
 * est gardé sans sortie : la page continue de tourner et le son ne se coupe pas.
 */
class VideoRenderer(private val context: Context) : SurfaceCallback {

    private val main = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("yuto", Context.MODE_PRIVATE)

    private var vd: VirtualDisplay? = null
    private var pres: Presentation? = null
    private var web: WebView? = null
    private var stage: FrameLayout? = null
    private var lock: LockView? = null
    private var fullView: View? = null
    private var fullCallback: WebChromeClient.CustomViewCallback? = null

    private var width = 0
    private var height = 0
    private var dpi = 160
    private var visible: Rect? = null

    /** Rideau baissé : pas d'image, pas de toucher, le son continue. */
    var locked = true
        private set

    var lastError: String? = null
        private set

    // ---------------- Surface Android Auto ----------------

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val s = container.surface ?: return
        width = container.width
        height = container.height
        dpi = container.dpi
        Log.i(TAG, "surface ${width}x$height @ ${dpi}dpi")
        if (width <= 0 || height <= 0) return
        val existing = vd
        if (existing != null) {
            runCatching {
                existing.resize(width, height, dpi)
                existing.surface = s
            }.onFailure { Log.w(TAG, "reattach failed", it) }
            applyPadding()
            return
        }
        try {
            create(s)
            lastError = null
        } catch (e: Throwable) {
            Log.e(TAG, "create failed", e)
            lastError = e.javaClass.simpleName + ": " + e.message
            release()
        }
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        // On garde la page en vie (et le son) : seule la sortie vers la voiture disparaît
        runCatching { vd?.surface = null }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        visible = Rect(visibleArea)
        applyPadding()
    }

    override fun onStableAreaChanged(stableArea: Rect) {}

    override fun onClick(x: Float, y: Float) {
        if (locked) return
        tap(x, y)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        if (locked || fullView != null) return
        web?.evaluateJavascript(Web.jsScroll(distanceY.toInt()), null)
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        if (locked || fullView != null) return
        web?.evaluateJavascript(Web.jsScroll((-velocityY * 0.35f).toInt()), null)
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {}

    // ---------------- Commandes de l'écran ----------------

    fun load(url: String) {
        exitFullscreen()
        web?.loadUrl(url)
    }

    fun home() = load(Web.HOME)

    fun search(query: String) {
        if (query.isNotBlank()) load(Web.search(query.trim()))
    }

    /** Retour : sort du plein écran, sinon page précédente. */
    fun back(): Boolean {
        if (fullView != null) {
            exitFullscreen()
            return true
        }
        val w = web ?: return false
        if (w.canGoBack()) {
            w.goBack()
            return true
        }
        return false
    }

    fun playPause() = web?.evaluateJavascript(Web.JS_PLAY_PAUSE, null)
    fun seek(seconds: Int) = web?.evaluateJavascript(Web.jsSeek(seconds), null)

    fun scrollPage(direction: Int) {
        if (locked) return
        web?.evaluateJavascript(Web.jsScroll(direction * height / 2), null)
    }

    fun setLock(isLocked: Boolean, title: String, subtitle: String, progress: Float) {
        locked = isLocked
        lock?.let {
            it.title = title
            it.subtitle = subtitle
            it.progress = progress
            it.visibility = if (isLocked) View.VISIBLE else View.GONE
        }
    }

    // ---------------- Construction ----------------

    @SuppressLint("ClickableViewAccessibility")
    private fun create(s: android.view.Surface) {
        val dm = context.getSystemService(DisplayManager::class.java)
        val display = dm.createVirtualDisplay(
            "YutoCarVideo", width, height, dpi, s,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
        ) ?: error("createVirtualDisplay returned null")
        vd = display

        val p = Presentation(context, display.display, android.R.style.Theme_DeviceDefault_NoActionBar)
        val c = p.context
        val density = dpi / 160f

        val w = WebView(c)
        Web.setup(w, autoplay = true)
        w.setBackgroundColor(Color.BLACK)
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                // Pas d'ouverture de l'appli YouTube ni de sites extérieurs sur l'écran de la voiture
                if (u.scheme != "https" && u.scheme != "http") return true
                return !Web.allowed(u.host)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (url != null && Web.allowed(Uri.parse(url).host)) prefs.edit().putString(KEY_LAST, url).apply()
                Web.flushCookies()
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                exitFullscreen()
                fullView = view
                fullCallback = callback
                view.setBackgroundColor(Color.BLACK)
                stage?.addView(view, 1, FrameLayout.LayoutParams(MATCH, MATCH))
            }

            override fun onHideCustomView() {
                removeFullView()
            }
        }

        val lk = LockView(c).apply {
            this.density = density
            title = context.getString(R.string.lock_checking)
            subtitle = context.getString(R.string.lock_checking_sub)
        }
        val st = FrameLayout(c).apply {
            setBackgroundColor(Color.BLACK)
            addView(w, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(lk, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        val root = FrameLayout(c).apply {
            setBackgroundColor(Color.BLACK)
            addView(st, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        p.setContentView(root)
        p.show()

        pres = p
        web = w
        stage = st
        lock = lk
        lk.visibility = if (locked) View.VISIBLE else View.GONE
        applyPadding()

        w.onResume()
        w.resumeTimers()
        w.loadUrl(prefs.getString(KEY_LAST, null) ?: Web.HOME)
    }

    /** La page n'est pas cachée sous les boutons d'Android Auto. */
    private fun applyPadding() {
        val st = stage ?: return
        val v = visible
        val lp = st.layoutParams as? FrameLayout.LayoutParams ?: return
        if (v == null || width == 0) {
            lp.setMargins(0, 0, 0, 0)
        } else {
            lp.setMargins(v.left.coerceAtLeast(0), v.top.coerceAtLeast(0),
                (width - v.right).coerceAtLeast(0), (height - v.bottom).coerceAtLeast(0))
        }
        st.layoutParams = lp
    }

    /** Rejoue un toucher de l'écran de la voiture sur la page. */
    private fun tap(x: Float, y: Float) {
        val target = pres?.window?.decorView ?: return
        val t = SystemClock.uptimeMillis()
        send(target, t, t, MotionEvent.ACTION_DOWN, x, y)
        main.postDelayed({ send(target, t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y) }, 70)
    }

    private fun send(target: View, down: Long, at: Long, action: Int, x: Float, y: Float) {
        val e = MotionEvent.obtain(down, at, action, x, y, 0)
        e.source = InputDevice.SOURCE_TOUCHSCREEN
        runCatching { target.dispatchTouchEvent(e) }
        e.recycle()
    }

    private fun exitFullscreen() {
        val cb = fullCallback
        removeFullView()
        runCatching { cb?.onCustomViewHidden() }
    }

    private fun removeFullView() {
        fullView?.let { v -> (v.parent as? ViewGroup)?.removeView(v) }
        fullView = null
        fullCallback = null
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        Web.flushCookies()
        removeFullView()
        web?.let { w ->
            runCatching { w.stopLoading() }
            runCatching { w.loadUrl("about:blank") }
            runCatching { w.destroy() }
        }
        runCatching { pres?.dismiss() }
        runCatching { vd?.release() }
        web = null
        pres = null
        vd = null
        stage = null
        lock = null
    }

    private companion object {
        const val TAG = "YutoVideo"
        const val KEY_LAST = "last_url"
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
