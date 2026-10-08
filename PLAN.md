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

## Nachträglich ergänzte Anforderungen (2026-10-08, noch nicht umgesetzt)

Nach Abschluss der ursprünglichen Schritte 1–12 kamen folgende Anforderungen hinzu, die
den Phase-1-Scope erweitern (Details siehe CLAUDE.md Kernfunktionen 5, 10, 11):

- [ ] Berechtigungs-Onboarding beim ersten Start (alle benötigten Berechtigungen mit
  Erklärung anfragen, nicht nur ad-hoc wie aktuell beim Exact-Alarm-Banner).
- [ ] System-Registrierung als Wecker-/Uhr-/Kalender-App (Manifest-Intent-Filter
  `SET_ALARM`/`SHOW_ALARMS`/`DISMISS_ALARM`/`SNOOZE_ALARM`, Kategorien
  `APP_CALENDAR`/`APP_CLOCK`).
- [ ] Umschaltbarer Schüler/Student-Modus: Wochenstundenplan statt Tages-Rotation, mit
  bis zu 12 Unterrichtsstunden/Tag (eigene Start-/Endzeit, Farbe) statt einer
  Schicht/Tag.
- [ ] Foto-Import: Stundenplan/Schichtplan abfotografieren, per OCR automatisch
  Schichten/Unterrichtsstunden (Start-/Endzeit, Farbe) erkennen und daraus automatisch
  eine Rotation bzw. einen Wochenstundenplan erzeugen.
- [ ] Konfigurierbarer Weckzeit-Abstand pro Schicht bzw. vor der ersten
  Unterrichtsstunde: "frühestes Wecken" und "spätestens aufstehen" in Minuten vor
  Schicht-/Unterrichtsbeginn, statt Wecken exakt zur Schicht-Startzeit
  (`AlarmScheduler`/Alarm↔Schicht-Verknüpfung aus Schritt 12 muss dafür angepasst
  werden).

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
