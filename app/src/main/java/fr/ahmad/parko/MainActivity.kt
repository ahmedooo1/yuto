package fr.ahmad.parko

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
            startActivity(Intent(this, LoginActivity::class.java))
        }
        buildSteps(findViewById(R.id.steps))
    }

    override fun onResume() {
        super.onResume()
        refresh()
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
