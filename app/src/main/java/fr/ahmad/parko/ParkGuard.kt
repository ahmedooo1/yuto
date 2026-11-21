package fr.ahmad.parko

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Verrou de stationnement : la vidéo ne s'affiche que voiture arrêtée.
 *
 * Deux sources, dans cet ordre :
 *  1. la vitesse de la voiture transmise par Android Auto (la plus fiable) ;
 *  2. la vitesse GPS du téléphone, en secours.
 *
 * Règles :
 *  - le moindre mouvement verrouille immédiatement ;
 *  - on ne déverrouille qu'après [UNLOCK_AFTER_MS] d'arrêt continu ;
 *  - sans aucune mesure récente, on reste verrouillé (dans le doute, pas de vidéo).
 */
object ParkGuard {

    enum class State { PARKED, MOVING, UNKNOWN }

    /** Ce que disent les capteurs à un instant donné, sans tenir compte de la durée. */
    enum class Raw { STOPPED, MOVING, UNKNOWN }

    const val CAR_MOVING_KMH = 3f
    const val GPS_MOVING_KMH = 4f
    const val CAR_FRESH_MS = 3_000L
    const val GPS_FRESH_MS = 5_000L
    const val GPS_MAX_ACCURACY_M = 60f
    const val UNLOCK_AFTER_MS = 10_000L

    private val _state = MutableStateFlow(State.UNKNOWN)
    val state: StateFlow<State> = _state

    @Volatile private var carKmh: Float? = null
    @Volatile private var carAt = 0L
    @Volatile private var gpsKmh: Float? = null
    @Volatile private var gpsAt = 0L
    @Volatile private var gpsAccuracy = 999f
    private var stoppedSince = 0L

    /** Combien de temps encore avant d'afficher la vidéo (pour le compte à rebours). */
    @Volatile var remainingMs = UNLOCK_AFTER_MS
        private set

    fun onCarSpeed(kmh: Float, now: Long = System.currentTimeMillis()) {
        carKmh = kmh
        carAt = now
        tick(now)
    }

    fun onGps(kmh: Float?, accuracyM: Float, now: Long = System.currentTimeMillis()) {
        gpsKmh = kmh
        gpsAccuracy = accuracyM
        gpsAt = now
        tick(now)
    }

    /** À appeler régulièrement : une mesure trop vieille ne compte plus. */
    @Synchronized
    fun tick(now: Long = System.currentTimeMillis()) {
        val raw = raw(carKmh, carAt, gpsKmh, gpsAt, gpsAccuracy, now)
        val (next, since) = step(_state.value, raw, stoppedSince, now)
        stoppedSince = since
        remainingMs = if (raw == Raw.STOPPED && since > 0) (UNLOCK_AFTER_MS - (now - since)).coerceAtLeast(0) else UNLOCK_AFTER_MS
        if (next != _state.value) _state.value = next
    }

    fun reset() {
        carKmh = null; carAt = 0; gpsKmh = null; gpsAt = 0; gpsAccuracy = 999f
        stoppedSince = 0
        remainingMs = UNLOCK_AFTER_MS
        _state.value = State.UNKNOWN
    }

    // ---------- Logique pure (testée) ----------

    fun raw(carKmh: Float?, carAt: Long, gpsKmh: Float?, gpsAt: Long, gpsAccuracy: Float, now: Long): Raw {
        if (carKmh != null && now - carAt <= CAR_FRESH_MS) {
            return if (carKmh >= CAR_MOVING_KMH) Raw.MOVING else Raw.STOPPED
        }
        if (gpsKmh != null && now - gpsAt <= GPS_FRESH_MS) {
            // Un GPS imprécis qui annonce de la vitesse : on la croit (sécurité)
            if (gpsKmh >= GPS_MOVING_KMH) return Raw.MOVING
            if (gpsAccuracy <= GPS_MAX_ACCURACY_M) return Raw.STOPPED
        }
        return Raw.UNKNOWN
    }

    /** @return nouvel état + début de l'arrêt en cours (0 si pas d'arrêt). */
    fun step(current: State, raw: Raw, stoppedSince: Long, now: Long): Pair<State, Long> = when (raw) {
        Raw.MOVING -> State.MOVING to 0L
        Raw.UNKNOWN -> State.UNKNOWN to 0L
        Raw.STOPPED -> {
            val since = if (stoppedSince == 0L) now else stoppedSince
            when {
                current == State.PARKED -> State.PARKED to since
                now - since >= UNLOCK_AFTER_MS -> State.PARKED to since
                // Arrêt trop court (feu rouge, bouchon) : on garde le verrou
                current == State.MOVING -> State.MOVING to since
                else -> State.UNKNOWN to since
            }
        }
    }
}
