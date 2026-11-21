package fr.ahmad.yuto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorGeometryTest {
    @Test fun portraitIsContainedInCar() {
        assertEquals(405 to 720, MirrorGeometry.fit(1280, 720, 1080, 1920))
    }
    @Test fun landscapeUsesAllAvailableWidth() {
        assertEquals(1280 to 720, MirrorGeometry.fit(1280, 720, 1920, 1080))
    }
    @Test fun rotationKeepsAspectRatio() {
        assertEquals(720 to 405, MirrorGeometry.fit(720, 1280, 1920, 1080))
    }
    @Test fun invalidDimensionsHaveSafeFallback() {
        assertEquals(1 to 1, MirrorGeometry.fit(0, 720, 1080, 1920))
        assertEquals(1 to 1, MirrorGeometry.fit(1280, 720, -1, 0))
    }
    @Test fun roundingNeverOverflowsCarBounds() {
        for (w in listOf(1, 101, 801, 1280)) for (h in listOf(1, 99, 480, 720)) {
            val (a, b) = MirrorGeometry.fit(w, h, 1081, 1921)
            assertTrue(a in 1..w && b in 1..h)
        }
    }
}
