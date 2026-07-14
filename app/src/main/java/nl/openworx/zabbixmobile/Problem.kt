/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import org.json.JSONArray
import org.json.JSONObject

data class Problem(
    val eventId: String,
    val triggerId: String,
    val name: String,
    val severity: Int,
    val clock: Long,
    val acknowledged: Boolean,
    val host: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("eventId", eventId)
        .put("triggerId", triggerId)
        .put("name", name)
        .put("severity", severity)
        .put("clock", clock)
        .put("acknowledged", acknowledged)
        .put("host", host)

    companion object {
        fun fromJson(o: JSONObject) = Problem(
            eventId = o.optString("eventId"),
            triggerId = o.optString("triggerId"),
            name = o.optString("name"),
            severity = o.optInt("severity"),
            clock = o.optLong("clock"),
            acknowledged = o.optBoolean("acknowledged"),
            host = o.optString("host")
        )

        fun listToJson(list: List<Problem>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(json: String): List<Problem> = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }

        /** Default Zabbix colors per severity (0..5). */
        val SEVERITY_COLORS = intArrayOf(
            0xFF97AAB3.toInt(), // Not classified
            0xFF7499FF.toInt(), // Information
            0xFFFFC859.toInt(), // Warning
            0xFFFFA059.toInt(), // Average
            0xFFE97659.toInt(), // High
            0xFFE45959.toInt()  // Disaster
        )

        fun colorFor(severity: Int): Int =
            SEVERITY_COLORS[severity.coerceIn(0, 5)]
    }
}
