package de.letheapp.nocturn.iobroker

import de.letheapp.nocturn.data.db.IoBrokerAccountDao
import de.letheapp.nocturn.data.db.IoBrokerAccountEntity
import de.letheapp.nocturn.onboarding.AppPreferences
import de.letheapp.nocturn.onboarding.HomeAssistantConfig
import de.letheapp.nocturn.onboarding.SmartHomeBackend
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Wartezeit zwischen zwei Schritten eines [IoBrokerRepository.runRamp]-Laufs. */
private const val RAMP_STEP_INTERVAL_MILLIS = 8_000L

/**
 * Pseudo-Konto-ID, unter der der Home-Assistant-Langzeit-Zugriffstoken im
 * [IoBrokerCredentialsStore] liegt (echte ioBroker-Konten haben Room-IDs ≥ 1).
 */
private const val HOME_ASSISTANT_TOKEN_ID = -1L

/**
 * Orchestriert die Smart-Home-Kopplung (siehe CLAUDE.md "Smart-Home-Kopplung"): je nach App-weit
 * gewähltem [SmartHomeBackend] entweder genau ein verbundener "iosync"-ioBroker-Server oder eine
 * Home-Assistant-Instanz, deren Datenpunkte/Entities einem Alarm/einer Schicht als
 * Lampe/Rollo/Skript-Auslöser zugeordnet werden können (siehe [IoBrokerActionConfig]). Beide
 * Backends liefern dieselben [IoBrokerDataPoint]s, Auswahl-Dialoge und Notfall-Alarm-Ausführung
 * bleiben dadurch backend-unabhängig. Analog zu [de.letheapp.nocturn.sync.NextcloudSyncRepository].
 */
@Singleton
class IoBrokerRepository @Inject constructor(
    private val client: IoBrokerClient,
    private val homeAssistantClient: HomeAssistantClient,
    private val credentialsStore: IoBrokerCredentialsStore,
    private val accountDao: IoBrokerAccountDao,
    private val appPreferences: AppPreferences
) {
    fun observeAccount(): Flow<IoBrokerAccountEntity?> = accountDao.observeAccount()

    /** `true`, sobald das aktuell gewählte Backend eingerichtet ist. */
    fun observeConnected(): Flow<Boolean> = combine(
        appPreferences.smartHomeBackend,
        accountDao.observeAccount(),
        appPreferences.homeAssistantConfig
    ) { backend, account, haConfig ->
        when (backend) {
            SmartHomeBackend.IOBROKER -> account != null
            SmartHomeBackend.HOME_ASSISTANT -> haConfig != null
        }
    }

    suspend fun testConnection(host: String, port: Int, useHttps: Boolean, username: String, password: String): Boolean =
        client.checkHealth(host, port, useHttps, username, password)

    /** Verbindet ein (einziges, bestehendes Konto wird ersetzt) ioBroker-Konto. */
    suspend fun connect(host: String, port: Int, useHttps: Boolean, username: String, password: String): Long {
        disconnect()
        val id = accountDao.insert(IoBrokerAccountEntity(host = host.trim(), port = port, useHttps = useHttps, username = username.trim()))
        credentialsStore.savePassword(id, password)
        return id
    }

    suspend fun disconnect() {
        accountDao.getAccountOnce()?.let { account ->
            credentialsStore.clearPassword(account.id)
            accountDao.delete(account)
        }
    }

    suspend fun testHomeAssistant(host: String, port: Int, useHttps: Boolean, token: String): Boolean =
        homeAssistantClient.checkHealth(host, port, useHttps, token)

    fun connectHomeAssistant(host: String, port: Int, useHttps: Boolean, token: String) {
        credentialsStore.savePassword(HOME_ASSISTANT_TOKEN_ID, token.trim())
        appPreferences.setHomeAssistantConfig(HomeAssistantConfig(host.trim(), port, useHttps))
    }

    fun disconnectHomeAssistant() {
        credentialsStore.clearPassword(HOME_ASSISTANT_TOKEN_ID)
        appPreferences.clearHomeAssistantConfig()
    }

    suspend fun fetchDataPoints(): Result<List<IoBrokerDataPoint>> =
        when (appPreferences.smartHomeBackend.value) {
            SmartHomeBackend.IOBROKER -> {
                val account = accountDao.getAccountOnce()
                    ?: return Result.failure(IllegalStateException("Kein ioBroker-Server verbunden"))
                val password = credentialsStore.getPassword(account.id).orEmpty()
                client.fetchDataPoints(account.host, account.port, account.useHttps, account.username, password)
            }
            SmartHomeBackend.HOME_ASSISTANT -> {
                val config = appPreferences.homeAssistantConfig.value
                    ?: return Result.failure(IllegalStateException("Kein Home Assistant verbunden"))
                homeAssistantClient.fetchDataPoints(config.host, config.port, config.useHttps, homeAssistantToken())
            }
        }

    /** Schreibt [value] über das aktuell gewählte Backend (ioBroker: setState, Home Assistant: Service-Call). */
    private suspend fun setValue(dataPointId: String, value: String): Result<Unit> =
        when (appPreferences.smartHomeBackend.value) {
            SmartHomeBackend.IOBROKER -> {
                val account = accountDao.getAccountOnce()
                    ?: return Result.failure(IllegalStateException("Kein ioBroker-Server verbunden"))
                val password = credentialsStore.getPassword(account.id).orEmpty()
                client.setState(account.host, account.port, account.useHttps, account.username, password, dataPointId, value)
            }
            SmartHomeBackend.HOME_ASSISTANT -> {
                val config = appPreferences.homeAssistantConfig.value
                    ?: return Result.failure(IllegalStateException("Kein Home Assistant verbunden"))
                homeAssistantClient.setState(config.host, config.port, config.useHttps, homeAssistantToken(), dataPointId, value)
            }
        }

    private fun homeAssistantToken(): String =
        runCatching { credentialsStore.getPassword(HOME_ASSISTANT_TOKEN_ID) }.getOrNull().orEmpty()

    /** Führt [config] aus (z. B. beim Auslösen des Notfall-Alarms) — Ein/Aus einmalig, oder stufenweises Erhöhen bis zum Zielwert, siehe [runRamp]. */
    suspend fun executeAction(config: IoBrokerActionConfig) {
        when (config.mode) {
            IoBrokerActionMode.ON_OFF -> turnOnOrOff(config.dataPointId, config.turnOn)
            IoBrokerActionMode.RAMP -> runRamp(config)
        }
    }

    /** Setzt [dataPointId] auf `"true"`/`"false"` (Home Assistant: turn_on/turn_off). Still bei fehlender Verbindung. */
    suspend fun turnOnOrOff(dataPointId: String, turnOn: Boolean): Result<Unit> =
        setValue(dataPointId, if (turnOn) "true" else "false")

    /**
     * Erhöht [IoBrokerActionConfig.dataPointId] in Schritten von [IoBrokerActionConfig.rampStep]
     * bis [IoBrokerActionConfig.rampTarget] erreicht ist (sanftes Aufdimmen, z. B. Helligkeit beim
     * Notfall-Alarm) — läuft weiter, bis entweder der Zielwert erreicht ist oder die aufrufende
     * Coroutine abgebrochen wird (z. B. wenn der Notfall-Alarm endet). Still bei fehlender
     * Verbindung oder nicht lesbarem Startwert.
     */
    suspend fun runRamp(config: IoBrokerActionConfig) {
        val step = config.rampStep?.takeIf { it != 0.0 } ?: return
        val target = config.rampTarget ?: return
        val points = fetchDataPoints().getOrNull() ?: return
        var current = points.find { it.id == config.dataPointId }?.value?.toDoubleOrNull() ?: 0.0
        val increasing = target >= current
        val absStep = abs(step)
        while (if (increasing) current < target else current > target) {
            current = if (increasing) (current + absStep).coerceAtMost(target) else (current - absStep).coerceAtLeast(target)
            setValue(config.dataPointId, formatIoBrokerNumber(current))
            if (current == target) break
            delay(RAMP_STEP_INTERVAL_MILLIS)
        }
    }
}
