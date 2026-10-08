package de.letheapp.nocturn.sound

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SoundOption(
    val uri: Uri,
    val title: String,
    val isCustomFile: Boolean = false
)

@Singleton
class SoundRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun defaultAlarmSoundUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: Uri.EMPTY

    fun systemAlarmSounds(): List<SoundOption> {
        val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
        val cursor = manager.cursor
        val result = mutableListOf<SoundOption>()
        while (cursor.moveToNext()) {
            val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
            val uri = manager.getRingtoneUri(cursor.position)
            result += SoundOption(uri, title)
        }
        return result
    }

    /** Vom Nutzer bereits dauerhaft freigegebene lokale Audiodateien (persistente URI-Berechtigung). */
    fun customSounds(): List<SoundOption> =
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { permission ->
                SoundOption(
                    uri = permission.uri,
                    title = displayNameFor(permission.uri) ?: permission.uri.lastPathSegment.orEmpty(),
                    isCustomFile = true
                )
            }

    fun takePersistableAccess(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun displayNameFor(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }.getOrNull()
}
