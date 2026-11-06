package com.atharva.phoneagent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * BRAIN — converts the user's sentence into ONE structured action.
 *
 * HOW: we send the sentence to an LLM (Groq, free tier) together with a
 * fixed TOOLBOX — the 10 actions this agent is allowed to take, each
 * with typed inputs. The model must answer either with a tool call
 * (machine-readable, the phone can run it) or with plain text (that's
 * the ask_anything answer, or a clarifying question).
 *
 * WHY tool calling: a free-chatting model gives paragraphs we cannot
 * execute. Tool calling forces a contract the code can trust.
 * WHY Groq: free tier + very fast (an agent that thinks for 5 seconds
 * feels broken) + OpenAI-compatible API, so swapping providers later
 * is a config change, not a rewrite.
 *
 * The API key is the user's own, stored on-device in SharedPreferences.
 * It is never hardcoded and never sent anywhere except api.groq.com.
 */
class Brain(private val apiKey: String) {

    /** What the brain decided. */
    sealed class Decision {
        /** Run this tool with these arguments (JSON). */
        data class Act(val tool: String, val args: JSONObject) : Decision()
        /** Speak this text (an answer, or a clarifying question). */
        data class Say(val text: String) : Decision()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Short memory: last few exchanges, so "message him again" works.
    private val history = mutableListOf<JSONObject>()

    fun think(userText: String, languageName: String, callback: (Decision?, String?) -> Unit) {
        if (apiKey.isBlank()) {
            callback(null, "No Groq API key set. Tap 'Set Groq API key' first.")
            return
        }
        Thread {
            try {
                val reply = callModel(userText, languageName)
                callback(reply, null)
            } catch (e: Exception) {
                callback(null, "Brain error: ${e.message}")
            }
        }.start()
    }

    private fun callModel(userText: String, languageName: String): Decision {
        val messages = JSONArray()
        messages.put(
            JSONObject().put("role", "system").put(
                "content",
                """
                You are Phone Agent, a voice assistant running on an Android phone in India.
                The user is speaking $languageName. Reply in the same language as the user.
                RULES:
                - If the user wants an action, call exactly ONE tool. Never invent tools.
                - If a contact name or detail is unclear, do NOT guess — reply with a short clarifying question instead of a tool call.
                - For general questions, answer briefly (1-2 spoken sentences) as plain text.
                - Never claim you performed an action in plain text; actions only happen via tool calls.
                """.trimIndent()
            )
        )
        history.forEach { messages.put(it) }
        messages.put(JSONObject().put("role", "user").put("content", userText))

        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", messages)
            .put("tools", TOOLBOX)
            .put("tool_choice", "auto")
            .put("temperature", 0.2)

        val request = Request.Builder()
            .url(API_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Groq API ${response.code}: ${raw.take(200)}")
            }
            val message = JSONObject(raw)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")

            // Remember this exchange (user + assistant summary) for context.
            history.add(JSONObject().put("role", "user").put("content", userText))
            history.add(
                JSONObject().put("role", "assistant")
                    .put("content", message.optString("content", ""))
            )
            while (history.size > 12) history.removeAt(0)

            val toolCalls = message.optJSONArray("tool_calls")
            if (toolCalls != null && toolCalls.length() > 0) {
                val call = toolCalls.getJSONObject(0).getJSONObject("function")
                val args = try {
                    JSONObject(call.optString("arguments", "{}"))
                } catch (e: Exception) {
                    JSONObject()
                }
                return Decision.Act(call.getString("name"), args)
            }
            val text = message.optString("content").trim()
            return Decision.Say(if (text.isEmpty()) "Sorry, I didn't understand that." else text)
        }
    }

    companion object {
        private const val API_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MODEL = "llama-3.3-70b-versatile"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** The fixed toolbox — the ONLY actions the brain may choose. */
        private val TOOLBOX = JSONArray().apply {
            fun tool(name: String, description: String, props: JSONObject, required: JSONArray) {
                put(
                    JSONObject()
                        .put("type", "function")
                        .put(
                            "function", JSONObject()
                                .put("name", name)
                                .put("description", description)
                                .put(
                                    "parameters", JSONObject()
                                        .put("type", "object")
                                        .put("properties", props)
                                        .put("required", required)
                                )
                        )
                )
            }
            fun str(desc: String) = JSONObject().put("type", "string").put("description", desc)
            fun req(vararg names: String) = JSONArray().apply { names.forEach { put(it) } }

            tool("make_call", "Call a contact by spoken name.",
                JSONObject().put("contact", str("Contact name as spoken, e.g. 'Mom'")), req("contact"))
            tool("send_sms", "Send an SMS text message to a contact.",
                JSONObject().put("contact", str("Contact name")).put("message", str("Message text")), req("contact", "message"))
            tool("send_whatsapp", "Send a WhatsApp message to a contact.",
                JSONObject().put("contact", str("Contact name")).put("message", str("Message text")), req("contact", "message"))
            tool("set_alarm", "Set an alarm clock.",
                JSONObject().put("hour", JSONObject().put("type", "integer").put("description", "Hour 0-23"))
                    .put("minute", JSONObject().put("type", "integer").put("description", "Minute 0-59"))
                    .put("label", str("Alarm label, may be empty")), req("hour", "minute"))
            tool("set_reminder", "Create a calendar reminder/event starting in a given number of minutes.",
                JSONObject().put("title", str("Reminder text"))
                    .put("in_minutes", JSONObject().put("type", "integer").put("description", "Minutes from now")), req("title", "in_minutes"))
            tool("open_app", "Open an installed app by its common name.",
                JSONObject().put("app", str("App name, e.g. 'YouTube'")), req("app"))
            tool("flashlight", "Turn the flashlight on or off.",
                JSONObject().put("on", JSONObject().put("type", "boolean").put("description", "true = on, false = off")), req("on"))
            tool("open_wifi_settings", "Open the Wi-Fi settings screen.",
                JSONObject(), req())
            tool("play_media", "Search and play a video/song on YouTube.",
                JSONObject().put("query", str("What to play, e.g. 'Arijit Singh songs'")), req("query"))
            tool("navigate", "Start navigation to a place with Google Maps.",
                JSONObject().put("place", str("Destination, e.g. 'railway station'")), req("place"))
        }
    }
}
