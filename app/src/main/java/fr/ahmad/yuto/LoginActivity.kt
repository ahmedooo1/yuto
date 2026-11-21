package fr.ahmad.yuto

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Connexion à YouTube sur le téléphone (clavier, double authentification…).
 * Les cookies sont partagés avec la page affichée dans la voiture.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var closing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        Web.setup(web, autoplay = false)
        web.setBackgroundColor(Color.BLACK)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme != "https" && u.scheme != "http") return true
                return !Web.allowed(u.host)
            }

            // Retour sur YouTube une fois connecté : on revient tout seul à l'écran principal
            override fun onPageFinished(view: WebView, url: String?) {
                Web.flushCookies()
                val host = url?.let { android.net.Uri.parse(it).host }.orEmpty()
                if (host.endsWith("youtube.com") && Web.isSignedIn() && !closing) {
                    closing = true
                    view.postDelayed({ finish() }, 600)
                }
            }
        }
        val done = Button(this).apply {
            setText(R.string.login_done)
            isAllCaps = false
            setTextColor(ContextCompat.getColor(context, R.color.ink))
            setBackgroundResource(R.drawable.btn_primary)
            stateListAnimator = null
            setOnClickListener { finish() }
        }
        val d = resources.displayMetrics.density
        val root = FrameLayout(this).apply {
            addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(done, FrameLayout.LayoutParams((140 * d).toInt(), (50 * d).toInt(), Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, (18 * d).toInt(), (24 * d).toInt())
            })
        }
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
        web.loadUrl(Web.LOGIN)
    }

    override fun onPause() {
        Web.flushCookies()
        super.onPause()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
