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
     Tageskacheln großzügig bemessen (mehr Platz unter der Tageszahl), zeigen bis zu
     4 Punkte sowie den Namen des ersten Eintrags klein darunter.
   - **Freie Termine**: eigenständige Termine (z. B. Kinoticket, Arzttermin) unabhängig
     von Schichten/Rotationen, als eigener Eintragstyp in der Monatsansicht, mit Titel,
     Ort, Notiz und wählbarer **Wichtigkeit** (Niedrig/Normal/Hoch).
   - **Aufgaben (Tasks)**: dritter Eintragstyp neben Termin/Schicht im "+"-Dialog, angelehnt
     an Google Tasks — anders als ein Termin kein fester Zeitraum, sondern ein abhakbarer
     To-Do-Eintrag mit Titel, Details, Fälligkeitstag und optionaler Frist-Uhrzeit
     (Ganztägig-Umschalter wie beim Termin-Formular). Grüner Eintragstyp in der
     Monatsansicht; in der Tagesansicht eigene Karte mit Checkbox zum Abhaken — ist eine
     Aufgabe erledigt, erscheint der Titel durchgestrichen, bleibt aber bis zum manuellen
     Löschen im Kalender sichtbar.
   - **Geburtstage aus Geräte-Kontakten**: Geburtstage der im Gerät gespeicherten Kontakte
     werden automatisch (jährlich wiederkehrend) als eigener Eintragstyp im Kalender
     angezeigt — in der Monatsansicht als farbiger Punkt (lila) plus Name mit Torten-Emoji,
     in der Tagesansicht zusätzlich mit dem **Kontaktbild** (falls vorhanden) und dem
     erreichten Alter (nur wenn der Kontakt ein Geburtsjahr hinterlegt hat). Gelesen über
     `ContactsContract` (Event-Rows vom Typ `TYPE_BIRTHDAY`), braucht die Laufzeit-
     Berechtigung `READ_CONTACTS` (siehe Kernfunktion 10); ohne sie bleiben die Geburtstage
     einfach leer. Kontaktbild wird ohne zusätzliche Bild-Bibliothek direkt per
     `ContentResolver`/`BitmapFactory` geladen (FOSS-Minimalismus). Neues Package `contacts`.
   - **"+"-Button in der Monatsansicht**: fragt ab, ob ein Termin oder eine Schicht
     angelegt werden soll. Als Datum wird der zuvor angetippte Tag vorbelegt (alternativ
     frei wählbar über einen Datums-Dialog). Eine manuell für einen einzelnen Tag
     eingetragene Schicht (Job + Schicht wählen) ergänzt die automatisch aus Rotationen
     fortgeschriebenen Einträge, ohne sie zu ersetzen.
   - **Ferien & gesetzliche Feiertage**: über einen eigenen Einstellungs-Screen (3-Punkte-
     Menü der Kalender-Monatsansicht) wird ein Bundesland gewählt; Feiertage und Schulferien
     werden optional (je einzeln togglebar, **standardmäßig beide deaktiviert**, bewusstes
     Opt-in) farbig in der Monatsansicht angezeigt (rot = Feiertag, grün = Ferien, gleiche
     Farbe wie der Punkt in der Seitenleisten-Legende). Datenquelle: kostenlose, schlüssellose
     **Schulferien-/Feiertage-API von schulferiendaten.de** — liefert sowohl Feiertage als
     auch Schulferien pro Bundesland für 2025–2030; verlangt laut Nutzungsbedingungen eine
     sichtbare Quellenangabe mit Link (siehe Button im Einstellungs-Screen). Ergebnisse werden
     pro Bundesland/Jahr lokal gecacht, Einfügen über `HolidayDao.insertAll` ist
     `OnConflictStrategy.IGNORE` + ein Unique-Index auf (Bundesland, Typ, Start, Ende, Name)
     gegen doppelte Zeilen abgesichert, und `HolidayRepository.ensureYearLoaded` läuft hinter
     einem Mutex — ohne beides konnten zwei fast gleichzeitige Nachlade-Trigger (Speichern im
     Einstellungs-Screen + reaktive Settings-Änderung in der Monatsansicht) denselben
     Jahresbestand doppelt einfügen.
   - **System-Registrierung als Kalender-Ziel für "Zum Kalender hinzufügen"**: NocTurn
     bietet sich über einen Manifest-Intent-Filter (`ACTION_INSERT`/`ACTION_EDIT` auf
     `CalendarContract.Events`) im Android-Auswahlmenü an, wenn eine Fremd-App (z. B.
     Kinoticket-/Buchungs-App) einen "Termin speichern"-Button anbietet. Titel/Ort/Zeit
     werden aus den Intent-Extras vorbefüllt und vor dem Speichern vom Nutzer bestätigt.
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
   - Synchronisierbar mit **Google Calendar** (geplant, noch nicht umgesetzt) und
     **Nextcloud** (CalDAV, umgesetzt): freie Termine bidirektional (Push lokaler
     Änderungen, Pull von Neuanlagen/Änderungen/Löschungen aus Nextcloud), Schichten aus
     Rotationen einseitig NocTurn → Nextcloud (rollendes Zeitfenster, da von der Rotation
     abgeleitet und nicht sinnvoll zurücklesbar). Eigener Screen "Nextcloud-Sync"
     (Server-URL/Benutzername/App-Passwort, Kalender-Auswahl per CalDAV-Discovery,
     Umschalter je Richtung, manueller "Jetzt synchronisieren"-Button), zusätzlich
     periodischer Hintergrund-Sync alle 4 Stunden über WorkManager.

7. **Home-Screen-Widgets:**
   - Uhr-Widget, Kalender-/Termine-Widget und Wetter-Widget für den Android-Homescreen.
   - Wetter-Widget nutzt **OpenWeatherMap** als Datenquelle (gleicher Ansatz wie im
     Iosync-Projekt: API-Key über `local.properties`/CI-Secret `OPENWEATHER_API_KEY`,
     nicht hart codiert) — **ins Uhr-Widget integriert statt eines eigenen dritten Widgets**:
     aktuelle Temperatur + Emoji-Icon (kein zusätzliches Icon-Asset) neben dem nächsten Alarm,
     Standort über Androids Plattform-`LocationManager` (letzte bekannte, ungefähre Position,
     Berechtigung `ACCESS_COARSE_LOCATION`) statt der proprietären Google-Play-Services-
     Standort-Lib, passend zur FOSS-Minimalismus-Leitlinie. 30 Minuten lokal gecacht, damit der
     minütliche Uhr-Tick nicht bei jedem Update einen Netzwerk-Request auslöst.
   - **Manuelle Ortswahl im Einstellungen-Screen**: statt zwingend der automatischen
     `LocationManager`-Position kann der Nutzer im Einstellungen-Screen über ein Suchfeld
     einen festen Ort wählen — Live-Vorschläge während der Eingabe über die
     OpenWeatherMap-Geocoding-API (`/geo/1.0/direct`, derselbe API-Key, kein zusätzliches Abo).
     Ein gewählter Ort hat Vorrang vor der GPS-Ermittlung; ein eigener Button setzt wieder auf
     die automatische Positionserkennung zurück. Der Wetter-Cache wird bei jedem Ortswechsel
     sofort gelöscht, damit die Anzeige nicht noch den alten Ort zeigt.
   - **Wetter ist ein bewusstes Opt-in über einen eigenen Einstellungen-Screen** (3-Punkte-Menü
     der Alarmliste → "Einstellungen", standardmäßig **aus**): ist es aktiviert, erscheint oben
     in der Titelleiste der Alarmliste ein Wettersymbol; Antippen öffnet eine Detailansicht mit
     allem, was der kostenlose OpenWeatherMap-"Current Weather Data"-Endpoint hergibt (Temperatur,
     gefühlte Temperatur, Tageshöchst-/-tiefstwert, Beschreibung, Luftfeuchtigkeit, Wind,
     Luftdruck, Sonnenauf-/-untergang) — bewusst ohne Stundenvorhersage, Wetterkarte, Luftqualität
     oder Pollenwerte, da die dafür nötigen OpenWeatherMap-Endpoints (One Call, Air Pollution)
     ein kostenpflichtiges Abo erfordern. Ist Wetter deaktiviert, bleibt das Symbol ausgeblendet
     und es wird kein Netzwerk-Request ausgelöst; derselbe Schalter steuert auch, ob das
     Uhr-Widget die Temperatur anzeigt (siehe unten).
   - Uhr-Widget-Layout (siehe Referenzbild): transparent über dem Homescreen-Hintergrund,
     von oben nach unten vertikal zentriert gestapelt:
     1. Datum klein/hell (z. B. "Do., 8. Okt.")
     2. Uhrzeit sehr groß (z. B. "00:29")
     3. Wetter (z. B. "☀ 14°") direkt darunter zentriert, auf Höhe der Mitte zwischen Stunde
        und Minute
     4. nächster Alarm klein mit Wecker-Icon davor (z. B. "⏰ Do., 04:47")
     5. darunter schmal die nächsten 2 anstehenden Kalendereinträge (Schichten + freie
        Termine), je Zeile ein farbiger Punkt passend zur Schicht-/Termin-Farbe; stehen am Tag
        des zuletzt gezeigten Eintrags noch weitere an, wird das als "(+N)" angehängt statt
        sie einzeln aufzulisten.
     - **Drei getrennte Tap-Zonen** statt einem einzigen Klick-Ziel fürs ganze Widget: Tippen auf
       Datum/Uhrzeit/nächsten-Alarm öffnet die Alarmliste (Wecker), Tippen auf das Wetter öffnet
       direkt den Wetter-Detail-Screen (`EXTRA_OPEN_WEATHER`, analog zu `EXTRA_OPEN_CALENDAR`),
       Tippen auf die Agenda-Zeilen öffnet direkt die Kalender-Monatsansicht.
   - Kalender-/Termine-Widget: Agenda-Ansicht der nächsten anstehenden Einträge
     (Schichten aus Rotationen + freie Termine), je Zeile farbiger Punkt passend zur
     Schicht-/Termin-Farbe, Tag/Uhrzeit und Bezeichnung. Tippen öffnet die App direkt in
     der Kalender-Monatsansicht.

8. **Ton-Auswahl für Wecker/Alarm:**
   - Neben lokalen/klassischen Wecktönen auch Auswahl aus **Spotify**, **Deezer** und der
     **Lethe-Bibliothek** als Sound-Quelle für den Alarmton.
   - **Lethe-Bibliothek-Tab (umgesetzt)**: NocTurn bekommt dafür bewusst NIEMALS einen vollen
     Lethe-Login-JWT oder Zugriff auf den UMK (Ende-zu-Ende-Verschlüsselungs-Schlüssel) des
     Lethe Messengers, sondern nur einen schmalen, jederzeit widerrufbaren `media:read`-Scope-
     Token (`nm_<token>`, eigener Codepfad/eigene DB-Tabelle `nocturn_media_grants` im
     Lethe-Backend, komplett getrennt vom Login-/Geräte-Authentifizierungs-Flow). Ablauf: Nutzer
     gibt im Lethe-Bibliothek-Tab Telefonnummer+Passwort ein (beweist nur Kontoinhaberschaft,
     wird nicht gespeichert) → Push-Benachrichtigung an das Lethe-Messenger-Host-Gerät → Nutzer
     bestätigt dort mit einem Tap (kein Passwort/Biometrie-Gate nötig, da die Anfrage schon
     passwortgesichert war) → NocTurn pollt den Token und speichert ihn AES/GCM-verschlüsselt
     (Android Keystore, wie beim Nextcloud-App-Passwort). Verwaltung/Widerruf der Kopplung über
     "Bekannte Geräte" im Lethe Messenger. Siehe `/opt/lethe/backend` Architektur-Abschnitt.

9. **UI/UX-Orientierung "Wecker" (Alarm-Bereich):**
   - Grundlegendes Look & Feel orientiert sich bewusst an der **Google-Uhr-App**
     (siehe Referenzbilder), damit sich Nutzer sofort zurechtfinden:
     - **Alarmliste**: eine Karte pro Alarm mit großer Uhrzeit, Wiederholungsmuster als
       Untertitel (z. B. "Jeden Tag"), Ein/Aus-Toggle rechts, farbiger Karten-Akzent,
       FAB ("+") zum Anlegen neuer Alarme. 3-Punkte-Menü in der Titelleiste zur
       Navigation zwischen den Hauptbereichen "Wecker" und "Kalender".
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
      - Aktivitätserkennung (`ACTIVITY_RECOGNITION`) sowie Health-Connect-Zugriff — für
        die Aufsteh-Erkennung (Kernfunktion 1). Bewusst **ohne** `BODY_SENSORS`: die App
        liest keine Körper-Vitaldaten (Herzfrequenz o. Ä.); der Schrittzähler braucht nur
        `ACTIVITY_RECOGNITION`, die Beschleunigungs-/Gyroskop-Auswertung gar keine
        Berechtigung. `BODY_SENSORS` hatte die Aufsteh-Erkennungs-Berechtigung früher
        fälschlich dauerhaft als "nicht erteilt" erscheinen lassen (Gerät ohne
        Körpersensor) und ist daher komplett entfernt.
      - Kontakte (`READ_CONTACTS`) — um Geburtstage der Kontakte im Kalender anzuzeigen
        (Kernfunktion 6).
      - Standort (`ACCESS_COARSE_LOCATION`) — für die Wetteranzeige im Uhr-Widget
        (Kernfunktion 7); nur die letzte bekannte, ungefähre Position wird genutzt.
      - Ignorieren von Akku-Optimierungen — damit die Überwachung nach dem Klingeln
        nicht durch Doze/Battery-Optimizations unterbrochen wird.
    - **Health-Connect-Berechtigung zuverlässig auch auf Android 14+**: neben dem
      `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`-Intent-Filter (für Android 13)
      ist zusätzlich eine `activity-alias` `ViewPermissionUsageActivity` mit
      `VIEW_PERMISSION_USAGE`/Kategorie `HEALTH_PERMISSIONS` im Manifest registriert; ohne
      sie bricht die Berechtigungsvergabe auf Geräten mit ins System integriertem Health
      Connect (Android 14+) still ab ("wird trotz mehrfachem Versuch nicht erteilt").
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
- **minSdk = API 33 (Android 13)** — Abwärtskompatibilität zu Android 13 ist Pflicht und
  bleibt unverändert. **compileSdk = targetSdk = API 36 (Android 16)** (angehoben von
  vormals compileSdk 34 / targetSdk 33), damit die App auf aktuellen Geräten
  (Android 14/15/16/17) ohne Kompatibilitätsmodus läuft. Höher (API 37 / Android 17)
  würde den kompletten Toolchain-Sprung auf AGP 9 + Gradle 9 + Kotlin 2.2 + KSP2 (statt
  AGP 8.9.1 / Gradle 8.11.1 / Kotlin 2.0.21 / KSP1) erzwingen und ist daher bewusst
  (noch) nicht erfolgt — funktional für Health Connect & Co. identisch. Play-Store-
  Veröffentlichung ist explizit kein Ziel, daher keine Rücksicht auf Play-Store-Vorgaben
  (z. B. aktueller targetSdk) nötig.
- **FOSS-Kompatibilität als Leitlinie**: Bei Abhängigkeiten werden offene/freie
  Lizenzen bevorzugt, proprietäre/closed-source Komponenten möglichst vermieden.
  Spotify-/Deezer-SDKs (closed-source, Streaming-Anbieter) bleiben rein optionale
  Zusatz-Tonquellen, keine Kern-Abhängigkeit der App.
- Health Connect: offizielle Android Health Connect API.
- Bewegungserkennung ohne Health Connect: `SensorManager` mit `TYPE_ACCELEROMETER` /
  `TYPE_GYROSCOPE`, ggf. Schrittzähler-Sensor `TYPE_STEP_COUNTER`/`TYPE_STEP_DETECTOR`.
- Alarm-Zuverlässigkeit: `AlarmManager` (exact alarms) + Foreground Service während der
  Überwachungsphase (Sensorik darf nicht durch Doze/Battery-Optimizations gekillt werden).
- Kalendersync: Google Calendar API (OAuth, geplant) + eigener, schlanker CalDAV-Client für
  Nextcloud (umgesetzt, Package `sync`) — bewusst ohne dav4jvm/biweekly o. Ä., sondern mit
  OkHttp (WebDAV PROPFIND/REPORT/PUT/DELETE) und handgerolltem ICS-Reader/Writer (RFC-5545-
  Subset), passend zur FOSS-Minimalismus-Leitlinie bei einer klar begrenzten Teilmenge des
  Formats. Nextcloud-App-Passwort AES/GCM-verschlüsselt mit einem Android-Keystore-Schlüssel
  abgelegt (keine zusätzliche Security-Crypto-Abhängigkeit nötig). Periodischer
  Hintergrund-Sync über `androidx.work` (Hilt-WorkManager-Integration).
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
- Home-Screen-Widgets (Kernfunktion 7): klassische `AppWidgetProvider`/`RemoteViews`
  statt Jetpack Glance, um keine zusätzliche Abhängigkeit einzuführen (FOSS-Minimalismus-
  Leitlinie) — für zwei einfache, überwiegend statische Widgets reicht das. Uhr-Widget
  stößt sich minütlich per `AlarmManager.setRepeating` selbst neu an (offizielle
  `updatePeriodMillis`-Updates sind auf 30-Minuten-Minimum begrenzt); "nächster Alarm"
  kommt direkt von `AlarmManager.getNextAlarmClock()` statt aus der eigenen DB, da
  `AlarmScheduler` Alarme bereits über `setAlarmClock` plant. Kalender-Widget liest
  `CalendarRepository.getUpcomingAgenda(...)` per Hilt-`@AndroidEntryPoint` auf dem
  `AppWidgetProvider` (gleiches Pattern wie bei `BootReceiver`/`AlarmReceiver`, da
  `AppWidgetProvider` selbst ein `BroadcastReceiver` ist). Uhr-Widget nutzt denselben
  Hilt-`@AndroidEntryPoint`-Ansatz, um zusätzlich `WeatherRepository.currentWeather()`
  (neues Package `weather`, OpenWeatherMap über OkHttp + `org.json`, Standort über
  Androids `LocationManager` statt proprietärer Play-Services-Standort-Lib, 30 Minuten
  SharedPreferences-Cache) und `CalendarRepository.getUpcomingAgendaForWidget(2)`
  (nächste 2 Einträge + "+N" für weitere Einträge am selben Tag) einzublenden.
- Ferien/Feiertage (Kernfunktion 6, neues Package `holidays`): Datenquelle ist die
  kostenlose, schlüssellose **Schulferien-/Feiertage-API von schulferiendaten.de**
  (`https://schulferiendaten.de/api/v1/`) — liefert sowohl gesetzliche Feiertage
  (`/feiertage/{bundesland}/{jahr}.json`) als auch Schulferien
  (`/ferien/{bundesland}/{jahr}.json`) pro Bundesland (zweistelliger Kleinbuchstaben-Code,
  z. B. `nw`), Datenjahre 2025–2030. Nutzungsbedingungen verlangen eine sichtbare
  Quellenangabe mit Link zu schulferiendaten.de (im Einstellungs-Screen umgesetzt).
  Anfragen über das bereits vorhandene OkHttp + Androids eingebautes `org.json` (keine neue
  JSON-Lib-Abhängigkeit). Ergebnisse werden pro Bundesland/Jahr lokal in Room gecacht, damit
  der Kalender auch offline funktioniert und nicht bei jedem Öffnen neu angefragt wird.
- Lethe-Bibliothek als Weckton-Quelle (Kernfunktion 8, neues Package `sound/lethe`): eigener
  `media:read`-Scope statt des Lethe-Login-JWT/UMK, umgesetzt als neue, vom Login komplett
  getrennte Tabelle `nocturn_media_grants` + Routen `/nocturn/media/pair/*` und
  `/nocturn/media/{library,stream/{id}}` im Lethe-Backend (`/opt/letheapp/Debian Server/main.py`)
  sowie `/nocturn/media/grants` zur Verwaltung/zum Widerruf. Freigabe-Push über denselben
  WS/FCM-Mechanismus wie die bestehende Geräte-Authentifizierung (`_create_device_auth_request`),
  aber eigener Push-Typ `nocturn_media_grant_request` und eigener Freigabe-Screen im Lethe
  Messenger (`NocturnMediaGrantScreen.kt`) ohne Passwort-/Biometrie-Gate (die Anfrage selbst ist
  bereits passwortgesichert). Stream-Endpoint prüft den Scope bei jedem Request neu (sofortiger
  Effekt bei Widerruf), statt den offenen `/uploads`-Static-Mount zu nutzen; Token zusätzlich als
  `?token=`-Query-Parameter akzeptiert, da Androids `MediaPlayer` beim Abspielen einer reinen URI
  keine eigenen Header mitsenden kann. NocTurn speichert den Scope-Token AES/GCM-verschlüsselt
  mit Android-Keystore-Schlüssel (`LetheMediaCredentialsStore`, gleiches Muster wie das
  Nextcloud-App-Passwort), ohne das Lethe-Passwort selbst je zu speichern.
- **Release-Signierung**: eigener PKCS12-Keystore unter `keystore/release.keystore.p12`
  (gitignored, niemals im Repo), Firma/Inhaber im Zertifikat hinterlegt (CN=NocTurn,
  O=letheapp.de, OU=Dennis Bassy). Pfad/Passwörter werden analog zum
  `OPENWEATHER_API_KEY`-Handling gelesen: lokal aus `local.properties`
  (`RELEASE_KEYSTORE_PATH`/`RELEASE_KEYSTORE_PASSWORD`/`RELEASE_KEY_ALIAS`/
  `RELEASE_KEY_PASSWORD`), im CI aus gleichnamigen GitHub Secrets (Keystore selbst als
  `RELEASE_KEYSTORE_BASE64`-Secret, wird im Workflow vor dem Release-Build dekodiert).
  `app/build.gradle.kts` signiert `release` nur, wenn ein Keystore-Pfad gefunden wird —
  PR-Builds ohne Secrets bleiben unsigniert. Der GitHub-Actions-Workflow
  (`build-apk.yml`) veröffentlicht bei Push auf `main`/`workflow_dispatch` zusätzlich ein
  echtes GitHub Release (Tag `v<versionName>-<run_number>`) mit der signierten APK als
  Anhang.
- **Eigener Download-Bereich auf letheapp.de** (`nocturn.php` im DocumentRoot der echten
  Lethe-Produktivseite auf dem Hauptserver `212.227.21.113`, **`/var/www/Lethe`** — nicht das
  gleichnamige, aber ungenutzte `/var/www/letheapp.de`/`/var/www/html`, siehe
  `/etc/apache2/sites-enabled/letheapp.de-le-ssl.conf`): Vorstellung der Kernfunktionen +
  Download-Button für die neueste APK, eigener Nav-Eintrag "NocTurn" in `includes/header.php`
  und `includes/footer.php` (inkl. `nav.nocturn`/`footer.nocturn` in `includes/lang/de.php` +
  `en.php`), Download-Zähler über `download-track.php?type=nocturn_apk` (gleiches Muster wie
  `messenger_apk`). Da `SmarterWecker` ein privates Repo ist (anders als `Letheapp`), liest
  `getLatestNocturnApk()` in `includes/config.php` die neueste Version nicht über die
  GitHub-Releases-API, sondern direkt aus dem lokal deployten Ordner
  `/var/www/Lethe/downloads/nocturn/NocTurn-*.apk` (neuestes `filemtime`). Zugang zum
  Hauptserver per `sshpass`/root-Passwort (siehe `/opt/letheapp/failover.sh` für das gleiche
  Muster), kein SSH-Key hinterlegt. `scripts/release-deploy.sh` erhöht die Versionsnummer,
  committet/pusht, wartet auf den GitHub-Actions-Build/-Release und lädt die signierte APK
  per `scp`/`sshpass` direkt nach `/var/www/Lethe/downloads/nocturn/` auf den Hauptserver hoch
  — der `git push` nutzt dafür einen expliziten `Authorization: Basic`-Header aus `GH_TOKEN`
  statt der Standard-Credential-Erkennung, da Letztere auf diesem Pi nicht zuverlässig griff.

## Wichtig

- Projekt-Memory liegt in `/opt/SmarterWecker/memory.md` — vor Arbeit an diesem Projekt
  immer zuerst dorthin wechseln und diese Datei lesen.
- Ausgewählt im Chat-Dropdown unter `/var/www` als "Smarter Wecker".
- Phase 1 (siehe PLAN.md) ist umgesetzt (Code vorhanden); neue Konzept-Erweiterungen
  werden weiterhin zuerst hier dokumentiert, bevor sie implementiert werden.
- **Commit & Push nur auf ausdrückliche Anweisung**: Code-Änderungen lokal umsetzen
  (inkl. Dokumentation in CLAUDE.md/PLAN.md/memory.md), aber `git commit`/`git push`
  erst ausführen, wenn der Nutzer das explizit in der jeweiligen Anfrage sagt — nicht
  automatisch nach jeder Implementierung wie bisher üblich.
- **Immer auf Deutsch antworten**: Alle Chat-Antworten in diesem Projekt auf Deutsch
  verfassen, unabhängig von der Sprache der Anfrage.
