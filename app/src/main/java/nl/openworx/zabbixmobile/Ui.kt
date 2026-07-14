/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

/** Shared UI helpers. */
object Ui {
    val SEVERITY_NAMES = listOf(
        "Not classified", "Information", "Warning", "Average", "High", "Disaster"
    )

    fun severityName(s: Int): String = SEVERITY_NAMES.getOrElse(s.coerceIn(0, 5)) { "?" }

    fun formatDuration(clock: Long): String {
        if (clock <= 0) return ""
        val secs = (System.currentTimeMillis() / 1000 - clock).coerceAtLeast(0)
        return when {
            secs < 60 -> "${secs}s"
            secs < 3600 -> "${secs / 60}m"
            secs < 86400 -> "${secs / 3600}h ${(secs % 3600) / 60}m"
            else -> "${secs / 86400}d ${(secs % 86400) / 3600}h"
        }
    }

    /** Pretty-print value + unit; bytes/bps become K/M/G/T. */
    fun formatValue(raw: String, units: String): String {
        val d = raw.toDoubleOrNull() ?: return raw.take(24)
        if (units == "B" || units == "Bps" || units == "bps") {
            var v = d
            val prefixes = listOf("", "K", "M", "G", "T", "P")
            var i = 0
            val div = if (units == "B" || units == "Bps") 1024.0 else 1000.0
            while (v >= div && i < prefixes.size - 1) { v /= div; i++ }
            return trim(v) + " " + prefixes[i] + units
        }
        if (units == "unixtime" || units == "uptime" || units == "s") {
            if (units == "s" && d < 60) return trim(d) + "s"
            if (units != "unixtime") {
                val secs = d.toLong()
                return when {
                    secs < 3600 -> "${secs / 60}m"
                    secs < 86400 -> "${secs / 3600}h ${(secs % 3600) / 60}m"
                    else -> "${secs / 86400}d ${(secs % 86400) / 3600}h"
                }
            }
        }
        val s = trim(d)
        return if (units.isNotEmpty()) "$s $units" else s
    }

    private fun trim(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.2f", v)
}
