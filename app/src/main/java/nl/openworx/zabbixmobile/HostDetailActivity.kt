/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import java.util.concurrent.Executors

class HostDetailActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private var allItems: List<ZabbixApi.Item> = emptyList()
    private var shownItems: List<ZabbixApi.Item> = emptyList()
    private lateinit var statusView: TextView
    private lateinit var hostId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host_detail)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        hostId = intent.getStringExtra("hostId") ?: ""
        val name = intent.getStringExtra("name") ?: ""
        val ip = intent.getStringExtra("ip") ?: ""
        val enabled = intent.getBooleanExtra("enabled", true)
        val maintenance = intent.getBooleanExtra("maintenance", false)

        findViewById<TextView>(R.id.host_header).text = name
        findViewById<TextView>(R.id.host_meta).text = buildString {
            append(ip)
            if (!enabled) append(" · disabled")
            if (maintenance) append(" · 🔧 in maintenance")
        }
        statusView = findViewById(R.id.items_status)

        val search = findViewById<EditText>(R.id.item_search)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = filter()
        })

        val list = findViewById<ListView>(R.id.items_list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val item = shownItems.getOrNull(pos) ?: return@setOnItemClickListener
            if (!item.isNumeric) return@setOnItemClickListener
            startActivity(
                Intent(this, GraphActivity::class.java)
                    .putExtra("itemId", item.itemId)
                    .putExtra("name", item.name)
                    .putExtra("units", item.units)
                    .putExtra("valueType", item.valueType)
                    .putExtra("host", name)
            )
        }

        loadItems()
    }

    private fun loadItems() {
        val api = ServerStore.api(this) ?: return
        statusView.text = getString(R.string.loading)
        executor.execute {
            try {
                val items = api.items(hostId)
                runOnUiThread {
                    allItems = items
                    filter()
                }
            } catch (e: Exception) {
                runOnUiThread { statusView.text = "Error: ${e.message}" }
            }
        }
    }

    private fun filter() {
        val q = findViewById<EditText>(R.id.item_search).text.toString().trim().lowercase()
        shownItems = allItems.filter {
            q.isEmpty() || it.name.lowercase().contains(q) || it.key.lowercase().contains(q)
        }
        statusView.text = "${shownItems.size} items · tap a numeric item for a graph"
        adapter.notifyDataSetChanged()
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shownItems.size
        override fun getItem(i: Int) = shownItems[i]
        override fun getItemId(i: Int) = i.toLong()
        override fun getView(i: Int, convert: View?, parent: ViewGroup?): View {
            val v = convert ?: LayoutInflater.from(this@HostDetailActivity)
                .inflate(R.layout.list_item_data, parent, false)
            val item = shownItems[i]
            v.findViewById<TextView>(R.id.item_name).text = item.name
            v.findViewById<TextView>(R.id.item_sub).text =
                item.key + if (item.isNumeric) "  📈" else ""
            v.findViewById<TextView>(R.id.item_value).text =
                if (item.lastValue.isEmpty()) "—"
                else Ui.formatValue(item.lastValue, item.units)
            return v
        }
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
