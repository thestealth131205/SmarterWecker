package de.letheapp.nocturn.iobroker

import de.letheapp.nocturn.data.db.IoBrokerAccountDao
import de.letheapp.nocturn.data.db.IoBrokerAccountEntity
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Wartezeit zwischen zwei Schritten eines [IoBrokerRepository.runRamp]-Laufs. */
private const val RAMP_STEP_INTERVAL_MILLIS = 8_000L

/**
 * Orchestriert die ioBroker-Smart-Home-Kopplung (siehe CLAUDE.md "Smart-Home-Kopplung
 * (ioBroker)"): genau ein verbundener Server, dessen Datenpunkte einem Alarm/einer Schicht als
 * Lampe/Rollo/Skript-Auslöser zugeordnet werden können (siehe [IoBrokerActionConfig]). Analog zu
 * [de.letheapp.nocturn.sync.NextcloudSyncRepository].
 */
@Singleton
class IoBrokerRepository @Inject constructor(
    private val client: IoBrokerClient,
    private val credentialsStore: IoBrokerCredentialsStore,
    private val accountDao: IoBrokerAccountDao
) {
    fun observeAccount(): Flow<IoBrokerAccountEntity?> = accountDao.observeAccount()

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

    suspend fun fetchDataPoints(): Result<List<IoBrokerDataPoint>> {
        val account = accountDao.getAccountOnce()
            ?: return Result.failure(IllegalStateException("Kein ioBroker-Server verbunden"))
        val password = credentialsStore.getPassword(account.id).orEmpty()
        return client.fetchDataPoints(account.host, account.port, account.useHttps, account.username, password)
    }

    /** Führt [config] aus (z. B. beim Auslösen des Notfall-Alarms) — Ein/Aus einmalig, oder stufenweises Erhöhen bis zum Zielwert, siehe [runRamp]. */
    suspend fun executeAction(config: IoBrokerActionConfig) {
        when (config.mode) {
            IoBrokerActionMode.ON_OFF -> turnOnOrOff(config.dataPointId, config.turnOn)
            IoBrokerActionMode.RAMP -> runRamp(config)
        }
    }

    /** Setzt [dataPointId] auf `"true"`/`"false"`. Still bei fehlender Verbindung. */
    suspend fun turnOnOrOff(dataPointId: String, turnOn: Boolean): Result<Unit> {
        val account = accountDao.getAccountOnce()
            ?: return Result.failure(IllegalStateException("Kein ioBroker-Server verbunden"))
        val password = credentialsStore.getPassword(account.id).orEmpty()
        return client.setState(
            account.host, account.port, account.useHttps, account.username, password,
            dataPointId, if (turnOn) "true" else "false"
        )
    }

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
        val account = accountDao.getAccountOnce() ?: return
        val password = credentialsStore.getPassword(account.id).orEmpty()
        val points = client.fetchDataPoints(account.host, account.port, account.useHttps, account.username, password)
            .getOrNull() ?: return
        var current = points.find { it.id == config.dataPointId }?.value?.toDoubleOrNull() ?: 0.0
        val increasing = target >= current
        val absStep = abs(step)
        while (if (increasing) current < target else current > target) {
            current = if (increasing) (current + absStep).coerceAtMost(target) else (current - absStep).coerceAtLeast(target)
            client.setState(
                account.host, account.port, account.useHttps, account.username, password,
                config.dataPointId, formatIoBrokerNumber(current)
            )
            if (current == target) break
            delay(RAMP_STEP_INTERVAL_MILLIS)
        }
    }
}
