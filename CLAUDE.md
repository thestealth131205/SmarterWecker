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
   - Drückt man statt "Snooze" auf "Stop", öffnet sich **sofort** die Notfall-Alarm-Aufgabe
     (eskalierender Ton + Zuordnungs-Puzzle, siehe Kernfunktion 4) — der Weckton reißt dabei
     nicht ab, sondern läuft nahtlos weiter.
   - Parallel dazu wird weiter per Sensorik/Health Connect überwacht, ob der Nutzer
     tatsächlich aufsteht und läuft (max. 10 Minuten bzw. bis zur "spätestens
     aufstehen"-Deadline, siehe Kernfunktion 5): wird echte Bewegung erkannt, beendet sich
     der Notfall-Alarm automatisch, **auch ohne gelöstes Puzzle**.
   - Ohne erkannte Bewegung bleibt das Lösen des Puzzles der einzige Weg, den Notfall-Alarm
     zu beenden.

4. **Notfall-Alarm:**
   - Lässt sich NICHT durch einfaches Drücken stoppen.
   - Tonart ändert sich fortlaufend/eskaliert weiter.
   - Einzige Möglichkeit zum Stoppen: eine schnell lösbare Zuordnungs-Aufgabe
     (kleines Drag-&-Drop-Puzzle/Minispiel) muss korrekt gelöst werden.
   - **Variierende Aufgabenstellung** (nicht immer dieselbe Aufgabe, damit man sie sich
     nicht einprägt/automatisiert löst):
     - Grundprinzip: oben wird eine Reihe farbiger Geometrien/Symbole in einer
       bestimmten Reihenfolge vorgegeben (z. B. 6 Elemente: blauer Kreis, gelbes
       Dreieck, pinkes Quadrat, ...). Darunter liegt ein größerer Pool an Elementen
       (z. B. 10 Geometrien, inkl. Ablenker, die nicht zur vorgegebenen Reihe passen),
       aus dem der Nutzer die passenden per Drag & Drop an die richtige Position in der
       zweiten Reihe ziehen muss.
     - Sowohl die Form/Farbe der vorgegebenen Elemente als auch die Zusammensetzung des
       Auswahl-Pools wechseln bei jedem Auftreten zufällig.
     - Elementtyp variiert ebenfalls zwischen Weckungen (z. B. statt Geometrien auch
       Smileys/Emojis), ebenso wie die Anzahl der zu sortierenden Elemente
       (z. B. mal 6 Geometrien, mal 5 Smileys in vorgegebener Reihenfolge).
     - Ziel: über mehrere Weckungen hinweg soll sich weder das visuelle Motiv noch die
       exakte Aufgabengröße wiederholen, damit die Lösung jedes Mal echtes Hinschauen
       statt Mustererkennung/Auswendiglernen erfordert.

5. **Schichtplan-Rotation / Stundenplan:**
   - Nutzer kann eigene Schichten definieren: Name der Schicht (z. B. "Frühschicht",
     "Spätschicht", "Nachtschicht") + frei wählbare Farbe pro Schicht.
   - Rotation der Schichten wird hinterlegt, damit die App weiß, an welchem Tag welche
     Schicht (und damit welche Weckzeit) gilt. Pro Tag ist bei der Schicht-Rotation
     **genau eine Schicht** vorgesehen.
   - **Umschaltbarer Schüler/Student-Modus**: alternativ zur Tages-Schicht-Rotation kann
     ein **Wochenstundenplan** hinterlegt werden (klassischer Schul-/Uni-Stundenplan).
     Hier gilt statt "eine Schicht pro Tag" ein Raster von **bis zu 12
     Unterrichtsstunden pro Tag**, jede mit eigener Start-/Endzeit und Farbe (z. B. nach
     Fach). Der Stundenplan wiederholt sich wöchentlich, es gibt also keinen
     mehrtägigen Rotationszyklus wie bei Schichtarbeit.
   - **Foto-Import**: Stundenplan bzw. Schichtplan kann mit der Kamera abfotografiert
     werden; per Texterkennung (OCR) werden Schichten/Unterrichtsstunden inkl.
     Start-/Endzeit automatisch erkannt, daraus wird automatisch eine Rotation bzw. ein
     Wochenstundenplan inkl. Farbzuweisung erzeugt. Der Nutzer kann das
     Erkennungsergebnis vor der Übernahme noch korrigieren.
   - **Konfigurierbarer Weckzeit-Abstand statt fixer Schicht-Startzeit**: Der Wecker
     klingelt nicht zwingend exakt zur Schicht-/Unterrichtsbeginn-Zeit, sondern mit
     einem einstellbaren Vorlauf. Pro Schicht bzw. vor der **ersten** Unterrichtsstunde
     des Tages werden zwei Werte in Minuten vor Schicht-/Unterrichtsbeginn hinterlegt:
     - **"frühestes Wecken"**: wann der (erste) Alarm klingelt.
     - **"spätestens aufstehen"**: späteste Zeit, zu der laut Aufsteh-Erkennung
       (Kernfunktion 1) tatsächlich aufgestanden sein muss — sonst greift dieselbe
       Stop-Weiterüberwachung/Notfall-Alarm-Logik wie in Kernfunktion 3 beschrieben.

6. **Kalender:**
   - Eingebauter Kalender, der die Schichtrotation visualisiert. Funktionsumfang
     orientiert sich an bestehenden Schichtplan-Apps (siehe Referenzbilder), soll aber
     bewusst **übersichtlicher/aufgeräumter** umgesetzt werden (weniger visuelles
     Rauschen in der Monatsansicht als im Vorbild).
   - **Vorlagen-Verwaltung** in drei Kategorien:
     - *Schichten*: Name, Farbe, Start-/Endzeit, Dauer (z. B. "früh" 06:00–14:00, 8h).
     - *Jobs*: mehrere Jobs/Arbeitsstellen anlegbar, jeweils mit eigenem Satz an
       Schichten (ein Nutzer kann z. B. Hauptjob + Nebenjob getrennt pflegen).
     - *Rotationen*: eine Rotation ist eine Tag-für-Tag-Abfolge von Schichten, die der
       Nutzer so lange einträgt, bis sich das Muster wiederholen würde (z. B. ein
       84-Tage-Zyklus aus 4× Spät, 1× Frei, 2× Wochenende, wiederholt mit 4× Früh,
       1× Frei, 2× Wochenende usw.). Eingabe über ein Raster (Tag-Nummer × 7
       Wochentags-Spalten), pro Tag wird eine Schicht per Farbe/Kürzel zugewiesen.
   - Sobald eine Rotation feststeht, trägt die App das Muster **fortlaufend automatisch**
     in den Kalender ein — nicht nur für die nächsten Wochen, sondern **mehrere Jahre im
     Voraus**, damit der Wecker jederzeit weiß, welche Schicht (und damit Weckzeit)
     ansteht, ohne dass der Nutzer manuell nachpflegen muss.
   - Monatsansicht zeigt pro Tag die zugeordnete(n) Schicht(en) farbig markiert;
     mehrere Einträge pro Tag möglich (z. B. Schicht + zusätzlicher Termin/Urlaub).
   - **Kalender teilen** (bestätigtes Pflichtfeature): eigener Kalender kann per Einladung
     mit anderen Personen geteilt werden, mit granularer Freigabe, welche Bereiche
     sichtbar sind (z. B. "Mein Job", "Termine", "Notizen" einzeln togglebar).
     - **Muss serverunabhängig funktionieren**: Sync/Teilen läuft direkt von
       App-Nutzer zu App-Nutzer (Peer-to-Peer), nicht über einen zentralen
       Smarter-Wecker-eigenen Server.
     - Falls reines P2P (Erreichbarkeit/NAT, Einladungs-Handshake, Offline-Geräte)
       technisch nicht zuverlässig genug ist, wird als schlanke Hilfskonstruktion eine
       Hilfsfunktion/Endpoint im **Lethe-Backend** (`/opt/lethe/backend`) ergänzt — rein
       als Vermittlungsstelle (z. B. Signaling für den P2P-Verbindungsaufbau oder
       verschlüsseltes Store-and-Forward für Einladung/Deltas, falls Empfänger offline
       ist), nicht als zentrale Datenhaltung für den gesamten Kalender.
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

9. **UI/UX-Orientierung "Wecker" (Alarm-Bereich):**
   - Grundlegendes Look & Feel orientiert sich bewusst an der **Google-Uhr-App**
     (siehe Referenzbilder), damit sich Nutzer sofort zurechtfinden:
     - **Alarmliste**: eine Karte pro Alarm mit großer Uhrzeit, Wiederholungsmuster als
       Untertitel (z. B. "Jeden Tag"), Ein/Aus-Toggle rechts, farbiger Karten-Akzent,
       FAB ("+") zum Anlegen neuer Alarme.
     - **Uhrzeit-Eingabe** per Dialog mit antippbarem/ziehbarem 24h-Zifferblatt
       (analoges Rad), alternativ direkte Zahleneingabe über Tastatur-Umschalter.
     - **Weckton-Auswahl** als eigener Screen mit Tabs: "Töne" (lokale/aufgezeichnete
       Töne) sowie je ein Tab pro Streaming-Quelle — bei uns **Spotify**, **Deezer**
       und **Lethe-Bibliothek** (anstelle von YouTube Music) zum Durchsuchen/Auswählen
       eines Songs als Weckton.
   - Die eigenen Zusatzfunktionen (Health-Connect-Überwachung, Notfall-Alarm,
     Schichtrotation-Verknüpfung je Alarm usw.) werden in dieses vertraute Grundgerüst
     integriert, nicht als komplett eigenes UI-Paradigma entworfen.

10. **Berechtigungen-Onboarding beim App-Start:**
    - Beim ersten Start fragt die App alle benötigten Laufzeit-Berechtigungen aktiv an
      (nicht erst beim ersten Bedarf), jeweils mit einer kurzen Erklärung, wofür sie
      gebraucht wird, z. B.:
      - Exakte Alarme (`SCHEDULE_EXACT_ALARM`) — damit der Wecker zuverlässig zur
        richtigen Zeit klingelt.
      - Benachrichtigungen (`POST_NOTIFICATIONS`) — für Alarm-/Notfall-Anzeige.
      - Kamera — für den Foto-Import von Stundenplan/Schichtplan (Kernfunktion 5).
      - Körpersensoren/Aktivitätserkennung (`BODY_SENSORS`/`ACTIVITY_RECOGNITION`)
        sowie Health-Connect-Zugriff — für die Aufsteh-Erkennung (Kernfunktion 1).
      - Ignorieren von Akku-Optimierungen — damit die Überwachung nach dem Klingeln
        nicht durch Doze/Battery-Optimizations unterbrochen wird.
    - (Noch) nicht erteilte Berechtigungen werden weiterhin mit Hinweis auf die
      eingeschränkte Funktion angezeigt (ähnlich dem bereits bestehenden Banner für
      fehlende Exact-Alarm-Berechtigung), nicht nur einmalig beim Start abgefragt.

11. **System-Registrierung als Wecker-/Uhr-/Kalender-App:**
    - Die App meldet sich über passende Intent-Filter/Kategorien im Manifest bei
      Android als Wecker-, Uhr- und Kalender-App an, damit das System sie entsprechend
      erkennt und einbindet, u. a.:
      - `android.intent.action.SET_ALARM` / `SHOW_ALARMS` / `DISMISS_ALARM` /
        `SNOOZE_ALARM` (Wecker-Intents, z. B. für Sprachassistenten wie
        "stelle einen Wecker").
      - `android.intent.category.APP_CALENDAR` und `android.intent.category.APP_CLOCK`,
        damit die App in den Android-Einstellungen unter "Standard-Apps" als Kalender-
        bzw. Uhr-App auswählbar ist.

## Architektur

- Zielplattform: Android (nativ), Kotlin + Jetpack Compose + Hilt (Dependency
  Injection, Apache-2.0/Open Source, kostenlos) ab Phase 1.
- **Package-Name: `de.letheapp.nocturn`** (Reverse-Domain von letheapp.de,
  App-Name "NocTurn").
- **minSdk = targetSdk = API 33 (Android 13)**. Play-Store-Veröffentlichung ist
  explizit kein Ziel, daher keine Rücksicht auf Play-Store-Vorgaben (z. B.
  aktueller targetSdk) nötig.
- **FOSS-Kompatibilität als Leitlinie**: Bei Abhängigkeiten werden offene/freie
  Lizenzen bevorzugt, proprietäre/closed-source Komponenten möglichst vermieden.
  Spotify-/Deezer-SDKs (closed-source, Streaming-Anbieter) bleiben rein optionale
  Zusatz-Tonquellen, keine Kern-Abhängigkeit der App.
- Health Connect: offizielle Android Health Connect API.
- Bewegungserkennung ohne Health Connect: `SensorManager` mit `TYPE_ACCELEROMETER` /
  `TYPE_GYROSCOPE`, ggf. Schrittzähler-Sensor `TYPE_STEP_COUNTER`/`TYPE_STEP_DETECTOR`.
- Alarm-Zuverlässigkeit: `AlarmManager` (exact alarms) + Foreground Service während der
  Überwachungsphase (Sensorik darf nicht durch Doze/Battery-Optimizations gekillt werden).
- Kalendersync: Google Calendar API (OAuth) + CalDAV-Client für Nextcloud.
- Kalender-Teilen (P2P): primär direkte App-zu-App-Synchronisation ohne eigenen
  Smarter-Wecker-Server (z. B. lokal/WLAN oder über vorhandene P2P-Mechanismen).
  Fallback/Hilfsfunktion bei Bedarf als schlanker Endpoint im Lethe-Backend
  (`/opt/lethe/backend`) — nur als Vermittlung/Relay, keine zentrale Kalenderdatenhaltung.
- Wecker-UI: Alarmliste, Zifferblatt-Zeitpicker und Weckton-Auswahl-Screen (Tabs)
  orientieren sich an der Google-Uhr-App, siehe Kernfunktion 9.
- Wetterdaten: OpenWeatherMap API (One Call / Current Weather), API-Key-Handling analog
  Iosync-Projekt (`/opt/Iosync`): Key aus `local.properties` lokal, aus GitHub-Secret
  `OPENWEATHER_API_KEY` im CI, Einbindung als `BuildConfig`-Feld — niemals im Repo.
- Foto-Import/OCR (Kernfunktion 5): Texterkennung on-device über **Tesseract4Android**
  (Apache-2.0), final gewählt gemäß FOSS-Leitlinie gegenüber Googles closed-source ML Kit.
  Wird nicht auf Maven Central, sondern nur über **JitPack** veröffentlicht (Repository in
  `settings.gradle.kts` ergänzt). Die deutschen Trainingsdaten werden nicht ins Repo gelegt,
  sondern beim ersten Foto-Import einmalig von der offiziellen `tesseract-ocr/tessdata`-
  Quelle in den App-internen Speicher nachgeladen.
- Berechtigungs-Onboarding (Kernfunktion 10): eigener Onboarding-Screen beim ersten
  Start, der alle benötigten Berechtigungen inkl. Begründung anfragt.
- System-Registrierung (Kernfunktion 11): Manifest-Intent-Filter für
  `SET_ALARM`/`SHOW_ALARMS`/`DISMISS_ALARM`/`SNOOZE_ALARM` sowie die Kategorien
  `APP_CALENDAR`/`APP_CLOCK`.

## Wichtig

- Projekt-Memory liegt in `/opt/SmarterWecker/memory.md` — vor Arbeit an diesem Projekt
  immer zuerst dorthin wechseln und diese Datei lesen.
- Ausgewählt im Chat-Dropdown unter `/var/www` als "Smarter Wecker".
- Phase 1 (siehe PLAN.md) ist umgesetzt (Code vorhanden); neue Konzept-Erweiterungen
  werden weiterhin zuerst hier dokumentiert, bevor sie implementiert werden.
