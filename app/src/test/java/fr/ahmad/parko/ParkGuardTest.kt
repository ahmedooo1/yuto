package fr.ahmad.parko

import fr.ahmad.parko.ParkGuard.Raw
import fr.ahmad.parko.ParkGuard.State
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ParkGuardTest {

    @Before fun setUp() = ParkGuard.reset()

    @Test fun carSpeedWinsOverGps() {
        // La voiture dit 0, le GPS dérive à 6 km/h : on croit la voiture
        assertEquals(Raw.STOPPED, ParkGuard.raw(0f, 1_000, 6f, 1_000, 10f, 2_000))
        assertEquals(Raw.MOVING, ParkGuard.raw(12f, 1_000, 0f, 1_000, 10f, 2_000))
    }

    @Test fun staleCarSpeedFallsBackToGps() {
        assertEquals(Raw.MOVING, ParkGuard.raw(0f, 0, 30f, 9_000, 10f, 10_000))
    }

    @Test fun nothingRecentMeansUnknown() {
        assertEquals(Raw.UNKNOWN, ParkGuard.raw(null, 0, null, 0, 999f, 50_000))
        assertEquals(Raw.UNKNOWN, ParkGuard.raw(0f, 0, 0f, 0, 5f, 50_000))
    }

    @Test fun inaccurateGpsAtRestIsNotTrusted() {
        assertEquals(Raw.UNKNOWN, ParkGuard.raw(null, 0, 0f, 1_000, 200f, 1_500))
        // …mais une vitesse annoncée verrouille quand même
        assertEquals(Raw.MOVING, ParkGuard.raw(null, 0, 20f, 1_000, 200f, 1_500))
    }

    @Test fun shortStopKeepsLock() {
        var (s, since) = ParkGuard.step(State.MOVING, Raw.STOPPED, 0, 1_000)
        assertEquals(State.MOVING, s)
        val r = ParkGuard.step(s, Raw.STOPPED, since, 1_000 + ParkGuard.UNLOCK_AFTER_MS - 1)
        s = r.first; since = r.second
        assertEquals(State.MOVING, s)
        assertEquals(State.PARKED, ParkGuard.step(s, Raw.STOPPED, since, 1_000 + ParkGuard.UNLOCK_AFTER_MS).first)
    }

    @Test fun anyMovementLocksAtOnce() {
        assertEquals(State.MOVING, ParkGuard.step(State.PARKED, Raw.MOVING, 500, 99_000).first)
        assertEquals(State.UNKNOWN, ParkGuard.step(State.PARKED, Raw.UNKNOWN, 500, 99_000).first)
    }

    @Test fun fullFlowWithCarSpeed() {
        ParkGuard.onCarSpeed(50f, 0)
        assertEquals(State.MOVING, ParkGuard.state.value)
        ParkGuard.onCarSpeed(0f, 1_000)
        assertEquals(State.MOVING, ParkGuard.state.value)
        ParkGuard.onCarSpeed(0f, 1_000 + ParkGuard.UNLOCK_AFTER_MS)
        assertEquals(State.PARKED, ParkGuard.state.value)
        ParkGuard.onCarSpeed(5f, 20_000)
        assertEquals(State.MOVING, ParkGuard.state.value)
    }
}
