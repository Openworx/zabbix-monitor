/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import android.content.Context
import android.content.SharedPreferences

/** Simple storage for settings and the most recently fetched problems. */
object Prefs {
    private const val NAME = "zabbix_widget"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var Context.serverUrl: String
        get() = sp(this).getString("url", "") ?: ""
        set(v) = sp(this).edit().putString("url", v.trim().trimEnd('/')).apply()

    var Context.apiToken: String
        get() = sp(this).getString("token", "") ?: ""
        set(v) = sp(this).edit().putString("token", v.trim()).apply()

    var Context.ignoreSsl: Boolean
        get() = sp(this).getBoolean("ignore_ssl", false)
        set(v) = sp(this).edit().putBoolean("ignore_ssl", v).apply()

    var Context.intervalMinutes: Int
        get() = sp(this).getInt("interval_min", 15)
        set(v) = sp(this).edit().putInt("interval_min", v).apply()

    var Context.hideAcknowledged: Boolean
        get() = sp(this).getBoolean("hide_acked", false)
        set(v) = sp(this).edit().putBoolean("hide_acked", v).apply()

    var Context.showSuppressed: Boolean
        get() = sp(this).getBoolean("show_suppressed", false)
        set(v) = sp(this).edit().putBoolean("show_suppressed", v).apply()

    var Context.compactMode: Boolean
        get() = sp(this).getBoolean("compact", true)
        set(v) = sp(this).edit().putBoolean("compact", v).apply()

    var Context.minSeverity: Int
        get() = sp(this).getInt("min_severity", 0)
        set(v) = sp(this).edit().putInt("min_severity", v).apply()

    var Context.cachedProblemsJson: String
        get() = sp(this).getString("problems_json", "[]") ?: "[]"
        set(v) = sp(this).edit().putString("problems_json", v).apply()

    var Context.lastUpdateMillis: Long
        get() = sp(this).getLong("last_update", 0L)
        set(v) = sp(this).edit().putLong("last_update", v).apply()

    var Context.lastError: String
        get() = sp(this).getString("last_error", "") ?: ""
        set(v) = sp(this).edit().putString("last_error", v).apply()

    var Context.notifyEnabled: Boolean
        get() = sp(this).getBoolean("notify_enabled", false)
        set(v) = sp(this).edit().putBoolean("notify_enabled", v).apply()

    var Context.notifyMinSeverity: Int
        get() = sp(this).getInt("notify_min_severity", 2)
        set(v) = sp(this).edit().putInt("notify_min_severity", v).apply()

    var Context.knownEventIds: Set<String>
        get() = sp(this).getStringSet("known_event_ids", null) ?: emptySet()
        set(v) = sp(this).edit().putStringSet("known_event_ids", v).apply()

    var Context.hasSeenEvents: Boolean
        get() = sp(this).getBoolean("has_seen_events", false)
        set(v) = sp(this).edit().putBoolean("has_seen_events", v).apply()

    fun isConfigured(ctx: Context): Boolean =
        ctx.serverUrl.isNotEmpty() && ctx.apiToken.isNotEmpty()
}
