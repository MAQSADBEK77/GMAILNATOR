package com.gmailnator.auto

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object Api {
    private const val KEY  = "03968cdfa2mshe3451f14a06bd97p1f11a0jsn63618796d7f7"
    private const val HOST = "gmailnator.p.rapidapi.com"
    private const val BASE = "https://$HOST/api"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json".toMediaType()

    private fun req(method: String, path: String, body: String? = null): JSONObject {
        val rb = body?.toRequestBody(JSON)
        val r = client.newCall(
            Request.Builder()
                .url("$BASE$path")
                .method(method, if (method == "GET") null else (rb ?: "{}".toRequestBody(JSON)))
                .header("Content-Type", "application/json")
                .header("x-rapidapi-host", HOST)
                .header("x-rapidapi-key", KEY)
                .build()
        ).execute()
        val j = JSONObject(r.body!!.string())
        if (!r.isSuccessful) throw Exception(j.optString("error", "HTTP ${r.code}"))
        return j
    }

    fun generateEmail(): String {
        val body = """{"type":["public_gmail_plus","public_gmail_dot","private_gmail_plus","private_gmail_dot"]}"""
        val j = req("POST", "/emails/generate", body)
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
        return req("GET", "/inbox/$id").optString("content", "")
    }

    fun extractCode(html: String): String? {
        val t = html.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ")
        for (p in listOf(
            Regex("""(?:login|verif|confirm|code|kod)[^\d]*(\d{6})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{6})\s*(?:is your|bu sizning)""", RegexOption.IGNORE_CASE),
            Regex("""\b(\d{6})\b"""),
        )) { p.find(t)?.let { return it.groupValues[1] } }
        return null
    }
}
