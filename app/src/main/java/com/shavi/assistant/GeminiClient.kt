package com.shavi.assistant

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to the Gemini API (generateContent endpoint) and asks it to return
 * a structured action (JSON) instead of free text, so the app knows
 * exactly what to do: make a call, tell the time, open an app, etc.
 */
class GeminiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    data class ShaviAction(
        val action: String,
        val target: String? = null,
        val message: String? = null,
        val reply: String
    )

    /**
     * Sends the user's spoken command to Gemini and asks for ONLY a JSON reply
     * describing which action to take.
     */
    fun resolveCommand(userCommand: String): ShaviAction {
        val systemPrompt = """
            Tum "SHAVi" ho, ek Hindi/Hinglish bolne wali phone assistant.
            User ne bola: "$userCommand"

            Tumhe SIRF ek JSON object return karna hai, koi aur text nahi, koi markdown fence nahi.
            JSON ka format bilkul yeh hoga:
            {
              "action": "one of [call, time, open_app, send_message, scroll, chat]",
              "target": "contact ka naam ya app ka naam agar zaroori ho, warna null",
              "message": "agar action send_message hai to message ka content, warna null",
              "reply": "SHAVi jo Hindi/Hinglish mein bolegi user ko, chhoti aur natural"
            }

            Rules:
            - Agar user call karne ko bole ("X ko call karo"), action = "call", target = contact ka naam.
            - Agar user time pooche, action = "time".
            - Agar user app kholne bole, action = "open_app", target = app ka naam.
            - Agar user message/SMS bhejne bole, action = "send_message", target = contact, message = content.
            - Agar user scroll/swipe bole, action = "scroll".
            - Baaki sab general baaton ke liye action = "chat" aur reply mein normal jawab do.
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                }
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            })
        }

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$endpoint?key=$apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return ShaviAction(
                    action = "chat",
                    reply = "Mujhe Gemini se jawab nahi mila, connection check karein."
                )
            }
            val bodyStr = response.body?.string() ?: return ShaviAction(
                action = "chat",
                reply = "Kuch gadbad ho gayi, dobara try karein."
            )
            return parseResponse(bodyStr)
        }
    }

    private fun parseResponse(rawBody: String): ShaviAction {
        return try {
            val root = JSONObject(rawBody)
            val text = root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val actionJson = JSONObject(text)
            ShaviAction(
                action = actionJson.optString("action", "chat"),
                target = actionJson.optString("target", null.toString()).takeIf { it != "null" },
                message = actionJson.optString("message", null.toString()).takeIf { it != "null" },
                reply = actionJson.optString("reply", "Ji, bataiye.")
            )
        } catch (e: Exception) {
            ShaviAction(action = "chat", reply = "Samajh nahi paayi, dobara bolein.")
        }
    }
}
