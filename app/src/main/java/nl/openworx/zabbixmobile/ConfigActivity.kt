/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import nl.openworx.zabbixmobile.Prefs.compactMode
import nl.openworx.zabbixmobile.Prefs.hideAcknowledged
import nl.openworx.zabbixmobile.Prefs.intervalMinutes
import nl.openworx.zabbixmobile.Prefs.minSeverity
import nl.openworx.zabbixmobile.Prefs.showSuppressed

/** Widget settings (servers are managed in the main app). */
class ConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val intervals = listOf(1, 5, 10, 15, 30, 60)
    private val intervalLabels = listOf(
        "1 minute (highest battery use)", "5 minutes", "10 minutes",
        "15 minutes", "30 minutes", "60 minutes"
    )
    private val severityLabels = listOf(
        "All", "Information and higher", "Warning and higher",
        "Average and higher", "High and higher", "Disaster only"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED)

        val checkHideAcked = findViewById<CheckBox>(R.id.check_hide_acked)
        val checkShowSuppressed = findViewById<CheckBox>(R.id.check_show_suppressed)
        val checkCompact = findViewById<CheckBox>(R.id.check_compact)
        val spinInterval = findViewById<Spinner>(R.id.spinner_interval)
        val spinSeverity = findViewById<Spinner>(R.id.spinner_severity)
        val status = findViewById<TextView>(R.id.status)

        spinInterval.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, intervalLabels
        )
        spinSeverity.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, severityLabels
        )

        checkHideAcked.isChecked = hideAcknowledged
        checkShowSuppressed.isChecked = showSuppressed
        checkCompact.isChecked = compactMode
        spinInterval.setSelection(
            intervals.indexOf(intervalMinutes).let { if (it < 0) intervals.indexOf(15) else it }
        )
        spinSeverity.setSelection(minSeverity.coerceIn(0, 5))

        findViewById<Button>(R.id.btn_manage_servers).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            if (ServerStore.active(this) == null) {
                status.text = "Add a server first via “Manage servers”."
                status.setTextColor(Color.parseColor("#FF8A80"))
                return@setOnClickListener
            }
            hideAcknowledged = checkHideAcked.isChecked
            showSuppressed = checkShowSuppressed.isChecked
            compactMode = checkCompact.isChecked
            intervalMinutes = intervals[spinInterval.selectedItemPosition]
            minSeverity = spinSeverity.selectedItemPosition

            RefreshWorker.schedulePeriodic(this)
            RefreshWorker.refreshNow(this)

            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                ProblemsWidgetProvider.updateWidget(
                    this, AppWidgetManager.getInstance(this), widgetId
                )
                setResult(
                    RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                )
            } else {
                ProblemsWidgetProvider.updateAllWidgets(this)
            }
            finish()
        }

        updateServerInfo()
    }

    override fun onResume() {
        super.onResume()
        updateServerInfo()
    }

    private fun updateServerInfo() {
        val active = ServerStore.active(this)
        findViewById<TextView>(R.id.active_server_info).text =
            if (active != null) "Active server: ${active.name} (${active.url})"
            else "No server configured yet."
    }
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

}
