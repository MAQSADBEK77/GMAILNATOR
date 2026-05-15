package com.gmailnator

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object Api {
    const val SERVER = "http://192.168.1.116:4000"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json".toMediaType()

    var deviceId   = ""
    var deviceName = ""

    private fun req(method: String, path: String, body: String? = null): JSONObject {
        val rb = body?.toRequestBody(JSON)
        val req = Request.Builder()
            .url("$SERVER$path")
            .method(method, if (method == "GET") null else (rb ?: "{}".toRequestBody(JSON)))
            .header("x-device-id", deviceId)
            .header("x-device-name", deviceName)
            .build()
        val resp = client.newCall(req).execute()
        val json = JSONObject(resp.body!!.string())
        if (!resp.isSuccessful) throw Exception(json.optString("error", "HTTP ${resp.code}"))
        return json
    }

    fun verifyToken(token: String): Boolean {
        val body = """{"token":"$token"}"""
        val req = Request.Builder()
            .url("$SERVER/verify-token")
            .post(body.toRequestBody(JSON))
            .build()
        val resp = client.newCall(req).execute()
        return resp.isSuccessful
    }

    fun connect(): Boolean {
        val req = Request.Builder()
            .url("$SERVER/connect")
            .post("{}".toRequestBody(JSON))
            .header("x-device-id", deviceId)
            .header("x-device-name", deviceName)
            .build()
        val resp = client.newCall(req).execute()
        val json = JSONObject(resp.body!!.string())
        if (!resp.isSuccessful) throw Exception(json.optString("error", "Xatolik"))
        return true
    }

    fun generateEmail(): String {
        val j = req("POST", "/generate")
        if (j.optString("status") != "success") throw Exception(j.optString("message", "Xatolik"))
        return j.getString("email")
    }

    data class Msg(val id: String, val from: String, val subject: String, val timeAgo: String)

    fun getInbox(email: String): List<Msg> {
        val body = JSONObject().apply { put("email", email); put("limit", 20) }.toString()
        val j = req("POST", "/inbox", body)
        val arr = j.optJSONArray("messages") ?: return emptyList()
        return (0 until arr.length()).map {
            val m = arr.getJSONObject(it)
            Msg(m.getString("id"), m.optString("from"), m.optString("subject"), m.optString("time_ago"))
        }
    }

    fun getMessage(msgId: String): String {
        val j = req("GET", "/message/$msgId")
        return j.optString("content", "")
    }

    fun extractCode(html: String): String? {
        val t = html.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ")
        for (p in listOf(
            Regex("""(?:login|verif|confirm|code|kod)[^\d]*(\d{4,8})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{5,6})\s*(?:is your|bu sizning)""", RegexOption.IGNORE_CASE),
            Regex("""\b(\d{5,6})\b"""),
            Regex("""\b(\d{4})\b"""),
        )) { p.find(t)?.let { return it.groupValues[1] } }
        return null
    }
}
