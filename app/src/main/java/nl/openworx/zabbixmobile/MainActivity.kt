/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import nl.openworx.zabbixmobile.Prefs.hideAcknowledged
import nl.openworx.zabbixmobile.Prefs.minSeverity
import nl.openworx.zabbixmobile.Prefs.notifyEnabled
import nl.openworx.zabbixmobile.Prefs.notifyMinSeverity
import nl.openworx.zabbixmobile.Prefs.showSuppressed
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()

    // Problems
    private lateinit var probList: ListView
    private lateinit var probStatus: TextView
    private lateinit var probSearch: EditText
    private lateinit var probSeverity: Spinner
    private var allProblems: List<Problem> = emptyList()
    private var shownProblems: List<Problem> = emptyList()

    // Hosts
    private lateinit var hostList: ListView
    private lateinit var hostStatus: TextView
    private lateinit var hostSearch: EditText
    private lateinit var hostGroup: Spinner
    private var groups: List<Pair<String, String>> = emptyList()
    private var allHosts: List<ZabbixApi.Host> = emptyList()
    private var shownHosts: List<ZabbixApi.Host> = emptyList()
    private var hostsLoaded = false

    private lateinit var pages: List<View>
    private lateinit var tabs: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pages = listOf<View>(
            findViewById(R.id.page_problems),
            findViewById(R.id.page_hosts),
            findViewById(R.id.page_settings)
        )
        tabs = listOf(
            findViewById(R.id.tab_problems),
            findViewById(R.id.tab_hosts),
            findViewById(R.id.tab_settings)
        )
        tabs.forEachIndexed { i, tab -> tab.setOnClickListener { showPage(i) } }

        setupProblemsPage()
        setupHostsPage()
        setupSettingsPage()

        if (ServerStore.active(this) == null) {
            showPage(2)
            startActivity(Intent(this, ServerEditActivity::class.java))
        } else {
            showPage(0)
            loadProblems()
        }
    }

    override fun onResume() {
        super.onResume()
        renderServers()
    }

    private fun showPage(index: Int) {
        pages.forEachIndexed { i, p -> p.visibility = if (i == index) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { i, t ->
            t.setTextColor(if (i == index) Color.parseColor("#7499FF") else Color.parseColor("#9AAAB8"))
        }
        if (index == 1 && !hostsLoaded) loadHosts()
    }

    // ================= Problems =================

    private fun setupProblemsPage() {
        probList = findViewById(R.id.prob_list)
        probStatus = findViewById(R.id.prob_status)
        probSearch = findViewById(R.id.prob_search)
        probSeverity = findViewById(R.id.prob_severity)

        probSeverity.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("All") + Ui.SEVERITY_NAMES.drop(1)
        )
        probSeverity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = filterProblems()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        probSearch.addTextChangedListener(simpleWatcher { filterProblems() })
        findViewById<ImageButton>(R.id.prob_refresh).setOnClickListener { loadProblems() }

        probList.setOnItemClickListener { _, _, pos, _ ->
            val p = shownProblems.getOrNull(pos) ?: return@setOnItemClickListener
            startActivity(
                Intent(this, ProblemDetailActivity::class.java)
                    .putExtra("eventId", p.eventId)
                    .putExtra("triggerId", p.triggerId)
                    .putExtra("name", p.name)
                    .putExtra("severity", p.severity)
                    .putExtra("clock", p.clock)
                    .putExtra("acknowledged", p.acknowledged)
                    .putExtra("host", p.host)
            )
        }
        probList.adapter = problemsAdapter
    }

    private fun loadProblems() {
        val api = ServerStore.api(this) ?: run {
            probStatus.text = getString(R.string.widget_not_configured); return
        }
        probStatus.text = getString(R.string.loading)
        executor.execute {
            try {
                val problems = api.currentProblems(
                    minSeverity = 0,
                    hideAcknowledged = hideAcknowledged,
                    showSuppressed = showSuppressed,
                    limit = 200
                )
                runOnUiThread {
                    allProblems = problems
                    filterProblems()
                }
            } catch (e: Exception) {
                runOnUiThread { probStatus.text = "Error: ${e.message}" }
            }
        }
    }

    private fun filterProblems() {
        val q = probSearch.text.toString().trim().lowercase()
        val sev = probSeverity.selectedItemPosition // 0=all, 1..5 = severity 1..5
        shownProblems = allProblems.filter { p ->
            (sev == 0 || p.severity >= sev) &&
                (q.isEmpty() || p.name.lowercase().contains(q) || p.host.lowercase().contains(q))
        }
        probStatus.text = "${shownProblems.size} problems · ${ServerStore.active(this)?.name ?: ""}"
        problemsAdapter.notifyDataSetChanged()
    }

    private val problemsAdapter = object : BaseAdapter() {
        override fun getCount() = shownProblems.size
        override fun getItem(i: Int) = shownProblems[i]
        override fun getItemId(i: Int) = i.toLong()
        override fun getView(i: Int, convert: View?, parent: ViewGroup?): View {
            val v = convert ?: LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.list_problem, parent, false)
            val p = shownProblems[i]
            v.findViewById<View>(R.id.sev_bar).setBackgroundColor(Problem.colorFor(p.severity))
            v.findViewById<TextView>(R.id.problem_name).text =
                if (p.acknowledged) "✓ ${p.name}" else p.name
            v.findViewById<TextView>(R.id.host_name).text = p.host
            v.findViewById<TextView>(R.id.duration).text = Ui.formatDuration(p.clock)
            return v
        }
    }

    // ================= Hosts =================

    private fun setupHostsPage() {
        hostList = findViewById(R.id.host_list)
        hostStatus = findViewById(R.id.host_status)
        hostSearch = findViewById(R.id.host_search)
        hostGroup = findViewById(R.id.host_group)

        hostGroup.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, listOf(getString(R.string.all_groups))
        )
        hostGroup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (hostsLoaded) loadHostList()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        hostSearch.addTextChangedListener(simpleWatcher { filterHosts() })
        findViewById<ImageButton>(R.id.host_refresh).setOnClickListener { loadHosts() }

        hostList.setOnItemClickListener { _, _, pos, _ ->
            val h = shownHosts.getOrNull(pos) ?: return@setOnItemClickListener
            startActivity(
                Intent(this, HostDetailActivity::class.java)
                    .putExtra("hostId", h.hostId)
                    .putExtra("name", h.name)
                    .putExtra("ip", h.ip)
                    .putExtra("enabled", h.enabled)
                    .putExtra("maintenance", h.inMaintenance)
            )
        }
        hostList.adapter = hostsAdapter
    }

    private fun loadHosts() {
        val api = ServerStore.api(this) ?: run {
            hostStatus.text = getString(R.string.widget_not_configured); return
        }
        hostStatus.text = getString(R.string.loading)
        executor.execute {
            try {
                val g = api.hostGroups()
                runOnUiThread {
                    groups = g
                    val labels = listOf(getString(R.string.all_groups)) + g.map { it.second }
                    hostGroup.adapter = ArrayAdapter(
                        this, android.R.layout.simple_spinner_dropdown_item, labels
                    )
                    hostsLoaded = true
                    loadHostList()
                }
            } catch (e: Exception) {
                runOnUiThread { hostStatus.text = "Error: ${e.message}" }
            }
        }
    }

    private fun loadHostList() {
        val api = ServerStore.api(this) ?: return
        val pos = hostGroup.selectedItemPosition
        val groupId = if (pos <= 0) null else groups.getOrNull(pos - 1)?.first
        hostStatus.text = getString(R.string.loading)
        executor.execute {
            try {
                val hosts = api.hosts(groupId)
                runOnUiThread {
                    allHosts = hosts
                    filterHosts()
                }
            } catch (e: Exception) {
                runOnUiThread { hostStatus.text = "Error: ${e.message}" }
            }
        }
    }

    private fun filterHosts() {
        val q = hostSearch.text.toString().trim().lowercase()
        shownHosts = allHosts.filter {
            q.isEmpty() || it.name.lowercase().contains(q) || it.ip.contains(q)
        }
        hostStatus.text = "${shownHosts.size} hosts"
        hostsAdapter.notifyDataSetChanged()
    }

    private val hostsAdapter = object : BaseAdapter() {
        override fun getCount() = shownHosts.size
        override fun getItem(i: Int) = shownHosts[i]
        override fun getItemId(i: Int) = i.toLong()
        override fun getView(i: Int, convert: View?, parent: ViewGroup?): View {
            val v = convert ?: LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.list_host, parent, false)
            val h = shownHosts[i]
            val dot = v.findViewById<TextView>(R.id.host_dot)
            when {
                !h.enabled -> { dot.text = "●"; dot.setTextColor(Color.parseColor("#66727C")) }
                h.available == 2 -> { dot.text = "●"; dot.setTextColor(Color.parseColor("#E45959")) }
                h.available == 1 -> { dot.text = "●"; dot.setTextColor(Color.parseColor("#59DB8F")) }
                else -> { dot.text = "●"; dot.setTextColor(Color.parseColor("#9AAAB8")) }
            }
            v.findViewById<TextView>(R.id.host_title).text = h.name
            v.findViewById<TextView>(R.id.host_sub).text = h.ip
            val badge = v.findViewById<TextView>(R.id.host_badge)
            when {
                h.inMaintenance -> { badge.text = "🔧 maintenance"; badge.setTextColor(Color.parseColor("#FFC859")) }
                !h.enabled -> { badge.text = "disabled"; badge.setTextColor(Color.parseColor("#66727C")) }
                else -> badge.text = ""
            }
            return v
        }
    }

    // ================= Settings =================

    private fun setupSettingsPage() {
        findViewById<Button>(R.id.btn_add_server).setOnClickListener {
            startActivity(Intent(this, ServerEditActivity::class.java))
        }
        findViewById<Button>(R.id.btn_widget_settings).setOnClickListener {
            startActivity(Intent(this, ConfigActivity::class.java))
        }

        val checkNotify = findViewById<CheckBox>(R.id.check_notify)
        val notifySeverity = findViewById<Spinner>(R.id.notify_severity)
        notifySeverity.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Ui.SEVERITY_NAMES
        )
        checkNotify.isChecked = notifyEnabled
        notifySeverity.setSelection(notifyMinSeverity.coerceIn(0, 5))

        checkNotify.setOnCheckedChangeListener { _, checked ->
            notifyEnabled = checked
            if (checked) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
                }
                RefreshWorker.schedulePeriodic(this)
            }
        }
        notifySeverity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                notifyMinSeverity = pos
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        findViewById<TextView>(R.id.about).text =
            "Openworx Mobile for Zabbix v2.2 — works with Zabbix 6.x/7.x via the JSON-RPC API.\n© Openworx · info@openworx.nl"
    }

    private fun renderServers() {
        val container = findViewById<LinearLayout>(R.id.server_container)
        container.removeAllViews()
        val servers = ServerStore.all(this)
        val activeId = ServerStore.activeId(this)
        servers.forEach { server ->
            val row = LayoutInflater.from(this).inflate(R.layout.list_server, container, false)
            row.findViewById<TextView>(R.id.server_name).text =
                server.name.ifEmpty { "(unnamed)" }
            row.findViewById<TextView>(R.id.server_url).text = server.url
            val radio = row.findViewById<RadioButton>(R.id.server_active)
            radio.isChecked = server.id == activeId
            radio.setOnClickListener {
                ServerStore.activate(this, server.id)
                renderServers()
                hostsLoaded = false
                allProblems = emptyList(); filterProblems()
                RefreshWorker.refreshNow(this)
                loadProblems()
            }
            row.findViewById<Button>(R.id.server_edit).setOnClickListener {
                startActivity(
                    Intent(this, ServerEditActivity::class.java)
                        .putExtra("serverId", server.id)
                )
            }
            container.addView(row)
        }
    }

    private fun simpleWatcher(onChange: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) = onChange()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
