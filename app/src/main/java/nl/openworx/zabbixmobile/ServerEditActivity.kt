/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import java.util.concurrent.Executors

class ServerEditActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var server: Server
    private var isNew = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_edit)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val serverId = intent.getStringExtra("serverId")
        server = ServerStore.all(this).firstOrNull { it.id == serverId }
            ?: ServerStore.newServer().also { isNew = true }

        val name = findViewById<EditText>(R.id.srv_name)
        val url = findViewById<EditText>(R.id.srv_url)
        val token = findViewById<EditText>(R.id.srv_token)
        val ssl = findViewById<CheckBox>(R.id.srv_ignore_ssl)
        val status = findViewById<TextView>(R.id.srv_status)

        name.setText(server.name)
        url.setText(server.url)
        token.setText(server.token)
        ssl.isChecked = server.ignoreSsl

        findViewById<Button>(R.id.srv_delete).apply {
            if (isNew) visibility = android.view.View.GONE
            setOnClickListener {
                ServerStore.delete(this@ServerEditActivity, server.id)
                RefreshWorker.refreshNow(this@ServerEditActivity)
                finish()
            }
        }

        findViewById<Button>(R.id.srv_test).setOnClickListener {
            val u = url.text.toString().trim()
            val t = token.text.toString().trim()
            if (u.isEmpty() || t.isEmpty()) {
                show(status, "Enter URL and API token.", true); return@setOnClickListener
            }
            show(status, "Testing…", false)
            executor.execute {
                try {
                    val api = ZabbixApi(u, t, ssl.isChecked)
                    val version = api.apiVersion()
                    runOnUiThread { show(status, "✓ Connected to Zabbix $version", false) }
                } catch (e: Exception) {
                    runOnUiThread { show(status, "✗ ${e.message}", true) }
                }
            }
        }

        findViewById<Button>(R.id.srv_save).setOnClickListener {
            val u = url.text.toString().trim().trimEnd('/')
            val t = token.text.toString().trim()
            if (u.isEmpty() || t.isEmpty()) {
                show(status, "Enter URL and API token.", true); return@setOnClickListener
            }
            server.name = name.text.toString().trim().ifEmpty { u.removePrefix("https://").removePrefix("http://") }
            server.url = u
            server.token = t
            server.ignoreSsl = ssl.isChecked
            ServerStore.save(this, server)
            RefreshWorker.schedulePeriodic(this)
            RefreshWorker.refreshNow(this)
            finish()
        }
    }

    private fun show(v: TextView, msg: String, error: Boolean) {
        v.text = msg
        v.setTextColor(if (error) Color.parseColor("#FF8A80") else Color.parseColor("#B9F6CA"))
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
