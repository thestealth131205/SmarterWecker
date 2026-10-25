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
    val lampDataPointLabel: String? = null,
    /**
     * Markiert diesen Alarm als automatisch von [de.letheapp.nocturn.calendar.ShiftWakeupAlarmSyncer]
     * verwaltet (siehe [ShiftEntity.latestWakeupHour]): der Tag-Index (epoch day) der Schicht, für
     * die [hour]/[minute]/[label] aktuell stehen. Wird bei jeder Kalenderänderung neu berechnet,
     * nicht manuell vom Nutzer gepflegt. Null = ein normaler, manuell angelegter Alarm.
     */
    val autoShiftWakeupEpochDay: Long? = null,
    /**
     * Die [ShiftEntity.id], aus der [autoShiftWakeupEpochDay] abgeleitet wurde — ermöglicht es,
     * beim Wieder-Klingeln nach einem ohne Bewegung gelösten Notfall-Puzzle (siehe
     * [de.letheapp.nocturn.monitoring.AutomaticShiftRetryScheduler]) das "Frühestes Wecken"/
     * "Spätestens aufstehen"-Fenster der Schicht nachzuschlagen. Null bei normalen Alarmen.
     */
    val autoShiftWakeupShiftId: Long? = null,
    /**
     * Engt eine Verknüpfung mit [rotationId] optional auf eine einzelne Schicht ein (z. B. nur
     * "Frühschicht" statt jeder Schicht der Rotation) — nur wirksam, wenn [rotationId] gesetzt
     * ist. Null = jede Schicht der Rotation löst den Alarm aus (bisheriges Verhalten).
     */
    val linkedShiftId: Long? = null,
    /**
     * Engt eine Verknüpfung mit [timetableJobId] optional auf einen einzelnen Wochentag ein
     * (0 = Montag .. 6 = Sonntag, siehe [LessonEntity.weekday]) — nur wirksam, wenn
     * [timetableJobId] gesetzt ist. Null = jeder Unterrichtstag löst den Alarm aus (erste Stunde
     * des Tages, bisheriges Verhalten).
     */
    val linkedTimetableWeekday: Int? = null,
    /**
     * Bis zu drei beim Notfall-Alarm ausgelöste ioBroker-Aktionen (siehe
     * [de.letheapp.nocturn.iobroker.IoBrokerActionConfig], ausgewertet über die
     * `ioBrokerAction1()`/`ioBrokerAction2()`/`ioBrokerScriptAction()`-Erweiterungsfunktionen in
     * `IoBrokerAction.kt`, ausgeführt in [de.letheapp.nocturn.emergency.EmergencyAlarmService]):
     * zwei frei wählbare Datenpunkte (z. B. Lampe/Rollo) plus ein dritter, eigens für einen
     * Skript-Auslöser gedachter Datenpunkt (z. B. Radio). Jede Aktion setzt ihren Datenpunkt
     * entweder einmalig auf "true"/"false" ([ioBrokerAction1TurnOn] etc.) oder erhöht ihn
     * stufenweise ([ioBrokerAction1RampStep] pro Schritt bis [ioBrokerAction1RampTarget]) —
     * [ioBrokerAction1Mode] hält dafür den Namen von [de.letheapp.nocturn.iobroker.IoBrokerActionMode]
     * (null = [de.letheapp.nocturn.iobroker.IoBrokerActionMode.ON_OFF]). Lösen die alten Felder
     * [lampDataPointId]/[lampDataPointLabel] ab — die bleiben nur für die einmalige Migrations-
     * Übernahme nach [ioBrokerAction1DataPointId] deklariert, werden sonst nicht mehr gelesen.
     */
    val ioBrokerAction1DataPointId: String? = null,
    val ioBrokerAction1DataPointLabel: String? = null,
    val ioBrokerAction1Mode: String? = null,
    val ioBrokerAction1TurnOn: Boolean? = null,
    val ioBrokerAction1RampStep: Double? = null,
    val ioBrokerAction1RampTarget: Double? = null,
    /**
     * Nur bei [ioBrokerAction1Mode] == ON_OFF und [ioBrokerAction1TurnOn] == true relevant: schaltet
     * den Datenpunkt automatisch wieder aus, sobald der Notfall-Alarm endet (Puzzle gelöst oder
     * Bewegung erkannt, siehe [de.letheapp.nocturn.emergency.EmergencyAlarmService]), statt
     * dauerhaft eingeschaltet zu bleiben.
     */
    val ioBrokerAction1AutoTurnOff: Boolean = false,
    val ioBrokerAction2DataPointId: String? = null,
    val ioBrokerAction2DataPointLabel: String? = null,
    val ioBrokerAction2Mode: String? = null,
    val ioBrokerAction2TurnOn: Boolean? = null,
    val ioBrokerAction2RampStep: Double? = null,
    val ioBrokerAction2RampTarget: Double? = null,
    val ioBrokerAction2AutoTurnOff: Boolean = false,
    val ioBrokerScriptDataPointId: String? = null,
    val ioBrokerScriptDataPointLabel: String? = null,
    val ioBrokerScriptMode: String? = null,
    val ioBrokerScriptTurnOn: Boolean? = null,
    val ioBrokerScriptRampStep: Double? = null,
    val ioBrokerScriptRampTarget: Double? = null,
    val ioBrokerScriptAutoTurnOff: Boolean = false
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
    val latestWakeupOffsetMinutes: Int = 0,
    /**
     * Optionale, feste Uhrzeit (Stunde), bis zu der bei dieser Schicht spätestens aufgestanden sein
     * muss — anders als [latestWakeupOffsetMinutes] (nur wirksam bei einem manuell angelegten und
     * über [AlarmEntity.rotationId] verknüpften Alarm) ein bewusstes Opt-in direkt an der Schicht:
     * Ist sie gesetzt, zeigt NocTurn automatisch, ganz ohne manuell angelegten Alarm, im Wecker-
     * Screen einen Alarm mit dieser Uhrzeit für die nächste anstehende Schicht an (siehe
     * [de.letheapp.nocturn.calendar.ShiftWakeupAlarmSyncer]). Null = keine automatische Anzeige.
     */
    val latestWakeupHour: Int? = null,
    /** Minute zu [latestWakeupHour], nur relevant wenn dieser gesetzt ist. */
    val latestWakeupMinute: Int? = null,
    /**
     * Dieselben bis zu drei ioBroker-Aktionen wie bei [AlarmEntity] (siehe dort), nur pro Schicht
     * statt pro Alarm hinterlegt — ermöglicht z. B. bei Nachtschicht einen anderen/helleren
     * Weck-Ablauf als bei Spätschicht (am Nachmittag ist es ohnehin schon hell draußen). Werden
     * von [de.letheapp.nocturn.calendar.ShiftWakeupAlarmSyncer] in den jeweils automatisch
     * verwalteten [AlarmEntity] übernommen, sobald [latestWakeupHour] gesetzt ist.
     */
    val ioBrokerAction1DataPointId: String? = null,
    val ioBrokerAction1DataPointLabel: String? = null,
    val ioBrokerAction1Mode: String? = null,
    val ioBrokerAction1TurnOn: Boolean? = null,
    val ioBrokerAction1RampStep: Double? = null,
    val ioBrokerAction1RampTarget: Double? = null,
    val ioBrokerAction1AutoTurnOff: Boolean = false,
    val ioBrokerAction2DataPointId: String? = null,
    val ioBrokerAction2DataPointLabel: String? = null,
    val ioBrokerAction2Mode: String? = null,
    val ioBrokerAction2TurnOn: Boolean? = null,
    val ioBrokerAction2RampStep: Double? = null,
    val ioBrokerAction2RampTarget: Double? = null,
    val ioBrokerAction2AutoTurnOff: Boolean = false,
    val ioBrokerScriptDataPointId: String? = null,
    val ioBrokerScriptDataPointLabel: String? = null,
    val ioBrokerScriptMode: String? = null,
    val ioBrokerScriptTurnOn: Boolean? = null,
    val ioBrokerScriptRampStep: Double? = null,
    val ioBrokerScriptRampTarget: Double? = null,
    val ioBrokerScriptAutoTurnOff: Boolean = false
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
    val name: String
)

/**
 * Eine Schicht an einem Tag-Index einer Rotation. Pro [dayIndex] kann es mehrere Zeilen geben,
 * wenn an diesem Tag mehrere Schichten gleichzeitig gelten sollen (z. B. Frühschicht + ein
 * zusätzlicher Termin wie "Kinder-Wochenende") — sie werden im Kalender untereinander
 * dargestellt. Ein freier Tag ohne jede Schicht wird durch genau eine Zeile mit `shiftId = null`
 * markiert, damit der Tag-Index auch dann erhalten bleibt (siehe
 * [de.letheapp.nocturn.data.repository.CalendarRepository.saveRotation]).
 */
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
 * Rotation gilt. Entsteht erst, wenn die Rotations-Vorlage über die Kalender-
 * Seitenleiste einem Zeitraum zugewiesen wird (siehe CalendarRepository.applyRotationRange),
 * nicht schon beim Speichern der Vorlage selbst. Pro (rotationId, dateEpochDay) kann es mehrere
 * Zeilen geben, wenn an diesem Tag laut Rotationsmuster mehrere Schichten gleichzeitig gelten
 * (siehe [RotationDayEntity]) — ein freier Tag wird durch genau eine Zeile mit `shiftId = null`
 * dargestellt.
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
    /**
     * Entspricht dem iCalendar-Feld TRANSP (OPAQUE/TRANSPARENT): true = der Zeitraum gilt als
     * belegt (z. B. beim Nextcloud-CalDAV-Sync und künftigem Kalender-Teilen für andere sichtbar
     * "beschäftigt"), false = der Termin blockiert den Zeitraum nicht ("Frei"/"Abwesend" o. Ä.).
     */
    val showAsBusy: Boolean = true,
    /** True = seit dem letzten Nextcloud-Sync lokal verändert/neu angelegt, muss noch hochgeladen werden. */
    val dirty: Boolean = true
)

/**
 * Angehängte Datei (z. B. eingescanntes Dokument, Bestätigungs-PDF) zu einem [AppointmentEntity].
 * Es wird nur die vom System-Dateiauswahldialog gelieferte content://-URI persistiert (per
 * [android.content.ContentResolver.takePersistableUriPermission]) statt die Datei zu kopieren —
 * spart Speicherplatz und zusätzliche Kopier-Logik, erfordert aber, dass die Quell-App die URI
 * dauerhaft freigibt (bei den meisten Dokumentenanbietern, z. B. Google Drive/Dateien, der Fall).
 */
@Entity(
    tableName = "appointment_attachments",
    foreignKeys = [
        ForeignKey(
            entity = AppointmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["appointmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("appointmentId")]
)
data class AppointmentAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appointmentId: Long,
    val uri: String,
    val fileName: String,
    val mimeType: String? = null
)

/** Über welchen Weg eine [AppointmentReminderEntity] zugestellt wird. */
enum class ReminderChannel {
    NOTIFICATION,
    EMAIL
}

/**
 * Eine von mehreren möglichen Erinnerungen zu einem [AppointmentEntity] (per "+" im
 * Termin-Formular hinzufügbar, siehe [de.letheapp.nocturn.calendar.AddAppointmentScreen]) —
 * entweder eine normale Benachrichtigung oder eine E-Mail an [emailAddress] (siehe
 * [de.letheapp.nocturn.calendar.AppointmentReminderReceiver]).
 */
@Entity(
    tableName = "appointment_reminders",
    foreignKeys = [
        ForeignKey(
            entity = AppointmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["appointmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("appointmentId")]
)
data class AppointmentReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appointmentId: Long,
    val minutesBefore: Int,
    val channel: String = ReminderChannel.NOTIFICATION.name,
    val emailAddress: String? = null
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
    /** Default aus: Anzeige ist ein bewusst zu aktivierendes Opt-in, nicht automatisch an. */
    val showPublicHolidays: Boolean = false,
    val showSchoolHolidays: Boolean = false
)

/**
 * Lokal gecachter Feiertag/Ferienzeitraum, von der kostenlosen, schlüssellosen
 * Schulferien-/Feiertage-API von schulferiendaten.de pro Bundesland und Jahr nachgeladen (siehe
 * [de.letheapp.nocturn.holidays.HolidayRepository]), damit der Kalender auch offline
 * funktioniert und nicht bei jedem Öffnen neu abgefragt werden muss. Unique Index verhindert
 * doppelte Einträge, falls [de.letheapp.nocturn.holidays.HolidayRepository.ensureYearLoaded]
 * trotz Mutex-Absicherung einmal für dasselbe Bundesland/Jahr parallel aufgerufen wird.
 */
@Entity(
    tableName = "holidays",
    indices = [
        Index(value = ["subdivisionCode", "type", "startEpochDay"]),
        Index("endEpochDay"),
        Index(value = ["subdivisionCode", "type", "startEpochDay", "endEpochDay", "name"], unique = true)
    ]
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
    /**
     * Veraltet/ungenutzt: früher ein zusätzlicher, rein generischer CalDAV-Export der
     * Schicht-Vorkommen als normale Termine. Wurde entfernt, da er parallel zum strukturierten
     * Schedule-Sync ([syncNocturnSchedule]) zu doppelt angezeigten Schichten führte — Schichten
     * laufen jetzt ausschließlich über die NocTurn-Schedule-REST-API. Spalte bleibt zur
     * Schema-Kompatibilität bestehen (immer `false` für neue/aktualisierte Konten), ohne eigene
     * Migration, da sie nie gelesen wird.
     */
    val syncShifts: Boolean = true,
    val lastSyncEpochMillis: Long = 0,
    val lastSyncError: String? = null,
    /**
     * `since`-Cursor (Server-Millis) für den nächsten inkrementellen Abruf über
     * `/apps/nocturnkalender/v1/nocturn/sync` (siehe [de.letheapp.nocturn.sync.NocturnScheduleSyncRepository]).
     * 0 = noch nie synchronisiert, nächster Lauf holt den vollen Bestand.
     */
    val nocturnScheduleSyncToken: Long = 0,
    /**
     * Opt-in (Default aus): Jobs/Schichten/Rotationen bidirektional mit der NocTurn-Kalender-App
     * abstimmen (siehe [de.letheapp.nocturn.sync.NocturnScheduleSyncRepository]) — der einzige Weg,
     * wie Schichten in Nextcloud erscheinen (siehe [syncShifts]). Setzt voraus, dass die
     * NocTurn-Kalender-App auf dem Zielserver installiert ist (siehe
     * [de.letheapp.nocturn.sync.NextcloudSyncRepository.checkNocturnKalenderAvailable]).
     */
    val syncNocturnSchedule: Boolean = false
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
 * Welcher lokale Tabellentyp einem Server-seitigen NocTurn-Schedule-Datensatz entspricht (siehe
 * [NocturnSyncMappingEntity]). Rotations-generierte und manuelle Tageseinträge liegen in
 * unterschiedlichen lokalen Tabellen ([CalendarEntryEntity]/[ManualShiftEntryEntity]) mit jeweils
 * eigenem, bei 1 beginnendem Autoincrement — deshalb eigene Typen statt eines gemeinsamen
 * "OCCURRENCE", sonst würden z. B. CalendarEntry-Id 5 und ManualShiftEntry-Id 5 im selben
 * (localType, localId)-Schlüssel kollidieren.
 */
enum class NocturnSyncLocalType {
    JOB,
    SHIFT,
    ROTATION,
    CALENDAR_ENTRY,
    MANUAL_ENTRY,
    LESSON,
    TASK
}

/**
 * Merkt sich pro lokalem Datensatz (Job/Schicht/Rotation/Tageseintrag) das zugehörige
 * Server-`uid` sowie den Server-`updatedAt`-Zeitstempel und einen einfachen Inhalts-Hash,
 * damit [de.letheapp.nocturn.sync.NocturnScheduleSyncRepository] bei jedem Sync-Lauf erkennen
 * kann, ob sich lokal etwas geändert hat (Hash-Vergleich, ohne jedes Mal alles neu hochzuladen)
 * und ob eine eingehende Server-Änderung neuer ist als der zuletzt gesehene Stand (Last-Write-
 * Wins anhand `remoteUpdatedAt`, analog zur Server-Logik in NocturnScheduleService). Verzicht auf
 * eine `deletedAt`-Spalte hier bewusst: ein lokal gelöschter Datensatz wird daran erkannt, dass
 * die Ziel-Zeile (z. B. in `jobs`) nicht mehr existiert, während diese Mapping-Zeile (ohne eigene
 * Fremdschlüssel-Kaskade) noch da ist.
 */
@Entity(
    tableName = "nocturn_sync_mappings",
    indices = [
        Index(value = ["localType", "localId"], unique = true),
        Index(value = ["remoteUid"], unique = true)
    ]
)
data class NocturnSyncMappingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localType: String,
    val localId: Long,
    val remoteUid: String,
    val remoteUpdatedAt: Long,
    val contentHash: Int = 0
)

/**
 * Eine Aufgabe (Task) im Kalender — anders als ein [AppointmentEntity] nicht zeitgebunden, sondern
 * ein abhakbarer To-Do-Eintrag mit Fälligkeitstag, angelehnt an Google Tasks (siehe Nutzer-
 * Referenzbild: Titel, Ganztägig/Frist-Uhrzeit, Details). [dueEpochMillis] ist nur gesetzt, wenn
 * [allDay] false ist (konkrete Fälligkeits-Uhrzeit statt nur -tag).
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val dueEpochDay: Long,
    val dueEpochMillis: Long? = null,
    val allDay: Boolean = true,
    val completed: Boolean = false,
    val colorArgb: Int
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

/** Welche Art von Kalenderquelle ein [WearVisibleSourceEntity]-Eintrag schaltet. */
enum class WearSourceType {
    /** Eine [JobEntity] — schaltet deren [ManualShiftEntryEntity]-Einzeleinträge frei. */
    JOB,
    /** Eine [RotationEntity] — schaltet deren fortgeschriebene [CalendarEntryEntity]-Einträge frei. */
    ROTATION,
    BIRTHDAYS,
    PUBLIC_HOLIDAYS,
    SCHOOL_HOLIDAYS
}

/**
 * Schaltet pro Kalenderquelle (Job, Rotation, Geburtstage, Feiertage, Ferien) frei, ob sie an eine
 * gekoppelte WearOS-Smartwatch übertragen wird (siehe "WearOS-Verwaltung" im 3-Punkte-Menü der
 * Kalender-Monatsansicht, [de.letheapp.nocturn.wear.WearSettingsScreen]) — unabhängig davon, ob die
 * Quelle auf dem Handy selbst gerade angezeigt wird. Fehlender Eintrag = nicht aktiviert (Opt-in,
 * analog [HolidaySettingsEntity], Default aus). [sourceKey] ist bei [WearSourceType.JOB]/[ROTATION]
 * die jobs.id/rotations.id, bei den drei Singleton-Quellen immer `0L` (Sentinel — echte
 * Autogenerate-IDs beginnen bei 1, daher kollisionsfrei und ohne die SQLite-Falle, dass zwei NULL-
 * Werte in einem UNIQUE-Index nicht als gleich gelten).
 */
@Entity(
    tableName = "wear_visible_sources",
    indices = [Index(value = ["sourceType", "sourceKey"], unique = true)]
)
data class WearVisibleSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String,
    val sourceKey: Long = 0L,
    val enabled: Boolean = true
)

/**
 * Ein per ICS-Datei importierter externer Kalender (siehe CLAUDE.md "ICS-Import"), z. B. aus einer
 * anderen Kalender-App exportiert. [visible] schaltet die Anzeige in der Monatsansicht per Kästchen
 * in der Kalender-Seitenleiste um (analog [HolidaySettingsEntity.showPublicHolidays]) — anders als
 * bei Feiertagen bleibt die Datei dabei einmalig importiert (kein periodischer Re-Fetch), da es sich
 * um eine statische Datei und keine Online-Quelle handelt.
 */
@Entity(tableName = "imported_calendars")
data class ImportedCalendarEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val visible: Boolean = true,
    val importedAtEpochMillis: Long
)

/** Ein einzelnes VEVENT aus einer importierten [ImportedCalendarEntity]-Datei, siehe [de.letheapp.nocturn.calendar.IcsImportRepository]. */
@Entity(
    tableName = "imported_calendar_events",
    indices = [Index(value = ["calendarId"])]
)
data class ImportedCalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val calendarId: Long,
    val title: String,
    val location: String = "",
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val allDay: Boolean
)
