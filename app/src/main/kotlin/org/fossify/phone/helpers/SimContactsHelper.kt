package org.fossify.phone.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SubscriptionManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact

object SimContactsHelper {
    const val SOURCE_SIM = "SIM"

    fun getContacts(context: Context, existing: List<Contact>): ArrayList<Contact> {
        val result = ArrayList<Contact>()
        val seen = HashSet<String>()
        existing.forEach { contact ->
            contact.phoneNumbers.forEach { seen.add(key(contact.getNameToDisplay(), it.normalizedNumber)) }
        }

        var nextId = -1
        getSimUris(context).forEach { uri ->
            readUri(context, uri).forEach { (name, number) ->
                val normalized = number.normalizePhoneNumber()
                if (normalized.isEmpty() || !seen.add(key(name, normalized))) return@forEach

                result.add(
                    Contact(
                        id = nextId,
                        firstName = name,
                        phoneNumbers = arrayListOf(
                            PhoneNumber(
                                value = number,
                                type = Phone.TYPE_MOBILE,
                                label = "",
                                normalizedNumber = normalized
                            )
                        ),
                        source = SOURCE_SIM,
                        contactId = nextId
                    )
                )
                nextId--
            }
        }
        return result
    }

    private fun key(name: String, number: String) = "${name.trim().lowercase()}|${number.normalizePhoneNumber()}"

    private fun getSimUris(context: Context): List<Uri> {
        val uris = arrayListOf(Uri.parse("content://icc/adn"))
        val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        if (hasPhoneState) {
            try {
                val manager = context.getSystemService(SubscriptionManager::class.java)
                manager?.activeSubscriptionInfoList?.forEach {
                    uris.add(Uri.parse("content://icc/adn/subId/${it.subscriptionId}"))
                }
            } catch (ignored: SecurityException) {
            }
        }
        return uris
    }

    private fun readUri(context: Context, uri: Uri): List<Pair<String, String>> {
        val rows = ArrayList<Pair<String, String>>()
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                val numberIndex = cursor.getColumnIndex("number")
                if (nameIndex == -1 || numberIndex == -1) return rows
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)?.trim().orEmpty()
                    val number = cursor.getString(numberIndex)?.trim().orEmpty()
                    if (number.isNotEmpty()) {
                        rows.add(Pair(name.ifEmpty { number }, number))
                    }
                }
            }
        } catch (ignored: Exception) {
        }
        return rows
    }
}
