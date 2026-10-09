# Smarter Wecker – Plan Phase 1 (MVP)

Dieser Plan grenzt den ersten Umsetzungsschritt ab: die Grundfunktionen des Weckers
plus die grundlegende Kalenderfunktion mit Rotation. Streaming-Ton-Quellen,
Kalender-Teilen und externe Kalendersync sind bewusst **nicht** Teil von Phase 1,
damit zuerst ein lauffähiger Kern steht.

## Ziel

Ein Wecker, der:
- zuverlässig klingelt und beim Snoozen/Stoppen aktiv prüft, ob der Nutzer wirklich
  aufgestanden ist,
- lokale Audiodateien (und System-Ringtones) als Weckton anbietet,
- Schichten/Jobs/Rotationen verwalten und daraus automatisch die passenden
  Alarmzeiten im Kalender fortschreiben kann.

## Enthalten in Phase 1

**Wecker-Grundfunktionen** (siehe CLAUDE.md Kernfunktionen 1–4, 9):
- Alarmliste + Zeit-Eingabe im Google-Uhr-Stil (Karten, Toggle, FAB, 24h-Zifferblatt).
- Weckton-Auswahl-Screen, aber in Phase 1 nur der Tab **"Töne"**: System-Ringtones +
  frei wählbare lokale Audiodateien (über Android `MediaStore`/Storage-Picker,
  persistente URI-Berechtigung). Tabs für Spotify/Deezer/Lethe existieren als
  UI-Grundgerüst, bleiben aber deaktiviert/ausgeblendet.
- Snooze mit zufälligem Intervall (3–5 Min), eskalierender/wechselnder Ton während
  des Klingelns.
- Stop löst keine sofortige Beendigung aus: Weiterüberwachung bis zu 10 Minuten.
- Aufsteh-Erkennung: Health Connect (falls verfügbar) + Sensor-Fallback
  (`TYPE_ACCELEROMETER`/`TYPE_GYROSCOPE`/`TYPE_STEP_COUNTER`) über Foreground
  Service.
- Notfall-Alarm nach Ablauf ohne Aufstehen, nur stoppbar durch das variierende
  Drag-&-Drop-Zuordnungs-Puzzle (Geometrien/Smileys, wechselnde Farbe/Form/Anzahl/
  Pool-Zusammensetzung).

**Kalender-Grundfunktion mit Rotation** (siehe CLAUDE.md Kernfunktion 5–6):
- Vorlagen-Verwaltung: Schichten (Name, Farbe, Start-/Endzeit, Dauer) und Jobs
  (mehrere Jobs mit je eigenem Schichten-Set).
- Rotationen: Raster-Eingabe (Tag × Wochentag) bis sich das Muster wiederholen
  würde; Erkennung der Zykluslänge.
- Automatisches, fortlaufendes Eintragen der Rotation in den lokalen Kalender für
  mehrere Jahre im Voraus (lokale Datenhaltung, kein Server nötig).
- Übersichtliche Monatsansicht mit farbiger Schichtanzeige.
- Verknüpfung Alarm ↔ Schicht: Wecker kann seine Zeit aus der für den Tag
  geltenden Schicht ableiten statt aus einer fest hinterlegten Uhrzeit.

## Explizit nicht Teil von Phase 1

- Spotify-, Deezer- und Lethe-Bibliothek-Anbindung als Ton-Quelle.
- Kalender-Teilen (P2P bzw. Lethe-Backend-Relay).
- Sync mit Google Calendar / Nextcloud (CalDAV).
- Home-Screen-Widgets (Uhr, Wetter/OpenWeatherMap).

Diese Punkte bleiben als Kernfunktionen in CLAUDE.md dokumentiert und folgen in
späteren Phasen.

## Umsetzungsstand

- [x] **Schritt 1 – Projekt-Setup**: Gradle-Grundgerüst (Version Catalog,
  Gradle-Wrapper 8.9), Kotlin 2.0.21 + Jetpack Compose + Hilt 2.52, Package
  `de.letheapp.nocturn`, minSdk = targetSdk = API 33 (compileSdk 34). App startet
  mit Platzhalter-Alarmliste (leere Liste + FAB).
- [x] **Schritt 2 – Datenmodell & Room (Grundgerüst)**: Entities `AlarmEntity`,
  `JobEntity`, `ShiftEntity`, `RotationEntity`, `RotationDayEntity` + DAOs +
  `NocturnDatabase`, per Hilt-Modul bereitgestellt.
- [x] **Schritt 3 – Alarmliste-UI + Zeit-Eingabe-Dialog, `AlarmManager`-Anbindung**:
  Alarmliste im Google-Uhr-Stil (Karten, Toggle, FAB), 24h-Zifferblatt-Dialog mit
  Tastatur-Umschalter, Edit-Screen (Label, Wiederholung, Löschen), `AlarmScheduler`
  (exakte Alarme via `AlarmManager.setAlarmClock`/`setAndAllowWhileIdle`),
  `AlarmReceiver` + `BootReceiver` zum Neuplanen nach Neustart.
- [x] **Schritt 4 – Weckton-Auswahl, Tab "Töne"**: `SoundRepository`
  (Systemtöne + persistente URI-Berechtigung für eigene Audiodateien),
  `SoundPickerScreen` mit Tabs (Spotify/Deezer/Lethe sichtbar, aber deaktiviert).
- [x] **Schritt 5 – Foreground Service & Snooze**: `AlarmRingingService` spielt den
  Weckton, eskaliert Lautstärke/Ton während des Klingelns, `AlarmRingingActivity`
  als Vollbild-UI über dem Sperrbildschirm; Snooze plant per Zufallsintervall
  (3–5 Min) über `AlarmScheduler.scheduleSnooze` neu.
- [x] **Schritt 6 – Aufsteh-Erkennung**: `SensorMovementMonitor`
  (Schrittzähler-Sensor, Beschleunigungssensor-Fallback), `HealthConnectMovementMonitor`
  (Schritte via Health Connect, nur falls verfügbar/Berechtigung erteilt),
  `CombinedMovementMonitor` kombiniert beide Quellen.
- [x] **Schritt 7 – Stop-Weiterüberwachung + Notfall-Trigger**:
  `PostAlarmMonitoringService` überwacht bis zu 10 Minuten nach "Stop" weiter und
  löst bei Timeout den Notfall-Alarm aus (Direktstart + Fullscreen-Notification-Fallback).
  `EmergencyAlarmService` lässt sich nicht über "Stop" beenden, Ton eskaliert
  fortlaufend (Lautstärke + Pitch).
- [x] **Schritt 8 – Notfall-Puzzle**: `PuzzleGenerator` erzeugt zufällig variierende
  Zuordnungsaufgaben (Geometrien/Emojis, Farbe/Form, Sequenzlänge 4–6, Pool inkl.
  Ablenker), `PuzzleScreen` mit echtem Drag & Drop (Pointer-Tracking + Treffer-Test
  gegen die Zielfelder), `EmergencyAlarmActivity` mit gesperrter Zurück-Taste.
- [x] **Schritt 9 – Kalender-Vorlagenverwaltung**: `CalendarRepository` (Jobs/
  Schichten/Rotationen/Kalendereinträge), `JobListScreen` (Jobs-CRUD, Einstieg über
  neues Kalender-Icon in der Alarmliste), `JobDetailScreen` mit Tabs "Schichten"/
  "Rotationen", `ShiftEditDialog` (Name, Farbpalette, Start-/Endzeit → Dauer).
- [x] **Schritt 10 – Rotationen**: `RotationEditorScreen` mit Tag-für-Tag-Raster
  (7-Wochentags-Spalten via `LazyVerticalGrid`, Zykluslänge = Anzahl eingetragener
  Tage), Start-Datum per `DatePicker`, Schicht-Zuweisung per Tap-Dialog je Tag.
  `CalendarRepository.saveRotation` schreibt daraus sofort `CalendarEntryEntity`s
  für `FORECAST_YEARS` (5) Jahre im Voraus fort (Modulo über die Zykluslänge).
  Neue Room-Entity `CalendarEntryEntity`, DB-Version auf 2 angehoben mit
  `fallbackToDestructiveMigration()` (keine Bestandsdaten vorhanden).
- [x] **Schritt 11 – Kalender-Monatsansicht**: `CalendarMonthScreen` + `CalendarMonthViewModel`
  zeigen eine aufgeräumte Monatsansicht (7-Spalten-Raster, Mo–So), pro Tag maximal 3 farbige
  Punkte für die an dem Tag laut Rotation(en) geltenden Schichten (+"N" bei mehr), Details per
  Tap auf den Tag als Dialog (Schicht- und Job-Name). Aggregiert über alle Jobs/Rotationen
  hinweg (neue DAO-Queries `ShiftDao.observeAll`/`getById`, `RotationDao.observeAll`).
  Einstieg über neues Kalender-Icon in der Alarmliste (`AlarmListScreen`), getrennt vom
  bisherigen Vorlagen-Icon.
- [x] **Schritt 12 – Alarm↔Schicht-Verknüpfung**: `AlarmEditScreen` erlaubt, einen Alarm statt
  an eine feste Uhrzeit/Wiederholung an eine Rotation zu koppeln (`AlarmEntity.rotationId`,
  Auswahl-Dialog über alle Jobs). `AlarmScheduler.schedule` ist jetzt `suspend` und leitet bei
  verknüpfter Rotation die nächste Weckzeit aus dem nächsten anstehenden Schicht-Termin ab
  (`CalendarRepository.getNextCalendarEntryWithShift`, neue DAO-Query `getNextWithShift`);
  freie Tage laut Rotation werden dabei automatisch übersprungen. `AlarmReceiver` plant
  rotationsgebundene Alarme nach dem Klingeln ebenfalls automatisch den nächsten Termin vor.

**Bekannte Vereinfachungen (Phase 1, bewusst zurückgestellt):** Health-Connect-
Berechtigungsanfrage läuft nur best-effort (keine eigene Onboarding-/Rationale-UI);
ohne lokales Android-SDK konnte kein `./gradlew assembleDebug` ausgeführt werden,
Verifikation erfolgt über die GitHub Action `build-apk.yml`. Schicht-Farbwahl nutzt
eine kuratierte Farbpalette (10 Farben) statt eines vollen Farbrads, um keine
Zusatz-Library einzuführen.

**Nachtrag (2026-10-08, Build-Verifikation für die fünf Erweiterungen):** Für diese Runde
wurde lokal doch ein Android-SDK (Kommandozeilen-Tools, Platform 34) installiert und
`./gradlew :app:compileDebugKotlin` (inkl. KSP-Lauf für Room/Hilt) erfolgreich ausgeführt —
alle Entity-/DAO-/Hilt-Graph-Änderungen sowie die neue Tesseract4Android-Abhängigkeit
(über JitPack) kompilieren und lösen sich auf. Ein vollständiger `assembleDebug`-Lauf
(inkl. Ressourcen-Verarbeitung über `aapt2`) war weiterhin nicht möglich, da Googles
`aapt2`-Binary für die hier verwendete Linux-ARM64-Plattform nicht existiert (nur
x86_64/macOS/Windows) — reine Kotlin-Compile-/Annotation-Processing-Verifikation war davon
aber nicht betroffen. Finale Verifikation weiterhin über die GitHub Action `build-apk.yml`
auf x86_64-Runnern.

## Nachträglich ergänzte Anforderungen (2026-10-08, umgesetzt)

Nach Abschluss der ursprünglichen Schritte 1–12 kamen folgende Anforderungen hinzu, die
den Phase-1-Scope erweitern (Details siehe CLAUDE.md Kernfunktionen 5, 10, 11). Alle fünf
Punkte sind jetzt implementiert:

- [x] **Berechtigungs-Onboarding**: neuer `onboarding`-Screen (`PermissionsScreen` +
  `AppPreferences`), der beim allerersten Start (vor der Alarmliste) alle benötigten
  Berechtigungen mit kurzer Erklärung anfragt (exakte Alarme, Benachrichtigungen, Kamera,
  Körpersensoren/Aktivitätserkennung, Health Connect falls verfügbar, Akku-Optimierung
  ignorieren). Über ein neues Schild-Icon in der Alarmliste jederzeit erneut erreichbar,
  da fehlende Berechtigungen dauerhaft (nicht nur beim ersten Start) relevant bleiben.
  Der bisherige stille Permission-Request in `AlarmListScreen` wurde dadurch ersetzt; der
  bestehende Exact-Alarm-Banner bleibt zusätzlich als Vereinfachung erhalten.
- [x] **System-Registrierung**: `MainActivity` bekommt zusätzliche Intent-Filter für
  `SET_ALARM`/`SHOW_ALARMS`/`DISMISS_ALARM`/`SNOOZE_ALARM` sowie eigene Intent-Filter mit
  den Kategorien `APP_CALENDAR`/`APP_CLOCK`, damit die App in den Android-Einstellungen
  unter "Standard-Apps" auswählbar ist. Die zusätzlichen Alarm-Aktionen öffnen aktuell
  schlicht die Alarmliste (kein eigener Deep-Link auf einen bestimmten Alarm), das reicht
  für die System-Erkennung als Wecker-/Uhr-/Kalender-App.
- [x] **Schüler/Student-Modus**: `JobEntity.mode` (`SHIFT`/`STUDENT`, Enum `JobMode`) ist
  pro Job umschaltbar (Segmented Button in `JobDetailScreen`). Im Student-Modus ersetzt ein
  neuer Wochenstundenplan-Tab (`LessonEntity`, bis zu 12 Unterrichtsstunden/Tag, 7
  Wochentags-Tabs) die Schichten/Rotationen-Tabs. Ein Alarm kann statt an eine Rotation an
  einen Student-Modus-Job gekoppelt werden (`AlarmEntity.timetableJobId`); die Wecker-Zeit
  wird aus der zeitlich ersten Stunde des jeweils nächsten Wochentags abgeleitet.
- [x] **Foto-Import**: neues Package `ocr` — `TesseractOcrEngine` (Tesseract4Android via
  JitPack, lädt die deutschen Trainingsdaten beim ersten Gebrauch einmalig von der
  offiziellen tessdata-Quelle nach, kein Binär-Blob im Repo), `TimetableTextParser`
  (Zeilen mit Zeitangabe wie "06:00-14:00" per Regex extrahieren), `PhotoImportScreen`
  (Foto via Kamera-App + `FileProvider` aufnehmen, erkannte Zeilen als Karten mit
  Korrektur-Dialog). Übernahme erzeugt je nach Job-Modus entweder Schichten + eine neue
  Rotation (Schichtarbeit, erkannte Zeilenreihenfolge = Tag-für-Tag-Muster) oder
  Unterrichtsstunden (Student-Modus). **Bewusste Vereinfachung**: die
  Tabellen-Spaltenstruktur eines Fotos (z. B. welche Spalte = welcher Wochentag) kann
  reiner Fließtext-OCR nicht zuverlässig entnehmen; im Student-Modus weist der Nutzer jeder
  erkannten Zeile daher im Korrektur-Dialog selbst einen Wochentag zu, statt dass das
  automatisch aus dem Foto erkannt wird.
- [x] **Konfigurierbarer Weckzeit-Abstand**: `ShiftEntity`/`LessonEntity` haben je zwei neue
  Felder `earliestWakeOffsetMinutes`/`latestWakeupOffsetMinutes` (Minuten vor Schicht-/
  Stundenbeginn, 0 = altes Verhalten). Neue Klasse `WakeWindowResolver` kapselt die
  Ableitung von Weckzeitpunkt und Aufsteh-Deadline aus Rotation oder Stundenplan und wird
  von `AlarmScheduler` (nächster Klingel-Zeitpunkt) und `PostAlarmMonitoringService`
  (Aufsteh-Deadline nach "Stop" statt des bisherigen festen 10-Minuten-Fensters) gemeinsam
  genutzt. Ist kein Offset konfiguriert oder keine Verknüpfung vorhanden, gilt weiterhin
  das feste 10-Minuten-Fenster als Fallback.

**Bekannte Vereinfachung dieser Erweiterung:** Die Kalender-Monatsansicht
(`CalendarMonthScreen`) zeigt bislang nur Rotations-Einträge (Schichtarbeit); der
wöchentliche Stundenplan im Student-Modus wird dort noch nicht separat eingeblendet.

## Nachträglich ergänzte Anforderungen (2026-10-08, Nextcloud-Synchronisierung, umgesetzt)

Phase 1 schloss Google-Calendar-/Nextcloud-Sync explizit aus (siehe oben); auf Nutzerwunsch
("Baue die Nextcloud Synchronisierung hinzu") jetzt nachträglich umgesetzt, Google Calendar
bleibt weiterhin offen/zukünftig:

- [x] **Neues Package `sync`**: `CalDavClient` (eigener, minimaler WebDAV/CalDAV-Client über
  OkHttp — PROPFIND-Discovery von current-user-principal/calendar-home-set/Kalenderliste,
  REPORT calendar-query für Events inkl. ETag, PUT/DELETE für einzelne Events; XML-Antworten
  werden über einen generischen, namespace-unabhängigen Baum aus Androids eingebautem
  `XmlPullParser` ausgewertet, da verschiedene Server unterschiedliche Namespace-Präfixe
  nutzen). `IcsFormat` ist ein selbst gebauter ICS-Reader/-Writer für das benötigte
  VEVENT-Subset (UID/DTSTART/DTEND/SUMMARY/LOCATION/DESCRIPTION, inkl. Line-Folding/-Unfolding
  und Escaping nach RFC 5545) — bewusst ohne Zusatz-Library (biweekly/ical4j), da nur eigene,
  einfach strukturierte Events gelesen/geschrieben werden müssen (FOSS-Minimalismus: keine
  weitere Abhängigkeit für eine klar begrenzte Teilmenge des Formats).
- [x] **Sync-Richtung aufgelöst** (vorher TBD in CLAUDE.md): freie Termine
  (`AppointmentEntity`, neues `dirty`-Flag für "seit letztem Sync lokal geändert") werden
  bidirektional abgeglichen (Push eigener Änderungen, Pull von Neuanlagen/Änderungen/
  Löschungen aus Nextcloud). Schicht-Kalendereinträge aus Rotationen werden nur einseitig
  NocTurn → Nextcloud exportiert (rollendes 180-Tage-Fenster, Abgleich per Inhalts-Hash statt
  bei jedem Lauf alles neu zu schreiben), da sie aus der Rotation abgeleitet sind und nicht
  sinnvoll zurückgelesen werden können. **Bekannte Vereinfachung**: lokal gelöschte Termine
  werden aktuell nicht automatisch auf Nextcloud entfernt (kein Tombstone-Tracking) — Löschen
  auf Nextcloud-Seite entfernt den Termin dagegen auch lokal korrekt wieder.
- [x] **Neue Room-Entities** `CalDavAccountEntity` (Server-URL, Benutzername,
  Ziel-Kalender-Href, Sync-Umschalter je Richtung, letzter Sync-Zeitpunkt/-Fehler) und
  `SyncedEventEntity` (pro Konto: lokaler Datensatz ↔ Remote-Href/ETag, bei Schichten
  zusätzlich ein Inhalts-Hash) für den Änderungsabgleich. DB-Version 4 → 5 (weiterhin
  `fallbackToDestructiveMigration()`, da noch keine veröffentlichte Version mit
  Bestandsdaten existiert). Aktuell ist nur ein Nextcloud-Konto gleichzeitig vorgesehen.
- [x] **`NextcloudCredentialsStore`**: App-Passwort AES/GCM-verschlüsselt mit einem
  Android-Keystore-Schlüssel in SharedPreferences, bewusst ohne
  `androidx.security-crypto`-Abhängigkeit (nur ein Geheimwert pro Konto, rohe Keystore-API
  reicht dafür aus).
- [x] **Hintergrund-Sync**: `NextcloudSyncWorker` (`@HiltWorker`, `androidx.work` +
  `androidx.hilt:hilt-work`), alle 4 Stunden über `WorkManager.enqueueUniquePeriodicWork` in
  `NocturnApplication` eingeplant (nur wirksam, wenn ein Konto verbunden ist).
- [x] **UI**: neuer Screen `NextcloudSyncScreen` (Server-URL/Benutzername/App-Passwort →
  "Kalender suchen" → Auswahl per CalDAV-Discovery → Verbinden; danach Status, Umschalter je
  Sync-Richtung, "Jetzt synchronisieren", "Verbindung trennen"), erreichbar über einen neuen
  Eintrag "Nextcloud-Sync" im bestehenden 3-Punkte-Menü der Alarmliste.
- **Bekannte Einschränkung**: Verifikation weiterhin nicht per lokalem `./gradlew
  assembleDebug`/`compileDebugKotlin` möglich — auf diesem Raspberry Pi (aarch64) scheitert
  inzwischen auch `compileDebugKotlin`, da es `processDebugResources` voraussetzt und das
  dafür nötige `aapt2` (nur als x86_64-Binary verfügbar) trotz registriertem
  `qemu-x86_64`-Binfmt mit "Exec format error" abbricht (Magic/Mask des Kernel-Eintrags passt
  nicht auf dieses dynamisch gelinkte PIE-Binary; eine vorhandene `qemu-x86_64-static`-Instanz
  hat zudem keinen x86_64-Sysroot). Verifikation erfolgt daher ausschließlich über die GitHub
  Action `build-apk.yml` auf x86_64-Runnern.

## Nachträglich ergänzte Anforderungen (2026-10-08, Uhr- und Kalender-Widget, umgesetzt)

Phase 1 schloss Home-Screen-Widgets explizit aus (siehe oben); auf Nutzerwunsch
("Baue das Uhr Widget ein sowie ein Widget für Termine Kalender") jetzt Uhr- und
Kalender-/Termine-Widget nachträglich umgesetzt. Wetter-Widget bleibt weiterhin offen
(nicht Teil dieser Anfrage).

- [x] **`ClockWidgetProvider`** (`widget`-Package): klassischer `AppWidgetProvider` +
  `RemoteViews` (keine Glance-Abhängigkeit, FOSS-Minimalismus). Zeigt Datum/Uhrzeit/
  nächsten Alarm transparent über dem Wallpaper, exakt nach Nutzer-Referenzbild gestapelt.
  "Nächster Alarm" kommt direkt von `AlarmManager.getNextAlarmClock()`, keine eigene DB-
  Abfrage nötig, da `AlarmScheduler` bereits über `setAlarmClock` plant. Minütliches
  Update selbst über `AlarmManager.setRepeating` angestoßen (offizielle
  `updatePeriodMillis`-Updates sind auf 30 Minuten limitiert, zu grob für eine Uhr).
- [x] **`CalendarWidgetProvider`**: zeigt die nächsten bis zu 4 anstehenden Einträge
  (Schichten aus Rotationen + freie Termine, 14-Tage-Vorschau) als Agenda mit farbigem
  Punkt, Tag/Uhrzeit und Bezeichnung. Nutzt Hilt-`@AndroidEntryPoint` direkt auf dem
  `AppWidgetProvider` (derselbe Ansatz wie bei `BootReceiver`/`AlarmReceiver`, da
  `AppWidgetProvider` selbst ein `BroadcastReceiver` ist) für den Zugriff auf
  `CalendarRepository.getUpcomingAgenda(...)` (neue Methode). Tippen öffnet
  `MainActivity` direkt in der Kalender-Monatsansicht (neuer `EXTRA_OPEN_CALENDAR`-Intent-
  Extra).
- Beide Widgets rein über klassische XML-Layouts/`RemoteViews` umgesetzt statt Jetpack
  Glance, da für zwei überwiegend statische Widgets keine zusätzliche Compose-für-Widgets-
  Abhängigkeit nötig ist.
- **Bekannte Einschränkung**: Diesmal ließ sich nicht einmal der bisherige
  `compileDebugKotlin`-Workaround (Ausschluss der `aapt2`-abhängigen Resource-Tasks)
  nutzen, da der neue Widget-Code tatsächlich `R.layout.widget_clock`/`R.id....`
  referenziert und damit ein echtes `R.jar` benötigt — der Workaround griff bisher nur,
  wenn keine Quelldatei die `R`-Klasse referenzierte. Verifikation läuft daher
  ausschließlich über die GitHub Action `build-apk.yml` beim nächsten Push; der Code wurde
  stattdessen besonders sorgfältig manuell durchgegangen (Layout-IDs ↔ Kotlin-Referenzen,
  Manifest-Receiver-Registrierung, Action-String-Konsistenz zwischen Manifest und Provider).

## Nachträglich ergänzte Anforderungen (2026-10-08, Kalender-FAB + Ferien/Feiertage, umgesetzt)

Nutzerwunsch: "+"-Button in der Kalender-Monatsansicht zum Anlegen von Terminen/Schichten,
plus ein neuer Menüpunkt "Ferien & Feiertage" (Bundesland wählen, Feiertage/Schulferien
optional im Kalender anzeigen).

- [x] **FAB "+" in `CalendarMonthScreen`**: fragt "Termin" oder "Schicht" ab. Ein zuvor
  angetippter Tag wird als Datums-Vorbelegung übernommen (dünn umrandet markiert), bleibt
  aber im Folgeformular über einen neuen `NocturnDatePickerDialog` (Material3-`DatePicker`-
  Wrapper) änderbar.
- [x] **Termin-Formular erweitert** (`AddAppointmentScreen`, bisher nur für den externen
  "Zum Kalender hinzufügen"-Chooser-Flow): jetzt zusätzlich mit Datums-Zeile und
  "Wichtigkeit"-Auswahl (Niedrig/Normal/Hoch, neues `AppointmentEntity.priority`-Feld),
  für beide Einstiege (FAB und externer Chooser) gemeinsam genutzt.
- [x] **Neuer Screen `AddShiftEntryScreen`** für die FAB-Option "Schicht": Job → Schicht →
  Datum, gespeichert als neue `ManualShiftEntryEntity` — ergänzt automatisch aus Rotationen
  fortgeschriebene Einträge, ersetzt sie nicht.
- [x] **Neues `holidays`-Package**: `HolidaySettingsScreen` (Bundesland-Auswahl aus allen
  16 deutschen Ländern, zwei Toggles für Feiertage/Ferien) + `HolidayRepository`/
  `HolidaysApi`. Datenquelle: kostenlose, schlüssellose **OpenHolidaysAPI**
  (openholidaysapi.org, ODbL) — als eine der wenigen freien Quellen recherchiert, die
  sowohl gesetzliche Feiertage als auch Schulferien pro Bundesland liefert. Ergebnisse
  werden pro Bundesland/Jahr lokal in Room gecacht (`holidays`-Tabelle), Anzeige in der
  Monatsansicht als zusätzliche farbige Punkte (rot = Feiertag, grün = Ferien).
- DB-Schema-Version 5 → 6 (`manual_shift_entries`, `holiday_settings`, `holidays`,
  `appointments.priority`) — via `fallbackToDestructiveMigration()` ohne manuelle Migration.
- **Bekannte Einschränkung**: lokale Verifikation weiterhin durch das aapt2-Problem
  blockiert (siehe oben); Code besonders sorgfältig manuell durchgegangen, u. a.
  Material3-`ExposedDropdownMenuBox`/`DatePickerDialog`-API-Signaturen vorab per Websuche
  gegen die im Projekt gepinnte Compose-BOM-Version 2024.09.03 verifiziert. Echte
  Verifikation läuft über die GitHub Action `build-apk.yml` beim nächsten Push.

## Nachträglich ergänzte Anforderungen (2026-10-09, Wetter im Uhr-Widget + Agenda-Zeile, umgesetzt)

Nutzerwunsch: Das Uhr-Widget soll zusätzlich das Wetter zeigen (OpenWeatherMap, Key als
GitHub-Secret) sowie die nächsten zwei Termine schmal unten anzeigen, mit "+N" falls am
betroffenen Tag noch mehr Termine anstehen.

- [x] **GitHub-Secret `OPENWEATHER_API_KEY`** im Repo `SmarterWecker` angelegt (per GitHub
  API mit `libsodium`/PyNaCl verschlüsselt übertragen, da kein `gh`-CLI verfügbar ist) —
  wiederverwendet der bereits im Iosync-Projekt genutzte OpenWeatherMap-Key. Lokal zusätzlich
  in `local.properties` (gitignored) hinterlegt.
- [x] **`app/build.gradle.kts`**: `buildConfigField("String", "OPENWEATHER_API_KEY", ...)`
  nach demselben lokal/CI-Fallback-Muster wie beim Release-Keystore; `buildFeatures.buildConfig
  = true` ergänzt.
- [x] **Neues Package `weather`** (`WeatherRepository`): OkHttp + `org.json` (kein Retrofit/
  Moshi, FOSS-Minimalismus), Standort über Androids Plattform-`LocationManager` (letzte
  bekannte Position) statt der proprietären Google-Play-Services-Standort-Lib — im Projekt
  bislang keine GMS-Abhängigkeit vorhanden, soll auch keine neue entstehen. Ergebnis 30
  Minuten in SharedPreferences gecacht, damit der minütliche Uhr-Tick nicht bei jedem Update
  einen Netzwerk-Request auslöst. Wetter-Icon als Emoji (kein zusätzliches Bild-Asset).
- [x] **Neue Berechtigung `ACCESS_COARSE_LOCATION`**: Manifest-Eintrag + neue Zeile im
  Berechtigungs-Onboarding (`PermissionsScreen`) mit Erklärung.
- [x] **`CalendarRepository.getUpcomingAgendaForWidget(maxShown)`** (neu, neben dem
  bestehenden `getUpcomingAgenda`, beide greifen jetzt auf eine gemeinsame private
  `buildAgenda()`-Hilfsfunktion zurück): liefert die nächsten `maxShown` Einträge plus die
  Anzahl weiterer Einträge am selben Tag wie der zuletzt gezeigte Eintrag.
- [x] **`ClockWidgetProvider`** auf Hilt-`@AndroidEntryPoint` umgestellt (gleiches Pattern wie
  `CalendarWidgetProvider`), zeigt Wetter neben dem nächsten Alarm und darunter schmal bis zu
  2 Agenda-Zeilen mit farbigem Punkt, bei Bedarf mit angehängtem "(+N)".
- **Bekannte Einschränkung**: lokale Verifikation weiterhin durch das aapt2-Problem
  blockiert (erneut mit einem frischen `./gradlew compileDebugKotlin`-Versuch bestätigt);
  alle geänderten/neuen Dateien stattdessen auf Klammern-/Strukturbalance sowie
  Wohlgeformtheit der XML-Dateien automatisiert geprüft. Echte Verifikation läuft über die
  GitHub Action `build-apk.yml` beim nächsten Push.

## Grobe Umsetzungsreihenfolge

1. Projekt-Setup: Gradle-Grundgerüst, Kotlin + Jetpack Compose + Hilt,
   minSdk = targetSdk = API 33 (Android 13), Package-Name `de.letheapp.nocturn`.
2. Datenmodell & lokale Persistenz (Room): `Alarm`, `Schicht`, `Job`, `Rotation`,
   `RotationTag`, Weckton-Referenz (URI).
3. Alarmliste-UI + Zeit-Eingabe-Dialog (Zifferblatt), `AlarmManager`-Anbindung
   (exact alarms).
4. Weckton-Auswahl-Screen, Tab "Töne" (lokale Dateien + Ringtones).
5. Foreground Service für die Klingel-/Überwachungsphase; Snooze-Logik
   (Zufallsintervall, Ton-Eskalation).
6. Aufsteh-Erkennung: Sensor-Fallback zuerst (ohne Health-Connect-Abhängigkeit),
   danach Health-Connect-Integration ergänzen.
7. Stop-Weiterüberwachung (10-Minuten-Fenster) + Trigger für Notfall-Alarm.
8. Notfall-Alarm-Puzzle: Drag-&-Drop-Komponente mit zufälliger Generierung
   (Elementtyp, Farbe/Form, Pool-Größe, Anzahl).
9. Kalender-Vorlagenverwaltung: Schichten-CRUD, Jobs-CRUD.
10. Rotationen: Raster-Eingabe-UI, Zyklus-Erkennung, Fortschreib-Logik
    (mehrere Jahre im Voraus).
11. ✅ Kalender-Monatsansicht (aufgeräumtes Layout).
12. ✅ Verknüpfung Alarm ↔ Schicht/Rotation.

Damit ist der komplette Phase-1-Scope (Schritte 1–12) umgesetzt.

## Entscheidungen (2026-10-08)

- **minSdk = targetSdk = API 33 (Android 13)**. Keine Play-Store-Veröffentlichung
  geplant, daher keine Rücksicht auf Play-Store-Mindestanforderungen nötig.
- **Hilt** wird ab Phase 1 eingeführt (kostenlos, Apache-2.0/Open Source — passt
  zum Ziel der FOSS-Kompatibilität).
- **FOSS-Kompatibilität**: offene/freie Lizenzen bei Abhängigkeiten bevorzugen,
  closed-source Komponenten (z. B. Spotify-/Deezer-SDKs in späteren Phasen) bleiben
  optionale Zusatzfunktion, keine Kern-Abhängigkeit.
- **Package-Name: `de.letheapp.nocturn`** (Reverse-Domain von letheapp.de).
