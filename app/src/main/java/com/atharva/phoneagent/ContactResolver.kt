package com.atharva.phoneagent

import android.content.Context
import android.provider.ContactsContract

/**
 * CONTACTS — turns a spoken name ("Mom") into a phone number.
 *
 * WHY it exists: the brain works with names humans use; Android
 * actions need numbers. This class is the translator between them.
 * It returns ALL matches, because guessing between two "Rahul"s is
 * the worst mistake this app can make — the caller must disambiguate.
 */
data class ContactMatch(val name: String, val number: String)

object ContactResolver {

    fun find(context: Context, spokenName: String): List<ContactMatch> {
        val results = mutableListOf<ContactMatch>()
        val seenNumbers = mutableSetOf<String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val needle = spokenName.trim().lowercase()
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                val number = (cursor.getString(numIdx) ?: continue).replace(" ", "")
                if (number in seenNumbers) continue
                // Match: exact, or the spoken name appears inside the contact
                // name ("mom" in "Mom"), or a word of the name matches.
                val lowerName = name.lowercase()
                val matches = lowerName == needle ||
                    lowerName.contains(needle) ||
                    lowerName.split(" ").any { it == needle }
                if (matches) {
                    seenNumbers.add(number)
                    results.add(ContactMatch(name, number))
                }
            }
        }
        return results
    }

    /** Digits-only international format for wa.me links (defaults to India +91). */
    fun toInternational(number: String): String {
        val digits = number.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "91$digits"              // bare Indian mobile
            digits.startsWith("0") && digits.length == 11 -> "91" + digits.drop(1)
            else -> digits                                   // already has country code
        }
    }
}
