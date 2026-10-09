# Smarter Wecker – Projekt-Memory

Projekt wurde am 2026-10-08 angelegt (Konzeptphase, noch kein Code).

## Anforderungen laut Nutzer (2026-10-08, Erstdefinition)

- Health-Connect-Daten + Gyro/ACC-Sensorik prüfen, ob Nutzer aufgestanden ist und
  Schritte zurückgelegt hat — Wegdrücken des Alarms allein reicht nicht.
- Snooze: zufälliges Intervall 3-5 Minuten bis zum erneuten Klingeln, Ton ändert sich
  während des Klingelns.
- Stop (statt Snooze) führt NICHT zum Ende der Überwachung — wird weiterhin bis zu
  10 Minuten überwacht, ob aufgestanden/gelaufen wurde.
- Nach Ablauf ohne Aufstehen: Notfall-Alarm, Ton eskaliert/ändert sich fortlaufend,
  nur stoppbar durch Lösen einer schnellen Farbkombinations-Aufgabe.
- Schichtplan-Rotation: benannte Schichten mit frei wählbarer Farbe, Rotation hinterlegt.
- Eingebauter Kalender, synchronisierbar mit Google Calendar und Nextcloud.

## Ergänzung (2026-10-08)

- Home-Screen-Widgets: Uhr-Widget und Wetter-Widget.
- Ton-Auswahl für den Wecker soll neben lokalen Tönen auch Spotify, Deezer und die
  Lethe-Bibliothek als Quelle anbieten.

## Ergänzung (2026-10-08, Weather/Uhr-Widget)

- Wetter-Widget-Datenquelle: OpenWeatherMap. API-Key wird genauso gehandhabt wie im
  Iosync-Projekt (`/opt/Iosync`) — lokal über `local.properties`
  (`OPENWEATHER_API_KEY=...`), im CI über GitHub Secret `OPENWEATHER_API_KEY`, Zugriff
  im Code über generiertes `BuildConfig`-Feld. Key nie ins Repo committen.
- Uhr-Widget-Layout vom Nutzer per Referenzbild vorgegeben: transparent über Wallpaper,
  gestapelt — Datum klein oben, Uhrzeit riesig in der Mitte, darunter klein mit
  Wecker-Icon der nächste gestellte Alarm (Tag + Uhrzeit).

## Ergänzung (2026-10-08, Kalender-Funktionsumfang)

- Nutzer hat 6 Screenshots einer bestehenden Schichtplan-App als Vorbild geschickt.
  Funktionsumfang soll übernommen werden, aber **übersichtlicher** umgesetzt werden.
- Vorlagen-Konzept mit drei Tabs: Schichten (Name/Farbe/Zeit/Dauer), Jobs (mehrere Jobs
  mit eigenen Schichten möglich), Rotationen (Tag-für-Tag-Muster per Raster-Eingabe,
  z. B. 84-Tage-Zyklus).
- Kernidee der Rotation: Nutzer trägt das Muster nur so lange ein, bis es sich
  wiederholen würde — die App trägt es danach automatisch fortlaufend für mehrere Jahre
  im Voraus in den Kalender ein, damit der Wecker durchgehend weiß, welche Schicht/
  Weckzeit ansteht.
- Vorbild bietet zusätzlich Kalender-Teilen mit anderen Personen (granular je Bereich:
  Mein Job/Termine/Notizen) — als Idee vermerkt, aber noch nicht explizit vom Nutzer als
  Pflichtfeature bestätigt, daher nicht in CLAUDE.md als Kernfunktion aufgenommen,
  sondern nur beiläufig unter Kalender erwähnt.

## Ergänzung (2026-10-08, Kalender teilen + Wecker-UI-Vorbild)

- Nutzer hat bestätigt: **Kalender teilen ist Pflichtfeature** (vorher nur als Idee
  vermerkt) und muss **serverunabhängig** sein — Sync direkt App-Nutzer zu App-Nutzer
  (P2P), nicht über eigenen Smarter-Wecker-Server.
- Falls P2P allein nicht zuverlässig genug ist, soll als Hilfskonstruktion eine
  Hilfsfunktion im **Lethe-Backend** ergänzt werden (Repo liegt unter
  `/opt/lethe/backend`) — nur als Vermittlungs-/Relay-Stelle (z. B. Signaling oder
  Store-and-Forward für Einladungen/Deltas), nicht als zentrale Kalender-Datenbank.
- Nutzer hat 3 Screenshots der **Google-Uhr-App** geschickt (Alarmliste mit Karten/
  Toggle/FAB, Weckton-Auswahl-Screen mit Tabs "Töne"/YouTube Music/Spotify, Uhrzeit-
  Auswahldialog mit antippbarem 24h-Zifferblatt). Die Wecker-Funktion der App soll sich
  an diesem Look & Feel orientieren — bei der Weckton-Auswahl ersetzen wir die
  Streaming-Tabs durch Spotify/Deezer/Lethe-Bibliothek statt YouTube Music.

## Ergänzung (2026-10-08, Notfall-Alarm-Puzzle variiert)

- Die Zuordnungs-Aufgabe zum Stoppen des Notfall-Alarms soll zwischen Weckungen
  variieren, nicht immer identisch sein (sonst prägt man sich die Lösung ein).
- Beispielmechanik vom Nutzer: oben eine vorgegebene Reihe farbiger Geometrien
  (z. B. 6 Elemente: blauer Kreis, gelbes Dreieck, pinkes Quadrat ...), darunter ein
  größerer Pool (z. B. 10 Geometrien inkl. Ablenker), aus dem die passenden Elemente
  per Drag & Drop an die richtige Position in der zweiten Reihe gezogen werden müssen.
- Variiert wird: Farbe/Form der Elemente, Zusammensetzung des Pools, Elementtyp
  (Geometrien vs. z. B. 5 Smileys in vorgegebener Reihenfolge) und Anzahl der zu
  sortierenden Elemente — jeweils bei jedem Auftreten neu.
- In CLAUDE.md unter Kernfunktion 4 "Notfall-Alarm" als eigener Unterpunkt
  "Variierende Aufgabenstellung" ergänzt.

## Ergänzung (2026-10-08, Phase-1-Plan / MVP-Scope)

- Nutzer möchte einen ersten Plan für die Grundfunktionen **ohne** Spotify/Deezer/
  Lethe-Bibliothek, aber **mit** lokal wählbaren Audiodateien als Weckton, sowie die
  grundlegende Kalenderfunktion inkl. Rotation.
- Daraus `PLAN.md` erstellt: Phase 1 umfasst Alarm-Grundfunktionen (Aufsteh-
  Erkennung, Snooze, Stop-Weiterüberwachung, Notfall-Alarm-Puzzle), lokale
  Weckton-Auswahl (System-Ringtones + eigene Audiodateien) und Kalender-
  Grundfunktion (Schichten/Jobs/Rotationen, automatisches Fortschreiben, Monats-
  ansicht, Alarm-Schicht-Verknüpfung).
- Explizit **nicht** in Phase 1: Spotify/Deezer/Lethe als Ton-Quelle, Kalender-
  Teilen (P2P/Lethe-Backend), Google-Calendar-/Nextcloud-Sync, Homescreen-Widgets.
- Offene Entscheidungen vor Codestart laut Plan: Package-Name, Min-/Target-SDK, ob
  Hilt schon in Phase 1 eingeführt wird.

## Ergänzung (2026-10-08, Architektur-Entscheidungen)

- Nutzer hat entschieden: **minSdk = targetSdk = API 33 (Android 13)**. Play-Store-
  Veröffentlichung interessiert ihn nicht, daher keine Rücksicht auf Play-Store-
  Vorgaben nötig.
- **Hilt** wird ab Phase 1 verwendet, nachdem bestätigt wurde, dass es kostenlos und
  Open Source (Apache-2.0, Teil von Dagger/Google, Code offen auf GitHub) ist.
- Neue generelle Leitlinie: Die App soll **FOSS-kompatibel** werden — bei
  Abhängigkeiten offene/freie Lizenzen bevorzugen, proprietäre/closed-source
  Komponenten vermeiden wo möglich. Spotify-/Deezer-SDKs (closed-source) bleiben
  daher explizit optionale Zusatz-Tonquellen in späteren Phasen, keine
  Kern-Abhängigkeit.
- Einträge dazu in CLAUDE.md (Architektur-Abschnitt) und PLAN.md (Schritt 1 /
  "Entscheidungen"-Abschnitt, ersetzt die vorherigen "offenen Entscheidungen")
  aktualisiert.
- **Package-Name final festgelegt: `de.letheapp.nocturn`** (Reverse-Domain von
  letheapp.de, nicht thestealth-Domain — letheapp.de ist die Domain, auf die sich
  der Nutzer bezieht, z. B. im Zusammenhang mit dem Lethe-Projekt).

Damit sind alle drei offenen Entscheidungen (Package-Name, Min-/Target-SDK, Hilt)
geklärt. Nächster Schritt wäre das Gradle-Projekt-Grundgerüst aufzusetzen.

## Ergänzung (2026-10-08, Phase 1 – Projekt-Grundgerüst umgesetzt)

- Nutzer: "beginne den Plan mit Phase 1" → Gradle-Projekt-Grundgerüst (PLAN.md
  Schritt 1) sowie Room-Datenmodell-Grundgerüst (Schritt 2) implementiert.
- Stack konkretisiert: Gradle 8.11.1 (Wrapper), AGP 8.9.1, Kotlin 2.0.21 (neuer
  Compose-Compiler-Plugin-Ansatz, keine separate `composeCompilerExtensionVersion`
  mehr nötig), KSP für Room- und Hilt-Compiler (statt kapt), Hilt 2.52, Room 2.6.1,
  Compose BOM 2024.09.03.
- **SDK-Anhebung 2026-10-09**: compileSdk + targetSdk von 34/33 auf **36 (Android 16)**
  angehoben (Gerät des Nutzers ist Android 17), minSdk bleibt **33 (Android 13, Pflicht-
  Abwärtskompatibilität)**. Dafür Gradle 8.9→8.11.1 und AGP 8.5.2→8.9.1 (Minimum für
  compileSdk 36). Bewusst NICHT auf API 37/Android 17 gegangen: das bräuchte AGP 9 +
  Gradle 9 + Kotlin 2.2 + KSP2 (+ vermutlich Hilt/Room-Bumps) — großer Toolchain-Sprung,
  hohes CI-Bruchrisiko, funktional für Health Connect identisch. Manifest war bereits
  vorbereitet (FGS-Types + FOREGROUND_SERVICE_*-Permissions, FLAG_IMMUTABLE-PendingIntents,
  ContextCompat.registerReceiver mit RECEIVER_NOT_EXPORTED), daher keine targetSdk-34+-
  Crash-Quellen offen. Edge-to-edge (targetSdk 35+) wird von Material3-Scaffold-Insets
  abgedeckt.
- App startet mit Platzhalter-Alarmliste-Screen (Scaffold, FAB, leere Liste) als
  Basis für PLAN.md Schritt 3.
- Room-Entities `AlarmEntity`/`JobEntity`/`ShiftEntity`/`RotationEntity`/
  `RotationDayEntity` + DAOs + `NocturnDatabase`, per Hilt-`DatabaseModule`
  bereitgestellt (`de.letheapp.nocturn.data.db`, `de.letheapp.nocturn.di`).
- Lokal steht kein Android SDK zur Verfügung — Build wurde nicht lokal mit
  `./gradlew assembleDebug` verifiziert, sondern nur die Gradle-Konfiguration
  (Wrapper-Generierung) geprüft. Verifikation erfolgt über die bereits vorhandene
  GitHub Action `build-apk.yml` (GitHub-gehostete Ubuntu-Runner bringen Android SDK
  bereits mit).

## Ergänzung (2026-10-08, Phase 1 – Schritte 3 bis 8 umgesetzt)

- Nutzer: "Da mache bis 8 weiter" → PLAN.md-Schritte 3–8 implementiert (Alarmliste/
  Zeit-Eingabe/AlarmManager, Weckton-Auswahl "Töne", Ringing-Service inkl. Snooze,
  Sensor-/Health-Connect-Bewegungserkennung, Stop-Weiterüberwachung mit
  Notfall-Trigger, Notfall-Puzzle mit Drag & Drop). Details stehen in PLAN.md unter
  "Umsetzungsstand". Weiterhin kein Android SDK lokal verfügbar — nicht mit
  `./gradlew assembleDebug` verifiziert, nur sorgfältig manuell durchgegangen
  (Importe, Signaturen, Manifest-Komponenten). Echte Verifikation läuft über die
  GitHub Action `build-apk.yml` beim nächsten Push.
- Architektur-Entscheidungen dabei getroffen (nicht vorher vom Nutzer vorgegeben,
  aber im Rahmen von "Grundfunktionen umsetzen" naheliegend und dokumentiert):
  - Exakte Alarme über `AlarmManager.setAlarmClock`, Fallback `setAndAllowWhileIdle`
    falls `SCHEDULE_EXACT_ALARM` nicht erteilt; eigenes Banner in der Alarmliste
    verlinkt zu den Systemeinstellungen, falls die Berechtigung fehlt.
  - Health-Connect-Integration bewusst minimal/best-effort (keine eigene
    Rationale-/Onboarding-UI für die Berechtigungsanfrage) — reine
    Sensor-Fallback-Erkennung (Schrittzähler/Beschleunigungssensor) ist in Phase 1
    die verlässliche Hauptquelle, Health Connect ergänzt nur, wenn bereits
    verfügbar und erlaubt.
  - Notfall-Alarm-Start aus dem Hintergrund doppelt abgesichert: direkter
    `startActivity`-Aufruf plus Fullscreen-Intent-Notification als Fallback, falls
    Android den direkten Start blockiert (Hintergrund-Start-Einschränkungen ab
    Android 10).
  - Kein eigenständiger Package-Name-/Minisdk-Wechsel nötig gewesen, alles auf
    Basis der bereits getroffenen Entscheidungen (`de.letheapp.nocturn`, API 33,
    Hilt) aufgebaut.
- Noch offen aus Phase 1: Schritte 9–12 (Kalender-Vorlagen/Rotationen/
  Monatsansicht/Alarm↔Schicht-Verknüpfung).

## Ergänzung (2026-10-08, Phase 1 – Schritte 9 und 10 umgesetzt)

- Nutzer: "Erledige 9+10" → PLAN.md-Schritte 9 (Kalender-Vorlagenverwaltung) und 10
  (Rotationen + automatisches Fortschreiben) implementiert.
- Neues Package `de.letheapp.nocturn.calendar`: `JobListScreen`/`JobListViewModel`
  (Jobs-CRUD, Einstieg über neues Kalender-Icon oben rechts in der Alarmliste),
  `JobDetailScreen`/`JobDetailViewModel` mit Tabs "Schichten"/"Rotationen",
  `ShiftEditDialog` (Name, Farbpalette, Start-/Endzeit → Dauer inkl.
  Nachtschicht-Unterstützung über Mitternacht), `RotationEditorScreen`/
  `RotationEditorViewModel` (Tag-für-Tag-Raster über `LazyVerticalGrid` mit 7
  Wochentags-Spalten, Start-Datum per Material3-`DatePicker`).
- Kernidee der Rotation wie vom Nutzer beschrieben umgesetzt: Zykluslänge ist
  schlicht die Anzahl der vom Nutzer eingetragenen Tage (kein automatisches
  Zyklus-Erkennen nötig) — `CalendarRepository.saveRotation` schreibt daraus beim
  Speichern sofort `CalendarEntryEntity`-Zeilen für 5 Jahre im Voraus fort (Modulo
  über die Zykluslänge), damit der Wecker jederzeit die geltende Schicht
  nachschlagen kann.
- Neue Room-Entity `CalendarEntryEntity` (Datum/Job/Rotation/Schicht), DB-Version
  von 1 auf 2 angehoben. Da es noch keine veröffentlichte Version mit
  Bestandsdaten gibt, wurde `Room.databaseBuilder(...).fallbackToDestructiveMigration()`
  ergänzt statt einer echten Migration — spart Aufwand, ist aber nur solange
  vertretbar, wie es keine Nutzerdaten gibt, die beim nächsten Schema-Wechsel
  verloren gehen dürften.
- Fremdschlüssel-Kaskaden bewusst gesetzt: `jobId`-FKs (Shifts/Rotationen) und
  `rotationId`-FKs (RotationDays/CalendarEntries) lösen `CASCADE` aus (Löschen
  eines Jobs/einer Rotation räumt automatisch auf), `shiftId`-FKs lösen `SET_NULL`
  aus (Löschen einer Schicht setzt betroffene Tage auf "frei", statt die Rotation
  zu zerstören).
- Schicht-Farbwahl: kuratierte 10-Farben-Palette statt echtem Farbrad, um keine
  zusätzliche Color-Picker-Library einzuführen (als bewusste Vereinfachung in
  PLAN.md vermerkt).
- Weiterhin kein Android-SDK lokal verfügbar — nicht mit `./gradlew assembleDebug`
  verifiziert, nur sorgfältig manuell durchgegangen (Imports, Navigation-Routen,
  Compose-API-Signaturen inkl. `@OptIn(ExperimentalMaterial3Api::class)` für
  `DatePicker`). Echte Verifikation läuft über die GitHub Action `build-apk.yml`
  beim nächsten Push.
- Noch offen aus Phase 1: Schritte 11–12 (Kalender-Monatsansicht,
  Alarm↔Schicht-Verknüpfung).

## Ergänzung (2026-10-08, Phase 1 – Schritte 11 und 12 umgesetzt)

- Nutzer: "11 und 12 jetzt" → PLAN.md-Schritte 11 (Kalender-Monatsansicht) und 12
  (Alarm↔Schicht-Verknüpfung) implementiert. Damit war laut PLAN.md der komplette
  ursprüngliche Phase-1-Scope (Schritte 1–12) umgesetzt.

## Ergänzung (2026-10-08, neue Anforderungen nach Phase-1-Abschluss)

Nutzer hat nach Abschluss der ursprünglichen 12 Schritte weitere Anforderungen
genannt, die als "noch nicht umgesetzt" in PLAN.md und als neue Kernfunktionen
10/11 bzw. Erweiterung von Kernfunktion 5 in CLAUDE.md dokumentiert wurden:

- **Berechtigungen beim Start**: alle benötigten Laufzeit-Berechtigungen sollen beim
  ersten App-Start aktiv angefragt werden, jeweils mit Erklärung wofür sie gebraucht
  werden (bisher nur ad-hoc, z. B. das bestehende Exact-Alarm-Banner).
- **System-Registrierung**: App soll sich bei Android explizit als Wecker-, Uhr- und
  Kalender-App anmelden (Intent-Filter `SET_ALARM`/`SHOW_ALARMS`/`DISMISS_ALARM`/
  `SNOOZE_ALARM`, Kategorien `APP_CALENDAR`/`APP_CLOCK` für die "Standard-Apps"-Auswahl
  in den Android-Einstellungen), damit das System sie entsprechend erkennt.
- **Schüler/Student-Modus**: Schicht-Rotation soll umschaltbar sein auf einen
  Wochenstundenplan (Schule/Uni) mit bis zu **12 Unterrichtsstunden pro Tag** (statt
  einer Schicht pro Tag bei normaler Schichtarbeit), jede Stunde mit eigener
  Start-/Endzeit und Farbe. Stundenplan wiederholt sich wöchentlich (kein
  mehrtägiger Zyklus wie bei Schichtrotation).
- **Foto-Import**: Stundenplan/Schichtplan soll abfotografiert werden können; per OCR
  sollen automatisch Schichten/Unterrichtsstunden (Zeiten, Farbe) erkannt und daraus
  automatisch eine Rotation/ein Wochenstundenplan erzeugt werden. Als FOSS-Kandidat für
  die Texterkennung wurde **Tesseract4Android** (Apache-2.0) vermerkt (Alternative zu
  Googles closed-source ML Kit), finale Wahl noch offen.
- **Konfigurierbarer Weckzeit-Abstand**: statt exakt zur Schicht-/Unterrichtsbeginn-Zeit
  zu wecken, sollen pro Schicht bzw. vor der ersten Unterrichtsstunde zwei
  Minuten-Werte hinterlegt werden können — "frühestes Wecken" (Normal-Alarmzeit) und
  "spätestens aufstehen" (späteste Zeit, bis zu der laut Aufsteh-Erkennung tatsächlich
  aufgestanden sein muss, sonst greift die bestehende Stop-Weiterüberwachungs-/
  Notfall-Logik). Das bedeutet eine Anpassung der in Schritt 12 gebauten
  Alarm↔Schicht-Verknüpfung (`AlarmScheduler` leitet aktuell die Weckzeit noch 1:1 aus
  dem Schicht-Start ab, nicht aus einem Vorlauf-Offset).
- Diese fünf Punkte sind bewusst nur in CLAUDE.md/PLAN.md **dokumentiert**, aber noch
  **nicht implementiert** (analog zum bisherigen Muster: Feature-Beschreibung zuerst
  festhalten, Umsetzung erst auf expliziten Folgeauftrag wie "jetzt implementieren"
  o. Ä.).
- Nebenbei korrigiert: Der "Wichtig"-Abschnitt in CLAUDE.md behauptete noch "noch kein
  Code vorhanden" — das war seit dem Phase-1-Grundgerüst (Schritt 1) nicht mehr aktuell
  und wurde auf den tatsächlichen Stand (Phase 1 umgesetzt) korrigiert.

## Ergänzung (2026-10-08, die fünf nachträglichen Anforderungen umgesetzt)

- Nutzer: "Setze es um" → alle fünf in der vorigen Runde nur dokumentierten Erweiterungen
  implementiert (Details siehe PLAN.md, Abschnitt "Nachträglich ergänzte Anforderungen"):
  Berechtigungen-Onboarding, System-Registrierung, Schüler/Student-Modus (Wochenstundenplan),
  Foto-Import/OCR, konfigurierbarer Weckzeit-Abstand.
- **Technische Leitentscheidung OCR**: Tesseract4Android (Apache-2.0, FOSS) als OCR-Lib
  final gewählt (vorher in CLAUDE.md noch als "TBD" vermerkt). Wichtig für die Zukunft:
  die Lib wird **nicht** auf Maven Central veröffentlicht, nur über **JitPack**
  (`https://jitpack.io`, Koordinate `com.github.adaptech-cz.Tesseract4Android:tesseract4android`,
  Version 4.8.0) — das wurde durch direktes Abfragen der JitPack-API/Maven-Metadaten
  verifiziert, bevor es ins Projekt übernommen wurde. `settings.gradle.kts` enthält jetzt
  zusätzlich das JitPack-Repository.
- Sprachdaten für Tesseract (`deu.traineddata`, ~15 MB) werden bewusst **nicht** ins Repo
  gelegt, sondern von `TesseractOcrEngine` beim ersten Foto-Import einmalig von der
  offiziellen `tesseract-ocr/tessdata`-GitHub-Quelle in den App-internen Speicher
  nachgeladen (dafür neue `INTERNET`-Permission im Manifest).
- **Wichtige technische Erkenntnis zur lokalen Build-Verifikation**: Diesmal stand
  tatsächlich Internetzugriff im Sandbox-Terminal zur Verfügung (anders als in früheren
  Runden vermutet/dokumentiert) — ein lokales Android-SDK (Kommandozeilen-Tools +
  Platform 34) ließ sich installieren. `./gradlew :app:compileDebugKotlin` lief damit
  erfolgreich durch (inkl. KSP-Lauf für Room/Hilt, d. h. der komplette Dagger/Hilt-Graph
  wurde validiert). Einschränkung bleibt: ein vollständiger `assembleDebug`-Lauf ist auf
  dieser Maschine weiterhin nicht möglich, weil Googles `aapt2`-Binary (Ressourcen-Compiler)
  **keine Linux-ARM64-Variante** besitzt (nur x86_64/macOS/Windows) — die Maschine hier
  läuft auf `aarch64` (Raspberry Pi). Für künftige Sessions: Kotlin-Compile-Verifikation via
  `compileDebugKotlin` (mit einem leeren Platzhalter-`R.jar` und `-x processDebugResources
  -x mergeDebugResources -x packageDebugResources -x parseDebugLocalResources
  -x createDebugCompatibleScreenManifests -x extractDeepLinksDebug`, falls `R` von keiner
  Quelldatei referenziert wird) ist auf diesem Pi möglich und sinnvoll, ein echter
  `assembleDebug`/`aapt2`-Lauf bleibt der GitHub Action vorbehalten.
- Neue Datenmodell-Felder: `JobEntity.mode` (Enum `JobMode.SHIFT`/`STUDENT`),
  `ShiftEntity`/neue `LessonEntity` haben je `earliestWakeOffsetMinutes`/
  `latestWakeupOffsetMinutes`, `AlarmEntity.timetableJobId` (Alternative zu `rotationId`
  für Student-Modus-Jobs). DB-Version auf 3 angehoben (weiterhin
  `fallbackToDestructiveMigration()`, da noch keine Nutzerdaten im Feld).
- Neue Klasse `WakeWindowResolver` (Package `alarm`) bündelt die gemeinsame Logik von
  `AlarmScheduler` (nächster Weck-Zeitpunkt) und `PostAlarmMonitoringService`
  (Aufsteh-Deadline nach "Stop") für Rotation- **und** Stundenplan-Verknüpfungen.
- **Bewusste Vereinfachung Foto-Import**: OCR liefert nur Fließtext ohne Tabellenstruktur;
  im Student-Modus muss der Nutzer jeder erkannten Zeile im Korrektur-Dialog selbst einen
  Wochentag zuweisen, das wird nicht automatisch aus dem Foto-Layout erkannt.
- **Bekannte Lücke**: `CalendarMonthScreen` zeigt bislang nur Rotations-Einträge, noch
  keine Student-Modus-Wochenstundenpläne.

## Ergänzung (2026-10-08, Nextcloud-CalDAV-Synchronisierung umgesetzt)

- Nutzer: "Baue die nextcloud synchronisierung hinzu" → CLAUDE.md hatte das bereits als
  Architektur-Punkt vermerkt ("Synchronisierbar mit ... Nextcloud (CalDAV), ... TBD ob Lesen
  reicht"), jetzt implementiert. Richtung dabei selbst festgelegt (nicht vom Nutzer
  spezifiziert): freie Termine bidirektional, Schichten aus Rotationen nur einseitig
  NocTurn → Nextcloud (Begründung siehe PLAN.md). Google-Calendar-Sync bleibt weiterhin
  offen/zukünftig, war nicht Teil dieser Anfrage.
- Bewusst **keine neuen Zusatz-Libraries für CalDAV/ICS** eingeführt (FOSS-Minimalismus):
  eigener `CalDavClient` auf Basis von OkHttp (WebDAV PROPFIND/REPORT/PUT/DELETE,
  generischer XML-Baum über Androids `XmlPullParser`) statt dav4jvm, eigener `IcsFormat`
  (RFC-5545-Subset) statt biweekly/ical4j, eigener AES/Keystore-`NextcloudCredentialsStore`
  statt `androidx.security-crypto`. Einzige neue Abhängigkeiten: OkHttp, `androidx.work` +
  `androidx.hilt:hilt-work` (für periodischen Hintergrund-Sync) — alle Apache-2.0.
  `NocturnApplication` implementiert jetzt `Configuration.Provider` (Hilt-WorkManager).
- Neuer Screen "Nextcloud-Sync", erreichbar über das bestehende 3-Punkte-Menü in der
  Alarmliste (`AlarmListScreen`) statt über ein weiteres Icon in der ohnehin schon vollen
  TopAppBar.
- **Lokale Build-Verifikation ist diesmal noch weiter eingeschränkt als zuvor
  dokumentiert**: Anders als in einer früheren Session (wo `compileDebugKotlin` trotz
  kaputtem `aapt2` erfolgreich lief) schlägt dieses Mal bereits `compileDebugKotlin` fehl,
  weil es `processDebugResources` voraussetzt. Ursache geprüft: `aapt2` (sowohl der
  Gradle-Transform-Cache als auch die SDK-Build-Tools-Kopien) ist ein dynamisch gelinktes
  x86_64-PIE-Binary; obwohl ein `qemu-x86_64`-Binfmt-Handler registriert ist, scheitert die
  automatische Ausführung mit "Exec format error" (Magic/Mask des Kernel-Eintrags passt
  nicht exakt auf dieses Binary-Layout), und die separat vorhandene
  `qemu-x86_64-static`-Binary hat keinen x86_64-Sysroot (`/lib64/ld-linux-x86-64.so.2`
  fehlt), kann also auch nicht händisch als Wrapper verwendet werden. Das Flicken dieser
  Pi-seitigen QEMU/Binfmt-Konfiguration wurde bewusst nicht versucht (System-Eingriff
  außerhalb des eigentlichen Auftrags) — stattdessen sorgfältige manuelle Code-Durchsicht
  (inkl. Verifikation von `Icons.Filled.CloudSync` direkt im `material-icons-extended`-Jar
  im Gradle-Cache) und Verifikation wie gewohnt über die GitHub Action `build-apk.yml`.

## Ergänzung (2026-10-08, Uhr- und Kalender-Widget umgesetzt + neue Arbeitsregel)

- Nutzer: "Baue das [Uhr] Widget ein sowie ein Widget für Termine Kalender" →
  `ClockWidgetProvider` und `CalendarWidgetProvider` (neues `widget`-Package) implementiert.
  Details siehe PLAN.md, Abschnitt "Nachträglich ergänzte Anforderungen (Uhr- und
  Kalender-Widget)". Wetter-Widget bleibt weiterhin offen (nicht Teil dieser Anfrage).
- Technische Leitentscheidung: klassische `AppWidgetProvider`/`RemoteViews` statt Jetpack
  Glance (FOSS-Minimalismus, keine zusätzliche Abhängigkeit für zwei überwiegend statische
  Widgets nötig). "Nächster Alarm" im Uhr-Widget kommt direkt von
  `AlarmManager.getNextAlarmClock()` statt aus der DB.
- **Wichtige neue Arbeitsregel vom Nutzer**: Ab jetzt **nur noch commit/push ausführen,
  wenn der Nutzer das explizit sagt** — nicht mehr automatisch nach jeder Implementierung
  wie in allen bisherigen Runden dieser Session. In CLAUDE.md unter "Wichtig" als
  dauerhafte Regel festgehalten. Diese Widget-Änderung selbst ist daher bewusst **nicht**
  committet/gepusht, bis der Nutzer es anweist.
- Lokale Verifikation diesmal eingeschränkter als zuvor: der bisherige
  `compileDebugKotlin`-Workaround (aapt2-abhängige Tasks ausschließen) greift nicht mehr,
  weil der neue Widget-Code echte `R`-Referenzen (`R.layout...`, `R.id...`) enthält und
  damit ein gebautes `R.jar` braucht. `processDebugResources` selbst schlägt wie zuvor an
  `aapt2` (x86_64-only-Binary auf diesem aarch64-Pi) fehl. Nur `./gradlew
  :app:compileDebugKotlin` ohne Resource-Ausschlüsse wurde versucht (schlägt erwartungsgemäß
  an `processDebugResources`/`AarResourcesCompilerTransform` fehl) — echte Verifikation
  bleibt der GitHub Action vorbehalten, sobald gepusht wird.

## Ergänzung (2026-10-08, Hauptbildschirm-Icons gebündelt, gebündelte Wecktöne, Kalender-FAB + Ferien/Feiertage)

- Hauptbildschirm der Alarmliste zeigt jetzt nur noch das Kalender-Icon direkt in der
  TopAppBar, alle übrigen Einträge (Berechtigungen, Kalender-Vorlagen, Nextcloud-Sync,
  jetzt auch "Ferien & Feiertage") liegen im 3-Punkte-Menü.
- 5 vom Nutzer hochgeladene MP3s sind als gebündelte Wecktöne unter
  `app/src/main/res/raw/alarm_ton_1.mp3` … `alarm_ton_5.mp3` eingebunden
  (`SoundRepository.bundledSounds()`, eigene Sektion "Wecker-Töne" im Weckton-Auswahl-
  Screen). `alarm_ton_1` ist der neue Standard-Weckton (ersetzt den bisherigen
  `RingtoneManager`-Systemton als Default), auch als Fallback-Ton für den Notfall-Alarm.
- **Kalender-Monatsansicht hat jetzt einen FAB ("+")**: Tippen fragt "Termin" oder
  "Schicht" ab. Ein zuvor angetippter Tag (auch ohne vorhandene Einträge, jetzt dünn
  umrandet markiert) wird als Vorbelegung für das Datum im Folgeformular verwendet, ist
  dort aber änderbar (neuer `NocturnDatePickerDialog`, Material3 `DatePicker`-Wrapper).
  - "Termin" nutzt das bestehende `AddAppointmentScreen` (bisher nur für den externen
    "Zum Kalender hinzufügen"-Chooser-Flow da) — jetzt zusätzlich mit Datums-Zeile und
    einer "Wichtigkeit"-Auswahl (Niedrig/Normal/Hoch, neues `AppointmentEntity.priority`-
    Feld), für beide Einstiege gemeinsam verwendet.
  - "Schicht" ist ein neuer Screen `AddShiftEntryScreen`: Job → Schicht (beide per
    `ExposedDropdownMenuBox`) → Datum, gespeichert als neue, von Rotationen unabhängige
    `ManualShiftEntryEntity` (eigene Tabelle statt Wiederverwendung von
    `CalendarEntryEntity`, um dessen NOT-NULL-`rotationId`-Fremdschlüssel nicht anfassen
    zu müssen — bewusst nach demselben Muster wie `AppointmentEntity` als eigenständiger,
    manuell gepflegter Eintragstyp neben den automatisch fortgeschriebenen
    Rotations-Einträgen).
- **Neues `holidays`-Package**: "Ferien & Feiertage"-Einstellungsscreen (Bundesland-Wahl
  aus allen 16 deutschen Ländern + zwei Anzeige-Toggles für Feiertage/Ferien getrennt).
  Datenquelle ist die **OpenHolidaysAPI** (`openholidaysapi.org`) — kostenlos, ohne
  API-Key, ODbL-lizenziert, und bewusst statt z. B. feiertage-api.de gewählt, weil sie
  als einzige der recherchierten freien Quellen **sowohl gesetzliche Feiertage als auch
  Schulferien** pro Bundesland liefert (ein Datensatz für beide vom Nutzer gewünschten
  Kategorien). Ergebnisse werden pro Bundesland/Jahr einmalig lokal in einer neuen
  `holidays`-Tabelle gecacht (`HolidayRepository.ensureYearLoaded`, Abfrage nur bei
  Cache-Miss) — kein JSON-Lib-Zusatz, sondern Androids eingebautes `org.json` über das
  bereits vorhandene OkHttp, passend zur FOSS-Minimalismus-Leitlinie. Feiertage/Ferien
  erscheinen in der Monatsansicht als zusätzliche farbige Punkte (rot = Feiertag, grün =
  Ferien) neben Schichten/Terminen.
- DB-Schema-Version 5 → 6 (neue Tabellen `manual_shift_entries`, `holiday_settings`,
  `holidays`, neue Spalte `appointments.priority`) — dank weiterhin aktivem
  `fallbackToDestructiveMigration()` ohne manuelle Room-Migration möglich (siehe
  CLAUDE.md/`DatabaseModule.kt`: bewusst so, solange es noch keine veröffentlichte
  Version mit Bestandsdaten gibt).
- Lokale Verifikation weiterhin durch das aapt2-Problem blockiert (`compileDebugKotlin`
  scheitert jetzt schon an `processDebugResources`, nicht erst bei eigenen
  `R`-Referenzen) — Code besonders sorgfältig manuell durchgegangen (u. a. Material3-
  `ExposedDropdownMenuBox`/`menuAnchor()`-API und `DatePickerDialog`-Signatur vorab per
  Websuche gegen die im Projekt gepinnte Compose-BOM-Version 2024.09.03 verifiziert),
  echte Verifikation bleibt der GitHub Action vorbehalten.

## Ergänzung (2026-10-08, Release-Signierung, Ton-Vorschau, Health-Connect-Fix, Kalender-Erweiterungen)

Nutzer hatte in einer Anfrage mit 4 Screenshots sieben Einzelpunkte genannt; eine erste
Antwort darauf brach wegen eines abgelaufenen OAuth-Tokens ab, der Nutzer bat danach "Setze
meine vorherigen Aufgaben um" — alle sieben wurden umgesetzt, nicht committet/gepusht
(Standing-Regel), da nicht explizit angewiesen:

- **Release-Signierung**: Da auf diesem Pi kein `keytool`/JDK vorhanden war (zum
  Zeitpunkt der Keystore-Erzeugung), wurde der PKCS12-Keystore per `openssl req` +
  `openssl pkcs12 -export` erzeugt (Android/Gradle akzeptiert PKCS12 genauso wie JKS).
  Zertifikat: CN=NocTurn, O=letheapp.de, OU=Dennis Bassy. Passwörter zufällig generiert,
  liegen in `local.properties` (gitignored) sowie als vier GitHub-Actions-Secrets
  (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
  `RELEASE_KEY_PASSWORD`) — Upload erfolgte über die GitHub-REST-API mit dem Token aus
  `/opt/githubtok3n.txt` (kein `gh`-CLI installiert), Verschlüsselung des Secret-Werts
  via neu in einem venv installiertem `pynacl` (sealed box, wie von der API verlangt).
  **Wichtig: die Keystore-Passwörter sind nur im `local.properties`-Klartext und als
  verschlüsselte GitHub-Secrets vorhanden — ohne Backup des `keystore/`-Ordners oder der
  Passwörter kann künftig keine APK mehr mit demselben Schlüssel signiert werden.**
  `build-apk.yml` baut bei `main`-Push/`workflow_dispatch` eine signierte Release-APK und
  veröffentlicht sie automatisch als GitHub Release (Tag `v<versionName>-<run_number>`).
- **Ton-Vorschau**: `SoundPickerViewModel` bekam einen `MediaPlayer`-basierten
  `previewSound()`/`stopPreview()`, der bei jeder Auswahl in der Töne-Liste **und** im
  Lethe-Bibliothek-Tab automatisch kurz anspielt; Vorschau stoppt beim Tab-Wechsel bzw.
  beim Verlassen des Screens (`onCleared`).
- **Health-Connect-Berechtigungs-Bug recherchiert und behoben**: Health Connect verlangt
  laut offizieller Doku eine registrierte `PermissionsRationaleActivity`
  (`androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`-Intent-Filter) als
  Datenschutz-Gegenstück zum Berechtigungsdialog — NocTurn hatte diese bislang nicht,
  was die unzuverlässige Berechtigungsvergabe erklären dürfte (jetzt ergänzt). Zusätzlich
  ist bekannt, dass Android nach zweimaligem Ablehnen die erneute Anfrage systemseitig
  sperrt (recherchiert); dafür gibt's jetzt im Health-Connect-Status-Screen einen "In
  Health Connect öffnen"-Button (`HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS`),
  über den der Nutzer die Berechtigung notfalls manuell erteilen kann.
- **Feiertage-Anzeige-Bug gefunden und behoben**: `HolidaySettingsViewModel.save()` rief
  `onBack()` auf, bevor der im `viewModelScope` gestartete DB-Schreibvorgang
  abgeschlossen war — beim Zurücknavigieren wird der ViewModel-Scope abgebrochen, der
  Speichervorgang lief oft nie durch (Race Condition). Fix: `save()` nimmt jetzt einen
  `onSaved`-Callback, der erst nach dem `suspend`-DB-Call aufgerufen wird — exakt das
  Muster, das `RotationEditorViewModel.save(onDone)` bereits vorher richtig gemacht hat.
- **Swipe zum Monatswechsel**: `CalendarMonthScreen` erkennt horizontale Drag-Gesten
  (`detectHorizontalDragGestures`) über dem Kalendergitter und wechselt bei
  Überschreiten eines Schwellwerts den Monat.
- **Neue Tagesansicht** (`CalendarDayScreen`/`CalendarDayViewModel`, Route
  `calendarDay/{epochDay}`): beim Antippen eines Tages mit Termin/Feiertag/Schicht
  öffnet sich jetzt eine eigene Tagesansicht (an Google Kalender angelehnt) statt des
  bisherigen `AlertDialog` — ganztägige Einträge (Feiertage/Ferien, ganztägige Termine)
  als Banner oben, darunter zeitlich sortierte Einträge mit Start-/Endzeit (Schicht-Zeit
  aus `ShiftEntity.startHour/startMinute/durationMinutes`, Termin-Zeit aus
  `AppointmentEntity.startEpochMillis/endEpochMillis`). Leere Tage öffnen weiterhin nur
  die Tagesauswahl für den "+"-Schnellzugriff, keine neue Ansicht.
- **Rotations-Seitenleiste** (`ModalNavigationDrawer` in `CalendarMonthScreen`, Vorbild:
  ausklappbares Google-Kalender-Menü): zeigt alle vorhandenen Rotationen jobübergreifend
  (`CalendarMonthViewModel.rotations`, neuer `CalendarRepository`/`CalendarEntryDao`-Weg
  `applyRotationRange`/`deleteForRotationInRange`). Flow: Rotation in der Seitenleiste
  antippen → Hinweis-Karte "Tippe auf den Starttag" → Starttag im Kalender antippen →
  schwebendes Dialogfenster mit Dauer-Auswahl (1/3/6 Monate, 1/2 Jahre) → Rotation wird ab
  diesem Tag für die gewählte Dauer neu eingetragen, **ohne** das beim Anlegen der
  Rotation gespeicherte Standard-Fortschreibefenster (5 Jahre ab ursprünglichem
  Startdatum) zu löschen — nur der neu gewählte Zeitraum wird für diese Rotation
  überschrieben.
- **Verifikation**: Für diese Session wurde erstmals versucht, ein lokales Android-SDK
  samt Build-Tools 34 neu aufzusetzen (JDK 17 per `apt`, SDK per `sdkmanager`) — `aapt2`
  bleibt aber weiterhin ein reines x86_64-Binary (auch in Build-Tools bis 37 noch keine
  Linux-ARM64-Variante von Google veröffentlicht). Ein Versuch, dies über
  `qemu-user-static` + `dpkg --add-architecture amd64`/`libc6:amd64` lauffähig zu machen,
  wurde **abgebrochen**, nachdem die Multiarch-Paketauflösung Kernsystempakete
  (libc6, tar, util-linux) als nicht installierbar meldete — zu hohes Risiko, das
  System zu beschädigen; `dpkg --remove-architecture amd64` wurde sofort danach
  ausgeführt, System-Zustand verifiziert sauber. `compileDebugKotlin` scheitert daher
  weiterhin an `processDebugResources`/fehlendem `R.jar` — echte Verifikation bleibt wie
  in allen vorherigen Sessions der GitHub Action vorbehalten; alle Änderungen wurden
  stattdessen besonders sorgfältig manuell gegen bestehende Code-Muster und exakte
  DAO-/Repository-Signaturen geprüft.

## Session 2026-10-09 (Teil 2): Kontakt-Geburtstage + Berechtigungs-Fixes

- **Geburtstage aus Geräte-Kontakten im Kalender** (neues Package `contacts`):
  `ContactBirthdayRepository` (@Singleton, Hilt-Constructor-Injection, kein Modul nötig)
  liest per `ContactsContract.Data` die Event-Rows vom Typ `TYPE_BIRTHDAY`
  (Spalten CONTACT_ID/DISPLAY_NAME/PHOTO_URI/START_DATE), parst beide Android-Formate
  "yyyy-MM-dd" und "--MM-dd" (Jahr optional → `year`), 29.02. fällt in Nicht-Schaltjahren
  auf den 28.02. `birthdaysInRange(from,to)` bildet die jährlich wiederkehrenden
  Geburtstage auf konkrete Daten im Anzeige-Jahr ab. Braucht `READ_CONTACTS`; ohne
  Berechtigung leere Liste (kein Throw).
  - Monatsansicht: neuer `CalendarDayEntry`-Subtyp `BirthdayDayEntry` (lila
    `BIRTHDAY_COLOR_ARGB`, Label "🎂 Name") — farbiger Punkt + Name wie andere Einträge.
  - Tagesansicht: neuer `DayAgendaItem`-Subtyp `BirthdayAgendaItem` mit `photoUri`;
    `CalendarDayScreen.BirthdayCard` zeigt zusätzlich das **Kontaktbild** (geladen ohne
    Bild-Lib direkt via `ContentResolver`/`BitmapFactory`, Fallback: Torten-Icon im
    Akzentkreis) und das erreichte Alter (nur wenn Geburtsjahr bekannt).
  - Beide ViewModels (`CalendarMonthViewModel`, `CalendarDayViewModel`) bekommen das Repo
    injiziert, Geburtstage über `flow { emit(...) }.flowOn(Dispatchers.IO)` als 4. Flow im
    jeweiligen finalen `combine` ergänzt.
  - Bewusste Produktentscheidung: Foto nur in der Tagesansicht, in der Monatsansicht nur
    Punkt+Name (Grid-Zellen zu klein für Avatare). Falls Avatare im Monatsraster gewünscht
    → gesondert nachziehen.
- **Sensor-Berechtigung "funktioniert nicht" behoben**: Die Onboarding-Zeile verlangte
  früher ACTIVITY_RECOGNITION **und** BODY_SENSORS zusammen. Nichts im Code liest
  Körper-Vitaldaten (kein Herzfrequenz-Sensor); `SensorMovementMonitor` nutzt nur
  Schrittzähler (ACTIVITY_RECOGNITION) + Accelerometer/Gyroskop (keine Berechtigung).
  BODY_SENSORS ließ die Zeile auf Geräten ohne Körpersensor dauerhaft "nicht erteilt"
  anzeigen. Fix: BODY_SENSORS komplett aus Manifest + PermissionsScreen entfernt, Zeile
  heißt jetzt "Aktivitätserkennung (Bewegung)" und fragt nur ACTIVITY_RECOGNITION an.
- **Health-Connect-Berechtigung "trotz mehrfachem Versuch nicht erteilt" behoben**:
  wahrscheinliche Ursache = Gerät auf Android 14+, wo Health Connect ins System integriert
  ist und statt `ACTION_SHOW_PERMISSIONS_RATIONALE` eine `activity-alias`
  `ViewPermissionUsageActivity` mit `VIEW_PERMISSION_USAGE` + Kategorie
  `HEALTH_PERMISSIONS` (permission `START_VIEW_PERMISSION_USAGE`) im Manifest erwartet.
  Diese Alias (Ziel = bestehende `PermissionsRationaleActivity`) ergänzt; der alte
  Rationale-Intent-Filter bleibt für Android 13 (minSdk) erhalten.
- Noch **nicht committet/gepusht** (Standing-Regel). `processDebugManifest` lief lokal
  durch (validiert die Manifest-XML-Änderungen), `compileDebugKotlin` weiterhin durch
  aapt2/x86_64 auf dem Pi blockiert.
