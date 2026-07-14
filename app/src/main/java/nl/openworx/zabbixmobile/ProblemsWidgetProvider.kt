/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import nl.openworx.zabbixmobile.Prefs.cachedProblemsJson
import nl.openworx.zabbixmobile.Prefs.lastError
import nl.openworx.zabbixmobile.Prefs.lastUpdateMillis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProblemsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(ctx, mgr, it) }
        // Also fetch fresh data on a system-initiated update
        if (Prefs.isConfigured(ctx)) RefreshWorker.refreshNow(ctx)
    }

    override fun onEnabled(ctx: Context) {
        if (Prefs.isConfigured(ctx)) RefreshWorker.schedulePeriodic(ctx)
    }

    override fun onDisabled(ctx: Context) {
        RefreshWorker.cancelAll(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACTION_REFRESH -> RefreshWorker.refreshNow(ctx)
            Intent.ACTION_BOOT_COMPLETED -> {
                // Re-establish the refresh schedule after a reboot
                if (Prefs.isConfigured(ctx)) {
                    RefreshWorker.schedulePeriodic(ctx)
                    RefreshWorker.refreshNow(ctx)
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "nl.openworx.zabbixmobile.ACTION_REFRESH"

        fun updateAllWidgets(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ProblemsWidgetProvider::class.java))
            ids.forEach { id ->
                updateWidget(ctx, mgr, id)
                mgr.notifyAppWidgetViewDataChanged(id, R.id.list)
            }
        }

        fun updateWidget(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_problems)

            // List adapter
            val svcIntent = Intent(ctx, ProblemsWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.list, svcIntent)
            views.setEmptyView(R.id.list, R.id.empty)

            // Summary in the header
            views.setTextViewText(R.id.summary, buildSummary(ctx))
            views.setTextViewText(
                R.id.empty,
                when {
                    !Prefs.isConfigured(ctx) -> ctx.getString(R.string.widget_not_configured)
                    ctx.lastError.isNotEmpty() -> ctx.getString(R.string.widget_error)
                    else -> ctx.getString(R.string.widget_empty)
                }
            )

            // ⟳ button
            val refreshIntent = Intent(ctx, ProblemsWidgetProvider::class.java)
                .setAction(ACTION_REFRESH)
            views.setOnClickPendingIntent(
                R.id.btn_refresh,
                PendingIntent.getBroadcast(
                    ctx, 0, refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Title/header and empty state → open the app
            val appIntent = Intent(ctx, MainActivity::class.java)
            views.setOnClickPendingIntent(
                R.id.title,
                PendingIntent.getActivity(
                    ctx, 1, appIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            views.setOnClickPendingIntent(
                R.id.empty,
                PendingIntent.getActivity(
                    ctx, 2, appIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Click template for list items → open the problem detail screen
            // with MainActivity underneath, so Back leads to the full app.
            // The fill-in intent is applied to the LAST intent in the array.
            // Explicit intents: Android 14+ forbids mutable PendingIntents
            // without a target component.
            val stack = arrayOf(
                Intent(ctx, MainActivity::class.java),
                Intent(ctx, ProblemDetailActivity::class.java)
            )
            views.setPendingIntentTemplate(
                R.id.list,
                PendingIntent.getActivities(
                    ctx, 3, stack,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )

            mgr.updateAppWidget(widgetId, views)
        }

        private fun buildSummary(ctx: Context): CharSequence {
            val problems = Problem.listFromJson(ctx.cachedProblemsJson)
            val sb = SpannableStringBuilder()

            if (problems.isEmpty()) {
                sb.append("0")
            } else {
                // Counts per severity, high → low, in Zabbix colors
                val counts = problems.groupingBy { it.severity }.eachCount()
                (5 downTo 0).forEach { sev ->
                    val n = counts[sev] ?: return@forEach
                    if (sb.isNotEmpty()) sb.append("  ")
                    val start = sb.length
                    sb.append("●$n")
                    sb.setSpan(
                        ForegroundColorSpan(Problem.colorFor(sev)),
                        start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            val ts = ctx.lastUpdateMillis
            if (ts > 0) {
                sb.append("  ·  ")
                sb.append(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts)))
            }
            if (ctx.lastError.isNotEmpty()) sb.append("  ⚠")
            return sb
        }
    }
}
