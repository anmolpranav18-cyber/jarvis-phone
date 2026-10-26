package com.jarvis.phone

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Keyword command router. Runs before the Gemini fallback so common phone
 * actions work offline and instantly. Each handler returns a spoken reply.
 */
object Commands {

    private var torchOn = false

    /** Returns null when the text is not a local command, so the caller falls back to Gemini. */
    fun handle(context: Context, raw: String): String? {
        val text = raw.lowercase(Locale.getDefault()).trim()
        if (text.isBlank()) return null

        return when {
            containsAny(text, listOf("what time", "time is it", "current time", "tell me the time")) -> timeAnswer()
            containsAny(text, listOf("what date", "what day", "today's date")) -> dateAnswer()
            text.startsWith("call ") || containsAny(text, listOf("phone ", "make a call", "dial ")) -> dial(context, text)
            containsAny(text, listOf("set an alarm", "set alarm", "wake me", "alarm for")) -> alarm(context, text)
            containsAny(text, listOf("torch", "flashlight", "flash light")) -> torch(context, text)
            containsAny(text, listOf("open ", "launch ", "start ")) -> open(context, text)
            containsAny(text, listOf("settings", "bluetooth", "wifi", "wi-fi", "airplane")) -> openSettings(context, text)
            containsAny(text, listOf("battery", "how much charge")) -> batteryAnswer(context)
            containsAny(text, listOf("note ", "write a note", "remember that")) -> note(context, text)
            containsAny(text, listOf("clear history", "reset conversation", "new conversation")) -> {
                GeminiClient.clearHistory()
                "Conversation history cleared, sir."
            }
            else -> null
        }
    }

    private fun containsAny(text: String, words: List<String>) = words.any { it in text }

    private fun timeAnswer(): String {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        return "The time is ${fmt.format(Date())}."
    }

    private fun dateAnswer(): String {
        val fmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        return "Today is ${fmt.format(Date())}."
    }

    private fun dial(context: Context, text: String): String {
        // Extract digits if spoken, otherwise open the dialer with the name for the user to pick.
        val digits = text.filter { it.isDigit() }.take(15)
        val number = if (digits.length >= 7) digits else null
        val name = text.removePrefix("call ").trim()
        var resolvedNumber: String? = number
        if (resolvedNumber == null && name.isNotBlank()) {
            resolvedNumber = lookupContact(context, name)
        }
        val target = resolvedNumber ?: return "I could not find a number for $name. Say the number, or add the contact to your phone."
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$target"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "Dialing $target."
    }

    private fun lookupContact(context: Context, name: String): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            context.contentResolver.query(
                Uri.parse("content://com.android.contacts/phone_lookup"),
                null, null, null, null
            )?.use { null } // phone_lookup needs a URI with the number; fall through to search below.
            context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun alarm(context: Context, text: String): String {
        // Pull the hour and minute out of phrases like "set an alarm for 7:30 am" or "alarm at 7".
        val hourMatch = Regex("\\b(1[0-2]|0?[1-9])\\b").find(text)
        val minuteMatch = Regex(":\\s*(\\d{1,2})\\b").find(text)
        val meridiemPm = text.contains("pm") || text.contains("evening") || text.contains("night")
        val meridiemAm = text.contains("am") || text.contains("morning")
        if (hourMatch == null) return "What time should I set the alarm for?"
        var hour = hourMatch.groupValues[1].toInt()
        if (meridiemPm && hour < 12) hour += 12
        if (meridiemAm && hour == 12) hour = 0
        val minute = minuteMatch?.groupValues?.get(1)?.toInt() ?: 0
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return "Alarm set for %02d:%02d.".format(hour, minute)
    }

    private fun torch(context: Context, text: String): String {
        val turnOn = containsAny(text, listOf("on", "turn on", "enable")) && !text.contains("off")
        val turnOff = text.contains("off")
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "This phone has no flash unit I can control."
            if (turnOff || (torchOn && !turnOn)) {
                cameraManager.setTorchMode(cameraId, false)
                torchOn = false
                "Torch off."
            } else {
                cameraManager.setTorchMode(cameraId, true)
                torchOn = true
                "Torch on."
            }
        } catch (e: Exception) {
            "I could not reach the flashlight: ${e.message ?: "unknown error"}."
        }
    }

    private fun open(context: Context, text: String): String {
        val target = text.substringAfter("open ").substringAfter("launch ").substringAfter("start ").trim()
        val known = mapOf(
            "youtube" to "com.google.android.youtube",
            "maps" to "com.google.android.apps.maps",
            "camera" to null, // ACTION_IMAGE_CAPTURE below
            "calculator" to "com.android.calculator2",
            "whatsapp" to "com.whatsapp",
            "gmail" to "com.google.android.gm",
            "chrome" to "com.android.chrome",
            "play store" to "com.android.vending"
        )
        val key = known.keys.firstOrNull { target.startsWith(it) }
        return when {
            key == "camera" -> {
                context.startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Camera open."
            }
            key != null && launchPackage(context, known[key]!!) -> "$key open."
            else -> {
                // Try the device's launcher for anything else, e.g. "open spotify".
                val pm = context.packageManager
                val intent = pm.getLaunchIntentForPackage(target) ?: Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                return try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "Opening $target."
                } catch (e: Exception) {
                    "I could not find an app for $target."
                }
            }
        }
    }

    private fun launchPackage(context: Context, pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openSettings(context: Context, text: String): String {
        val action = when {
            "bluetooth" in text -> Settings.ACTION_BLUETOOTH_SETTINGS
            "wifi" in text || "wi-fi" in text -> Settings.ACTION_WIFI_SETTINGS
            "airplane" in text -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Settings open."
    }

    private fun batteryAnswer(context: Context): String {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            "Battery is at $level percent."
        } catch (e: Exception) {
            "I could not read the battery level."
        }
    }

    private fun note(context: Context, text: String): String {
        val body = text
            .removePrefix("note ")
            .removePrefix("write a note ")
            .removePrefix("remember that ")
            .trim()
        if (body.isBlank()) return "What should the note say?"
        context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
            .edit().putString("last_note", "${SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date())}: $body").apply()
        Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show()
        return "Noted."
    }
}
