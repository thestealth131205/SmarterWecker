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
