package com.gmailnator

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object Api {
    private val client = OkHttpClient()
    private val JSON = "application/json".toMediaType()
    private const val BASE = "https://gmailnator.p.rapidapi.com/api"
    private const val HOST = "gmailnator.p.rapidapi.com"

    private val TYPES = """{"type":["public_gmail_plus","public_gmail_dot","private_gmail_plus","private_gmail_dot"]}"""

    fun generateEmail(apiKey: String): String {
        val req = Request.Builder()
            .url("$BASE/emails/generate")
            .post(TYPES.toRequestBody(JSON))
            .header("x-rapidapi-host", HOST)
            .header("x-rapidapi-key", apiKey)
            .build()
        val body = client.newCall(req).execute().body!!.string()
        val j = JSONObject(body)
        if (j.optString("status") != "success") throw Exception(j.optString("message", "Xatolik"))
        return j.getString("email")
    }

    data class Message(val id: String, val from: String, val subject: String, val timeAgo: String)

    fun getInbox(apiKey: String, email: String): List<Message> {
        val body = """{"email":"$email","limit":20}"""
        val req = Request.Builder()
            .url("$BASE/inbox")
            .post(body.toRequestBody(JSON))
            .header("x-rapidapi-host", HOST)
            .header("x-rapidapi-key", apiKey)
            .build()
        val resp = JSONObject(client.newCall(req).execute().body!!.string())
        val arr = resp.optJSONArray("messages") ?: return emptyList()
        return (0 until arr.length()).map {
            val m = arr.getJSONObject(it)
            Message(m.getString("id"), m.optString("from"), m.optString("subject"), m.optString("time_ago"))
        }
    }

    fun getMessage(apiKey: String, msgId: String): String {
        val req = Request.Builder()
            .url("$BASE/inbox/${msgId}")
            .get()
            .header("x-rapidapi-host", HOST)
            .header("x-rapidapi-key", apiKey)
            .build()
        val resp = JSONObject(client.newCall(req).execute().body!!.string())
        return resp.optString("content", "")
    }

    fun extractCode(html: String): String? {
        val text = html.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ")
        val patterns = listOf(
            Regex("""(?:login|verif|confirm|code|kod)[^\d]*(\d{4,8})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{5,6})\s*(?:is your|bu sizning)""", RegexOption.IGNORE_CASE),
            Regex("""\b(\d{5,6})\b"""),
            Regex("""\b(\d{4})\b"""),
        )
        for (p in patterns) {
            val m = p.find(text)
            if (m != null) return m.groupValues[1]
        }
        return null
    }
}
