package fr.ahmad.yuto.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.ahmad.yuto.LocalVideo
import fr.ahmad.yuto.R
import fr.ahmad.yuto.Settings

/** Menu : accueil YouTube, plein écran, mode nuit, vidéos du téléphone. */
class MenuScreen(carContext: CarContext, private val renderer: VideoRenderer) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        list.addItem(row(str(R.string.menu_home)) {
            renderer.home()
            screenManager.pop()
        })
        val full = renderer.isFullscreen()
        list.addItem(row(str(if (full) R.string.menu_fullscreen_off else R.string.menu_fullscreen_on)) {
            renderer.setFullscreen(!full)
            screenManager.pop()
        })
        val night = Settings.night(carContext)
        list.addItem(row(str(if (night) R.string.menu_night_off else R.string.menu_night_on)) {
            Settings.setNight(carContext, !night)
            renderer.applyNight()
            screenManager.pop()
        })
        val n = LocalVideo.list(carContext).size
        list.addItem(row(if (n == 0) str(R.string.menu_local_none) else str(R.string.menu_local, n)) {
            if (n > 0) screenManager.push(VideosScreen(carContext, renderer))
        })
        if (renderer.isLocalPlaying()) {
            list.addItem(row(str(R.string.menu_local_stop)) {
                renderer.stopLocal()
                screenManager.pop()
            })
        }
        return ListTemplate.Builder()
            .setTitle(str(R.string.menu_title))
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }

    private fun str(id: Int, vararg a: Any) = carContext.getString(id, *a)

    private fun row(title: String, onClick: () -> Unit) =
        Row.Builder().setTitle(title).setOnClickListener { onClick() }.build()
}

/** Liste des vidéos du téléphone ; la lecture continue avec la suivante et reprend où elle s'était arrêtée. */
class VideosScreen(carContext: CarContext, private val renderer: VideoRenderer) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        LocalVideo.list(carContext).take(MAX).forEachIndexed { i, v ->
            val pos = LocalVideo.position(carContext, v.uri) / 1000
            val b = Row.Builder().setTitle(v.name).setOnClickListener {
                renderer.playLocal(i)
                screenManager.popToRoot()
            }
            if (pos >= 5) b.addText(carContext.getString(R.string.resume_at, pos / 60, pos % 60))
            list.addItem(b.build())
        }
        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.menu_videos_title))
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }

    private companion object {
        const val MAX = 12
    }
}
