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

## Grobe Umsetzungsreihenfolge

1. Projekt-Setup: Gradle-Grundgerüst, Kotlin + Jetpack Compose + Hilt,
   minSdk = targetSdk = API 33 (Android 13), Package-Name festlegen.
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
11. Kalender-Monatsansicht (aufgeräumtes Layout).
12. Verknüpfung Alarm ↔ Schicht/Rotation.

## Entscheidungen (2026-10-08)

- **minSdk = targetSdk = API 33 (Android 13)**. Keine Play-Store-Veröffentlichung
  geplant, daher keine Rücksicht auf Play-Store-Mindestanforderungen nötig.
- **Hilt** wird ab Phase 1 eingeführt (kostenlos, Apache-2.0/Open Source — passt
  zum Ziel der FOSS-Kompatibilität).
- **FOSS-Kompatibilität**: offene/freie Lizenzen bei Abhängigkeiten bevorzugen,
  closed-source Komponenten (z. B. Spotify-/Deezer-SDKs in späteren Phasen) bleiben
  optionale Zusatzfunktion, keine Kern-Abhängigkeit.
- **Offen:** Package-Name der App (z. B. `de.<domain>.smarterwecker` o. ä.) — noch
  nicht final geklärt.
