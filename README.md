# Smarter Wecker

Android-App, die es praktisch unmöglich macht, den Wecker zu verschlafen. Statt
simplem Wegdrücken wird nach dem Klingeln aktiv per Sensorik/Health Connect
überprüft, ob man tatsächlich aufgestanden ist und sich bewegt hat.

> **Status:** Konzeptphase – es existiert noch kein Code, siehe `CLAUDE.md` und
> `memory.md` für Details und offene Architekturentscheidungen.

## Kernfunktionen

- **Aufsteh-Erkennung:** Health Connect (Schritte/Herzfrequenz) + direkte
  Sensorauswertung (Gyroskop/Beschleunigungssensor) als Fallback ohne Smartwatch.
- **Snooze:** Erneutes Klingeln nach zufälligem Intervall (3–5 Min), Weckton
  eskaliert/ändert sich während des Klingelns.
- **Stop ≠ fertig:** Nach "Stop" wird bis zu 10 Minuten weiter überwacht, ob der
  Nutzer aufsteht und läuft. Sonst: Notfall-Alarm.
- **Notfall-Alarm:** Nicht per Knopfdruck stoppbar – nur durch Lösen einer
  Farbkombinations-Aufgabe (Mini-Puzzle).
- **Schichtplan-Rotation:** Frei definierbare Schichten (Name + Farbe) mit
  hinterlegter Rotation, die die jeweilige Weckzeit bestimmt.
- **Kalender:** Eingebaute Visualisierung der Schichtrotation, Sync mit Google
  Calendar und Nextcloud (CalDAV) geplant.

## Geplanter Tech-Stack

- Kotlin + Jetpack Compose (Android, nativ)
- Health Connect API, `SensorManager` (ACC/Gyro/Step Counter) als Fallback
- `AlarmManager` (exact alarms) + Foreground Service für die Überwachungsphase
- Google Calendar API (OAuth) + CalDAV-Client für Nextcloud

Details und offene Fragen siehe [`CLAUDE.md`](CLAUDE.md).
