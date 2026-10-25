package de.letheapp.nocturn.iobroker

import de.letheapp.nocturn.data.db.AlarmEntity
import de.letheapp.nocturn.data.db.ShiftEntity

/** Betriebsart einer konfigurierten ioBroker-Aktion, siehe [IoBrokerActionConfig]. */
enum class IoBrokerActionMode { ON_OFF, RAMP }

/**
 * Eine konfigurierte ioBroker-Aktion für einen Alarm ([AlarmEntity]) oder eine Schicht
 * ([ShiftEntity]), ausgelöst beim Notfall-Alarm (siehe
 * [de.letheapp.nocturn.emergency.EmergencyAlarmService]). Pro Alarm/Schicht sind bis zu drei
 * solcher Aktionen möglich (zwei frei wählbare Datenpunkte, z. B. Lampe/Rollo, plus ein dritter,
 * eigens für einen Skript-Auslöser gedachter Datenpunkt, z. B. für Radio).
 *
 * [IoBrokerActionMode.ON_OFF] setzt [dataPointId] einmalig auf `"true"`/`"false"` ([turnOn]).
 * [IoBrokerActionMode.RAMP] erhöht einen numerischen Datenpunkt (z. B. Helligkeit) schrittweise um
 * [rampStep] bis [rampTarget] erreicht ist (sanftes Aufdimmen über die Dauer des Notfall-Alarms),
 * siehe [IoBrokerRepository.runRamp].
 */
data class IoBrokerActionConfig(
    val dataPointId: String,
    val dataPointLabel: String,
    val mode: IoBrokerActionMode = IoBrokerActionMode.ON_OFF,
    val turnOn: Boolean = true,
    val rampStep: Double? = null,
    val rampTarget: Double? = null,
    /**
     * Nur bei [mode] == [IoBrokerActionMode.ON_OFF] und [turnOn] == true relevant: schaltet den
     * Datenpunkt automatisch wieder aus, sobald der Notfall-Alarm endet (siehe
     * [de.letheapp.nocturn.emergency.EmergencyAlarmService]), statt dauerhaft eingeschaltet zu
     * bleiben.
     */
    val autoTurnOff: Boolean = false
)

/** Kurze Anzeige-Zusammenfassung für eine Zeile im Alarm-/Schicht-Bearbeiten-Screen. */
fun IoBrokerActionConfig.summaryLabel(): String = when (mode) {
    IoBrokerActionMode.ON_OFF -> {
        val state = if (turnOn) "Ein" else "Aus"
        val autoOffSuffix = if (turnOn && autoTurnOff) ", auto. aus" else ""
        "$dataPointLabel ($state$autoOffSuffix)"
    }
    IoBrokerActionMode.RAMP -> "$dataPointLabel (bis ${formatIoBrokerNumber(rampTarget)})"
}

/** Formatiert einen ioBroker-Zahlenwert ohne unnötige Nachkommastelle (z. B. "80" statt "80.0"). */
fun formatIoBrokerNumber(value: Double?): String {
    if (value == null) return "?"
    return if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()
}

private fun buildConfig(
    dataPointId: String?,
    dataPointLabel: String?,
    mode: String?,
    turnOn: Boolean?,
    rampStep: Double?,
    rampTarget: Double?,
    autoTurnOff: Boolean = false
): IoBrokerActionConfig? {
    val id = dataPointId ?: return null
    return IoBrokerActionConfig(
        dataPointId = id,
        dataPointLabel = dataPointLabel ?: id,
        mode = if (mode == IoBrokerActionMode.RAMP.name) IoBrokerActionMode.RAMP else IoBrokerActionMode.ON_OFF,
        turnOn = turnOn ?: true,
        rampStep = rampStep,
        rampTarget = rampTarget,
        autoTurnOff = autoTurnOff
    )
}

// --- AlarmEntity: zwei frei wählbare Datenpunkte (z. B. Lampe/Rollo) + ein Skript-Auslöser ---

fun AlarmEntity.ioBrokerAction1(): IoBrokerActionConfig? = buildConfig(
    ioBrokerAction1DataPointId, ioBrokerAction1DataPointLabel, ioBrokerAction1Mode,
    ioBrokerAction1TurnOn, ioBrokerAction1RampStep, ioBrokerAction1RampTarget, ioBrokerAction1AutoTurnOff
)

fun AlarmEntity.withIoBrokerAction1(config: IoBrokerActionConfig?): AlarmEntity = copy(
    ioBrokerAction1DataPointId = config?.dataPointId,
    ioBrokerAction1DataPointLabel = config?.dataPointLabel,
    ioBrokerAction1Mode = config?.mode?.name,
    ioBrokerAction1TurnOn = config?.turnOn,
    ioBrokerAction1RampStep = config?.rampStep,
    ioBrokerAction1RampTarget = config?.rampTarget,
    ioBrokerAction1AutoTurnOff = config?.autoTurnOff ?: false
)

fun AlarmEntity.ioBrokerAction2(): IoBrokerActionConfig? = buildConfig(
    ioBrokerAction2DataPointId, ioBrokerAction2DataPointLabel, ioBrokerAction2Mode,
    ioBrokerAction2TurnOn, ioBrokerAction2RampStep, ioBrokerAction2RampTarget, ioBrokerAction2AutoTurnOff
)

fun AlarmEntity.withIoBrokerAction2(config: IoBrokerActionConfig?): AlarmEntity = copy(
    ioBrokerAction2DataPointId = config?.dataPointId,
    ioBrokerAction2DataPointLabel = config?.dataPointLabel,
    ioBrokerAction2Mode = config?.mode?.name,
    ioBrokerAction2TurnOn = config?.turnOn,
    ioBrokerAction2RampStep = config?.rampStep,
    ioBrokerAction2RampTarget = config?.rampTarget,
    ioBrokerAction2AutoTurnOff = config?.autoTurnOff ?: false
)

fun AlarmEntity.ioBrokerScriptAction(): IoBrokerActionConfig? = buildConfig(
    ioBrokerScriptDataPointId, ioBrokerScriptDataPointLabel, ioBrokerScriptMode,
    ioBrokerScriptTurnOn, ioBrokerScriptRampStep, ioBrokerScriptRampTarget, ioBrokerScriptAutoTurnOff
)

fun AlarmEntity.withIoBrokerScriptAction(config: IoBrokerActionConfig?): AlarmEntity = copy(
    ioBrokerScriptDataPointId = config?.dataPointId,
    ioBrokerScriptDataPointLabel = config?.dataPointLabel,
    ioBrokerScriptMode = config?.mode?.name,
    ioBrokerScriptTurnOn = config?.turnOn,
    ioBrokerScriptRampStep = config?.rampStep,
    ioBrokerScriptRampTarget = config?.rampTarget,
    ioBrokerScriptAutoTurnOff = config?.autoTurnOff ?: false
)

/** Alle bis zu drei konfigurierten Aktionen dieses Alarms, siehe [de.letheapp.nocturn.emergency.EmergencyAlarmService]. */
fun AlarmEntity.allIoBrokerActions(): List<IoBrokerActionConfig> =
    listOfNotNull(ioBrokerAction1(), ioBrokerAction2(), ioBrokerScriptAction())

// --- ShiftEntity: dieselben drei Slots, pro Schicht statt pro Alarm (siehe ShiftWakeupAlarmSyncer) ---

fun ShiftEntity.ioBrokerAction1(): IoBrokerActionConfig? = buildConfig(
    ioBrokerAction1DataPointId, ioBrokerAction1DataPointLabel, ioBrokerAction1Mode,
    ioBrokerAction1TurnOn, ioBrokerAction1RampStep, ioBrokerAction1RampTarget, ioBrokerAction1AutoTurnOff
)

fun ShiftEntity.withIoBrokerAction1(config: IoBrokerActionConfig?): ShiftEntity = copy(
    ioBrokerAction1DataPointId = config?.dataPointId,
    ioBrokerAction1DataPointLabel = config?.dataPointLabel,
    ioBrokerAction1Mode = config?.mode?.name,
    ioBrokerAction1TurnOn = config?.turnOn,
    ioBrokerAction1RampStep = config?.rampStep,
    ioBrokerAction1RampTarget = config?.rampTarget,
    ioBrokerAction1AutoTurnOff = config?.autoTurnOff ?: false
)

fun ShiftEntity.ioBrokerAction2(): IoBrokerActionConfig? = buildConfig(
    ioBrokerAction2DataPointId, ioBrokerAction2DataPointLabel, ioBrokerAction2Mode,
    ioBrokerAction2TurnOn, ioBrokerAction2RampStep, ioBrokerAction2RampTarget, ioBrokerAction2AutoTurnOff
)

fun ShiftEntity.withIoBrokerAction2(config: IoBrokerActionConfig?): ShiftEntity = copy(
    ioBrokerAction2DataPointId = config?.dataPointId,
    ioBrokerAction2DataPointLabel = config?.dataPointLabel,
    ioBrokerAction2Mode = config?.mode?.name,
    ioBrokerAction2TurnOn = config?.turnOn,
    ioBrokerAction2RampStep = config?.rampStep,
    ioBrokerAction2RampTarget = config?.rampTarget,
    ioBrokerAction2AutoTurnOff = config?.autoTurnOff ?: false
)

fun ShiftEntity.ioBrokerScriptAction(): IoBrokerActionConfig? = buildConfig(
    ioBrokerScriptDataPointId, ioBrokerScriptDataPointLabel, ioBrokerScriptMode,
    ioBrokerScriptTurnOn, ioBrokerScriptRampStep, ioBrokerScriptRampTarget, ioBrokerScriptAutoTurnOff
)

fun ShiftEntity.withIoBrokerScriptAction(config: IoBrokerActionConfig?): ShiftEntity = copy(
    ioBrokerScriptDataPointId = config?.dataPointId,
    ioBrokerScriptDataPointLabel = config?.dataPointLabel,
    ioBrokerScriptMode = config?.mode?.name,
    ioBrokerScriptTurnOn = config?.turnOn,
    ioBrokerScriptRampStep = config?.rampStep,
    ioBrokerScriptRampTarget = config?.rampTarget,
    ioBrokerScriptAutoTurnOff = config?.autoTurnOff ?: false
)
