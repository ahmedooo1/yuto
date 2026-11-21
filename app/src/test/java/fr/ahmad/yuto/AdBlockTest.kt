package fr.ahmad.yuto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockTest {

    @Test fun adNetworksAreBlocked() {
        assertTrue(AdBlock.blocked("https://googleads.g.doubleclick.net/pagead/ads?x=1"))
        assertTrue(AdBlock.blocked("https://ad.doubleclick.net/ddm/activity/"))
        assertTrue(AdBlock.blocked("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(AdBlock.blocked("https://www.googleadservices.com/pagead/conversion.js"))
        assertTrue(AdBlock.blocked("https://www.googletagservices.com/tag/js/gpt.js"))
        assertTrue(AdBlock.blocked("https://adservice.google.fr/adsid/integrator.js"))
        assertTrue(AdBlock.blocked("https://adservice.google.com/adsid/integrator.js"))
    }

    @Test fun youtubeAdEndpointsAreBlocked() {
        assertTrue(AdBlock.blocked("https://m.youtube.com/api/stats/ads?x=1"))
        assertTrue(AdBlock.blocked("https://www.youtube.com/api/stats/ads?x=1"))
        assertTrue(AdBlock.blocked("https://m.youtube.com/ptracking?x=1"))
        assertTrue(AdBlock.blocked("https://www.youtube.com/pagead/1p-user-list/123/"))
    }

    @Test fun normalPlaybackIsNotBlocked() {
        assertFalse(AdBlock.blocked("https://m.youtube.com/"))
        assertFalse(AdBlock.blocked("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(AdBlock.blocked("https://m.youtube.com/youtubei/v1/player?x=1"))
        assertFalse(AdBlock.blocked("https://m.youtube.com/api/stats/watchtime?x=1"))
        assertFalse(AdBlock.blocked("https://rr1---sn-25ge7nsd.googlevideo.com/videoplayback?x=1"))
        assertFalse(AdBlock.blocked("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"))
        assertFalse(AdBlock.blocked("https://www.gstatic.com/youtube/img/branding/favicon/favicon_144x144.png"))
        assertFalse(AdBlock.blocked(null))
        assertFalse(AdBlock.blocked("not a url"))
    }
}
