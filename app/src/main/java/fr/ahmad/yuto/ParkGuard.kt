package fr.ahmad.yuto

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
 *  - on ne déverrouille qu'après [unlockAfterMs] d'arrêt continu (10 s en mode strict) ;
 *  - sans aucune mesure récente, on reste verrouillé (dans le doute, pas de vidéo).
 *
 * Mode souple (case décochée sur le téléphone) : pour le cas où les capteurs se trompent.
 * L'attente passe à [QUICK_UNLOCK_MS] et l'absence de mesure ne cache plus la vidéo ;
 * une vitesse réellement mesurée la cache toujours.
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
    const val QUICK_UNLOCK_MS = 2_000L

    /** Attente actuelle avant d'afficher la vidéo (selon le réglage strict / souple). */
    @Volatile var unlockAfterMs = UNLOCK_AFTER_MS

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
        val wait = unlockAfterMs
        val (next, since) = step(_state.value, raw, stoppedSince, now, wait)
        stoppedSince = since
        remainingMs = if (raw == Raw.STOPPED && since > 0) (wait - (now - since)).coerceAtLeast(0) else wait
        if (next != _state.value) _state.value = next
    }

    fun reset() {
        carKmh = null; carAt = 0; gpsKmh = null; gpsAt = 0; gpsAccuracy = 999f
        stoppedSince = 0
        remainingMs = unlockAfterMs
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
    /** Rideau baissé ? En mode strict, tout sauf PARKED ; en souple, seulement MOVING. */
    fun shouldLock(state: State, strict: Boolean): Boolean =
        if (strict) state != State.PARKED else state == State.MOVING

    /** Choix explicite de l'utilisateur : « vidéo même en roulant » enlève tout verrou. */
    fun shouldLock(state: State, strict: Boolean, allowDriving: Boolean): Boolean =
        !allowDriving && shouldLock(state, strict)

    fun step(
        current: State,
        raw: Raw,
        stoppedSince: Long,
        now: Long,
        unlockMs: Long = UNLOCK_AFTER_MS,
    ): Pair<State, Long> = when (raw) {
        Raw.MOVING -> State.MOVING to 0L
        Raw.UNKNOWN -> State.UNKNOWN to 0L
        Raw.STOPPED -> {
            val since = if (stoppedSince == 0L) now else stoppedSince
            when {
                current == State.PARKED -> State.PARKED to since
                now - since >= unlockMs -> State.PARKED to since
                // Arrêt trop court (feu rouge, bouchon) : on garde le verrou
                current == State.MOVING -> State.MOVING to since
                else -> State.UNKNOWN to since
            }
        }
    }
}
