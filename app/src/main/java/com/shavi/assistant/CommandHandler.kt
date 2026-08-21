package com.shavi.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Executes the action that Gemini decided on. This is where SHAVi actually
 * touches the phone: dialing, reading contacts, opening apps, sending SMS.
 */
class CommandHandler(private val context: Context) {

    /** Returns the current time as a spoken Hindi/Hinglish sentence. */
    fun getSpokenTime(): String {
        val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val now = format.format(Date())
        return "Abhi time hai $now"
    }

    /**
     * Looks up a contact by (partial, case-insensitive) name and returns
     * their phone number, or null if not found.
     */
    @SuppressLint("Range")
    fun findContactNumber(name: String): String? {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
            }
        }
        return null
    }

    /**
     * Places a direct call using CALL_PHONE permission.
     * Falls back to the dialer (ACTION_DIAL) if permission is missing.
     */
    fun callContact(name: String, hasCallPermission: Boolean): String {
        val number = findContactNumber(name)
            ?: return "$name naam ka koi contact nahi mila."

        val intent = if (hasCallPermission) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        } else {
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        return if (hasCallPermission) "$name ko call laga rahi hoon." else "Dialer khol rahi hoon $name ke liye."
    }

    /** Opens an installed app by its (partial) visible name. */
    fun openApp(appName: String): String {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)

        val match = apps.firstOrNull {
            pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true)
        }

        return if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                "$appName khol rahi hoon."
            } else {
                "$appName ko khol nahi payi."
            }
        } else {
            "$appName naam ka app nahi mila."
        }
    }

    /** Sends an SMS to a contact by name. Requires SEND_SMS permission. */
    fun sendMessage(name: String, message: String, hasSmsPermission: Boolean): String {
        val number = findContactNumber(name)
            ?: return "$name naam ka koi contact nahi mila."

        if (!hasSmsPermission) {
            return "Message bhejne ke liye SMS permission chahiye."
        }

        return try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(number, null, message, null, null)
            "$name ko message bhej diya."
        } catch (e: Exception) {
            "Message bhejte waqt error aaya: ${e.message}"
        }
    }

    /** Triggers a scroll gesture through the Accessibility Service, if enabled. */
    fun scroll(direction: String): String {
        val service = ShaviAccessibilityService.instance
        return if (service != null) {
            service.performScroll(direction)
            "Scroll kar rahi hoon."
        } else {
            "Scroll karne ke liye pehle Accessibility permission on karein SHAVi ke liye."
        }
    }
}
