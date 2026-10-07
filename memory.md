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

Noch keine technischen Entscheidungen/Code — nächster Schritt wäre, mit dem Nutzer die
Architektur (siehe CLAUDE.md "Architektur") konkret festzulegen und erste Screens/Setup
(Gradle-Projekt, Package-Name, Min/Target SDK) aufzusetzen.
