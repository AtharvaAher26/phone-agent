package com.atharva.phoneagent

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * MAIN SCREEN + ORCHESTRATOR.
 *
 * This activity wires the five parts together:
 *   tap mic → SpeechInput (EARS) → Brain (LLM tool-calling)
 *   → CONFIRM GATE if the action is sensitive
 *   → ActionExecutor (HANDS) → Speaker (VOICE) reports the result,
 *     and everything is written to the on-screen log.
 *
 * Nothing sensitive (call / SMS / WhatsApp) executes without the
 * confirm gate. Permissions are requested at first use, not in a
 * wall of popups at startup.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var logText: TextView
    private lateinit var statusText: TextView
    private lateinit var speechInput: SpeechInput
    private lateinit var speaker: Speaker
    private lateinit var executor: ActionExecutor

    private data class Language(val label: String, val tag: String, val locale: Locale)
    private val languages = listOf(
        Language("English", "en-IN", Locale("en", "IN")),
        Language("Hindi (हिन्दी)", "hi-IN", Locale("hi", "IN")),
        Language("Marathi (मराठी)", "mr-IN", Locale("mr", "IN"))
    )
    private var currentLanguage = languages[0]

    // When the executor asks for a permission, we store its retry here
    // and run it if the user grants the permission.
    private var pendingRetry: (() -> ActionExecutor.Outcome)? = null
    private val permissionRequestCode = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logText = findViewById(R.id.logText)
        statusText = findViewById(R.id.statusText)
        executor = ActionExecutor(this)
        speaker = Speaker(this)
        speechInput = SpeechInput(
            this,
            onResult = { text -> onHeard(text) },
            onError = { message -> setStatus(message); speaker.say(message) }
        )

        val spinner = findViewById<Spinner>(R.id.languageSpinner)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            languages.map { it.label }
        )
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                currentLanguage = languages[pos]
                speechInput.languageTag = currentLanguage.tag
                speaker.locale = currentLanguage.locale
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.micButton).setOnClickListener {
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                startListening()
            } else {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.RECORD_AUDIO), permissionRequestCode
                )
            }
        }

        findViewById<Button>(R.id.settingsButton).setOnClickListener { showApiKeyDialog() }

        if (apiKey().isBlank()) {
            log("⚠️ No Groq API key yet. Tap 'Set Groq API key' and paste your free key from console.groq.com.")
        }
    }

    // ---- conversation flow -------------------------------------------------

    private fun startListening() {
        setStatus("Listening…")
        speechInput.startListening()
    }

    private fun onHeard(text: String) {
        log("🧑 You: $text")
        setStatus("Thinking…")
        val brain = Brain(apiKey())
        brain.think(text, currentLanguage.label) { decision, error ->
            runOnUiThread {
                when {
                    error != null -> {
                        setStatus("Error")
                        log("⚠️ $error")
                        speaker.say("Sorry, something went wrong.")
                    }
                    decision is Brain.Decision.Say -> {
                        log("🤖 Agent: ${decision.text}")
                        setStatus("Tap the mic and speak.")
                        speaker.say(decision.text)
                    }
                    decision is Brain.Decision.Act -> handleAction(decision)
                }
            }
        }
    }

    private fun handleAction(action: Brain.Decision.Act) {
        log("🎯 Action: ${action.tool} ${action.args}")
        if (executor.isSensitive(action.tool)) {
            // CONFIRM GATE: speak + show the exact action, wait for the user.
            val description = executor.describe(action)
            speaker.say(description)
            AlertDialog.Builder(this)
                .setTitle("Confirm")
                .setMessage(description)
                .setPositiveButton("Confirm") { _, _ -> runOutcome(executor.execute(action)) }
                .setNegativeButton("Cancel") { _, _ ->
                    speaker.say("Cancelled.")
                    setStatus("Cancelled. Tap the mic and speak.")
                }
                .show()
        } else {
            runOutcome(executor.execute(action))
        }
    }

    private fun runOutcome(outcome: ActionExecutor.Outcome) {
        when (outcome) {
            is ActionExecutor.Outcome.Done -> {
                log("✅ ${outcome.spoken}")
                setStatus("Tap the mic and speak.")
                speaker.say(outcome.spoken)
            }
            is ActionExecutor.Outcome.Failed -> {
                log("❌ ${outcome.spoken}")
                setStatus("Tap the mic and speak.")
                speaker.say(outcome.spoken)
            }
            is ActionExecutor.Outcome.NeedsPermission -> {
                pendingRetry = outcome.retry
                ActivityCompat.requestPermissions(
                    this, arrayOf(outcome.permission), permissionRequestCode
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != permissionRequestCode) return
        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        val retry = pendingRetry
        pendingRetry = null
        when {
            granted && retry != null -> runOutcome(retry())
            granted -> startListening() // the mic-permission path
            else -> {
                Toast.makeText(this, "Permission denied — that action can't run.", Toast.LENGTH_LONG).show()
                setStatus("Permission denied.")
            }
        }
    }

    // ---- API key -------------------------------------------------------------

    private fun apiKey(): String =
        getSharedPreferences("phone_agent", MODE_PRIVATE).getString("groq_api_key", "") ?: ""

    private fun showApiKeyDialog() {
        val input = EditText(this).apply {
            hint = "gsk_…"
            setText(apiKey())
        }
        AlertDialog.Builder(this)
            .setTitle("Groq API key")
            .setMessage("Paste your free key from console.groq.com. It stays on this phone.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                getSharedPreferences("phone_agent", MODE_PRIVATE)
                    .edit().putString("groq_api_key", input.text.toString().trim()).apply()
                Toast.makeText(this, "Key saved.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- helpers ---------------------------------------------------------------

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun log(line: String) {
        logText.append(line + "\n\n")
    }

    private fun setStatus(text: String) {
        statusText.text = text
    }

    override fun onDestroy() {
        speechInput.destroy()
        speaker.shutdown()
        super.onDestroy()
    }
}
