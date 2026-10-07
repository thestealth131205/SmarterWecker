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

Noch keine technischen Entscheidungen/Code — nächster Schritt wäre, mit dem Nutzer die
Architektur (siehe CLAUDE.md "Architektur") konkret festzulegen und erste Screens/Setup
(Gradle-Projekt, Package-Name, Min/Target SDK) aufzusetzen.
