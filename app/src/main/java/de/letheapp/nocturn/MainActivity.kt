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
import de.letheapp.nocturn.calendar.CalendarMonthScreen
import de.letheapp.nocturn.calendar.JobDetailScreen
import de.letheapp.nocturn.calendar.JobListScreen
import de.letheapp.nocturn.calendar.RotationEditorScreen
import de.letheapp.nocturn.sound.SoundPickerScreen
import de.letheapp.nocturn.ui.theme.NocTurnTheme
import java.net.URLDecoder
import java.net.URLEncoder

private const val ROUTE_ALARM_LIST = "alarmList"
private const val ROUTE_ALARM_EDIT = "alarmEdit/{alarmId}"
private const val ROUTE_SOUND_PICKER = "soundPicker/{currentUri}"
private const val ROUTE_JOB_LIST = "jobList"
private const val ROUTE_JOB_DETAIL = "jobDetail/{jobId}/{jobName}"
private const val ROUTE_ROTATION_EDITOR = "rotationEditor/{jobId}/{rotationId}"
private const val ROUTE_CALENDAR_MONTH = "calendarMonth"
private const val KEY_SELECTED_SOUND_URI = "selected_sound_uri"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NocTurnTheme {
                NocturnNavHost()
            }
        }
    }
}

@Composable
private fun NocturnNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_ALARM_LIST) {
        composable(ROUTE_ALARM_LIST) {
            AlarmListScreen(
                onEditAlarm = { id -> navController.navigate("alarmEdit/$id") },
                onOpenCalendarTemplates = { navController.navigate(ROUTE_JOB_LIST) },
                onOpenCalendarMonth = { navController.navigate(ROUTE_CALENDAR_MONTH) }
            )
        }

        composable(ROUTE_CALENDAR_MONTH) {
            CalendarMonthScreen(onBack = { navController.popBackStack() })
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
