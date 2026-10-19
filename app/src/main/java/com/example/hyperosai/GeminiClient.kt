package com.example.hyperosai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiClient {
    private val http = OkHttpClient.Builder()
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun ask(apiKey: String, hyperOsVersion: String, userText: String, history: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw IllegalArgumentException("Add your Gemini API key in Settings first.")
            val contents = JSONArray()
            val systemPrompt = """
                You are HyperOS AI, a careful technical assistant for Xiaomi Redmi Note 14 5G.
                User-selected HyperOS version: ${hyperOsVersion.ifBlank { "not specified" }}.
                Give version-aware guidance. Distinguish verified facts from uncertainty.
                Prefer reversible, low-risk steps. Explain the Settings path and expected result.
                Never claim a command is tested unless it is. Warn before bootloader, root,
                data-wiping, security, or write-capable ADB operations. Ask for the exact build
                number when it affects compatibility. Do not invent hidden HyperOS settings.
            """.trimIndent()
            contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            history.takeLast(12).forEach { (role, text) ->
                contents.put(JSONObject().put("role", if (role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", text))))
            }
            contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userText))))
            val body = JSONObject().put("contents", contents)
                .put("generationConfig", JSONObject().put("temperature", 0.55).put("maxOutputTokens", 2048))
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                .addHeader("x-goog-api-key", apiKey)
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            http.newCall(req).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()
                    throw IOException("Gemini error ${response.code}: ${message ?: raw.take(300)}")
                }
                val json = JSONObject(raw)
                val candidates = json.optJSONArray("candidates")
                val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                buildString {
                    if (parts != null) for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
                }.ifBlank { "Gemini returned an empty response. Try again." }
            }
        }
}
