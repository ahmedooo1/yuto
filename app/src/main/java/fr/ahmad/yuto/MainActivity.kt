package fr.ahmad.yuto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Côté téléphone : autorisations, connexion YouTube, et comment l'ouvrir dans la voiture. */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var allowButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        allowButton = findViewById(R.id.allowButton)
        allowButton.setOnClickListener { askPermissions() }
        findViewById<Button>(R.id.loginButton).setOnClickListener {
            if (Web.isSignedIn()) {
                Web.signOut { runOnUiThread { refreshLogin() } }
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }
        val strict = findViewById<android.widget.CheckBox>(R.id.strictBox)
        strict.isChecked = Settings.strictLock(this)
        strictHint(strict.isChecked)
        strict.setOnCheckedChangeListener { _, on ->
            Settings.setStrictLock(this, on)
            strictHint(on)
        }
        val driving = findViewById<android.widget.CheckBox>(R.id.drivingBox)
        driving.isChecked = Settings.allowDriving(this)
        drivingHint(driving.isChecked)
        driving.setOnCheckedChangeListener { _, on ->
            Settings.setAllowDriving(this, on)
            drivingHint(on)
        }
        buildSteps(findViewById(R.id.steps))
    }

    override fun onResume() {
        super.onResume()
        refresh()
        refreshLogin()
    }

    private fun strictHint(on: Boolean) {
        findViewById<TextView>(R.id.strictHint).setText(if (on) R.string.strict_on else R.string.strict_off)
    }

    private fun drivingHint(on: Boolean) {
        findViewById<TextView>(R.id.drivingHint).setText(if (on) R.string.driving_on else R.string.driving_off)
    }

    /** Après "Terminé" : on montre clairement si le compte est connecté. */
    private fun refreshLogin() {
        val signed = Web.isSignedIn()
        findViewById<TextView>(R.id.loginStatus).apply {
            setText(if (signed) R.string.login_connected else R.string.login_not_connected)
            setTextColor(ContextCompat.getColor(context, if (signed) R.color.ok else R.color.muted))
        }
        findViewById<Button>(R.id.loginButton).setText(if (signed) R.string.logout_button else R.string.login_button)
    }

    private fun refresh() {
        val ok = ParkGuardService.hasLocation(this)
        statusText.setText(if (ok) R.string.status_ok else R.string.status_missing)
        allowButton.visibility = if (ok && granted(CAR_SPEED)) android.view.View.GONE else android.view.View.VISIBLE
        findViewById<android.view.View>(R.id.brandBar)
            .setBackgroundColor(ContextCompat.getColor(this, if (ok) R.color.lime else R.color.warn))
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        val wanted = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            CAR_SPEED,
            // Recherche vocale depuis l'écran de la voiture
            Manifest.permission.RECORD_AUDIO,
        )
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        val missing = wanted.filterNot { granted(it) }
        if (missing.isEmpty()) refresh() else requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun buildSteps(box: LinearLayout) {
        val steps = listOf(R.string.car_step1, R.string.car_step2, R.string.car_step3)
        steps.forEachIndexed { i, res ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(14), 0, 0)
            }
            val num = TextView(this).apply {
                text = (i + 1).toString()
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.lime))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setBackgroundResource(R.drawable.step_dot)
            }
            val txt = TextView(this).apply {
                setText(res)
                setTextColor(ContextCompat.getColor(context, R.color.text))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.15f)
            }
            row.addView(num, LinearLayout.LayoutParams(dp(30), dp(30)))
            row.addView(txt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
                topMargin = dp(3)
            })
            box.addView(row)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val CAR_SPEED = "com.google.android.gms.permission.CAR_SPEED"
    }
}
