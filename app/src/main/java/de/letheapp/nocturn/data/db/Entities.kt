package de.letheapp.nocturn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val label: String = "",
    val repeatDays: Int = 0,
    val soundUri: String? = null,
    val rotationId: Long? = null,
    /** Verknüpfung mit einem Job im Schüler/Student-Modus statt mit einer Rotation (siehe [JobEntity.mode]). */
    val timetableJobId: Long? = null,
    /**
     * ioBroker-Datenpunkt-ID (z. B. "hm-rpc.0.OEQ123.1.STATE"), der beim Auslösen des
     * Notfall-Alarms (siehe [de.letheapp.nocturn.emergency.EmergencyAlarmService]) auf "true"
     * gesetzt wird, um eine Lampe/Steckdose einzuschalten. Null = keine Smart-Home-Kopplung.
     */
    val lampDataPointId: String? = null,
    /** Anzeigename des [lampDataPointId] zum Zeitpunkt der Auswahl, fürs UI ohne erneuten Verbindungsaufbau. */
    val lampDataPointLabel: String? = null
)

/** Ob ein Job als klassische Schichtarbeit (Rotation) oder als Wochenstundenplan geführt wird. */
enum class JobMode {
    SHIFT,
    STUDENT
}

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mode: String = JobMode.SHIFT.name
)

@Entity(
    tableName = "shifts",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("jobId")]
)
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val name: String,
    val colorArgb: Int,
    val startHour: Int,
    val startMinute: Int,
    val durationMinutes: Int,
    /** Minuten vor Schichtbeginn, zu denen der (erste) Alarm klingelt. 0 = genau zum Schichtbeginn. */
    val earliestWakeOffsetMinutes: Int = 0,
    /** Minuten vor Schichtbeginn, bis zu denen laut Aufsteh-Erkennung aufgestanden sein muss. 0 = genau zum Schichtbeginn. */
    val latestWakeupOffsetMinutes: Int = 0
)

/**
 * Eine Unterrichtsstunde im Wochenstundenplan eines Jobs im Schüler/Student-Modus
 * ([JobEntity.mode] == [JobMode.STUDENT]). Der Stundenplan wiederholt sich wöchentlich,
 * es gibt keinen mehrtägigen Rotationszyklus wie bei Schichtarbeit. Nur die zeitlich erste
 * Stunde eines Wochentags ist für die Wecker-Ableitung relevant (siehe WakeWindowResolver).
 */
@Entity(
    tableName = "lessons",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("jobId")]
)
data class LessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    /** 0 = Montag .. 6 = Sonntag. */
    val weekday: Int,
    val name: String,
    val colorArgb: Int,
    val startHour: Int,
    val startMinute: Int,
    val durationMinutes: Int,
    val earliestWakeOffsetMinutes: Int = 0,
    val latestWakeupOffsetMinutes: Int = 0
)

@Entity(
    tableName = "rotations",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("jobId")]
)
data class RotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val name: String,
    val startDateEpochDay: Long
)

@Entity(
    tableName = "rotation_days",
    foreignKeys = [
        ForeignKey(
            entity = RotationEntity::class,
            parentColumns = ["id"],
            childColumns = ["rotationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("rotationId"), Index("shiftId")]
)
data class RotationDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rotationId: Long,
    val dayIndex: Int,
    val shiftId: Long?
)

/**
 * Vom Wecker fortgeschriebener Kalendereintrag: welche Schicht an welchem Tag laut
 * Rotation gilt. Wird beim Speichern einer Rotation für mehrere Jahre im Voraus
 * neu berechnet (siehe CalendarRepository.regenerateCalendarEntries).
 */
@Entity(
    tableName = "calendar_entries",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RotationEntity::class,
            parentColumns = ["id"],
            childColumns = ["rotationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("jobId"), Index("rotationId"), Index("shiftId"), Index("dateEpochDay")]
)
data class CalendarEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val rotationId: Long,
    val dateEpochDay: Long,
    val shiftId: Long?
)

/** Wichtigkeit eines frei angelegten Termins, wählbar beim Anlegen über die Kalender-Monatsansicht. */
enum class AppointmentPriority {
    LOW,
    NORMAL,
    HIGH
}

/**
 * Frei angelegter Kalender-Termin (z. B. Kinoticket, Arzttermin) — unabhängig von
 * Schichten/Rotationen. Wird sowohl über die eigene Kalender-Ansicht angelegt als auch
 * über externe Apps, die NocTurn im Android-"Zum Kalender hinzufügen"-Menü auswählen
 * (siehe [de.letheapp.nocturn.calendar.AddAppointmentActivity]).
 */
@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val location: String = "",
    val description: String = "",
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    /** Für die Tag-Zuordnung in der Monatsansicht vorab aus den Millis berechnet. */
    val startEpochDay: Long,
    val endEpochDay: Long,
    val allDay: Boolean = false,
    val colorArgb: Int,
    val priority: String = AppointmentPriority.NORMAL.name,
    /** Minuten vor [startEpochMillis] für eine Erinnerungs-Benachrichtigung, null = keine Erinnerung. */
    val reminderMinutesBefore: Int? = null,
    /** True = seit dem letzten Nextcloud-Sync lokal verändert/neu angelegt, muss noch hochgeladen werden. */
    val dirty: Boolean = true
)

/**
 * Manuell für einen einzelnen Tag eingetragene Schicht, unabhängig von einer Rotation
 * (siehe FAB in [de.letheapp.nocturn.calendar.CalendarMonthScreen], Option "Schicht").
 * Wird zusätzlich zu ggf. vorhandenen Rotations-Einträgen ([CalendarEntryEntity]) desselben
 * Tages angezeigt, nicht mit ihnen verrechnet.
 */
@Entity(
    tableName = "manual_shift_entries",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("jobId"), Index("shiftId"), Index("dateEpochDay")]
)
data class ManualShiftEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val shiftId: Long,
    val dateEpochDay: Long
)

/** Ob ein gecachter [HolidayEntity]-Eintrag ein gesetzlicher Feiertag oder eine Schulferien-Periode ist. */
enum class HolidayType {
    PUBLIC,
    SCHOOL
}

/**
 * Einstellung für die Ferien-/Feiertage-Anzeige im Kalender (siehe CLAUDE.md, 3-Punkte-Menü
 * "Ferien & Feiertage"): gewähltes Bundesland (ISO-3166-2-Subdivision-Code, z. B. "DE-NW")
 * sowie ob Feiertage/Ferien jeweils eingeblendet werden sollen. Singleton-Tabelle, analog
 * [CalDavAccountEntity].
 */
@Entity(tableName = "holiday_settings")
data class HolidaySettingsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subdivisionCode: String,
    val subdivisionLabel: String,
    val showPublicHolidays: Boolean = true,
    val showSchoolHolidays: Boolean = true
)

/**
 * Lokal gecachter Feiertag/Ferienzeitraum, von der kostenlosen, schlüssellosen
 * Schulferien-/Feiertage-API von schulferiendaten.de pro Bundesland und Jahr nachgeladen (siehe
 * [de.letheapp.nocturn.holidays.HolidayRepository]), damit der Kalender auch offline
 * funktioniert und nicht bei jedem Öffnen neu abgefragt werden muss.
 */
@Entity(
    tableName = "holidays",
    indices = [Index(value = ["subdivisionCode", "type", "startEpochDay"]), Index("endEpochDay")]
)
data class HolidayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subdivisionCode: String,
    val type: String,
    val name: String,
    val startEpochDay: Long,
    val endEpochDay: Long
)

/**
 * Ein per CalDAV verbundenes Nextcloud-Konto (siehe CLAUDE.md "Synchronisierbar mit ... Nextcloud
 * (CalDAV)"). Es wird genau ein Zielkalender gewählt, in den NocTurn synchronisiert. Das
 * App-Passwort liegt NICHT hier, sondern AES/Android-Keystore-verschlüsselt in
 * [de.letheapp.nocturn.sync.NextcloudCredentialsStore].
 */
@Entity(tableName = "caldav_accounts")
data class CalDavAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverUrl: String,
    val username: String,
    val calendarHref: String,
    val calendarDisplayName: String,
    val syncAppointments: Boolean = true,
    val syncShifts: Boolean = true,
    val lastSyncEpochMillis: Long = 0,
    val lastSyncError: String? = null
)

/** Welcher lokale Datentyp über CalDAV synchronisiert wird (siehe [SyncedEventEntity]). */
enum class SyncedLocalType {
    APPOINTMENT,
    SHIFT
}

/**
 * Merkt sich pro Nextcloud-Konto, welchem lokalen Datensatz welches Remote-Event (Href + ETag)
 * entspricht — Grundlage, um beim nächsten Sync-Lauf zu erkennen, ob sich lokal oder remote etwas
 * geändert hat, ohne jedes Mal alles neu hochzuladen.
 * [localKey] ist bei Terminen die Appointment-Id als String, bei Schichten
 * "<rotationId>:<dateEpochDay>" (siehe [de.letheapp.nocturn.sync.NextcloudSyncRepository]).
 */
@Entity(
    tableName = "synced_events",
    foreignKeys = [
        ForeignKey(
            entity = CalDavAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId"), Index(value = ["accountId", "localType", "localKey"], unique = true)]
)
data class SyncedEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val localType: String,
    val localKey: String,
    val href: String,
    val etag: String,
    /** Einfacher Inhalts-Hash (nur bei Schichten genutzt), um unveränderte Events nicht erneut hochzuladen. */
    val contentHash: Int = 0
)

/**
 * Ein verbundenes ioBroker-Konto (siehe CLAUDE.md "Smart-Home-Kopplung (ioBroker)"). Es wird genau
 * ein Server verbunden, dessen Datenpunkte einem Alarm als Lampe/Steckdose zugeordnet werden können
 * (siehe [AlarmEntity.lampDataPointId]). Das Passwort liegt NICHT hier, sondern AES/Android-
 * Keystore-verschlüsselt in [de.letheapp.nocturn.iobroker.IoBrokerCredentialsStore].
 */
@Entity(tableName = "iobroker_accounts")
data class IoBrokerAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val port: Int,
    val useHttps: Boolean = false,
    val username: String = ""
)
