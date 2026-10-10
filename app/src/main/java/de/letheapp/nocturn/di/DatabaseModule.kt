package de.letheapp.nocturn.di

import android.content.Context
import androidx.room.Room
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
import de.letheapp.nocturn.data.db.IoBrokerAccountDao
import de.letheapp.nocturn.data.db.JobDao
import de.letheapp.nocturn.data.db.LessonDao
import de.letheapp.nocturn.data.db.ManualShiftEntryDao
import de.letheapp.nocturn.data.db.NocturnDatabase
import de.letheapp.nocturn.data.db.RotationDao
import de.letheapp.nocturn.data.db.ShiftDao
import de.letheapp.nocturn.data.db.SyncedEventDao
import de.letheapp.nocturn.data.db.TaskDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NocturnDatabase =
        Room.databaseBuilder(context, NocturnDatabase::class.java, "nocturn.db")
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
}
