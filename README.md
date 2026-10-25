![NocTurn – Wecker-App mit Schichtkalender](docs/screenshots/01-hero.jpg)

# NocTurn (Smarter Wecker)

Android-App, die es praktisch unmöglich macht, den Wecker zu verschlafen. Statt
simplem Wegdrücken wird nach dem Klingeln aktiv per Sensorik/Health Connect
überprüft, ob man tatsächlich aufgestanden ist und sich bewegt hat. Dazu ein
übersichtlicher Schichtplan-/Stundenplan-Kalender mit automatischer Rotation,
Wetter, Homescreen-Widgets, Smart-Home-Kopplung und eine eigene, darauf
abgestimmte Nextcloud-Kalender-App.

<p align="center">
  <img src="docs/screenshots/02-entspannt.jpg" width="45%" alt="Entspannt den Tag genießen" />
  <img src="docs/screenshots/03-kalender.jpg" width="45%" alt="Kalender mit selbst definierten Farben" />
</p>

- **Entspannt den Tag genießen** – ohne Angst vor dem Verschlafen oder unnötigem
  Stress, wenn man schon wach ist: Die Aufsteh-Erkennung (Kernfunktion 1) prüft
  zuverlässig per Sensorik/Health Connect, ob wirklich aufgestanden wurde, statt
  blind einer fixen Schlummerzeit zu vertrauen.
- **Alles im Blick mit selbst definierten Farben** – der Schichtkalender zeigt
  Rotationen, manuelle Zusatzeinträge (z. B. "Kinder-Wochenende"), Feiertage und
  mehr übersichtlich nebeneinander, jede Schicht in frei wählbarer Pastellfarbe.

> **Status:** Phase 1 (MVP) ist umgesetzt und seitdem um sehr zahlreiche
> Zusatzfunktionen erweitert (siehe unten). Details und offene Konzept-Punkte
> siehe [`CLAUDE.md`](CLAUDE.md), Umsetzungsstand siehe [`PLAN.md`](PLAN.md).
> Aktuelle Version: **0.42.0**.

## Kernfunktionen

- **Aufsteh-Erkennung:** Health Connect (Schritte) + direkte Sensorauswertung
  (Gyroskop/Beschleunigungssensor) als Fallback ohne Smartwatch. Nach "Stop" wird
  mindestens 3,5 Sekunden anhaltende Bewegung verlangt – kurzes Aufschütteln reicht
  nicht.
- **Snooze:** Erneutes Klingeln nach zufälligem Intervall (3–5 Min), Weckton
  eskaliert/ändert sich während des Klingelns. Bleibt es während der gesamten
  Schlummer-Dauer bewegungslos, wählt das nächste Klingeln zufällig einen anderen
  Ton aus der Liste statt des konfigurierten – man gewöhnt sich so nicht an einen
  zweiten, unveränderten Weckversuch.
- **Stop ≠ fertig:** "Stop" öffnet sofort die Notfall-Aufgabe (Ton läuft nahtlos
  weiter), parallel wird bis zu 10 Minuten weiter überwacht, ob der Nutzer aufsteht
  und läuft – dann beendet sich der Notfall-Alarm automatisch, auch ohne gelöstes
  Puzzle.
- **Notfall-Alarm:** Nicht per Knopfdruck stoppbar – nur durch ein variierendes
  Drag-&-Drop-Zuordnungs-Puzzle (Geometrien, Emojis oder farbige Zahlen, Größe und
  Motiv wechseln bei jedem Auftreten, Farbe statt Form entscheidet beim Matching).
  Der Ton eskaliert zusätzlich alle 30 s in Lautstärke/Tonhöhe und wechselt alle
  zwei kompletten Durchläufe zufällig zu einem anderen Ton. Optional lassen sich
  bis zu drei **ioBroker-Smart-Home-Aktionen** hinterlegen (z. B. Lampe einschalten,
  Rollo hoch, ein Skript für Radio auslösen) – inklusive automatischem Ausschalten,
  sobald der Alarm endet, und stufenweisem Aufdimmen bei numerischen Datenpunkten.
- **Weckton-Auswahl:** Lokale Audiodateien/Systemtöne mit Vorschau beim Antippen,
  automatischer Lautstärke-Boost für leise aufgenommene Töne (min. 3 dB), sowie als
  optionale Zusatzquelle die Lethe-Bibliothek (schmaler, widerrufbarer
  `media:read`-Scope statt vollem Messenger-Zugriff); Spotify/Deezer sind als Tabs
  vorbereitet.
- **Schichtplan-Rotation & Stundenplan:** Rotationen sind reine, wiederverwendbare
  Vorlagen (mehrere Schichten pro Tag möglich) – Zuweisung in den Kalender erfolgt
  erst über die Kalender-Seitenleiste (Starttag wählen, Zeitraum 1/3/6 Monate oder
  1/2 Jahre festlegen, inklusive automatischer Phasen-Erkennung bei mehrdeutigem
  Starttag). Umschaltbarer Schüler/Student-Modus mit Wochenstundenplan (bis zu 12
  Stunden/Tag). Konfigurierbarer Weckzeit-Abstand ("frühestes Wecken"/"spätestens
  aufstehen") sowie ein optionaler, vollautomatischer "Nächste Schicht"-Wecker pro
  Schicht inkl. Vorab-Bewegungscheck (überspringt das Klingeln, wenn man schon wach
  ist) und automatischem Wieder-Klingeln im Schicht-Fenster, falls das Notfall-Puzzle
  ohne echte Bewegung gelöst wurde. Foto-Import per On-Device-OCR
  (Tesseract4Android, inkl. eigens feingetuntem Modell für Termine/Daten/Uhrzeiten)
  mit Korrekturmöglichkeit vor der Übernahme.
- **Kalender:** Übersichtliche, pastellfarbene Monats- und Tagesansicht (Schichten,
  freie Termine mit mehreren Erinnerungen pro Termin als Benachrichtigung oder
  E-Mail, Anhänge, Aufgaben/Tasks, Feiertage/Schulferien, Geburtstage aus
  Geräte-Kontakten inkl. Kontaktbild), Swipe-Navigation zwischen Monaten,
  Bearbeiten-Modus zum Mehrfach-Löschen/Zuweisen per langem Gedrückthalten,
  ICS-Import externer Kalender, Foto-/Datei-Import von Terminzetteln und Briefen
  (OCR + deterministisches Parsing, unterstützt auch PDF/DOCX/TXT/RTF), Export als
  PDF/JPG/PNG, serverunabhängiges Teilen per ICS (bevorzugt Lethe als Ziel) sowie
  bidirektionaler Sync mit Nextcloud (CalDAV, inkl. Erinnerungen/Anhängen); Google
  Calendar und P2P-Kalenderfreigabe sind geplant.
- **Eigene Nextcloud-Kalender-App ("NocTurn Kalender"):** Vollständiger Fork von
  `nextcloud/calendar` mit eigener Vue-Seitenleiste zur Verwaltung von Jobs,
  Schichten, Rotationen, Stundenplänen und Aufgaben – bidirektional synchronisiert
  mit der Android-App über eine eigene REST-API, inklusive Job-Sharing (Lesen/
  Schreiben) mit anderen Nextcloud-Nutzern.
- **Wetter:** OpenWeatherMap-Anbindung mit eigenständigem Wetter-Screen (Tab in der
  unteren Navigationsleiste) – aktuelle Bedingungen, stündliche Vorhersage
  (horizontal scrollbar) und 8-Tage-Vorschau mit Tages-Detailansicht. Mehrere
  frei wählbare Orte zum Durchblättern per Wischen, Live-Standortsuche,
  Regen-Vorwarnung und tägliche Morgen-Übersicht als Push-Benachrichtigung.
- **Homescreen-Widgets:** Uhr-Widget (minütlich live aktualisierte Uhrzeit, Wetter,
  nächster Alarm, nächste Termine, automatische helle/dunkle Textfarbe je
  Wallpaper, wählbare Links-/Mitte-/Rechts-Ausrichtung) und Kalender-/Agenda-Widget.
- **Stoppuhr & Timer:** Eigene Screens analog zur Google-Uhr-App, der Timer
  registriert zusätzlich Androids systemweite `SET_TIMER`-Aktion und ist damit auch
  aus anderen Apps/Sprachassistenten heraus nutzbar.
- **Sprachbefehle:** Lokale Android-Spracherkennung (keine Cloud/kein eigener
  Server) für "stelle einen Timer auf X Minuten", Stoppuhr starten/stoppen/
  zurücksetzen und "wecke mich um X Uhr" (einmaliger Alarm).
- **Smart-Home (ioBroker):** Pro Alarm bzw. Schicht bis zu drei Datenpunkt-Aktionen
  bei Notfall-Alarm, mit automatischer Ein/Aus- bzw. Stufenweise-Erhöhen-Erkennung.
- **Berechtigungen-Onboarding:** Alle benötigten Laufzeit-Berechtigungen werden
  beim ersten Start mit Begründung angefragt; Status jederzeit im 3-Punkte-Menü
  einsehbar (inkl. eigenem Health-Connect-Verbindungsstatus-Screen).
- **System-Registrierung:** Meldet sich bei Android als Wecker-, Uhr- und
  Kalender-App an (`SET_ALARM`-Intents, `APP_CALENDAR`/`APP_CLOCK`-Kategorien),
  inklusive Schnellzugriff auf die System-Standard-App-Einstellungen.
- **App-Design:** Festes Neon-Gelb-Branding im Dark-Mode, optional umschaltbar auf
  Material-You-Dynamic-Color aus dem Geräte-Wallpaper.

## Tech-Stack

- Kotlin + Jetpack Compose + Hilt, Room als lokale Datenbank (mit echten
  Migrationen statt destruktivem Fallback – Nutzdaten überleben App-Updates)
- Health Connect API, `SensorManager` (ACC/Gyro/Step Counter) als Fallback
- `AlarmManager` (exact alarms) + Foreground Service für Klingel-/Überwachungsphase
- OkHttp + handgerollter CalDAV/ICS-Client für Nextcloud; eigene, vom Fork
  `nextcloud/calendar` abgeleitete Nextcloud-App samt REST-API für den
  Schichtplan-Sync (`Nocturn-Kalender/`); OpenWeatherMap für Wetter (Current
  Weather + One Call API 4.0); schulferiendaten.de für Feiertage/Schulferien;
  Tesseract4Android für OCR (inkl. lokal feingetuntem Modell, siehe
  `ocr-training/`)
- **minSdk 33 (Android 13)**, compileSdk/targetSdk 36 — bewusst kein Play-Store-Ziel
- FOSS-Kompatibilität als Leitlinie (offene Lizenzen bevorzugt, closed-source-Dienste
  wie Spotify/Deezer nur optionale Zusatzquellen)
- Release-Signierung + automatisches GitHub Release über GitHub Actions, inkl.
  eigenem Download-Bereich auf [letheapp.de/nocturn.php](https://letheapp.de/nocturn.php)
  und eigenem F-Droid-Repo (`fdroid.letheapp.de`)

Details und offene Fragen siehe [`CLAUDE.md`](CLAUDE.md), Umsetzungsstand siehe
[`PLAN.md`](PLAN.md).
