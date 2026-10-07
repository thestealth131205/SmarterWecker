# CLAUDE.md

Diese Datei gibt Claude Code Kontext für das Projekt **Smarter Wecker**.

## Projektübersicht

Android-App (Kotlin/Jetpack Compose geplant), die es praktisch unmöglich macht, den
Wecker zu verschlafen. Kernidee: nach dem Klingeln wird aktiv über Sensorik überwacht,
ob der Nutzer tatsächlich aufgestanden ist — reines Wegdrücken des Alarms reicht nicht.

### Kernfunktionen (Konzept, noch nicht implementiert)

1. **Aufsteh-Erkennung nach dem Klingeln:**
   - Health Connect API (Schritte, ggf. Herzfrequenz/Aktivitätsstatus) wird ausgelesen.
   - Zusätzlich direkte Sensor-Auswertung über Gyroskop/Beschleunigungssensor (ACC),
     um Bewegung/Aufstehen auch ohne Health-Connect-Datenquelle (z. B. keine Smartwatch)
     erkennen zu können.
   - Nach dem Klingeln muss erkennbar sein: Nutzer ist aufgestanden UND hat sich
     fortbewegt (Schritte zurückgelegt).

2. **Snooze-Verhalten:**
   - Bei "Snooze" wird in einem zufälligen Intervall zwischen 3 und 5 Minuten erneut
     geweckt (nicht fest, damit man sich nicht daran gewöhnt/die Zeit totliegt).
   - Der Weckton/die Tonart ändert sich während des Klingelns (eskalierend), damit man
     sich nicht an einen Sound gewöhnt und ihn ausblendet.

3. **"Stop" ist kein Freifahrtschein:**
   - Drückt man statt "Snooze" auf "Stop", wird trotzdem weiter überwacht (Sensorik/
     Health Connect), ob der Nutzer innerhalb von max. 10 Minuten tatsächlich aufsteht
     und läuft.
   - Steht der Nutzer nicht rechtzeitig auf, wird ein **Notfall-Alarm** ausgelöst.

4. **Notfall-Alarm:**
   - Lässt sich NICHT durch einfaches Drücken stoppen.
   - Tonart ändert sich fortlaufend/eskaliert weiter.
   - Einzige Möglichkeit zum Stoppen: eine schnell lösbare
     Farbkombinations-Aufgabe (kleines Puzzle/Minispiel) muss korrekt gelöst werden.

5. **Schichtplan-Rotation:**
   - Nutzer kann eigene Schichten definieren: Name der Schicht (z. B. "Frühschicht",
     "Spätschicht", "Nachtschicht") + frei wählbare Farbe pro Schicht.
   - Rotation der Schichten wird hinterlegt, damit die App weiß, an welchem Tag welche
     Schicht (und damit welche Weckzeit) gilt.

6. **Kalender:**
   - Eingebauter Kalender, der die Schichtrotation visualisiert.
   - Synchronisierbar mit **Google Calendar** und **Nextcloud** (CalDAV), bidirektional
     geplant (TBD ob Lesen reicht oder auch Schreiben nötig ist).

7. **Home-Screen-Widgets:**
   - Uhr-Widget und Wetter-Widget für den Android-Homescreen.
   - Wetter-Widget nutzt **OpenWeatherMap** als Datenquelle (gleicher Ansatz wie im
     Iosync-Projekt: API-Key über `local.properties`/CI-Secret `OPENWEATHER_API_KEY`,
     nicht hart codiert).
   - Uhr-Widget-Layout (siehe Referenzbild): transparent über dem Homescreen-Hintergrund,
     von oben nach unten vertikal zentriert gestapelt:
     1. Datum klein/hell (z. B. "Do., 8. Okt.")
     2. Uhrzeit sehr groß (z. B. "00:29")
     3. nächster Alarm klein mit Wecker-Icon davor (z. B. "⏰ Do., 04:47")

8. **Ton-Auswahl für Wecker/Alarm:**
   - Neben lokalen/klassischen Wecktönen auch Auswahl aus **Spotify**, **Deezer** und der
     **Lethe-Bibliothek** als Sound-Quelle für den Alarmton.

## Architektur (noch offen / zu entscheiden)

- Zielplattform: Android (nativ, analog zum Lethe-Messenger-Stack vermutlich Kotlin +
  Jetpack Compose + Hilt, aber noch nicht festgelegt).
- Health Connect: offizielle Android Health Connect API.
- Bewegungserkennung ohne Health Connect: `SensorManager` mit `TYPE_ACCELEROMETER` /
  `TYPE_GYROSCOPE`, ggf. Schrittzähler-Sensor `TYPE_STEP_COUNTER`/`TYPE_STEP_DETECTOR`.
- Alarm-Zuverlässigkeit: `AlarmManager` (exact alarms) + Foreground Service während der
  Überwachungsphase (Sensorik darf nicht durch Doze/Battery-Optimizations gekillt werden).
- Kalendersync: Google Calendar API (OAuth) + CalDAV-Client für Nextcloud.
- Wetterdaten: OpenWeatherMap API (One Call / Current Weather), API-Key-Handling analog
  Iosync-Projekt (`/opt/Iosync`): Key aus `local.properties` lokal, aus GitHub-Secret
  `OPENWEATHER_API_KEY` im CI, Einbindung als `BuildConfig`-Feld — niemals im Repo.

## Wichtig

- Projekt-Memory liegt in `/opt/SmarterWecker/memory.md` — vor Arbeit an diesem Projekt
  immer zuerst dorthin wechseln und diese Datei lesen.
- Ausgewählt im Chat-Dropdown unter `/var/www` als "Smarter Wecker".
- Noch in der Konzeptionsphase — noch kein Code vorhanden.
