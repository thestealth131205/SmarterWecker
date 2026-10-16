package de.letheapp.nocturn.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.letheapp.nocturn.data.db.AlarmDao
import de.letheapp.nocturn.data.db.AppointmentDao
import de.letheapp.nocturn.data.db.CalDavAccountDao
import de.letheapp.nocturn.data.db.CalendarEntryDao
import de.letheapp.nocturn.data.db.HolidayDao
import de.letheapp.nocturn.data.db.HolidaySettingsDao
import de.letheapp.nocturn.data.db.ImportedCalendarDao
import de.letheapp.nocturn.data.db.IoBrokerAccountDao
import de.letheapp.nocturn.data.db.JobDao
import de.letheapp.nocturn.data.db.LessonDao
import de.letheapp.nocturn.data.db.ManualShiftEntryDao
import de.letheapp.nocturn.data.db.NocturnDatabase
import de.letheapp.nocturn.data.db.NocturnSyncMappingDao
import de.letheapp.nocturn.data.db.RotationDao
import de.letheapp.nocturn.data.db.ShiftDao
import de.letheapp.nocturn.data.db.SyncedEventDao
import de.letheapp.nocturn.data.db.TaskDao
import de.letheapp.nocturn.data.db.WearVisibleSourceDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Ergänzt [de.letheapp.nocturn.data.db.AlarmEntity.autoShiftWakeupShiftId] (siehe
     * [de.letheapp.nocturn.monitoring.AutomaticShiftRetryScheduler]) — eine reine
     * Spalten-Erweiterung, bestehende Zeilen bekommen NULL.
     */
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE alarms ADD COLUMN autoShiftWakeupShiftId INTEGER")
        }
    }

    /**
     * Ergänzt [de.letheapp.nocturn.data.db.AlarmEntity.linkedShiftId]/[linkedTimetableWeekday] —
     * engt eine bestehende Rotation-/Stundenplan-Verknüpfung optional auf eine einzelne Schicht
     * bzw. einen einzelnen Wochentag ein. Reine Spalten-Erweiterung, bestehende Zeilen (= "jede
     * Schicht"/"jeder Tag", bisheriges Verhalten) bekommen NULL.
     */
    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE alarms ADD COLUMN linkedShiftId INTEGER")
            db.execSQL("ALTER TABLE alarms ADD COLUMN linkedTimetableWeekday INTEGER")
        }
    }

    /**
     * Neue Tabelle für [de.letheapp.nocturn.data.db.WearVisibleSourceEntity] — pro Kalenderquelle
     * (Job/Rotation/Geburtstage/Feiertage/Ferien), ob sie an eine gekoppelte WearOS-Smartwatch
     * übertragen wird (siehe [de.letheapp.nocturn.wear.WearSettingsScreen]).
     */
    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS wear_visible_sources (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "sourceType TEXT NOT NULL, " +
                    "sourceKey INTEGER NOT NULL, " +
                    "enabled INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_wear_visible_sources_sourceType_sourceKey " +
                    "ON wear_visible_sources (sourceType, sourceKey)"
            )
        }
    }

    /**
     * Neue Tabelle für [de.letheapp.nocturn.data.db.NocturnSyncMappingEntity] (lokal<->Server-uid-
     * Zuordnung für den bidirektionalen Nextcloud-Schedule-Sync, siehe
     * [de.letheapp.nocturn.sync.NocturnScheduleSyncRepository]) sowie die neue Sync-Cursor-Spalte
     * auf [de.letheapp.nocturn.data.db.CalDavAccountEntity].
     */
    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE caldav_accounts ADD COLUMN nocturnScheduleSyncToken INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS nocturn_sync_mappings (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "localType TEXT NOT NULL, " +
                    "localId INTEGER NOT NULL, " +
                    "remoteUid TEXT NOT NULL, " +
                    "remoteUpdatedAt INTEGER NOT NULL, " +
                    "contentHash INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_nocturn_sync_mappings_localType_localId " +
                    "ON nocturn_sync_mappings (localType, localId)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_nocturn_sync_mappings_remoteUid " +
                    "ON nocturn_sync_mappings (remoteUid)"
            )
        }
    }

    /**
     * Neue Opt-in-Spalte [de.letheapp.nocturn.data.db.CalDavAccountEntity.syncNocturnSchedule] —
     * schaltet den bidirektionalen Jobs/Schichten/Rotationen-Abgleich über
     * [de.letheapp.nocturn.sync.NocturnScheduleSyncRepository] frei, unabhängig vom reinen
     * CalDAV-Export in [syncShifts].
     */
    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE caldav_accounts ADD COLUMN syncNocturnSchedule INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** Neue Tabellen für den ICS-Import (siehe [de.letheapp.nocturn.calendar.IcsImportRepository]). */
    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS imported_calendars (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    colorArgb INTEGER NOT NULL,
                    visible INTEGER NOT NULL DEFAULT 1,
                    importedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS imported_calendar_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    calendarId INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    location TEXT NOT NULL DEFAULT '',
                    startEpochMillis INTEGER NOT NULL,
                    endEpochMillis INTEGER NOT NULL,
                    allDay INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_imported_calendar_events_calendarId ON imported_calendar_events(calendarId)")
        }
    }

    /**
     * Ergänzt [de.letheapp.nocturn.data.db.AppointmentEntity.showAsBusy] (iCalendar-TRANSP-
     * Äquivalent) sowie die neue Tabelle [de.letheapp.nocturn.data.db.AppointmentAttachmentEntity]
     * für Termin-Anhänge (siehe [de.letheapp.nocturn.calendar.AddAppointmentScreen]).
     */
    private val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE appointments ADD COLUMN showAsBusy INTEGER NOT NULL DEFAULT 1")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS appointment_attachments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    appointmentId INTEGER NOT NULL,
                    uri TEXT NOT NULL,
                    fileName TEXT NOT NULL,
                    mimeType TEXT,
                    FOREIGN KEY(appointmentId) REFERENCES appointments(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_appointment_attachments_appointmentId ON appointment_attachments(appointmentId)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NocturnDatabase =
        Room.databaseBuilder(context, NocturnDatabase::class.java, "nocturn.db")
            .addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
            // Es gibt jetzt reale Nutzdaten auf installierten Geräten (Alarme, Jobs, Schichten,
            // Rotationen, Kalendereinträge, ...) — ein destruktiver Neuaufbau bei jedem
            // Schema-Bump würde sie bei jedem App-Update löschen. Deshalb bewusst KEIN
            // fallbackToDestructiveMigration() mehr: jede künftige Erhöhung von
            // NocturnDatabase.version muss eine eigene Migration(...) hier ergänzen, die das
            // bestehende Schema per ALTER/CREATE TABLE überführt, statt die Tabellen zu
            // verwerfen. Nur beim Downgrade (z. B. Debug-Build einer älteren Version drüber
            // installiert) ist ein Neuaufbau unkritisch, da das kein produktiver Update-Pfad ist.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideAlarmDao(db: NocturnDatabase): AlarmDao = db.alarmDao()

    @Provides
    fun provideJobDao(db: NocturnDatabase): JobDao = db.jobDao()

    @Provides
    fun provideShiftDao(db: NocturnDatabase): ShiftDao = db.shiftDao()

    @Provides
    fun provideRotationDao(db: NocturnDatabase): RotationDao = db.rotationDao()

    @Provides
    fun provideCalendarEntryDao(db: NocturnDatabase): CalendarEntryDao = db.calendarEntryDao()

    @Provides
    fun provideLessonDao(db: NocturnDatabase): LessonDao = db.lessonDao()

    @Provides
    fun provideAppointmentDao(db: NocturnDatabase): AppointmentDao = db.appointmentDao()

    @Provides
    fun provideCalDavAccountDao(db: NocturnDatabase): CalDavAccountDao = db.calDavAccountDao()

    @Provides
    fun provideSyncedEventDao(db: NocturnDatabase): SyncedEventDao = db.syncedEventDao()

    @Provides
    fun provideManualShiftEntryDao(db: NocturnDatabase): ManualShiftEntryDao = db.manualShiftEntryDao()

    @Provides
    fun provideHolidaySettingsDao(db: NocturnDatabase): HolidaySettingsDao = db.holidaySettingsDao()

    @Provides
    fun provideHolidayDao(db: NocturnDatabase): HolidayDao = db.holidayDao()

    @Provides
    fun provideIoBrokerAccountDao(db: NocturnDatabase): IoBrokerAccountDao = db.ioBrokerAccountDao()

    @Provides
    fun provideTaskDao(db: NocturnDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideWearVisibleSourceDao(db: NocturnDatabase): WearVisibleSourceDao = db.wearVisibleSourceDao()

    @Provides
    fun provideNocturnSyncMappingDao(db: NocturnDatabase): NocturnSyncMappingDao = db.nocturnSyncMappingDao()

    @Provides
    fun provideImportedCalendarDao(db: NocturnDatabase): ImportedCalendarDao = db.importedCalendarDao()
}
