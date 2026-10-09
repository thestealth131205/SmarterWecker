package de.letheapp.nocturn.iobroker

import de.letheapp.nocturn.data.db.IoBrokerAccountDao
import de.letheapp.nocturn.data.db.IoBrokerAccountEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestriert die ioBroker-Smart-Home-Kopplung (siehe CLAUDE.md "Smart-Home-Kopplung
 * (ioBroker)"): genau ein verbundener Server, dessen Datenpunkte einem Alarm als
 * Lampe/Steckdose zugeordnet werden können. Analog zu [de.letheapp.nocturn.sync.NextcloudSyncRepository].
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

    /** Schaltet [dataPointId] ein (z. B. beim Auslösen des Notfall-Alarms). Still bei fehlender Verbindung. */
    suspend fun turnOn(dataPointId: String): Result<Unit> {
        val account = accountDao.getAccountOnce()
            ?: return Result.failure(IllegalStateException("Kein ioBroker-Server verbunden"))
        val password = credentialsStore.getPassword(account.id).orEmpty()
        return client.setState(account.host, account.port, account.useHttps, account.username, password, dataPointId, "true")
    }
}
