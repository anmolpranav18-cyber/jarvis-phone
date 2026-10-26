package com.jarvis.phone

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Gemini client: raw HttpURLConnection, no external dependencies.
 * The API key is stored in SharedPreferences (never committed to the repo).
 */
object GeminiClient {

    private const val TAG = "GeminiClient"
    private const val MODEL = "gemini-2.0-flash"
    private const val BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private const val SYSTEM_PROMPT =
        "You are JARVIS, a personal phone assistant with a dry British wit, in the style of Tony Stark's assistant. " +
        "Keep spoken replies short (1-3 sentences) since they are read aloud by text-to-speech. " +
        "Be helpful first, witty second. Answer questions, do mental math, give weather or general-knowledge answers from your own knowledge."

    private val history = mutableListOf<Pair<String, String>>()
    private const val MAX_HISTORY = 8

    fun ask(context: Context, userText: String): String? {
        val key = getApiKey(context)
        if (key.isNullOrBlank()) return null
        return try {
            val contents = JSONArray()
            for ((q, a) in history.takeLast(MAX_HISTORY)) {
                contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", q))))
                contents.put(JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", a))))
            }
            contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userText))))

            val body = JSONObject()
                .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))))
                .put("contents", contents)
                .put("generationConfig", JSONObject().put("maxOutputTokens", 300).put("temperature", 0.7))

            val conn = URL("$BASE_URL?key=$key").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            conn.disconnect()

            if (code !in 200..299) {
                Log.e(TAG, "Gemini HTTP $code: $text")
                return "Gemini refused the request (HTTP $code)."
            }

            val reply = JSONObject(text)
                .getJSONArray("candidates")[0]
                .let { it as JSONObject }
                .getJSONObject("content")
                .getJSONArray("parts")[0]
                .let { it as JSONObject }
                .optString("text", "")
                .trim()

            if (reply.isNotBlank()) {
                history.add(userText to reply)
                if (history.size > MAX_HISTORY) history.removeAt(0)
            }
            reply.ifBlank { null }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini call failed", e)
            null
        }
    }

    fun getApiKey(context: Context): String? {
        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        return prefs.getString("gemini_api_key", null)
    }

    fun saveApiKey(context: Context, key: String) {
        context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
            .edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun clearHistory() {
        history.clear()
    }
}
