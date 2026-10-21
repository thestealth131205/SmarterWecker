package de.letheapp.nocturn.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Eine aus den Geräte-Kontakten gelesene E-Mail-Adresse für die Erinnerungs-Empfänger-Auswahl. */
data class ContactEmailSuggestion(
    val displayName: String,
    val address: String
)

/**
 * Liest E-Mail-Adressen aus den Geräte-Kontakten für die Empfänger-Autocomplete bei der
 * E-Mail-Erinnerung (siehe [de.letheapp.nocturn.calendar.AddAppointmentScreen]) — dieselbe
 * Android-Boardmittel-Quelle, die auch Mail-Apps wie VectoMail für ihr eigenes Adressbuch nutzen,
 * daher kein zusätzlicher, VectoMail-spezifischer Zugriffsweg nötig. Braucht
 * [Manifest.permission.READ_CONTACTS] (bereits für die Geburtstags-Anzeige vorhanden); ohne
 * Berechtigung liefert die Suche einfach eine leere Liste.
 */
@Singleton
class ContactEmailRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun search(query: String, limit: Int = 8): List<ContactEmailSuggestion> {
        if (!hasPermission() || query.isBlank()) return emptyList()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME
        )
        val selection = "${ContactsContract.CommonDataKinds.Email.ADDRESS} LIKE ? OR " +
            "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME} LIKE ?"
        val likeArg = "%$query%"

        val result = mutableListOf<ContactEmailSuggestion>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            projection,
            selection,
            arrayOf(likeArg, likeArg),
            null
        )?.use { cursor ->
            val addressIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
            while (cursor.moveToNext() && result.size < limit) {
                val address = cursor.getString(addressIdx) ?: continue
                val name = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: address
                result += ContactEmailSuggestion(displayName = name, address = address)
            }
        }
        return result.distinctBy { it.address }
    }
}
