/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class ProblemDetailActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var eventId: String
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_problem_detail)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        eventId = intent.getStringExtra("eventId") ?: ""
        val triggerId = intent.getStringExtra("triggerId") ?: ""
        val name = intent.getStringExtra("name") ?: ""
        val severity = intent.getIntExtra("severity", 0)
        val clock = intent.getLongExtra("clock", 0)
        val acknowledged = intent.getBooleanExtra("acknowledged", false)
        val host = intent.getStringExtra("host") ?: ""

        status = findViewById(R.id.action_status)

        val sevView = findViewById<TextView>(R.id.detail_severity)
        sevView.text = Ui.severityName(severity)
        sevView.setBackgroundColor(Problem.colorFor(severity))
        sevView.setTextColor(Color.parseColor("#1F2A33"))

        findViewById<TextView>(R.id.detail_name).text = name
        val started = SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault())
            .format(Date(clock * 1000))
        findViewById<TextView>(R.id.detail_meta).text = buildString {
            append(host)
            append("\nStarted: $started (${Ui.formatDuration(clock)} ago)")
            if (acknowledged) append("\n✓ Acknowledged")
        }

        findViewById<Button>(R.id.btn_open_web).setOnClickListener {
            val base = ServerStore.active(this)?.url ?: return@setOnClickListener
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("$base/tr_events.php?triggerid=$triggerId&eventid=$eventId")
                )
            )
        }

        val message = findViewById<EditText>(R.id.ack_message)
        val sevSpinner = findViewById<Spinner>(R.id.severity_spinner)
        sevSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Ui.SEVERITY_NAMES
        )
        sevSpinner.setSelection(severity)

        // action bits: 1=close, 2=ack, 4=message, 8=severity
        findViewById<Button>(R.id.btn_ack).setOnClickListener {
            val msg = message.text.toString()
            doAction(if (msg.isBlank()) 2 else 6, msg, null, "Acknowledged")
        }
        findViewById<Button>(R.id.btn_message).setOnClickListener {
            val msg = message.text.toString()
            if (msg.isBlank()) { showStatus("Type a message first.", true); return@setOnClickListener }
            doAction(4, msg, null, "Message posted")
        }
        findViewById<Button>(R.id.btn_severity).setOnClickListener {
            doAction(8, null, sevSpinner.selectedItemPosition, "Severity changed")
        }
        findViewById<Button>(R.id.btn_close_problem).setOnClickListener {
            val msg = message.text.toString()
            doAction(if (msg.isBlank()) 1 else 5, msg, null, "Problem closed")
        }

        loadAcks()
    }

    private fun doAction(bits: Int, msg: String?, newSeverity: Int?, successText: String) {
        val api = ServerStore.api(this) ?: return
        showStatus("Working…", false)
        executor.execute {
            try {
                api.eventAction(eventId, bits, msg, newSeverity)
                runOnUiThread {
                    showStatus("✓ $successText", false)
                    findViewById<EditText>(R.id.ack_message).setText("")
                    loadAcks()
                    RefreshWorker.refreshNow(this)
                }
            } catch (e: Exception) {
                runOnUiThread { showStatus("✗ ${e.message}", true) }
            }
        }
    }

    private fun loadAcks() {
        val api = ServerStore.api(this) ?: return
        executor.execute {
            try {
                val acks = api.acknowledges(eventId)
                runOnUiThread {
                    val container = findViewById<LinearLayout>(R.id.ack_container)
                    container.removeAllViews()
                    if (acks.isEmpty()) {
                        container.addView(TextView(this).apply {
                            text = getString(R.string.no_data)
                            setTextColor(Color.parseColor("#9AAAB8"))
                        })
                        return@runOnUiThread
                    }
                    val fmt = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
                    acks.forEach { a ->
                        container.addView(TextView(this).apply {
                            val actions = mutableListOf<String>()
                            if (a.action and 2 != 0) actions.add("acknowledged")
                            if (a.action and 1 != 0) actions.add("closed")
                            if (a.action and 8 != 0) actions.add(
                                "severity ${Ui.severityName(a.oldSeverity)} → ${Ui.severityName(a.newSeverity)}"
                            )
                            if (a.action and 16 != 0) actions.add("unacknowledged")
                            val head = "${a.user} · ${fmt.format(Date(a.clock * 1000))}" +
                                if (actions.isNotEmpty()) " · ${actions.joinToString(", ")}" else ""
                            text = if (a.message.isNotBlank()) "$head\n${a.message}" else head
                            setTextColor(Color.parseColor("#F2F5F7"))
                            textSize = 13f
                            setPadding(0, 14, 0, 14)
                        })
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun showStatus(msg: String, error: Boolean) {
        status.text = msg
        status.setTextColor(if (error) Color.parseColor("#FF8A80") else Color.parseColor("#B9F6CA"))
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

}
