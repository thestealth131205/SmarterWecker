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
import de.letheapp.nocturn.data.db.JobDao
import de.letheapp.nocturn.data.db.LessonDao
import de.letheapp.nocturn.data.db.NocturnDatabase
import de.letheapp.nocturn.data.db.RotationDao
import de.letheapp.nocturn.data.db.ShiftDao
import de.letheapp.nocturn.data.db.SyncedEventDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NocturnDatabase =
        Room.databaseBuilder(context, NocturnDatabase::class.java, "nocturn.db")
            // Noch keine veröffentlichte Version mit Bestandsdaten, daher reicht ein
            // destruktiver Neuaufbau bei Schema-Änderungen statt echter Migrationen.
            .fallbackToDestructiveMigration()
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
}
