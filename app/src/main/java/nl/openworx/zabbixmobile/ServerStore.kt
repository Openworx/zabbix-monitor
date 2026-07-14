/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import android.content.Context
import nl.openworx.zabbixmobile.Prefs.apiToken
import nl.openworx.zabbixmobile.Prefs.ignoreSsl
import nl.openworx.zabbixmobile.Prefs.serverUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Server(
    val id: String,
    var name: String,
    var url: String,
    var token: String,
    var ignoreSsl: Boolean
)

/**
 * Manages multiple Zabbix servers. The ACTIVE server is mirrored to the
 * legacy Prefs fields (serverUrl/apiToken/ignoreSsl) so the widget and
 * worker keep working without changes.
 */
object ServerStore {
    private const val KEY_SERVERS = "servers_json"
    private const val KEY_ACTIVE = "active_server_id"

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences("zabbix_widget", Context.MODE_PRIVATE)

    fun all(ctx: Context): MutableList<Server> {
        migrateIfNeeded(ctx)
        val json = sp(ctx).getString(KEY_SERVERS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Server(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    url = o.optString("url"),
                    token = o.optString("token"),
                    ignoreSsl = o.optBoolean("ignoreSsl")
                )
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun activeId(ctx: Context): String = sp(ctx).getString(KEY_ACTIVE, "") ?: ""

    fun active(ctx: Context): Server? {
        val id = activeId(ctx)
        return all(ctx).firstOrNull { it.id == id } ?: all(ctx).firstOrNull()
    }

    fun save(ctx: Context, server: Server) {
        val list = all(ctx)
        val idx = list.indexOfFirst { it.id == server.id }
        if (idx >= 0) list[idx] = server else list.add(server)
        persist(ctx, list)
        if (activeId(ctx).isEmpty() || activeId(ctx) == server.id) activate(ctx, server.id)
    }

    fun delete(ctx: Context, serverId: String) {
        val list = all(ctx).filter { it.id != serverId }
        persist(ctx, list)
        if (activeId(ctx) == serverId) {
            list.firstOrNull()?.let { activate(ctx, it.id) } ?: run {
                sp(ctx).edit().remove(KEY_ACTIVE).apply()
                ctx.serverUrl = ""
                ctx.apiToken = ""
            }
        }
    }

    /** Makes this server active and mirrors it to the legacy fields (widget/worker). */
    fun activate(ctx: Context, serverId: String) {
        val server = all(ctx).firstOrNull { it.id == serverId } ?: return
        sp(ctx).edit().putString(KEY_ACTIVE, serverId).apply()
        ctx.serverUrl = server.url
        ctx.apiToken = server.token
        ctx.ignoreSsl = server.ignoreSsl
    }

    fun newServer(): Server =
        Server(UUID.randomUUID().toString(), "", "", "", false)

    /** Migrates an existing single-server configuration (v1.x) into the server list. */
    private fun migrateIfNeeded(ctx: Context) {
        val prefs = sp(ctx)
        if (prefs.getString(KEY_SERVERS, null) != null) return
        val url = ctx.serverUrl
        if (url.isEmpty()) {
            prefs.edit().putString(KEY_SERVERS, "[]").apply()
            return
        }
        val server = Server(
            UUID.randomUUID().toString(),
            name = "Zabbix",
            url = url,
            token = ctx.apiToken,
            ignoreSsl = ctx.ignoreSsl
        )
        persist(ctx, listOf(server))
        prefs.edit().putString(KEY_ACTIVE, server.id).apply()
    }

    private fun persist(ctx: Context, list: List<Server>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("url", it.url)
                    .put("token", it.token)
                    .put("ignoreSsl", it.ignoreSsl)
            )
        }
        sp(ctx).edit().putString(KEY_SERVERS, arr.toString()).apply()
    }

    /** Convenience helper: API client for the active server, or null if none. */
    fun api(ctx: Context): ZabbixApi? =
        active(ctx)?.takeIf { it.url.isNotEmpty() && it.token.isNotEmpty() }
            ?.let { ZabbixApi(it.url, it.token, it.ignoreSsl) }
}
