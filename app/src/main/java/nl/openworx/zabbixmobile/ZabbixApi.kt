/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Minimal JSON-RPC client for the Zabbix 7 API.
 * Authentication via an API token in the Authorization: Bearer header
 * (supported since Zabbix 6.4, the default in 7.x).
 */
class ZabbixApi(
    baseUrl: String,
    private val token: String,
    private val ignoreSsl: Boolean = false
) {
    private val endpoint = baseUrl.trimEnd('/') + "/api_jsonrpc.php"
    private var requestId = 1

    fun apiVersion(): String =
        call("apiinfo.version", JSONObject(), withAuth = false).let {
            it.optString("result", "?")
        }

    /**
     * Fetches CURRENT problems only, matching Monitoring → Problems in the frontend:
     * unresolved problems only (recent=false), no suppressed problems (unless requested),
     * and only from enabled triggers on monitored hosts.
     */
    fun currentProblems(
        minSeverity: Int = 0,
        hideAcknowledged: Boolean = false,
        showSuppressed: Boolean = false,
        limit: Int = 60
    ): List<Problem> {
        val severities = JSONArray()
        for (s in minSeverity..5) severities.put(s)

        val params = JSONObject()
            .put("output", JSONArray(listOf("eventid", "objectid", "name", "severity", "clock", "acknowledged")))
            .put("recent", false) // unresolved problems only, no recently resolved ones
            .put("severities", severities)
            .put("sortfield", JSONArray(listOf("eventid")))
            .put("sortorder", JSONArray(listOf("DESC")))
            .put("limit", limit)
        if (!showSuppressed) params.put("suppressed", false)
        if (hideAcknowledged) params.put("acknowledged", false)

        val result = call("problem.get", params).getJSONArray("result")

        val raw = (0 until result.length()).map { result.getJSONObject(it) }
        val triggerIds = raw.map { it.optString("objectid") }.distinct()

        // Fetch host names; 'monitored' filters out deleted/disabled triggers
        // and unmonitored hosts (the frontend hides those as well).
        val hostByTrigger = mutableMapOf<String, String>()
        if (triggerIds.isNotEmpty()) {
            val tParams = JSONObject()
                .put("output", JSONArray(listOf("triggerid")))
                .put("triggerids", JSONArray(triggerIds))
                .put("monitored", true)
                .put("selectHosts", JSONArray(listOf("name")))
                .put("preservekeys", true)
            val tResult = call("trigger.get", tParams).getJSONObject("result")
            tResult.keys().forEach { tid ->
                val hosts = tResult.getJSONObject(tid).optJSONArray("hosts")
                if (hosts != null && hosts.length() > 0) {
                    hostByTrigger[tid] = (0 until hosts.length())
                        .joinToString(", ") { hosts.getJSONObject(it).optString("name") }
                }
            }
        }

        return raw
            .filter { hostByTrigger.containsKey(it.optString("objectid")) }
            .map {
                val tid = it.optString("objectid")
                Problem(
                    eventId = it.optString("eventid"),
                    triggerId = tid,
                    name = it.optString("name"),
                    severity = it.optString("severity", "0").toIntOrNull() ?: 0,
                    clock = it.optString("clock", "0").toLongOrNull() ?: 0L,
                    acknowledged = it.optString("acknowledged") == "1",
                    host = hostByTrigger[tid] ?: ""
                )
            }
            .sortedWith(compareByDescending<Problem> { it.severity }.thenByDescending { it.clock })
    }

    // ---------- Hosts & groups ----------

    /** Host groups containing at least one host. List of (groupid, name). */
    fun hostGroups(): List<Pair<String, String>> {
        val params = JSONObject()
            .put("output", JSONArray(listOf("groupid", "name")))
            .put("with_hosts", true)
            .put("sortfield", JSONArray(listOf("name")))
        val res = call("hostgroup.get", params).getJSONArray("result")
        return (0 until res.length()).map {
            val o = res.getJSONObject(it)
            o.optString("groupid") to o.optString("name")
        }
    }

    data class Host(
        val hostId: String,
        val name: String,
        val enabled: Boolean,
        val inMaintenance: Boolean,
        val available: Int, // 0=unknown 1=available 2=unavailable (worst interface)
        val ip: String
    )

    fun hosts(groupId: String? = null): List<Host> {
        val params = JSONObject()
            .put("output", JSONArray(listOf("hostid", "host", "name", "status", "maintenance_status")))
            .put("selectInterfaces", JSONArray(listOf("available", "ip", "type")))
            .put("sortfield", JSONArray(listOf("name")))
        if (groupId != null) params.put("groupids", JSONArray(listOf(groupId)))
        val res = call("host.get", params).getJSONArray("result")
        return (0 until res.length()).map { i ->
            val o = res.getJSONObject(i)
            val ifs = o.optJSONArray("interfaces")
            var avail = 0
            var ip = ""
            if (ifs != null) {
                for (j in 0 until ifs.length()) {
                    val f = ifs.getJSONObject(j)
                    if (ip.isEmpty()) ip = f.optString("ip")
                    val a = f.optString("available", "0").toIntOrNull() ?: 0
                    if (a == 2) avail = 2 else if (a == 1 && avail != 2) avail = 1
                }
            }
            Host(
                hostId = o.optString("hostid"),
                name = o.optString("name").ifEmpty { o.optString("host") },
                enabled = o.optString("status") == "0",
                inMaintenance = o.optString("maintenance_status") == "1",
                available = avail,
                ip = ip
            )
        }
    }

    // ---------- Items ----------

    data class Item(
        val itemId: String,
        val name: String,
        val key: String,
        val lastValue: String,
        val units: String,
        val valueType: Int,
        val lastClock: Long
    ) {
        val isNumeric: Boolean get() = valueType == 0 || valueType == 3
    }

    fun items(hostId: String, search: String? = null): List<Item> {
        val params = JSONObject()
            .put("output", JSONArray(listOf("itemid", "name", "key_", "lastvalue", "units", "value_type", "lastclock")))
            .put("hostids", JSONArray(listOf(hostId)))
            .put("monitored", true)
            .put("sortfield", JSONArray(listOf("name")))
        if (!search.isNullOrBlank()) {
            params.put("search", JSONObject().put("name", search))
        }
        val res = call("item.get", params).getJSONArray("result")
        return (0 until res.length()).map { i ->
            val o = res.getJSONObject(i)
            Item(
                itemId = o.optString("itemid"),
                name = o.optString("name"),
                key = o.optString("key_"),
                lastValue = o.optString("lastvalue"),
                units = o.optString("units"),
                valueType = o.optString("value_type", "-1").toIntOrNull() ?: -1,
                lastClock = o.optString("lastclock", "0").toLongOrNull() ?: 0L
            )
        }
    }

    // ---------- History / graphs ----------

    /** (time, value) points for a numeric item; falls back to trends for long ranges. */
    fun history(itemId: String, valueType: Int, fromEpoch: Long, tillEpoch: Long): List<Pair<Long, Double>> {
        val params = JSONObject()
            .put("output", "extend")
            .put("history", valueType)
            .put("itemids", JSONArray(listOf(itemId)))
            .put("time_from", fromEpoch)
            .put("time_till", tillEpoch)
            .put("sortfield", "clock")
            .put("sortorder", "ASC")
            .put("limit", 5000)
        val res = call("history.get", params).getJSONArray("result")
        val points = (0 until res.length()).mapNotNull { i ->
            val o = res.getJSONObject(i)
            val v = o.optString("value").toDoubleOrNull() ?: return@mapNotNull null
            (o.optString("clock", "0").toLongOrNull() ?: 0L) to v
        }
        if (points.isNotEmpty()) return points

        // Fallback: trends (hourly averages) for older data
        val tParams = JSONObject()
            .put("output", "extend")
            .put("itemids", JSONArray(listOf(itemId)))
            .put("time_from", fromEpoch)
            .put("time_till", tillEpoch)
            .put("limit", 5000)
        val tRes = call("trend.get", tParams).getJSONArray("result")
        return (0 until tRes.length()).mapNotNull { i ->
            val o = tRes.getJSONObject(i)
            val v = o.optString("value_avg").toDoubleOrNull() ?: return@mapNotNull null
            (o.optString("clock", "0").toLongOrNull() ?: 0L) to v
        }.sortedBy { it.first }
    }

    // ---------- Acknowledge & events ----------

    data class AckEntry(
        val user: String,
        val clock: Long,
        val message: String,
        val action: Int,
        val oldSeverity: Int,
        val newSeverity: Int
    )

    /** Acknowledge history (comments) of an event. */
    fun acknowledges(eventId: String): List<AckEntry> {
        val params = JSONObject()
            .put("output", JSONArray(listOf("eventid")))
            .put("eventids", JSONArray(listOf(eventId)))
            .put("select_acknowledges", "extend")
        val res = call("event.get", params).getJSONArray("result")
        if (res.length() == 0) return emptyList()
        val acks = res.getJSONObject(0).optJSONArray("acknowledges") ?: return emptyList()
        return (0 until acks.length()).map { i ->
            val o = acks.getJSONObject(i)
            val userName = listOf(o.optString("name"), o.optString("surname"))
                .filter { it.isNotEmpty() }.joinToString(" ")
                .ifEmpty { o.optString("username").ifEmpty { "user ${o.optString("userid")}" } }
            AckEntry(
                user = userName,
                clock = o.optString("clock", "0").toLongOrNull() ?: 0L,
                message = o.optString("message"),
                action = o.optString("action", "0").toIntOrNull() ?: 0,
                oldSeverity = o.optString("old_severity", "0").toIntOrNull() ?: 0,
                newSeverity = o.optString("new_severity", "0").toIntOrNull() ?: 0
            )
        }.sortedByDescending { it.clock }
    }

    /**
     * Performs actions on an event via event.acknowledge.
     * Action bits: 1=close, 2=acknowledge, 4=add message, 8=change severity, 16=unacknowledge.
     */
    fun eventAction(eventId: String, actionBits: Int, message: String? = null, newSeverity: Int? = null) {
        val params = JSONObject()
            .put("eventids", JSONArray(listOf(eventId)))
            .put("action", actionBits)
        if (!message.isNullOrBlank()) params.put("message", message)
        if (newSeverity != null) params.put("severity", newSeverity)
        call("event.acknowledge", params)
    }

    private fun call(method: String, params: JSONObject, withAuth: Boolean = true): JSONObject {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("method", method)
            .put("params", params)
            .put("id", requestId++)

        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            if (conn is HttpsURLConnection && ignoreSsl) {
                conn.sslSocketFactory = insecureSslContext().socketFactory
                conn.setHostnameVerifier { _, _ -> true }
            }
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json-rpc")
            if (withAuth) conn.setRequestProperty("Authorization", "Bearer $token")

            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            if (code !in 200..299) throw ApiException("HTTP $code: ${text.take(200)}")

            val json = JSONObject(text)
            if (json.has("error")) {
                val err = json.getJSONObject("error")
                val full = "${err.optString("message")} ${err.optString("data")}".trim()
                // Zabbix returns "Invalid params. / Session terminated, re-login, please."
                // (or "Not authorised.") when the API token is not accepted.
                if (full.contains("re-login", ignoreCase = true) ||
                    full.contains("Not authorised", ignoreCase = true) ||
                    full.contains("Not authorized", ignoreCase = true)
                ) {
                    throw ApiException(
                        "API token rejected by the server. Check the token " +
                            "(Zabbix → Users → API tokens), its expiry date and whether it is enabled."
                    )
                }
                throw ApiException(full)
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun insecureSslContext(): SSLContext {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustAll), SecureRandom())
        }
    }

    class ApiException(message: String) : Exception(message)
}
