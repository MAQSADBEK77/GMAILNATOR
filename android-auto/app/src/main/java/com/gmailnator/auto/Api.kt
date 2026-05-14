package com.gmailnator.auto

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object Api {
    const val SERVER = "http://192.168.1.116:4000"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json".toMediaType()

    var deviceId   = ""
    var deviceName = ""

    private fun req(method: String, path: String, body: String? = null): JSONObject {
        val rb = body?.toRequestBody(JSON)
        val r = client.newCall(
            Request.Builder()
                .url("$SERVER$path")
                .method(method, if (method == "GET") null else (rb ?: "{}".toRequestBody(JSON)))
                .header("x-device-id", deviceId)
                .header("x-device-name", deviceName)
                .build()
        ).execute()
        val j = JSONObject(r.body!!.string())
        if (!r.isSuccessful) throw Exception(j.optString("error", "HTTP ${r.code}"))
        return j
    }

    fun ping(): Boolean {
        return try {
            val r = client.newCall(
                Request.Builder().url("$SERVER/connect")
                    .post("{}".toRequestBody(JSON))
                    .header("x-device-id", deviceId)
                    .header("x-device-name", deviceName)
                    .build()
            ).execute()
            r.isSuccessful
        } catch (e: Exception) { false }
    }

    fun generateEmail(): String {
        val j = req("POST", "/generate")
        if (j.optString("status") != "success") throw Exception(j.optString("message", "Xatolik"))
        return j.getString("email")
    }

    data class Msg(val id: String, val from: String)

    fun getInbox(email: String): List<Msg> {
        val body = """{"email":"$email","limit":10}"""
        val j = req("POST", "/inbox", body)
        val arr = j.optJSONArray("messages") ?: return emptyList()
        return (0 until arr.length()).map {
            val m = arr.getJSONObject(it)
            Msg(m.getString("id"), m.optString("from"))
        }
    }

    fun getMessage(id: String): String {
        return req("GET", "/message/$id").optString("content", "")
    }

    fun extractCode(html: String): String? {
        val t = html.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ")
        // Telegram faqat 6 xonali kod yuboradi
        for (p in listOf(
            Regex("""(?:login|verif|confirm|code|kod)[^\d]*(\d{6})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{6})\s*(?:is your|bu sizning)""", RegexOption.IGNORE_CASE),
            Regex("""\b(\d{6})\b"""),
        )) { p.find(t)?.let { return it.groupValues[1] } }
        return null
    }
}
