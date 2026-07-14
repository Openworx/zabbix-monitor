/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import nl.openworx.zabbixmobile.Prefs.hasSeenEvents
import nl.openworx.zabbixmobile.Prefs.knownEventIds
import nl.openworx.zabbixmobile.Prefs.notifyEnabled
import nl.openworx.zabbixmobile.Prefs.notifyMinSeverity

/** Notifies about new problems (based on polling by RefreshWorker). */
object Notifier {
    private const val CHANNEL = "zabbix_problems"

    fun processProblems(ctx: Context, problems: List<Problem>) {
        val currentIds = problems.map { it.eventId }.toSet()

        if (!ctx.hasSeenEvents) {
            // First run: only remember, do not spam with pre-existing problems
            ctx.knownEventIds = currentIds
            ctx.hasSeenEvents = true
            return
        }

        val known = ctx.knownEventIds
        val fresh = problems.filter {
            it.eventId !in known && it.severity >= ctx.notifyMinSeverity
        }
        ctx.knownEventIds = currentIds

        if (!ctx.notifyEnabled || fresh.isEmpty()) return

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL, "Zabbix problems", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notifications for new Zabbix problems" }
        )

        val contentIntent = PendingIntent.getActivity(
            ctx, 100,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val worst = fresh.maxByOrNull { it.severity }!!
        val title = if (fresh.size == 1) worst.name
        else "${fresh.size} new Zabbix problems"
        val text = if (fresh.size == 1) "${worst.host} · ${Ui.severityName(worst.severity)}"
        else fresh.take(3).joinToString("\n") { "${Ui.severityName(it.severity)}: ${it.name}" }

        val notification = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentText(text.lineSequence().first())
            .setColor(Problem.colorFor(worst.severity))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        try {
            nm.notify(worst.eventId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Notification permission denied
        }
    }
}
