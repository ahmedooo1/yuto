package fr.ahmad.yuto.car

import android.content.Intent
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.validation.HostValidator
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import fr.ahmad.yuto.ParkGuard
import fr.ahmad.yuto.ParkGuardService
import fr.ahmad.yuto.R
import fr.ahmad.yuto.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Yuto sur l'écran de la voiture (Android Auto). */
class YutoCarAppService : CarAppService() {

    // Appli installée hors Play Store : on accepte tous les hôtes Android Auto
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onCreateSession(): Session = YutoSession()
}

class YutoSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = VideoScreen(carContext)
}

class VideoScreen(carContext: CarContext) : Screen(carContext) {

    private val renderer = VideoRenderer(carContext)
    private var state = ParkGuard.State.UNKNOWN
    private var carSpeedOk = false

    private val speedListener = OnCarDataAvailableListener<Speed> { s ->
        val v = s.rawSpeedMetersPerSecond
        if (v.status == CarValue.STATUS_SUCCESS) {
            v.value?.let { ParkGuard.onCarSpeed(kotlin.math.abs(it) * 3.6f) }
        }
    }

    init {
        ParkGuard.reset()
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
        listenCarSpeed()
        ParkGuardService.start(carContext)

        lifecycleScope.launch {
            ParkGuard.state.collect { st ->
                val changed = st != state
                state = st
                pushLock()
                if (changed) invalidate()
            }
        }
        // Les mesures vieillissent même sans nouvelle donnée ; et le compte à rebours avance
        lifecycleScope.launch {
            while (true) {
                ParkGuard.tick()
                pushLock()
                delay(500)
            }
        }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (carSpeedOk) {
                    runCatching {
                        carContext.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(speedListener)
                    }
                }
                ParkGuardService.stop(carContext)
                renderer.release()
            }
        })
    }

    /** Vitesse réelle de la voiture, si Android Auto la transmet. */
    private fun listenCarSpeed() {
        runCatching {
            val hw = carContext.getCarService(CarHardwareManager::class.java)
            hw.carInfo.addSpeedListener(ContextCompat.getMainExecutor(carContext), speedListener)
            carSpeedOk = true
        }.onFailure { Log.i(TAG, "car speed not available: ${it.message}") }
    }

    /** Vidéo visible ? (selon l'état et la case "verrou strict" du téléphone) */
    private fun videoShown() = !ParkGuard.shouldLock(state, Settings.strictLock(carContext))
    private var shownInTemplate: Boolean? = null

    private fun pushLock() {
        val st = state
        val strict = Settings.strictLock(carContext)
        val wait = if (strict) ParkGuard.UNLOCK_AFTER_MS else ParkGuard.QUICK_UNLOCK_MS
        ParkGuard.unlockAfterMs = wait
        val locked = ParkGuard.shouldLock(st, strict)
        val waiting = ParkGuard.remainingMs.coerceAtMost(wait)
        val (title, sub) = when (st) {
            ParkGuard.State.PARKED -> "" to ""
            ParkGuard.State.MOVING ->
                if (waiting < wait) {
                    str(R.string.lock_stopped) to str(R.string.lock_countdown, ((waiting + 999) / 1000).toInt())
                } else str(R.string.lock_driving) to str(R.string.lock_driving_sub)
            ParkGuard.State.UNKNOWN ->
                if (waiting < wait) {
                    str(R.string.lock_stopped) to str(R.string.lock_countdown, ((waiting + 999) / 1000).toInt())
                } else str(R.string.lock_checking) to str(
                    if (ParkGuardService.hasLocation(carContext)) R.string.lock_checking_sub else R.string.lock_no_permission
                )
        }
        val progress = if (locked && waiting < wait) 1f - waiting.toFloat() / wait else -1f
        renderer.setLock(locked, title, sub, progress)
        // Les boutons de la voiture suivent (recherche, défilement)
        if (shownInTemplate != null && shownInTemplate != !locked) invalidate()
    }

    private fun str(id: Int, vararg args: Any) = carContext.getString(id, *args)

    private fun icon(res: Int) =
        CarIcon.Builder(IconCompat.createWithResource(carContext, res)).setTint(CarColor.DEFAULT).build()

    override fun onGetTemplate(): Template {
        val parked = videoShown()
        shownInTemplate = parked

        val actions = ActionStrip.Builder()
            .addAction(Action.Builder().setIcon(icon(R.drawable.ic_search)).setOnClickListener {
                if (videoShown()) screenManager.push(SearchScreen(carContext, renderer))
                else toast(R.string.only_parked)
            }.build())
            .addAction(Action.Builder().setIcon(icon(R.drawable.ic_back)).setOnClickListener {
                renderer.back()
            }.build())
            .addAction(Action.Builder().setIcon(icon(R.drawable.ic_home)).setOnClickListener {
                renderer.home()
            }.build())
            .addAction(Action.Builder().setIcon(icon(R.drawable.ic_play_pause))
                .setOnClickListener { renderer.playPause() }.build())
            .build()

        val b = NavigationTemplate.Builder().setActionStrip(actions)

        // Boutons sur le côté : défilement de la page (glisser marche aussi en mode déplacement)
        val side = ActionStrip.Builder()
        if (parked) {
            side.addAction(Action.PAN)
            side.addAction(Action.Builder().setIcon(icon(R.drawable.ic_up)).setOnClickListener { renderer.scrollPage(-1) }.build())
            side.addAction(Action.Builder().setIcon(icon(R.drawable.ic_down)).setOnClickListener { renderer.scrollPage(+1) }.build())
        }
        side.addAction(Action.Builder().setIcon(icon(R.drawable.ic_replay10)).setOnClickListener { renderer.seek(-10) }.build())
        b.setMapActionStrip(side.build())
        if (parked) b.setPanModeListener { }
        return b.build()
    }

    private fun toast(id: Int) = runCatching { CarToast.makeText(carContext, str(id), CarToast.LENGTH_LONG).show() }

    private companion object {
        const val TAG = "YutoScreen"
    }
}

/** Recherche YouTube avec le clavier de la voiture (Android Auto le permet à l'arrêt). */
class SearchScreen(carContext: CarContext, private val renderer: VideoRenderer) : Screen(carContext) {
    override fun onGetTemplate(): Template =
        SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchSubmitted(searchText: String) {
                renderer.search(searchText)
                screenManager.pop()
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(R.string.search_hint))
            .setShowKeyboardByDefault(true)
            .setItemList(ItemList.Builder().build())
            .build()
}
