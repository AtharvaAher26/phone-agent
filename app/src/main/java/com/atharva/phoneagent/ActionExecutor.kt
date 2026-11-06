package com.atharva.phoneagent

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * HANDS — executes the brain's structured action.
 *
 * HOW: each tool maps to the Android mechanism that really does it.
 * Two flavours:
 *  - INTENTS: Android's official request system — we hand a structured
 *    request to the responsible app (Clock, YouTube, Maps, WhatsApp).
 *    Reliable and update-proof; used wherever an intent exists.
 *  - DIRECT APIs: only where no intent path exists — flashlight
 *    (CameraManager torch) and SMS (SmsManager).
 *
 * Every function returns a sentence for the agent to SPEAK. Failure is
 * always reported, never silent.
 *
 * Sensitive actions (call / SMS / WhatsApp) are NOT executed here until
 * MainActivity's CONFIRM GATE has approved them.
 */
class ActionExecutor(private val activity: Activity) {

    sealed class Outcome {
        data class Done(val spoken: String) : Outcome()
        /** Needs a permission first; MainActivity will request and retry. */
        data class NeedsPermission(val permission: String, val retry: () -> Outcome) : Outcome()
        data class Failed(val spoken: String) : Outcome()
    }

    fun describe(action: Brain.Decision.Act): String {
        val a = action.args
        return when (action.tool) {
            "make_call" -> "Call ${a.optString("contact")}?"
            "send_sms" -> "Send SMS to ${a.optString("contact")}: \"${a.optString("message")}\"?"
            "send_whatsapp" -> "Send WhatsApp to ${a.optString("contact")}: \"${a.optString("message")}\"?"
            else -> action.tool
        }
    }

    fun isSensitive(tool: String) = tool in setOf("make_call", "send_sms", "send_whatsapp")

    fun execute(action: Brain.Decision.Act): Outcome {
        val a = action.args
        return try {
            when (action.tool) {
                "make_call" -> makeCall(a.getString("contact"))
                "send_sms" -> sendSms(a.getString("contact"), a.getString("message"))
                "send_whatsapp" -> sendWhatsApp(a.getString("contact"), a.getString("message"))
                "set_alarm" -> setAlarm(a.getInt("hour"), a.getInt("minute"), a.optString("label"))
                "set_reminder" -> setReminder(a.getString("title"), a.optInt("in_minutes", 60))
                "open_app" -> openApp(a.getString("app"))
                "flashlight" -> flashlight(a.getBoolean("on"))
                "open_wifi_settings" -> openWifi()
                "play_media" -> playMedia(a.getString("query"))
                "navigate" -> navigate(a.getString("place"))
                else -> Outcome.Failed("I don't know how to do that yet.")
            }
        } catch (e: Exception) {
            Outcome.Failed("That didn't work: ${e.message}")
        }
    }

    // ---- contacts-based actions -------------------------------------------

    private fun resolveOne(spokenName: String): Pair<ContactMatch?, String?> {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            return null to "PERMISSION:" + Manifest.permission.READ_CONTACTS
        }
        val matches = ContactResolver.find(activity, spokenName)
        return when {
            matches.isEmpty() -> null to "I couldn't find $spokenName in your contacts."
            matches.size > 1 -> null to
                "I found ${matches.size} contacts matching $spokenName: " +
                    matches.joinToString(", ") { it.name } + ". Please say the full name."
            else -> matches[0] to null
        }
    }

    private fun makeCall(contactName: String): Outcome {
        val (contact, problem) = resolveOne(contactName)
        if (problem != null) return problemOutcome(problem) { makeCall(contactName) }
        contact!!
        return if (hasPermission(Manifest.permission.CALL_PHONE)) {
            activity.startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:${contact.number}"))
            )
            Outcome.Done("Calling ${contact.name}.")
        } else {
            // No CALL permission: open the dialer pre-filled instead of failing.
            activity.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.number}"))
            )
            Outcome.Done("Opening the dialer for ${contact.name}. Tap call to dial.")
        }
    }

    private fun sendSms(contactName: String, message: String): Outcome {
        val (contact, problem) = resolveOne(contactName)
        if (problem != null) return problemOutcome(problem) { sendSms(contactName, message) }
        contact!!
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            return Outcome.NeedsPermission(Manifest.permission.SEND_SMS) { sendSms(contactName, message) }
        }
        val sms = activity.getSystemService(SmsManager::class.java)
            ?: @Suppress("DEPRECATION") SmsManager.getDefault()
        sms.sendTextMessage(contact.number, null, message, null, null)
        return Outcome.Done("SMS sent to ${contact.name}.")
    }

    private fun sendWhatsApp(contactName: String, message: String): Outcome {
        val (contact, problem) = resolveOne(contactName)
        if (problem != null) return problemOutcome(problem) { sendWhatsApp(contactName, message) }
        contact!!
        val intl = ContactResolver.toInternational(contact.number)
        val uri = Uri.parse("https://wa.me/$intl?text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")
        return try {
            activity.startActivity(intent)
            Outcome.Done("Opening WhatsApp for ${contact.name} with your message ready. Tap send to deliver.")
        } catch (e: Exception) {
            Outcome.Failed("WhatsApp doesn't seem to be installed on this phone.")
        }
    }

    // ---- intent-based actions ----------------------------------------------

    private fun setAlarm(hour: Int, minute: Int, label: String): Outcome {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        activity.startActivity(intent)
        val labelPart = if (label.isBlank()) "" else " for $label"
        return Outcome.Done("Alarm set for $hour:${"%02d".format(minute)}$labelPart.")
    }

    private fun setReminder(title: String, inMinutes: Int): Outcome {
        val start = System.currentTimeMillis() + inMinutes * 60_000L
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 30 * 60_000L)
        }
        activity.startActivity(intent)
        return Outcome.Done("Reminder created: $title, in $inMinutes minutes. Confirm it in your calendar.")
    }

    private fun openApp(appName: String): Outcome {
        val pm = activity.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(mainIntent, 0)
        val needle = appName.trim().lowercase()
        val match = apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase().contains(needle)
        } ?: return Outcome.Failed("I couldn't find an app called $appName.")
        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return Outcome.Failed("That app can't be opened.")
        activity.startActivity(launch)
        return Outcome.Done("Opening ${match.loadLabel(pm)}.")
    }

    private fun playMedia(query: String): Outcome {
        val intent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra("query", query)
        }
        return try {
            activity.startActivity(intent)
            Outcome.Done("Searching YouTube for $query.")
        } catch (e: Exception) {
            // Fall back to a web search if the YouTube app is absent.
            val web = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            )
            activity.startActivity(web)
            Outcome.Done("Opening YouTube in the browser for $query.")
        }
    }

    private fun navigate(place: String): Outcome {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=${Uri.encode(place)}")
        ).setPackage("com.google.android.apps.maps")
        return try {
            activity.startActivity(intent)
            Outcome.Done("Starting navigation to $place.")
        } catch (e: Exception) {
            Outcome.Failed("Google Maps doesn't seem to be installed on this phone.")
        }
    }

    // ---- direct-API actions --------------------------------------------------

    private fun flashlight(on: Boolean): Outcome {
        val cm = activity.getSystemService(CameraManager::class.java)
        val cameraId = cm.cameraIdList.firstOrNull()
            ?: return Outcome.Failed("This phone has no camera flash.")
        cm.setTorchMode(cameraId, on)
        return Outcome.Done(if (on) "Flashlight on." else "Flashlight off.")
    }

    private fun openWifi(): Outcome {
        activity.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        return Outcome.Done("Opening Wi-Fi settings.")
    }

    // ---- helpers ---------------------------------------------------------------

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun problemOutcome(problem: String, retry: () -> Outcome): Outcome {
        return if (problem.startsWith("PERMISSION:")) {
            Outcome.NeedsPermission(problem.removePrefix("PERMISSION:"), retry)
        } else {
            Outcome.Failed(problem)
        }
    }
}
