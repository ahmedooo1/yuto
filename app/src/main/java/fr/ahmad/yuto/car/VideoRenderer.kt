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
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.VideoView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import fr.ahmad.yuto.AdBlock
import fr.ahmad.yuto.LocalVideo
import fr.ahmad.yuto.Settings
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import fr.ahmad.yuto.AdBlock
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
    private var localBox: FrameLayout? = null
    private var localView: VideoView? = null
    private var localBar: View? = null
    private var queueIndex = -1
    private var dim: View? = null
    private var density = 1f
    private var immersive = false

    /** Appelé quand le plein écran ou la lecture locale change (la voiture met ses boutons à jour). */
    var onModeChanged: (() -> Unit)? = null

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
        stopLocal()
        setFullscreen(false)
        exitFullscreen()
        web?.loadUrl(url)
    }

    fun home() = load(Web.HOME)

    fun search(query: String) {
        if (query.isNotBlank()) load(Web.search(query.trim()))
    }

    /** Retour : sort du plein écran, sinon page précédente. */
    fun back(): Boolean {
        if (localView != null) {
            stopLocal()
            return true
        }
        if (immersive) {
            setFullscreen(false)
            return true
        }
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

    fun playPause() {
        val lv = localView
        if (lv != null) {
            if (lv.isPlaying) {
                lv.pause()
                saveLocalPosition()
            } else lv.start()
        } else web?.evaluateJavascript(Web.JS_PLAY_PAUSE, null)
    }

    fun seek(seconds: Int) {
        val lv = localView
        if (lv != null) {
            lv.seekTo((lv.currentPosition + seconds * 1000).coerceIn(0, lv.duration.coerceAtLeast(0)))
        } else web?.evaluateJavascript(Web.jsSeek(seconds), null)
    }

    // ---------------- Mode nuit ----------------

    /** Image assombrie + thème sombre des pages (réglage du téléphone ou menu de la voiture). */
    fun applyNight() {
        val on = Settings.night(context)
        dim?.visibility = if (on) View.VISIBLE else View.GONE
        web?.let { w ->
            runCatching {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(w.settings, on)
                }
            }
        }
    }

    // ---------------- Plein écran ----------------

    fun isFullscreen() = immersive || fullView != null || localView != null

    /**
     * Plein écran : la page passe sous les boutons de la voiture (marges à zéro) et
     * la vidéo est agrandie sur toute la surface.
     */
    fun setFullscreen(on: Boolean) {
        if (immersive == on) return
        immersive = on
        web?.evaluateJavascript(if (on) JS_FILL_ON else JS_FILL_OFF, null)
        applyPadding()
        onModeChanged?.invoke()
    }

    // ---------------- Vidéos du téléphone ----------------

    fun isLocalPlaying() = localView != null

    private val saver = object : Runnable {
        override fun run() {
            saveLocalPosition()
            main.postDelayed(this, 5000)
        }
    }

    private fun saveLocalPosition() {
        val lv = localView ?: return
        val item = LocalVideo.list(context).getOrNull(queueIndex) ?: return
        LocalVideo.savePosition(context, item.uri, lv.currentPosition)
    }

    /** Lit la vidéo n° [index] de la file, là où elle s'était arrêtée ; enchaîne ensuite avec la suivante. */
    fun playLocal(index: Int) {
        val st = stage ?: return
        val items = LocalVideo.list(context)
        val item = items.getOrNull(index) ?: return
        stopLocal()
        web?.evaluateJavascript(Web.JS_PAUSE, null)
        queueIndex = index
        val c = st.context
        val lv = VideoView(c)
        val resume = LocalVideo.position(context, item.uri)
        lv.setOnPreparedListener { mp ->
            if (resume in 5000 until (mp.duration - 5000)) lv.seekTo(resume)
            lv.start()
        }
        lv.setOnCompletionListener {
            LocalVideo.savePosition(context, item.uri, 0)
            if (index + 1 < items.size) playLocal(index + 1) else stopLocal()
        }
        lv.setOnErrorListener { _, _, _ ->
            lastError = "video"
            stopLocal()
            true
        }
        val box = FrameLayout(c).apply { setBackgroundColor(Color.BLACK) }
        box.addView(lv, FrameLayout.LayoutParams(MATCH, MATCH))
        val bar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC000000.toInt())
            val h = (96 * density).toInt()
            fun btn(label: String, onClick: () -> Unit) = Button(c).apply {
                text = label
                textSize = 30f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { onClick() }
            }.also { addView(it, LinearLayout.LayoutParams(0, h, 1f)) }
            btn("\u23EE") { prevLocal() }
            btn("-10") { seek(-10) }
            btn("\u23EF") { playPause() }
            btn("+10") { seek(10) }
            btn("\u23ED") { nextLocal() }
            btn("\u2715") { stopLocal() }
        }
        box.addView(bar, FrameLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM))
        // Un toucher sur l'image montre ou cache les gros boutons
        lv.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                bar.visibility = if (bar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
            true
        }
        // Sous le rideau (verrou) et le voile de nuit : le verrou de stationnement garde la priorité
        val at = (st.indexOfChild(lock).takeIf { it >= 0 } ?: st.childCount)
        st.addView(box, at, FrameLayout.LayoutParams(MATCH, MATCH))
        localBox = box
        localView = lv
        localBar = bar
        lv.setVideoURI(item.uri)
        main.removeCallbacks(saver)
        main.postDelayed(saver, 5000)
        applyPadding()
        onModeChanged?.invoke()
    }

    private fun nextLocal() {
        val n = LocalVideo.list(context).size
        if (queueIndex + 1 < n) {
            saveLocalPosition()
            playLocal(queueIndex + 1)
        }
    }

    private fun prevLocal() {
        val lv = localView ?: return
        // Comme un lecteur audio : d'abord retour au début, puis vidéo précédente
        if (lv.currentPosition > 3000 || queueIndex == 0) lv.seekTo(0)
        else {
            saveLocalPosition()
            playLocal(queueIndex - 1)
        }
    }

    fun stopLocal() {
        val box = localBox ?: return
        saveLocalPosition()
        main.removeCallbacks(saver)
        runCatching { localView?.stopPlayback() }
        (box.parent as? ViewGroup)?.removeView(box)
        localBox = null
        localView = null
        localBar = null
        applyPadding()
        onModeChanged?.invoke()
    }

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
        this.density = density

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

            // Anti-pubs : requêtes des régies pub remplacées par une réponse vide
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                AdBlock.intercept(request)

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                AdBlock.inject(view)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                AdBlock.inject(view)
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
                applyPadding()
                onModeChanged?.invoke()
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
            addView(View(c).apply {
                setBackgroundColor(0x99000000.toInt())
                visibility = View.GONE
                dim = this
            }, FrameLayout.LayoutParams(MATCH, MATCH))
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
        applyNight()

        w.onResume()
        w.resumeTimers()
        w.loadUrl(prefs.getString(KEY_LAST, null) ?: Web.HOME)
    }

    /** La page n'est pas cachée sous les boutons d'Android Auto. */
    private fun applyPadding() {
        val st = stage ?: return
        val v = visible
        val lp = st.layoutParams as? FrameLayout.LayoutParams ?: return
        if (v == null || width == 0 || isFullscreen()) {
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
        applyPadding()
        onModeChanged?.invoke()
    }

    fun release() {
        stopLocal()
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
        dim = null
    }

    private companion object {
        const val TAG = "YutoVideo"
        const val KEY_LAST = "last_url"

        /** La vidéo de la page prend tout l'écran (YouTube mobile garde sa propre mise en page). */
        const val JS_FILL_ON =
            "(function(){var s=document.getElementById('yuto-fill');if(!s){s=document.createElement('style');" +
                "s.id='yuto-fill';s.textContent='video{position:fixed!important;top:0!important;left:0!important;" +
                "width:100vw!important;height:100vh!important;object-fit:contain!important;" +
                "z-index:2147483647!important;background:#000!important}';document.head.appendChild(s)}})()"
        const val JS_FILL_OFF = "(function(){var s=document.getElementById('yuto-fill');if(s)s.remove()})()"
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
