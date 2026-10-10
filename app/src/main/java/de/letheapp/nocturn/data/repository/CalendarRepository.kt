package de.letheapp.nocturn.data.repository

import de.letheapp.nocturn.data.db.AppointmentDao
import de.letheapp.nocturn.data.db.AppointmentEntity
import de.letheapp.nocturn.data.db.AppointmentPriority
import de.letheapp.nocturn.data.db.CalendarEntryDao
import de.letheapp.nocturn.data.db.CalendarEntryEntity
import de.letheapp.nocturn.data.db.JobDao
import de.letheapp.nocturn.data.db.JobEntity
import de.letheapp.nocturn.data.db.JobMode
import de.letheapp.nocturn.data.db.LessonDao
import de.letheapp.nocturn.data.db.LessonEntity
import de.letheapp.nocturn.data.db.ManualShiftEntryDao
import de.letheapp.nocturn.data.db.ManualShiftEntryEntity
import de.letheapp.nocturn.data.db.RotationDao
import de.letheapp.nocturn.data.db.RotationDayEntity
import de.letheapp.nocturn.data.db.RotationEntity
import de.letheapp.nocturn.data.db.ShiftDao
import de.letheapp.nocturn.data.db.ShiftEntity
import de.letheapp.nocturn.data.db.TaskDao
import de.letheapp.nocturn.data.db.TaskEntity
import de.letheapp.nocturn.calendar.AppointmentReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Verwaltet die Kalender-Vorlagen (Jobs, Schichten, Rotationen). Eine Rotation ist dabei
 * reine Vorlage ohne eigenes Startdatum — erst wenn sie über die Kalender-Seitenleiste
 * einem gewählten Starttag und Zeitraum zugewiesen wird ([applyRotationRange]), entstehen
 * daraus [CalendarEntryEntity]s, damit der Wecker weiß, welche Schicht an welchem Tag gilt.
 */
class CalendarRepository @Inject constructor(
    private val jobDao: JobDao,
    private val shiftDao: ShiftDao,
    private val rotationDao: RotationDao,
    private val calendarEntryDao: CalendarEntryDao,
    private val lessonDao: LessonDao,
    private val appointmentDao: AppointmentDao,
    private val manualShiftEntryDao: ManualShiftEntryDao,
    private val appointmentReminderScheduler: AppointmentReminderScheduler,
    private val taskDao: TaskDao
) {
    companion object {
        /** Standardfarbe für frei angelegte Termine (Orange), unabhängig vom Theme. */
        const val DEFAULT_APPOINTMENT_COLOR_ARGB = 0xFFFF7043.toInt()

        /** Standardfarbe für Aufgaben (Grün, angelehnt an Google Tasks), unabhängig vom Theme. */
        const val DEFAULT_TASK_COLOR_ARGB = 0xFF33B679.toInt()

        /** Zeitfenster, in dem das Kalender-Widget nach anstehenden Einträgen sucht. */
        private const val AGENDA_LOOKAHEAD_DAYS = 14L
    }

    // Jobs

    fun observeJobs(): Flow<List<JobEntity>> = jobDao.observeAll()

    fun observeJob(jobId: Long): Flow<JobEntity?> = jobDao.observeById(jobId)

    suspend fun getJob(jobId: Long): JobEntity? = jobDao.getById(jobId)

    suspend fun addJob(name: String, mode: JobMode = JobMode.SHIFT): Long =
        jobDao.insert(JobEntity(name = name, mode = mode.name))

    suspend fun setJobMode(jobId: Long, mode: JobMode) {
        val job = jobDao.getById(jobId) ?: return
        jobDao.update(job.copy(mode = mode.name))
    }

    suspend fun deleteJob(job: JobEntity) = jobDao.delete(job)

    // Stundenplan (Schüler/Student-Modus)

    fun observeLessonsForJob(jobId: Long): Flow<List<LessonEntity>> = lessonDao.observeForJob(jobId)

    suspend fun saveLesson(lesson: LessonEntity): Long =
        if (lesson.id == 0L) lessonDao.insert(lesson) else lessonDao.update(lesson).let { lesson.id }

    suspend fun deleteLesson(lesson: LessonEntity) = lessonDao.delete(lesson)

    /** Zeitlich erste Unterrichtsstunde an [weekday] (0=Mo..6=So) — einzig relevant für die Wecker-Ableitung. */
    suspend fun getFirstLessonForWeekday(jobId: Long, weekday: Int): LessonEntity? =
        lessonDao.getFirstForJobAndWeekday(jobId, weekday)

    // Schichten

    fun observeShiftsForJob(jobId: Long): Flow<List<ShiftEntity>> = shiftDao.observeForJob(jobId)

    fun observeAllShifts(): Flow<List<ShiftEntity>> = shiftDao.observeAll()

    suspend fun getShift(shiftId: Long): ShiftEntity? = shiftDao.getById(shiftId)

    suspend fun saveShift(shift: ShiftEntity): Long =
        if (shift.id == 0L) shiftDao.insert(shift) else shiftDao.update(shift).let { shift.id }

    suspend fun deleteShift(shift: ShiftEntity) = shiftDao.delete(shift)

    // Rotationen

    fun observeRotationsForJob(jobId: Long): Flow<List<RotationEntity>> = rotationDao.observeForJob(jobId)

    fun observeAllRotations(): Flow<List<RotationEntity>> = rotationDao.observeAll()

    fun observeRotationDays(rotationId: Long): Flow<List<RotationDayEntity>> = rotationDao.observeDays(rotationId)

    suspend fun getRotation(rotationId: Long): RotationEntity? = rotationDao.getById(rotationId)

    suspend fun getRotationDaysOnce(rotationId: Long): List<RotationDayEntity> = rotationDao.getDaysOnce(rotationId)

    suspend fun deleteRotation(rotation: RotationEntity) = rotationDao.delete(rotation)

    /**
     * Entfernt alle bereits im Kalender eingetragenen Vorkommen dieser Rotation (jeder Zeitraum,
     * mit dem sie je über die Seitenleiste zugewiesen wurde), lässt die Rotations-**Vorlage**
     * selbst aber unverändert bestehen — anders als [deleteRotation], das die Vorlage komplett
     * löscht (und über die FK-`CASCADE` ebenfalls alle Kalendereinträge mitreißt).
     */
    suspend fun removeRotationEntries(rotationId: Long) = calendarEntryDao.deleteForRotation(rotationId)

    /**
     * Speichert eine Rotation inkl. ihres Tag-für-Tag-Musters (Zykluslänge = Anzahl der
     * übergebenen Tage) als reine Vorlage — ohne Startdatum und ohne den Kalender zu
     * verändern. Bereits im Kalender eingetragene Vorkommen dieser Rotation bleiben
     * unverändert; ein geändertes Muster wirkt sich erst bei der nächsten [applyRotationRange]
     * aus. Das Zuweisen zu einem Zeitraum erfolgt separat über die Kalender-Seitenleiste.
     *
     * [dayShiftIds] listet pro Tag die an diesem Tag gleichzeitig geltenden Schichten (z. B.
     * Frühschicht + ein zusätzlicher Termin wie "Kinder-Wochenende") — eine leere Liste bedeutet
     * "frei".
     */
    suspend fun saveRotation(
        jobId: Long,
        rotationId: Long?,
        name: String,
        dayShiftIds: List<List<Long>>
    ) {
        val savedId = if (rotationId == null) {
            rotationDao.insert(RotationEntity(jobId = jobId, name = name))
        } else {
            rotationDao.update(RotationEntity(id = rotationId, jobId = jobId, name = name))
            rotationId
        }

        rotationDao.deleteDays(savedId)
        val days = dayShiftIds.flatMapIndexed { index, shiftIds ->
            if (shiftIds.isEmpty()) {
                listOf(RotationDayEntity(rotationId = savedId, dayIndex = index, shiftId = null))
            } else {
                shiftIds.map { shiftId -> RotationDayEntity(rotationId = savedId, dayIndex = index, shiftId = shiftId) }
            }
        }
        rotationDao.insertDays(days)
    }

    /**
     * Wendet eine bereits bestehende Rotation ab [startEpochDay] für [totalDays] Tage erneut an
     * (Seitenleiste in der Kalender-Monatsansicht: Rotation wählen → Starttag antippen → Dauer
     * im schwebenden Fenster festlegen). Ersetzt nur die Einträge dieser Rotation im gewählten
     * Zeitraum, lässt ihr beim Anlegen gespeichertes Standard-Fortschreibefenster unangetastet.
     *
     * [phaseStartIndex] ist der Tag-Index im Rotationsmuster, der [startEpochDay] entspricht
     * (siehe [rotationPhaseCandidates]/[resolveRotationPhase]) — nicht zwingend 0, da das
     * Muster beim Anlegen einen Montag als Tag-Index 0 annimmt, der gewählte Starttag im
     * Kalender aber ein beliebiger Wochentag sein kann.
     */
    suspend fun applyRotationRange(rotationId: Long, startEpochDay: Long, totalDays: Int, phaseStartIndex: Int = 0) {
        val rotation = rotationDao.getById(rotationId) ?: return
        val daysByIndex = rotationDao.getDaysOnce(rotationId).groupBy({ it.dayIndex }, { it.shiftId })
        val cycleLength = daysByIndex.keys.size
        if (cycleLength == 0 || totalDays <= 0) return

        val endEpochDay = startEpochDay + totalDays - 1
        calendarEntryDao.deleteForRotationInRange(rotationId, startEpochDay, endEpochDay)
        val entries = (0 until totalDays).flatMap { offset ->
            val dateEpochDay = startEpochDay + offset
            val shiftIds = daysByIndex[(phaseStartIndex + offset) % cycleLength].orEmpty().filterNotNull()
            if (shiftIds.isEmpty()) {
                listOf(CalendarEntryEntity(jobId = rotation.jobId, rotationId = rotationId, dateEpochDay = dateEpochDay, shiftId = null))
            } else {
                shiftIds.map { shiftId ->
                    CalendarEntryEntity(jobId = rotation.jobId, rotationId = rotationId, dateEpochDay = dateEpochDay, shiftId = shiftId)
                }
            }
        }
        calendarEntryDao.insertAll(entries)
    }

    /**
     * Alle im Muster dieser Rotation tatsächlich verwendeten Schichten (für die Chip-Auswahl
     * der Phasen-Rückfrage, siehe [resolveRotationPhase]) — `null` steht dabei für "Frei",
     * falls das Muster freie Tage enthält. Eine Schicht, die im gesamten Zyklus nur an genau
     * einem Tag-Index vorkommt, zählt nicht als echter, wiederkehrender Teil des Musters
     * (meist ein versehentlicher Tipp beim schnellen Aufbau des Rasters über die Schnellauswahl-
     * Chips) und wird daher ignoriert — siehe [occurrenceCount].
     */
    suspend fun getRotationShiftOptions(rotationId: Long): List<ShiftEntity?> {
        val days = rotationDao.getDaysOnce(rotationId)
        val daysByIndex = days.groupBy({ it.dayIndex }, { it.shiftId })
        val cycleLength = daysByIndex.keys.size
        val shifts = days.mapNotNull { it.shiftId }.distinct()
            .filter { occurrenceCount(daysByIndex, cycleLength, it) > 1 }
            .mapNotNull { shiftDao.getById(it) }
        return if (days.any { it.shiftId == null }) shifts + listOf(null) else shifts
    }

    /** Wie oft [shiftId] (`null` = Frei) im gesamten Zyklus laut [daysByIndex] vorkommt. */
    private fun occurrenceCount(daysByIndex: Map<Int, List<Long?>>, cycleLength: Int, shiftId: Long?): Int =
        (0 until cycleLength).count { shiftMatchesDay(daysByIndex[it].orEmpty(), shiftId) }

    /**
     * Tag-Indizes im Rotationsmuster, die vom Wochentag her zu [startEpochDay] passen würden.
     * Das Muster wird im Editor als 7-Wochentags-Spalten-Raster angelegt (Tag-Index 0 = Montag,
     * siehe [de.letheapp.nocturn.calendar.RotationEditorScreen]), Tag-Index mod 7 ergibt also
     * den Wochentag. Bei Zykluslängen über einer Woche kann derselbe Wochentag mehrfach im
     * Muster vorkommen — eine Liste mit mehr als einem Eintrag bedeutet, dass der gewählte
     * Starttag ohne Rückfrage (siehe [resolveRotationPhase]) nicht eindeutig zuordenbar ist.
     */
    suspend fun rotationPhaseCandidates(rotationId: Long, startEpochDay: Long): List<Int> {
        val cycleLength = rotationDao.getDaysOnce(rotationId).map { it.dayIndex }.distinct().size
        if (cycleLength == 0) return emptyList()
        val weekday = LocalDate.ofEpochDay(startEpochDay).dayOfWeek.value - 1
        return (0 until cycleLength).filter { it % 7 == weekday }
    }

    /** Bei Tagen mit mehreren gleichzeitigen Schichten genügt es, dass die genannte Schicht
     *  an diesem Tag-Index überhaupt vorkommt, um ihn eindeutig zu identifizieren. */
    private fun shiftMatchesDay(shiftIds: List<Long?>, picked: Long?): Boolean =
        if (picked == null) shiftIds.all { it == null } else shiftIds.contains(picked)

    /**
     * Wochentag-passende Kandidaten (siehe [rotationPhaseCandidates]), die zusätzlich die am
     * Starttag genannte Schicht [todayShiftId] tatsächlich an diesem Tag-Index führen — der
     * erste von zwei Rückfrage-Schritten, siehe [needsNextOccurrenceDateQuery].
     */
    suspend fun candidatesMatchingTodayShift(
        rotationId: Long,
        startEpochDay: Long,
        todayShiftId: Long?
    ): List<Int> {
        val daysByIndex = rotationDao.getDaysOnce(rotationId).groupBy({ it.dayIndex }, { it.shiftId })
        val cycleLength = daysByIndex.keys.size
        if (cycleLength == 0) return emptyList()
        val weekday = LocalDate.ofEpochDay(startEpochDay).dayOfWeek.value - 1
        return (0 until cycleLength).filter {
            it % 7 == weekday && shiftMatchesDay(daysByIndex[it].orEmpty(), todayShiftId)
        }
    }

    /**
     * true, wenn die am Starttag genannte Schicht [todayShiftId] allein nicht reicht (mehrere
     * Kandidaten bleiben übrig) UND auch die Schicht am nächsten Freitag diese Kandidaten nicht
     * zuverlässig unterscheiden würde — typisch für eine im Muster nur unregelmäßig/selten
     * vorkommende Schicht (z. B. "Kinder-Wochenende" an wechselnden, nicht wochentaktisch
     * wiederkehrenden Tag-Indizes). Die Wochentag-Rückfrage ("nächster Freitag") bringt in
     * diesem Fall nichts; stattdessen muss das tatsächliche Datum der nächsten Wiederholung
     * dieser Schicht erfragt werden, siehe [resolveRotationPhaseByNextOccurrence].
     */
    suspend fun needsNextOccurrenceDateQuery(rotationId: Long, startEpochDay: Long, todayShiftId: Long?): Boolean {
        val remaining = candidatesMatchingTodayShift(rotationId, startEpochDay, todayShiftId)
        if (remaining.size <= 1) return false
        val daysByIndex = rotationDao.getDaysOnce(rotationId).groupBy({ it.dayIndex }, { it.shiftId })
        val cycleLength = daysByIndex.keys.size
        val weekday = LocalDate.ofEpochDay(startEpochDay).dayOfWeek.value - 1
        val daysUntilFriday = (4 - weekday + 7) % 7
        val fridayOffset = if (daysUntilFriday == 0) 7 else daysUntilFriday
        val fridaySignatures = remaining.map { idx ->
            daysByIndex[(idx + fridayOffset) % cycleLength].orEmpty().filterNotNull().toSet()
        }
        return fridaySignatures.toSet().size != fridaySignatures.size
    }

    /**
     * Ermittelt anhand der Nutzerantworten — welche Schicht gilt am Starttag, welche am
     * nächsten Freitag — den tatsächlichen Tag-Index im Rotationsmuster für [startEpochDay],
     * wenn [rotationPhaseCandidates] mehrdeutig war. Fällt auf den ersten wochentag-passenden
     * Kandidaten zurück, falls keiner exakt zu beiden Antworten passt. Nur sinnvoll, wenn
     * [needsNextOccurrenceDateQuery] für dieselbe Schicht-Antwort `false` zurückgibt.
     */
    suspend fun resolveRotationPhase(
        rotationId: Long,
        startEpochDay: Long,
        todayShiftId: Long?,
        nextFridayShiftId: Long?
    ): Int {
        val daysByIndex = rotationDao.getDaysOnce(rotationId).groupBy({ it.dayIndex }, { it.shiftId })
        val cycleLength = daysByIndex.keys.size
        if (cycleLength == 0) return 0
        val weekday = LocalDate.ofEpochDay(startEpochDay).dayOfWeek.value - 1
        val candidates = (0 until cycleLength).filter { it % 7 == weekday }
        if (candidates.isEmpty()) return 0
        val daysUntilFriday = (4 - weekday + 7) % 7
        val fridayOffset = if (daysUntilFriday == 0) 7 else daysUntilFriday

        val exactMatch = candidates.firstOrNull { index ->
            shiftMatchesDay(daysByIndex[index].orEmpty(), todayShiftId) &&
                shiftMatchesDay(daysByIndex[(index + fridayOffset) % cycleLength].orEmpty(), nextFridayShiftId)
        }
        return exactMatch
            ?: candidates.firstOrNull { shiftMatchesDay(daysByIndex[it].orEmpty(), todayShiftId) }
            ?: candidates.first()
    }

    /**
     * Ermittelt, nach welcher Schicht bei der Datums-Rückfrage ([RotationNextOccurrenceDateDialog])
     * gefragt werden soll, wenn [needsNextOccurrenceDateQuery] `true` ergab: die Freitags-Frage
     * hilft hier nicht, weil die eigentlich unterscheidende Schicht nicht wochentaktisch auftritt
     * (z. B. eine seltene Zusatzschicht wie "Kinder-Wochenende", die nur alle paar Wochen auf
     * denselben Wochentag fällt). Gesucht wird die Schicht, deren nächstes Vorkommen ab jedem
     * verbleibenden Kandidaten-Tag-Index einen **eindeutigen** Abstand ergibt (die Nutzerantwort
     * also wirklich zwischen den Kandidaten unterscheidet) — bevorzugt die im gesamten Muster am
     * seltensten vorkommende Schicht, da gerade deren nächstes Auftreten dem Nutzer typischerweise
     * noch im Kopf ist. Schichten mit nur einem einzigen Vorkommen im gesamten Zyklus gelten nicht
     * als echter, wiederkehrender Teil des Musters (siehe [getRotationShiftOptions]) und werden
     * dabei ignoriert. Fällt auf [todayShiftId] zurück, falls keine andere Schicht eindeutig
     * unterscheidet.
     */
    suspend fun findDistinguishingShiftForOccurrenceQuery(
        rotationId: Long,
        startEpochDay: Long,
        todayShiftId: Long?
    ): Long? {
        val remaining = candidatesMatchingTodayShift(rotationId, startEpochDay, todayShiftId)
        if (remaining.size <= 1) return todayShiftId
        val daysByIndex = rotationDao.getDaysOnce(rotationId).groupBy({ it.dayIndex }, { it.shiftId })
        val cycleLength = daysByIndex.keys.size
        if (cycleLength == 0) return todayShiftId

        fun nextOccurrenceOffset(idx: Int, shiftId: Long?): Int {
            for (offset in 1..cycleLength) {
                if (shiftMatchesDay(daysByIndex[(idx + offset) % cycleLength].orEmpty(), shiftId)) return offset
            }
            return cycleLength
        }

        val allShiftIds: List<Long?> = daysByIndex.values.flatten().distinct()
        val distinguishing = allShiftIds.mapNotNull { shiftId ->
            val occurrenceCount = occurrenceCount(daysByIndex, cycleLength, shiftId)
            // Ein einzelnes Vorkommen ist meist ein versehentlicher Tipp, kein echter Teil des
            // Musters (siehe getRotationShiftOptions) — "Frei" bleibt davon ausgenommen, da auch
            // ein einzelner freier Tag ein legitimer Ankerpunkt im Muster ist.
            if (shiftId != null && occurrenceCount <= 1) return@mapNotNull null
            val offsets = remaining.map { nextOccurrenceOffset(it, shiftId) }
            if (offsets.toSet().size == remaining.size) {
                shiftId to occurrenceCount
            } else {
                null
            }
        }
        return distinguishing.minByOrNull { it.second }?.first ?: todayShiftId
    }

    /**
     * Ermittelt den tatsächlichen Tag-Index, wenn die Schicht am Starttag allein nicht reicht und
     * [needsNextOccurrenceDateQuery] `true` ergab: statt eines Wochentags wird das konkrete
     * Datum der nächsten Wiederholung von [queryShiftId] (siehe
     * [findDistinguishingShiftForOccurrenceQuery]) erfragt, und darüber unter den verbleibenden
     * Kandidaten derjenige ausgewählt, dessen berechnete nächste Wiederholung auf
     * [nextOccurrenceEpochDay] fällt.
     */
    suspend fun resolveRotationPhaseByNextOccurrence(
        rotationId: Long,
        startEpochDay: Long,
        todayShiftId: Long?,
        queryShiftId: Long?,
        nextOccurrenceEpochDay: Long
    ): Int {
        val remaining = candidatesMatchingTodayShift(rotationId, startEpochDay, todayShiftId)
        if (remaining.size <= 1) return remaining.firstOrNull() ?: 0
        val daysByIndex = rotationDao.getDaysOnce(rotationId).groupBy({ it.dayIndex }, { it.shiftId })
        val cycleLength = daysByIndex.keys.size

        fun nextOccurrenceEpochDayFor(idx: Int): Long {
            for (offset in 1..cycleLength) {
                if (shiftMatchesDay(daysByIndex[(idx + offset) % cycleLength].orEmpty(), queryShiftId)) {
                    return startEpochDay + offset
                }
            }
            return startEpochDay + cycleLength
        }

        return remaining.firstOrNull { nextOccurrenceEpochDayFor(it) == nextOccurrenceEpochDay }
            ?: remaining.first()
    }

    fun observeCalendarEntries(fromEpochDay: Long, toEpochDay: Long): Flow<List<CalendarEntryEntity>> =
        calendarEntryDao.observeRange(fromEpochDay, toEpochDay)

    /**
     * Alle Einträge einer Rotation an einem Tag — mehrere, wenn an diesem Tag laut Muster
     * mehrere Schichten gleichzeitig gelten (siehe [RotationDayEntity]).
     */
    suspend fun getCalendarEntriesForDateAndRotation(rotationId: Long, epochDay: Long): List<CalendarEntryEntity> =
        calendarEntryDao.getForDate(epochDay, rotationId)

    /**
     * Nächster Kalendereintrag einer Rotation ab [fromEpochDay] (inklusive), an dem laut
     * Rotationsmuster tatsächlich eine Schicht ansteht (freie Tage werden übersprungen).
     * Grundlage für Alarme, die an eine Rotation statt an eine feste Uhrzeit gekoppelt sind.
     * Gelten an diesem Tag mehrere Schichten gleichzeitig, wird die mit dem frühesten
     * Schichtbeginn gewählt — das ist die, für die man tatsächlich geweckt werden muss.
     */
    suspend fun getNextCalendarEntryWithShift(rotationId: Long, fromEpochDay: Long): CalendarEntryEntity? {
        val candidates = calendarEntryDao.getNextWithShift(rotationId, fromEpochDay)
        val firstDay = candidates.minOfOrNull { it.dateEpochDay } ?: return null
        val sameDay = candidates.filter { it.dateEpochDay == firstDay }
        if (sameDay.size == 1) return sameDay.first()
        return sameDay.minByOrNull { entry ->
            val shift = entry.shiftId?.let { shiftDao.getById(it) }
            (shift?.startHour ?: 0) * 60 + (shift?.startMinute ?: 0)
        }
    }

    // Termine

    fun observeAppointments(fromEpochDay: Long, toEpochDay: Long): Flow<List<AppointmentEntity>> =
        appointmentDao.observeRange(fromEpochDay, toEpochDay)

    /**
     * Legt einen Termin an, u. a. Ziel von [de.letheapp.nocturn.calendar.AddAppointmentActivity],
     * wenn sich NocTurn im System-"Zum Kalender hinzufügen"-Menü einer Fremd-App anbietet.
     */
    suspend fun addAppointment(
        title: String,
        location: String,
        description: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        allDay: Boolean,
        priority: AppointmentPriority = AppointmentPriority.NORMAL,
        reminderMinutesBefore: Int? = null,
        colorArgb: Int = DEFAULT_APPOINTMENT_COLOR_ARGB
    ): Long {
        val zone = java.time.ZoneId.systemDefault()
        val startDay = java.time.Instant.ofEpochMilli(startEpochMillis).atZone(zone).toLocalDate().toEpochDay()
        val endDay = java.time.Instant.ofEpochMilli(endEpochMillis).atZone(zone).toLocalDate().toEpochDay()
        val appointment = AppointmentEntity(
            title = title,
            location = location,
            description = description,
            startEpochMillis = startEpochMillis,
            endEpochMillis = endEpochMillis,
            startEpochDay = startDay,
            endEpochDay = maxOf(startDay, endDay),
            allDay = allDay,
            colorArgb = colorArgb,
            priority = priority.name,
            reminderMinutesBefore = reminderMinutesBefore
        )
        val id = appointmentDao.insert(appointment)
        appointmentReminderScheduler.schedule(appointment.copy(id = id))
        return id
    }

    suspend fun deleteAppointment(appointment: AppointmentEntity) {
        appointmentReminderScheduler.cancel(appointment.id)
        appointmentDao.delete(appointment)
    }

    // Aufgaben (Tasks)

    fun observeTasks(fromEpochDay: Long, toEpochDay: Long): Flow<List<TaskEntity>> =
        taskDao.observeRange(fromEpochDay, toEpochDay)

    suspend fun addTask(
        title: String,
        notes: String,
        dueEpochDay: Long,
        dueEpochMillis: Long?,
        allDay: Boolean
    ): Long = taskDao.insert(
        TaskEntity(
            title = title,
            notes = notes,
            dueEpochDay = dueEpochDay,
            dueEpochMillis = dueEpochMillis,
            allDay = allDay,
            colorArgb = DEFAULT_TASK_COLOR_ARGB
        )
    )

    suspend fun setTaskCompleted(taskId: Long, completed: Boolean) {
        val task = taskDao.getById(taskId) ?: return
        taskDao.update(task.copy(completed = completed))
    }

    suspend fun deleteTask(task: TaskEntity) = taskDao.delete(task)

    // Manuell eingetragene Einzel-Schichten (siehe CalendarMonthScreen-FAB, Option "Schicht")

    fun observeManualShifts(fromEpochDay: Long, toEpochDay: Long): Flow<List<ManualShiftEntryEntity>> =
        manualShiftEntryDao.observeRange(fromEpochDay, toEpochDay)

    suspend fun saveManualShiftEntry(jobId: Long, shiftId: Long, dateEpochDay: Long): Long =
        manualShiftEntryDao.insert(ManualShiftEntryEntity(jobId = jobId, shiftId = shiftId, dateEpochDay = dateEpochDay))

    suspend fun deleteManualShiftEntry(entry: ManualShiftEntryEntity) = manualShiftEntryDao.delete(entry)

    /**
     * Für das Kalender-Homescreen-Widget: die nächsten [maxItems] anstehenden Einträge
     * (Schichten aus Rotationen + freie Termine) ab heute, über die nächsten
     * [AGENDA_LOOKAHEAD_DAYS] Tage, chronologisch sortiert.
     */
    suspend fun getUpcomingAgenda(maxItems: Int): List<AgendaEntry> = buildAgenda().take(maxItems)

    /**
     * Für das Uhr-Homescreen-Widget: die nächsten [maxShown] anstehenden Einträge schmal
     * zur Anzeige unter der Uhrzeit, plus die Anzahl weiterer Einträge, die am selben Tag wie
     * der zuletzt gezeigte Eintrag noch anstehen (als "+N weitere" statt sie alle aufzulisten).
     */
    suspend fun getUpcomingAgendaForWidget(maxShown: Int): WidgetAgenda {
        val all = buildAgenda()
        val shown = all.take(maxShown)
        val extraCount = if (shown.size == maxShown) {
            val lastShownDay = shown.last().epochDay
            all.count { it.epochDay == lastShownDay } - shown.count { it.epochDay == lastShownDay }
        } else {
            0
        }
        return WidgetAgenda(entries = shown, extraCountForLastDay = extraCount)
    }

    private suspend fun buildAgenda(): List<AgendaEntry> {
        val today = LocalDate.now().toEpochDay()
        val toDay = today + AGENDA_LOOKAHEAD_DAYS

        val entries = calendarEntryDao.observeRange(today, toDay).first()
        val shiftsById = shiftDao.observeAll().first().associateBy { it.id }
        val appointments = appointmentDao.observeRange(today, toDay).first()
        val zone = ZoneId.systemDefault()
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val shiftAgenda = entries.mapNotNull { entry ->
            val shiftId = entry.shiftId ?: return@mapNotNull null
            val shift = shiftsById[shiftId] ?: return@mapNotNull null
            AgendaEntry(
                epochDay = entry.dateEpochDay,
                label = shift.name,
                colorArgb = shift.colorArgb,
                timeLabel = "%02d:%02d".format(shift.startHour, shift.startMinute)
            )
        }

        val appointmentAgenda = appointments.map { appointment ->
            AgendaEntry(
                epochDay = appointment.startEpochDay,
                label = appointment.title,
                colorArgb = appointment.colorArgb,
                timeLabel = if (appointment.allDay) {
                    null
                } else {
                    Instant.ofEpochMilli(appointment.startEpochMillis).atZone(zone).toLocalTime().format(timeFormatter)
                }
            )
        }

        return (shiftAgenda + appointmentAgenda).sortedBy { it.epochDay }
    }
}

/** Ein Eintrag im Kalender-Widget: entweder eine Schicht an einem Rotationstag oder ein freier Termin. */
data class AgendaEntry(
    val epochDay: Long,
    val label: String,
    val colorArgb: Int,
    val timeLabel: String?
)

/** Für das Uhr-Widget (CLAUDE.md Kernfunktion 7): die schmal angezeigten nächsten Termine. */
data class WidgetAgenda(
    val entries: List<AgendaEntry>,
    val extraCountForLastDay: Int
)
