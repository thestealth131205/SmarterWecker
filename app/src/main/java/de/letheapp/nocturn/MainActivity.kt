package de.letheapp.nocturn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.alarm.AlarmEditScreen
import de.letheapp.nocturn.alarm.AlarmListScreen
import de.letheapp.nocturn.calendar.AddAppointmentScreen
import de.letheapp.nocturn.calendar.AddShiftEntryScreen
import de.letheapp.nocturn.calendar.AddTaskScreen
import de.letheapp.nocturn.calendar.CalendarDayScreen
import de.letheapp.nocturn.calendar.CalendarMonthScreen
import de.letheapp.nocturn.calendar.JobDetailScreen
import de.letheapp.nocturn.calendar.JobListScreen
import de.letheapp.nocturn.calendar.RotationEditorScreen
import de.letheapp.nocturn.export.CalendarExportScreen
import de.letheapp.nocturn.holidays.HolidaySettingsScreen
import de.letheapp.nocturn.iobroker.IoBrokerSettingsScreen
import de.letheapp.nocturn.monitoring.HealthConnectStatusScreen
import de.letheapp.nocturn.ocr.PhotoImportScreen
import de.letheapp.nocturn.onboarding.AppPreferences
import de.letheapp.nocturn.onboarding.PermissionsScreen
import de.letheapp.nocturn.settings.SettingsScreen
import de.letheapp.nocturn.sound.SoundPickerScreen
import de.letheapp.nocturn.sync.NextcloudSyncScreen
import de.letheapp.nocturn.ui.theme.NocTurnTheme
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private const val ROUTE_ALARM_LIST = "alarmList"
private const val ROUTE_ALARM_EDIT = "alarmEdit/{alarmId}"
private const val ROUTE_SOUND_PICKER = "soundPicker/{currentUri}"
private const val ROUTE_JOB_LIST = "jobList"
private const val ROUTE_JOB_DETAIL = "jobDetail/{jobId}/{jobName}"
private const val ROUTE_ROTATION_EDITOR = "rotationEditor/{jobId}/{rotationId}"
private const val ROUTE_CALENDAR_MONTH = "calendarMonth"
private const val ROUTE_CALENDAR_DAY = "calendarDay/{epochDay}"
private const val ROUTE_CALENDAR_ADD_APPOINTMENT = "calendarAddAppointment/{epochDay}"
private const val ROUTE_CALENDAR_ADD_TASK = "calendarAddTask/{epochDay}"
private const val ROUTE_CALENDAR_ADD_SHIFT = "calendarAddShift/{epochDay}"
private const val ROUTE_HOLIDAY_SETTINGS = "holidaySettings"
private const val ROUTE_PERMISSIONS = "permissions/{firstRun}"
private const val ROUTE_PHOTO_IMPORT = "photoImport/{jobId}"
private const val ROUTE_NEXTCLOUD_SYNC = "nextcloudSync"
private const val ROUTE_IOBROKER_SETTINGS = "ioBrokerSettings"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_HEALTH_CONNECT_STATUS = "healthConnectStatus"
private const val ROUTE_CALENDAR_EXPORT = "calendarExport"
private const val KEY_SELECTED_SOUND_URI = "selected_sound_uri"

/** Von Widgets (siehe `widget`-Package) genutzt, um nach dem Start direkt die Monatsansicht zu öffnen. */
const val EXTRA_OPEN_CALENDAR = "de.letheapp.nocturn.EXTRA_OPEN_CALENDAR"

/** Von Widgets genutzt, um nach dem Start direkt den Wetter-Screen zu öffnen. */
const val EXTRA_OPEN_WEATHER = "de.letheapp.nocturn.EXTRA_OPEN_WEATHER"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openCalendar = intent?.getBooleanExtra(EXTRA_OPEN_CALENDAR, false) == true
        val openWeather = intent?.getBooleanExtra(EXTRA_OPEN_WEATHER, false) == true
        val startDestination = when {
            !appPreferences.isOnboardingCompleted() -> "permissions/true"
            openCalendar -> ROUTE_CALENDAR_MONTH
            else -> ROUTE_ALARM_LIST
        }
        setContent {
            NocTurnTheme {
                NocturnNavHost(
                    startDestination = startDestination,
                    appPreferences = appPreferences,
                    autoOpenWeather = openWeather
                )
            }
        }
    }
}

@Composable
private fun NocturnNavHost(
    startDestination: String,
    appPreferences: AppPreferences,
    autoOpenWeather: Boolean = false
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(ROUTE_ALARM_LIST) {
            AlarmListScreen(
                onEditAlarm = { id -> navController.navigate("alarmEdit/$id") },
                onOpenCalendarMonth = { navController.navigate(ROUTE_CALENDAR_MONTH) },
                onOpenPermissions = { navController.navigate("permissions/false") },
                onOpenHealthConnectStatus = { navController.navigate(ROUTE_HEALTH_CONNECT_STATUS) },
                onOpenIoBrokerSettings = { navController.navigate(ROUTE_IOBROKER_SETTINGS) },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                autoOpenWeather = autoOpenWeather
            )
        }

        composable(ROUTE_IOBROKER_SETTINGS) {
            IoBrokerSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(ROUTE_HOLIDAY_SETTINGS) {
            HolidaySettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(ROUTE_HEALTH_CONNECT_STATUS) {
            HealthConnectStatusScreen(onBack = { navController.popBackStack() })
        }

        composable(ROUTE_NEXTCLOUD_SYNC) {
            NextcloudSyncScreen(onBack = { navController.popBackStack() })
        }

        composable(ROUTE_PERMISSIONS) { backStackEntry ->
            val isFirstRun = backStackEntry.arguments?.getString("firstRun") == "true"
            PermissionsScreen(
                isFirstRun = isFirstRun,
                onBack = { navController.popBackStack() },
                onFinished = {
                    if (isFirstRun) {
                        appPreferences.setOnboardingCompleted()
                        navController.navigate(ROUTE_ALARM_LIST) {
                            popUpTo(ROUTE_PERMISSIONS) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(ROUTE_PHOTO_IMPORT) {
            PhotoImportScreen(onBack = { navController.popBackStack() })
        }

        composable(ROUTE_CALENDAR_MONTH) {
            CalendarMonthScreen(
                onBack = { navController.popBackStack() },
                onAddAppointment = { date -> navController.navigate("calendarAddAppointment/${date.toEpochDay()}") },
                onAddTask = { date -> navController.navigate("calendarAddTask/${date.toEpochDay()}") },
                onAddShift = { date -> navController.navigate("calendarAddShift/${date.toEpochDay()}") },
                onOpenDay = { date -> navController.navigate("calendarDay/${date.toEpochDay()}") },
                onOpenExport = { navController.navigate(ROUTE_CALENDAR_EXPORT) },
                onOpenCalendarTemplates = { navController.navigate(ROUTE_JOB_LIST) },
                onOpenHolidaySettings = { navController.navigate(ROUTE_HOLIDAY_SETTINGS) },
                onOpenNextcloudSync = { navController.navigate(ROUTE_NEXTCLOUD_SYNC) },
                onOpenRotationEditor = { jobId -> navController.navigate("rotationEditor/$jobId/-1") },
                onOpenJobTimetable = { jobId, jobName ->
                    val encodedName = URLEncoder.encode(jobName, "UTF-8")
                    navController.navigate("jobDetail/$jobId/$encodedName")
                }
            )
        }

        composable(ROUTE_CALENDAR_EXPORT) {
            CalendarExportScreen(onBack = { navController.popBackStack() })
        }

        composable(ROUTE_CALENDAR_DAY) {
            CalendarDayScreen(
                onBack = { navController.popBackStack() },
                onAddAppointment = { date -> navController.navigate("calendarAddAppointment/${date.toEpochDay()}") },
                onAddTask = { date -> navController.navigate("calendarAddTask/${date.toEpochDay()}") },
                onAddShift = { date -> navController.navigate("calendarAddShift/${date.toEpochDay()}") },
                onOpenRotationEditor = { jobId -> navController.navigate("rotationEditor/$jobId/-1") },
                onOpenJobTimetable = { jobId, jobName ->
                    val encodedName = URLEncoder.encode(jobName, "UTF-8")
                    navController.navigate("jobDetail/$jobId/$encodedName")
                }
            )
        }

        composable(ROUTE_CALENDAR_ADD_APPOINTMENT) { backStackEntry ->
            val epochDay = backStackEntry.arguments?.getString("epochDay")?.toLongOrNull() ?: LocalDate.now().toEpochDay()
            val date = LocalDate.ofEpochDay(epochDay)
            val zone = ZoneId.systemDefault()
            val beginMillis = date.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
            val endMillis = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

            AddAppointmentScreen(
                initialTitle = "",
                initialLocation = "",
                initialDescription = "",
                initialBeginMillis = beginMillis,
                initialEndMillis = endMillis,
                initialAllDay = false,
                onSaved = { navController.popBackStack() },
                onCancelled = { navController.popBackStack() }
            )
        }

        composable(ROUTE_CALENDAR_ADD_TASK) { backStackEntry ->
            val epochDay = backStackEntry.arguments?.getString("epochDay")?.toLongOrNull() ?: LocalDate.now().toEpochDay()
            AddTaskScreen(
                initialEpochDay = epochDay,
                onSaved = { navController.popBackStack() },
                onCancelled = { navController.popBackStack() }
            )
        }

        composable(ROUTE_CALENDAR_ADD_SHIFT) { backStackEntry ->
            val epochDay = backStackEntry.arguments?.getString("epochDay")?.toLongOrNull() ?: LocalDate.now().toEpochDay()
            AddShiftEntryScreen(
                initialEpochDay = epochDay,
                onSaved = { navController.popBackStack() },
                onCancelled = { navController.popBackStack() }
            )
        }

        composable(ROUTE_JOB_LIST) {
            JobListScreen(
                onOpenJob = { jobId, jobName ->
                    val encodedName = URLEncoder.encode(jobName, "UTF-8")
                    navController.navigate("jobDetail/$jobId/$encodedName")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_JOB_DETAIL) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId")?.toLongOrNull() ?: return@composable
            val encodedName = backStackEntry.arguments?.getString("jobName").orEmpty()
            val jobName = URLDecoder.decode(encodedName, "UTF-8")

            JobDetailScreen(
                jobName = jobName,
                onOpenRotationEditor = { rotationId ->
                    navController.navigate("rotationEditor/$jobId/${rotationId ?: -1L}")
                },
                onOpenPhotoImport = { navController.navigate("photoImport/$jobId") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_ROTATION_EDITOR) {
            RotationEditorScreen(onBack = { navController.popBackStack() })
        }

        composable(ROUTE_ALARM_EDIT) { backStackEntry ->
            val alarmId = backStackEntry.arguments?.getString("alarmId")?.toLongOrNull() ?: return@composable
            val pendingSoundUri by backStackEntry.savedStateHandle
                .getStateFlow<String?>(KEY_SELECTED_SOUND_URI, null)
                .collectAsState()

            AlarmEditScreen(
                alarmId = alarmId,
                pendingSoundUri = pendingSoundUri,
                onPickSound = { currentUri ->
                    val encoded = URLEncoder.encode(currentUri ?: "", "UTF-8")
                    navController.navigate("soundPicker/$encoded")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_SOUND_PICKER) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("currentUri").orEmpty()
            val currentUri = URLDecoder.decode(encodedUri, "UTF-8").ifBlank { null }

            SoundPickerScreen(
                currentSoundUri = currentUri,
                onSoundSelected = { uri ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_SELECTED_SOUND_URI, uri)
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
