/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import nl.openworx.zabbixmobile.Prefs.cachedProblemsJson
import nl.openworx.zabbixmobile.Prefs.compactMode

class ProblemsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ProblemsFactory(applicationContext)
}

class ProblemsFactory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {

    private var problems: List<Problem> = emptyList()
    private var compact: Boolean = true

    override fun onCreate() {}

    override fun onDataSetChanged() {
        problems = Problem.listFromJson(ctx.cachedProblemsJson)
        compact = ctx.compactMode
    }

    override fun onDestroy() {}

    override fun getCount(): Int = problems.size

    override fun getViewAt(position: Int): RemoteViews {
        val p = problems[position]
        val layout = if (compact) R.layout.widget_item_compact else R.layout.widget_item
        val rv = RemoteViews(ctx.packageName, layout)

        rv.setInt(R.id.sev_bar, "setBackgroundColor", Problem.colorFor(p.severity))
        val ack = if (p.acknowledged) "✓ " else ""
        if (compact) {
            // Single line: host: problem
            val label = if (p.host.isNotEmpty()) "$ack${p.host}: ${p.name}" else "$ack${p.name}"
            rv.setTextViewText(R.id.problem_name, label)
        } else {
            rv.setTextViewText(R.id.problem_name, "$ack${p.name}")
            rv.setTextViewText(R.id.host_name, p.host)
        }
        rv.setTextViewText(R.id.duration, formatDuration(p.clock))

        // Tap → open the problem detail screen in the app
        val fillIn = Intent()
            .putExtra("eventId", p.eventId)
            .putExtra("triggerId", p.triggerId)
            .putExtra("name", p.name)
            .putExtra("severity", p.severity)
            .putExtra("clock", p.clock)
            .putExtra("acknowledged", p.acknowledged)
            .putExtra("host", p.host)
        rv.setOnClickFillInIntent(R.id.item_root, fillIn)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 2
    override fun getItemId(position: Int): Long =
        problems.getOrNull(position)?.eventId?.toLongOrNull() ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private fun formatDuration(clock: Long): String {
        if (clock <= 0) return ""
        val secs = (System.currentTimeMillis() / 1000 - clock).coerceAtLeast(0)
        return when {
            secs < 60 -> "${secs}s"
            secs < 3600 -> "${secs / 60}m"
            secs < 86400 -> "${secs / 3600}h ${(secs % 3600) / 60}m"
            else -> "${secs / 86400}d ${(secs % 86400) / 3600}h"
        }
    }
}
