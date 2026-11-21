package fr.ahmad.parko

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import java.net.URLEncoder

/** Réglages communs des WebView YouTube (voiture et téléphone partagent les mêmes cookies). */
object Web {

    const val HOME = "https://m.youtube.com/"
    const val LOGIN = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F"

    fun search(query: String) = "https://m.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")

    /** Le site YouTube et la connexion Google, rien d'autre n'est ouvert dans l'appli. */
    fun allowed(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return ALLOWED.any { h == it || h.endsWith(".$it") }
    }

    private val ALLOWED = listOf(
        "youtube.com", "youtu.be", "google.com", "google.fr", "googleusercontent.com",
        "gstatic.com", "googlevideo.com", "ytimg.com", "ggpht.com", "youtube-nocookie.com",
    )

    /**
     * Agent "Chrome mobile" sans la marque WebView ("; wv") : Google refuse sinon
     * la connexion dans une WebView, et YouTube sert une version réduite.
     */
    fun userAgent(c: Context): String =
        WebSettings.getDefaultUserAgent(c)
            .replace("; wv", "")
            .replace(Regex("Version/[0-9.]+ "), "")

    @SuppressLint("SetJavaScriptEnabled")
    fun setup(w: WebView, autoplay: Boolean) {
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = !autoplay
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            userAgentString = userAgent(w.context)
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(w, true)
        }
        w.isVerticalScrollBarEnabled = false
        w.isHorizontalScrollBarEnabled = false
    }

    fun flushCookies() = runCatching { CookieManager.getInstance().flush() }

    // ---------- Commandes du lecteur (JavaScript sur la page YouTube) ----------

    const val JS_PLAY_PAUSE =
        "(function(){var v=document.querySelector('video');if(!v)return 'none';" +
            "if(v.paused){v.play();return 'play'}else{v.pause();return 'pause'}})()"

    const val JS_PAUSE = "(function(){var v=document.querySelector('video');if(v&&!v.paused)v.pause()})()"

    fun jsScroll(dy: Int) = "window.scrollBy({top:$dy,left:0,behavior:'auto'})"

    fun jsSeek(seconds: Int) =
        "(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.max(0,v.currentTime+($seconds))})()"
}
