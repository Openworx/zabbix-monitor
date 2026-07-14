/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import java.util.concurrent.Executors

class GraphActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var chart: ChartView
    private lateinit var meta: TextView
    private lateinit var itemId: String
    private var valueType = 0
    private var units = ""
    private var rangeButtons: List<Button> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        itemId = intent.getStringExtra("itemId") ?: ""
        valueType = intent.getIntExtra("valueType", 0)
        units = intent.getStringExtra("units") ?: ""

        findViewById<TextView>(R.id.graph_item_name).text = intent.getStringExtra("name")
        meta = findViewById(R.id.graph_meta)
        meta.text = intent.getStringExtra("host")
        chart = findViewById(R.id.chart)

        val ranges = listOf(
            R.id.range_1h to 3600L,
            R.id.range_6h to 6 * 3600L,
            R.id.range_24h to 24 * 3600L,
            R.id.range_7d to 7 * 86400L
        )
        rangeButtons = ranges.map { findViewById(it.first) }
        ranges.forEachIndexed { idx, (id, secs) ->
            findViewById<Button>(id).setOnClickListener {
                highlight(idx)
                load(secs)
            }
        }
        highlight(2)
        load(24 * 3600L)
    }

    private fun highlight(active: Int) {
        rangeButtons.forEachIndexed { i, b ->
            b.setTextColor(if (i == active) Color.parseColor("#7499FF") else Color.parseColor("#9AAAB8"))
        }
    }

    private fun load(rangeSecs: Long) {
        val api = ServerStore.api(this) ?: return
        val till = System.currentTimeMillis() / 1000
        val from = till - rangeSecs
        meta.text = getString(R.string.loading)
        executor.execute {
            try {
                var points = api.history(itemId, valueType, from, till)
                // Downsample for smooth drawing
                if (points.size > 400) {
                    val step = points.size / 400.0
                    points = (0 until 400).map { i -> points[(i * step).toInt()] }
                }
                runOnUiThread {
                    chart.setData(points, units)
                    meta.text = "${intent.getStringExtra("host")} · ${points.size} points" +
                        if (points.isNotEmpty())
                            " · last: ${Ui.formatValue(points.last().second.toString(), units)}"
                        else ""
                }
            } catch (e: Exception) {
                runOnUiThread {
                    chart.setData(emptyList(), units)
                    meta.text = "Error: ${e.message}"
                }
            }
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
