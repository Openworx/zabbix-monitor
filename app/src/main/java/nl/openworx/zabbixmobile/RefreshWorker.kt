/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import nl.openworx.zabbixmobile.Prefs.apiToken
import nl.openworx.zabbixmobile.Prefs.cachedProblemsJson
import nl.openworx.zabbixmobile.Prefs.hideAcknowledged
import nl.openworx.zabbixmobile.Prefs.ignoreSsl
import nl.openworx.zabbixmobile.Prefs.intervalMinutes
import nl.openworx.zabbixmobile.Prefs.lastError
import nl.openworx.zabbixmobile.Prefs.lastUpdateMillis
import nl.openworx.zabbixmobile.Prefs.minSeverity
import nl.openworx.zabbixmobile.Prefs.serverUrl
import nl.openworx.zabbixmobile.Prefs.showSuppressed
import java.util.concurrent.TimeUnit

class RefreshWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        if (!Prefs.isConfigured(ctx)) return Result.success()

        try {
            val api = ZabbixApi(ctx.serverUrl, ctx.apiToken, ctx.ignoreSsl)
            val problems = api.currentProblems(
                minSeverity = ctx.minSeverity,
                hideAcknowledged = ctx.hideAcknowledged,
                showSuppressed = ctx.showSuppressed
            )
            ctx.cachedProblemsJson = Problem.listToJson(problems)
            ctx.lastUpdateMillis = System.currentTimeMillis()
            ctx.lastError = ""
            Notifier.processProblems(ctx, problems)
        } catch (e: Exception) {
            ctx.lastError = e.message ?: "Unknown error"
        }

        ProblemsWidgetProvider.updateAllWidgets(ctx)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "zabbix_refresh_periodic"
        private const val ONESHOT = "zabbix_refresh_now"

        fun refreshNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniqueWork(ONESHOT, ExistingWorkPolicy.REPLACE, req)
        }

        fun schedulePeriodic(ctx: Context) {
            val minutes = ctx.intervalMinutes.coerceAtLeast(1)
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = alarmPendingIntent(ctx)
            am.cancel(pi)

            if (minutes < 15) {
                // Intervals below 15 min are not supported by WorkManager → inexact repeating alarm.
                // Android may defer it slightly while in Doze (screen off for a long time).
                WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
                val intervalMs = minutes * 60_000L
                am.setRepeating(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + intervalMs,
                    intervalMs,
                    pi
                )
            } else {
                val req = PeriodicWorkRequestBuilder<RefreshWorker>(
                    minutes.toLong(), TimeUnit.MINUTES
                )
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .build()
                WorkManager.getInstance(ctx)
                    .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
            }
        }

        fun cancelAll(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(alarmPendingIntent(ctx))
        }

        private fun alarmPendingIntent(ctx: Context): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, 10,
                Intent(ctx, ProblemsWidgetProvider::class.java)
                    .setAction(ProblemsWidgetProvider.ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
