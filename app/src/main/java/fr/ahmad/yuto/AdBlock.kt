package fr.ahmad.yuto

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.io.ByteArrayInputStream
import java.net.URI

/**
 * Blocage gratuit des pubs YouTube dans la WebView (aucun abonnement).
 *
 * Deux couches :
 *  - réseau : on jette les requêtes vers les régies pub (doubleclick…) et les pings
 *    YouTube de mesure des pubs (/api/stats/ads, /pagead/, /ptracking) ;
 *  - page : un petit JavaScript injecté qui retire les emplacements pub du lecteur
 *    (adPlacements/adSlots), cache les encarts pub de l'accueil, et passe les pubs
 *    vidéo restantes en accéléré pendant que « Ignorer » est cliqué tout seul.
 *
 * Les vidéos et les pubs vidéo sortent des mêmes serveurs (googlevideo.com) : le
 * filtrage par domaine ne peut pas tout faire, le JavaScript fait le gros du travail.
 * Si YouTube renomme ses classes ou ses régies, tout se met à jour dans ce fichier.
 */
object AdBlock {

    // ---------------- Couche réseau ----------------

    /** Régies pub bloquées, avec tous leurs sous-domaines. */
    private val BLOCKED_HOSTS = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagservices.com",
    )

    /** Chemins qui ne servent qu'aux pubs, quel que soit le domaine. */
    private val BLOCKED_PATHS = listOf(
        "/api/stats/ads",
        "/pagead/",
        "/ptracking",
    )

    /** Vrai si la requête ne sert qu'à la pub ou à sa mesure. */
    fun blocked(url: String?): Boolean {
        val uri = runCatching { URI(url?.lowercase() ?: return false) }.getOrNull() ?: return false
        val host = uri.host ?: return false
        if (host.startsWith("adservice.google.")) return true
        if (BLOCKED_HOSTS.any { host == it || host.endsWith(".$it") }) return true
        val path = uri.path ?: return false
        return BLOCKED_PATHS.any { path.startsWith(it) }
    }

    /** Une requête bloquée reçoit une réponse vide ; les autres passent normalement. */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (!blocked(request.url?.toString())) return null
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
    }

    // ---------------- Couche page (JavaScript injecté) ----------------

    /** À appeler au début et à la fin du chargement de chaque page YouTube. */
    fun inject(web: WebView) = web.evaluateJavascript(JS, null)

    private val JS = """
(function () {
  if (window.__yutoAdBlock) return 'on';
  window.__yutoAdBlock = true;

  // 1) Encarts pub de l'accueil, des recommandations et du lecteur : cachés
  var css = 'ytm-ad-slot-renderer,ytm-promoted-sparkles-web-renderer,ytm-promoted-video-renderer,' +
    'ytm-companion-ad-renderer,ytm-in-feed-ad-layout-renderer,ytm-display-ad-renderer,' +
    'ytm-ad-action-interstitial-renderer,.ytp-ad-module,.ytp-ad-overlay-container,' +
    '.ytp-ad-text-overlay{display:none!important}';
  var style = document.createElement('style');
  style.textContent = css;
  (document.head || document.documentElement).appendChild(style);

  // 2) Emplacements pub retirés des réponses lues par le lecteur
  var parse = JSON.parse;
  JSON.parse = function () {
    var r = parse.apply(this, arguments);
    try { strip(r, 0); } catch (e) {}
    return r;
  };
  function strip(o, depth) {
    if (!o || typeof o !== 'object' || depth > 4) return;
    if (o.adPlacements) o.adPlacements = [];
    if (o.adSlots) o.adSlots = [];
    if (o.playerAds) o.playerAds = [];
    for (var k in o) strip(o[k], depth + 1);
  }

  // 3) Pub vidéo qui passerait quand même : muette, accélérée, "Ignorer" cliqué
  var mutedByUs = false;
  setInterval(function () {
    var v = document.querySelector('video');
    var p = document.querySelector('.html5-video-player,#movie_player');
    var ad = (p && (p.classList.contains('ad-showing') || p.classList.contains('ytp-ad-showing'))) ||
      document.querySelector('.ytp-ad-player-overlay');
    if (v && ad) {
      mutedByUs = true;
      v.muted = true;
      v.playbackRate = 16;
      if (isFinite(v.duration) && v.duration > 0) v.currentTime = v.duration;
    } else if (v && mutedByUs) {
      mutedByUs = false;
      v.muted = false;
      if (v.playbackRate > 4) v.playbackRate = 1;
    }
    var skip = document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,' +
      '.ytp-ad-skip-button-container button,ytm-ad-skip-button-renderer button');
    if (skip) skip.click();
  }, 500);
  return 'on';
})()
""".trimIndent()
}
