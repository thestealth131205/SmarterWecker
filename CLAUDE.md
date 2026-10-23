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
   - **Zufälliger Ton bei bewegungslosem Schlummern**: Während der gesamten Schlummer-Dauer
     überwacht `AlarmRingingService` per Sensorik durchgehend auf anhaltende Bewegung (der
     Dienst bleibt dafür mit einer leisen "Schlummert bis..."-Benachrichtigung am Leben statt
     sich zu beenden). Bleibt es komplett bewegungslos, ignoriert das nächste Klingeln den
     eigentlich konfigurierten Weckton und wählt stattdessen zufällig einen anderen aus der
     Töne-Liste — wer schon einmal verschlafen hat, soll sich nicht auch noch an einen zweiten,
     unveränderten Weckversuch gewöhnen können.

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
   - Tonart ändert sich fortlaufend/eskaliert weiter — zusätzlich zur schrittweisen
     Lautstärke-/Tonhöhen-Eskalation (`EmergencyAlarmService`, alle 30 s) wird der Ton nicht
     endlos in einer Schleife wiederholt, sondern läuft einmal komplett durch und wechselt
     alle zwei solcher Durchläufe zufällig zu einem anderen Ton aus der Töne-Liste, damit man
     sich auch beim Notfall-Alarm nicht an einen einzelnen Sound gewöhnen kann.
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
   - **Automatischer "Nächste Schicht"-Wecker (optionales Schicht-Feld)**: Im Schicht-
     anlegen-Screen lässt sich zusätzlich, unabhängig vom obigen Minuten-Abstand-Paar,
     eine feste Uhrzeit hinterlegen, bis zu der bei dieser Schicht spätestens aufgestanden
     sein muss — ein bewusstes Opt-in per Schicht (Default: aus). Ist sie gesetzt und die
     Schicht über eine Rotation oder manuell im Kalender eingetragen, zeigt NocTurn im
     Wecker-Screen automatisch einen Alarm mit dieser Uhrzeit für die jeweils nächste
     anstehende Schicht an — ganz ohne dass der Nutzer dafür wie in Kernfunktion 12 manuell
     einen Alarm anlegen und mit einer Rotation verknüpfen muss. Der Alarm wird bei jeder
     Kalenderänderung (Rotation zugewiesen, Schicht gespeichert/gelöscht, Einzel-Schicht
     eingetragen) sowie beim App-Start automatisch auf die jeweils nächste passende Schicht
     aktualisiert (`ShiftWakeupAlarmSyncer`).
   - **Vorprüfung vor dem automatischen Klingeln**: `AlarmScheduler` plant für einen solchen
     automatischen Schicht-Wecker zusätzlich 2 Minuten vor der Weckzeit einen
     `PreAlarmMovementCheckService` (Foreground Service, gestartet über `PreAlarmCheckReceiver`),
     der alle 20 Sekunden für 3 Sekunden per Gyroskop/Beschleunigungssensor prüft, ob der Nutzer
     bereits aufgestanden ist (kürzere Sustained-Schwelle als beim Stop-Tasten-Check, da das
     Prüffenster selbst nur 3 s lang ist). Wird Bewegung bestätigt, sagt er den eigentlichen Alarm
     ab (`AlarmScheduler.cancel`) und zeigt statt des Klingelns eine "Wecken geskippt"-
     Benachrichtigung; bleibt es bis zur Weckzeit ohne Bewegung, klingelt der Alarm normal.
   - **Kein Freifahrtschein durch bloßes Lösen des Notfall-Puzzles**: Wird bei einem solchen
     automatischen Schicht-Wecker das Notfall-Puzzle gelöst, OHNE dass `PostAlarmMonitoringService`
     zuvor echte Bewegung bestätigt hat, plant `AutomaticShiftRetryScheduler` ein Wieder-Klingeln zu
     einem zufälligen späteren Zeitpunkt innerhalb des an der Schicht konfigurierten Fensters
     ("Frühestes Wecken" bis "Spätestens aufstehen", spätestens aber der Schichtbeginn selbst).
     `AutoShiftRetryService` (ausgelöst über `AutoShiftRetryReceiver`) prüft davor noch einmal kurz
     per Sensorik, ob der Nutzer inzwischen doch aufgestanden ist — falls ja, nur eine
     Benachrichtigung statt erneutem Klingeln, falls nein klingelt derselbe Alarm erneut, als wäre
     es das erste Mal (inkl. eigener Stop-Bewegungsprüfung und ggf. erneutem Notfall-Alarm). Ist das
     Fenster bereits vorbei, entfällt das Wieder-Klingeln und das gelöste Puzzle beendet den Alarm
     wie gewohnt endgültig. Dafür merkt sich `AlarmEntity.autoShiftWakeupShiftId` die Quell-Schicht
     des automatischen Weckers (Schema-Version 13, echte Migration in `DatabaseModule.kt`).
   - **Manuell angelegter Alarm optional auf eine einzelne Schicht/einen Wochentag einengen**: Die
     "Mit Schicht/Stundenplan verknüpfen"-Auswahl im Alarm-Bearbeiten-Screen koppelt einen Alarm
     standardmäßig an *jede* Schicht einer Rotation bzw. *jeden* Unterrichtstag eines Stundenplan-
     Jobs (erste Stunde des Tages). Hat die gewählte Rotation mehr als eine im Muster verwendete
     Schicht (bzw. der Stundenplan-Job Unterricht an mehr als einem Wochentag), fragt ein zweiter
     Dialog direkt danach, ob stattdessen auf eine einzelne Schicht (z. B. nur "Frühschicht") bzw.
     einen einzelnen Wochentag eingeengt werden soll — `AlarmEntity.linkedShiftId`/
     `linkedTimetableWeekday` (Schema-Version 14, echte Migration in `DatabaseModule.kt`),
     ausgewertet in `WakeWindowResolver`.

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
       Wochentags-Spalten), pro Tag werden per Mehrfachauswahl **eine oder mehrere
       Schichten gleichzeitig** zugewiesen (z. B. Frühschicht + ein zusätzlicher Termin
       wie "Kinder-Wochenende") — im Kalender werden sie an diesem Tag untereinander
       dargestellt, genau wie andere mehrfache Tageseinträge. Eine Rotation ist dabei
       eine **reine Vorlage ohne eigenes Startdatum** — das Muster selbst sagt nur, wie
       die Tage aufeinander folgen, nicht ab wann.
   - **Geführter "Rotation anlegen"-Ablauf** aus dem "+"-Dialog der Monats-/Tagesansicht
     (Eintragstyp "Rotation"): ein `AlertDialog` mit einheitlichem Titel „Rotation anlegen"
     fragt zunächst nach einem bestehenden Job oder legt direkt einen neuen an (Name +
     Schichtarbeit/Schüler-Student). Bei einem Schichtarbeit-Job folgt danach **immer** erst
     der `ShiftSetupScreen` — eine schlanke Liste der Schichten dieses Jobs mit "+"-FAB, deren
     Topbar-Aktion je nach Zustand „Überspringen" (noch keine Schicht angelegt) oder „Weiter"
     zeigt — bevor es zum eigentlichen Rotationsraster im `RotationEditorScreen` weitergeht.
     Ein Schüler/Student-Job überspringt diesen Zwischenschritt (führt direkt zum
     Stundenplan-Tab in `JobDetailScreen`), da dort Unterrichtsstunden ohnehin inline verwaltet
     werden. Die Schnellauswahl-Chips beim Schicht-Anlegen (`ShiftEditDialog`) heißen
     Urlaub/**Freischicht**/Früh/Nacht/Spät — bewusst "Freischicht" statt "Frei", da ein Tag
     ohne zugewiesene Schicht im Rotationsraster bereits "Frei" heißt und eine gleichnamige
     Schicht sonst mit dem leeren Tag verwechselbar wäre. Speichert der Nutzer die neu
     angelegte Rotation im `RotationEditorScreen` ab (Haken in der Topbar oder "Rotation
     speichern"-Button), springt die App direkt zur Kalender-Monatsansicht zurück statt nur
     eine einzelne Ebene des Assistenten (`ShiftSetupScreen`) zu schließen — der komplette
     "+"-Ablauf gilt als eine zusammenhängende Aufgabe. Beim Bearbeiten einer bestehenden
     Rotation aus der Job-Verwaltung heraus (`JobDetailScreen` → Rotationen-Tab) bleibt das
     Speichern dagegen ein einfaches Zurück zur Job-Detailansicht, da dort kein mehrstufiger
     Assistent vorausgeht.
   - Eine fertige Rotations-Vorlage wird erst über die ausklappbare Seitenleiste der
     Kalender-Monatsansicht tatsächlich in den Kalender eingetragen: Rotation wählen →
     Starttag im Kalender antippen → in einem schwebenden Fenster den Zeitraum festlegen
     (1/3/6 Monate, 1/2 Jahre). Dieselbe Vorlage lässt sich so beliebig oft, an
     unterschiedlichen Starttagen und mit unterschiedlicher Länge einfügen — z. B. um sie
     nach einer Dienstplanänderung ab einem neuen Starttag erneut anzuwenden, ohne die
     Vorlage selbst neu anlegen zu müssen.
   - **Rotation wieder aus dem Kalender entfernen**: Dieselbe Seitenleiste bietet zusätzlich
     einen eigenen Abschnitt "Rotation aus Kalender entfernen" — Auswahl einer Rotation löscht
     dort (nach Bestätigungsdialog) **alle** ihre bisher eingetragenen Kalendertage, unabhängig
     davon, über wie viele einzelne Zuweisungen/Zeiträume sie entstanden sind (es gibt kein
     Tracking einzelner Zuweisungen, nur die `rotationId` an jedem Kalendereintrag). Die
     Rotations-**Vorlage** selbst bleibt dabei erhalten und lässt sich danach erneut zuweisen —
     anders als das Löschen der Vorlage selbst (löscht über Kaskade automatisch auch alle
     zugehörigen Kalendereinträge). Die Vorlage selbst löschen lässt sich direkt im Abschnitt
     "Rotation anwenden" derselben Seitenleiste per langem Gedrückthalten eines Rotations-
     Eintrags — es erscheint ein rotes Löschen-Icon neben dem Eintrag, das nach Bestätigung
     die Rotation inklusive aller ihrer Kalendereinträge endgültig entfernt.
   - **Phasen-Rückfrage bei mehrdeutigem Starttag**: Das Rotations-Raster im Editor nimmt
     Tag-Index 0 implizit als Montag an (Tag-Index mod 7 = Wochentag). Tippt der Nutzer in
     der Seitenleiste einen Starttag an, dessen Wochentag bei einer Zykluslänge über einer
     Woche mehrfach im Muster vorkommt (z. B. Samstag bei einem 84-Tage-Zyklus), lässt sich
     der gemeinte Tag im Muster nicht allein aus dem Datum ableiten. In diesem Fall fragt die
     App vor dem Zeitraum-Dialog per Chip-Auswahl (alle im Muster verwendeten Schichten +
     „Frei") zwei Schichten ab — die am Starttag selbst und die am darauffolgenden Freitag —
     und ermittelt daraus den tatsächlichen Tag-Index, ab dem die Rotation im gewählten
     Zeitraum fortgeschrieben wird. Ist der Wochentag im Muster eindeutig (Zykluslänge ≤ 7
     Tage oder nur ein Kandidat), entfällt die Rückfrage automatisch.
   - **Fallback bei unregelmäßig/selten vorkommenden Schichten**: Reicht die Antwort zur
     Schicht am Starttag allein nicht aus (mehrere Kandidaten bleiben übrig) UND würde auch
     die Schicht am nächsten Freitag diese Kandidaten nicht zuverlässig unterscheiden — z. B.
     weil eine seltene Zusatzschicht wie "Kinder-Wochenende" an nicht wochentaktisch
     wiederkehrenden Tag-Indizes im Muster auftaucht — fragt die App statt des Freitags-Tages
     das konkrete Kalenderdatum ab, an dem diese Schicht das nächste Mal vorkommt, und wählt
     darüber den passenden Kandidaten. Erkennung über
     `CalendarRepository.needsNextOccurrenceDateQuery`; welche Schicht dabei konkret erfragt
     wird, ermittelt `findDistinguishingShiftForOccurrenceQuery` — nicht zwingend die am
     Starttag genannte Schicht, sondern diejenige im Muster, deren nächstes Vorkommen die
     verbleibenden Kandidaten tatsächlich eindeutig unterscheidet (bevorzugt die seltenste,
     z. B. "Kinder-Wochenende" statt der häufigen Frühschicht) — Auflösung über
     `resolveRotationPhaseByNextOccurrence`. Die Datums-Rückfrage selbst
     (`RotationNextOccurrenceDateDialog`) nennt dem Nutzer explizit, nach welcher Schicht
     gefragt wird ("Wann ist deine nächste Schicht „…“?"), als `title`-Slot von Compose'
     `DatePicker` statt als zusätzliches Geschwister-Element — Letzteres wurde von der festen
     Dialoghöhe von `DatePicker` weggequetscht und blieb dadurch für den Nutzer unsichtbar.
   - Monatsansicht zeigt pro Tag die zugeordnete(n) Schicht(en) farbig markiert;
     mehrere Einträge pro Tag möglich (z. B. Schicht + zusätzlicher Termin/Urlaub).
     Tageskacheln großzügig bemessen (mehr Platz unter der Tageszahl), zeigen bis zu
     4 Punkte sowie den Namen des ersten Eintrags klein darunter.
   - **Freie Termine**: eigenständige Termine (z. B. Kinoticket, Arzttermin) unabhängig
     von Schichten/Rotationen, als eigener Eintragstyp in der Monatsansicht, mit Titel,
     Ort, Notiz und wählbarer **Wichtigkeit** (Niedrig/Normal/Hoch). Zusätzlich **Anzeigen
     als** (Beschäftigt/Frei, Äquivalent zum iCalendar-Feld `TRANSP`) — wird beim Nextcloud-
     CalDAV-Sync mitgesynct (`OPAQUE`/`TRANSPARENT`) und bestimmt künftig beim Kalender-
     Teilen, ob der Zeitraum für andere als belegt sichtbar ist. Außerdem **Anhänge**
     (`AppointmentAttachmentEntity`): beliebige Dateien über den System-Dateiauswahldialog
     hinzufügbar, nur die `content://`-URI wird dauerhaft referenziert
     (`takePersistableUriPermission`, keine Kopie der Datei), antippen in der Tagesansicht
     öffnet den Anhang über die registrierte Fremd-App.
   - **Mehrere Erinnerungen pro Termin** (`AppointmentReminderEntity`, Schema-Version 20, echte
     Migration in `DatabaseModule.kt` löst das bisherige einzelne `reminderMinutesBefore`-Feld ab):
     im Termin-Formular per "+" beliebig viele Erinnerungen hinzufügbar, je mit Voreinstellung
     (30 Min./1 Std./1 Tag/2 Tage) oder frei wählbarer Anzahl + Einheit (Minuten/Stunden/Tage).
     Jede Erinnerung ist entweder eine normale **Benachrichtigung** oder eine **E-Mail** —
     Letztere nutzt bewusst Android-Boardmittel (`ACTION_SEND`/`ACTION_SENDTO`) statt eigener
     SMTP-Zugangsdaten in NocTurn: ist VectoMail installiert, wird direkt dorthin adressiert
     (`EmailReminderSender`), sonst öffnet sich die normale App-Auswahl für installierte
     Mail-Apps. Empfänger-Autocomplete über die Geräte-Kontakte (`ContactEmailRepository`,
     `READ_CONTACTS`-Berechtigung bereits vorhanden). E-Mail-Text enthält einen Hinweis-Footer
     mit Link zu VectoMail — ein eigenes Logo-Bild/eine eigene Landingpage hat VectoMail noch
     nicht (Email-Projekt in früher Build-Phase), daher vorerst Link auf letheapp.de statt
     einer erfundenen VectoMail-URL oder eines Platzhalter-Logos.
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
   - **ICS-Import externer Kalender**: Menüpunkt "ICS importieren" im 3-Punkte-Menü der
     Kalender-Monatsansicht öffnet den System-Dateiauswahldialog (Android Storage Access
     Framework); nach Auswahl einer `.ics`-Datei fragt ein Dialog Name (vorausgefüllt mit dem
     Dateinamen) und Farbe ab, danach werden alle enthaltenen VEVENT-Blöcke einmalig als neuer
     externer Kalender importiert (`IcsImportRepository`, Tabellen `imported_calendars`/
     `imported_calendar_events`, Schema-Version 18, echte Migration in `DatabaseModule.kt`).
     Wiederholungsregeln (RRULE) werden bewusst nicht expandiert — importiert wird nur das
     jeweils einzelne VEVENT-Vorkommen (kein eigener RRULE-Expander, FOSS-Minimalismus). Jeder
     importierte Kalender erscheint in der Kalender-Seitenleiste (siehe unten) mit einem
     farbigen Kästchen zum Ein-/Ausschalten der Anzeige sowie Löschen per langem Gedrückthalten
     (inkl. aller seiner importierten Events); die Events selbst erscheinen dann wie Termine
     sowohl in der Monatsansicht als auch in der Tagesansicht.
   - **Kästchen statt Punkt in der Kalender-Seitenleiste**: Die Legende für Feiertage/Ferien
     sowie importierte ICS-Kalender zeigt statt eines reinen Farbpunkts ein mit der jeweiligen
     Farbe gefülltes Kästchen (`ColorCheckboxRow`). Antippen der ganzen Zeile schaltet die
     Anzeige im Kalender direkt um (Kästchen wird leer, Inhalt verschwindet aus Monats- und
     Tagesansicht) — ohne dass dafür wie bisher zwingend der separate Ferien/Feiertage-
     Einstellungs-Screen geöffnet werden muss (`HolidayRepository.setHolidayVisibility`, ändert
     nur die Sichtbarkeits-Flags, nicht das gewählte Bundesland).
   - **Brief-/Terminzettel-Foto-Import**: eigenes Kamera-Symbol in der Topbar der
     Kalender-Monatsansicht (zwischen "Kalender teilen" und dem 3-Punkte-Menü) öffnet sofort
     die Kamera; danach lokale OCR über dieselbe Tesseract4Android-Engine wie der
     Stundenplan-Foto-Import (Kernfunktion 5) und ein deterministisches, LLM-freies
     Regex-/Heuristik-Regelwerk (`AppointmentTextParser`), das Datum (numerisch, ausgeschriebene
     Monate, relative Tage/Wochentage), Uhrzeit (Einzelzeit, Zeitspanne, Dauer-Angabe),
     Betreff/Titel (Betreff-Zeile oder typische Termin-Substantive), Ort (PLZ+Ort plus
     vorangehende Straßenzeile) und Hinweise ("Bitte mitbringen" o. Ä.) aus dem erkannten Text
     extrahiert. Bei mehreren gefundenen Datumsangaben (z. B. Brief-Ausstellungsdatum vs.
     eigentlicher Termin) entscheidet ein einfaches Scoring (Nähe zu Termin-Schlüsselwörtern,
     Zukunft-Bonus/Vergangenheit-Malus, Nähe zu einer Uhrzeit). Das Ergebnis füllt direkt die
     normale Termin-Kontrollmaske (`AddAppointmentScreen`) vor — nichts wird automatisch
     gespeichert, der Nutzer bestätigt oder korrigiert immer erst per Hand.
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
   - **Mehrere Wetter-Orte, per Wischen durchblätterbar**: Statt eines einzigen Orts verwaltet
     ein eigener **Orte-Screen** (3-Punkte-Menü des Wetter-Screens → "Ort") eine Liste von Orten.
     Der **erste Eintrag ist immer der aktive Hauptort** (Name = manuell gewählter Ort oder
     "Automatisch") — Antippen öffnet den bisherigen Hauptort-Einstellungs-Screen (Suchfeld mit
     Live-Vorschlägen über die OpenWeatherMap-Geocoding-API `/geo/1.0/direct`, derselbe API-Key,
     kein zusätzliches Abo; ein gewählter Ort hat Vorrang vor der GPS-Ermittlung, ein eigener
     Button setzt wieder auf automatische Positionserkennung zurück; der Wetter-Cache wird bei
     jedem Ortswechsel sofort gelöscht). Darunter folgen frei benannte **zusätzliche Orte**
     (`AppPreferences.additionalLocations`, feste Koordinaten statt Geräte-Standort), über ein
     "+" oberhalb der unteren Tab-Leiste hinzufügbar, per Lösch-Icon entfernbar. Im Wetter-Screen
     selbst lassen sich alle angelegten Orte per Links/Rechts-Wischen durchblättern
     (`HorizontalPager`, Hauptort immer an erster Stelle) — jeder Ort wird erst beim ersten
     Anzeigen geladen und danach separat im `WeatherViewModel` zwischengespeichert, inkl.
     eigenem Cache pro Ort in `WeatherRepository` (Cache-Schlüssel um die jeweilige Orts-ID
     ergänzt, `MAIN_LOCATION_ID` für den Hauptort). Das **Uhr-Widget fragt beim Hinzufügen**,
     welcher Ort gezeigt werden soll, falls mehr als ein Ort angelegt ist
     (`ClockWidgetConfigureActivity`, Android-`android:configure`-Mechanismus) — gibt es nur den
     Hauptort, entfällt die Rückfrage automatisch (die Konfigurationsaktivität schließt sich
     dann sofort unsichtbar selbst mit der Hauptort-Auswahl).
   - **"Standort bei jeder Aktualisierung neu ermitteln"-Schalter** (Hauptort-Einstellungs-
     Screen, standardmäßig aus, nur sichtbar ohne manuell gewählten Hauptort): fordert statt der
     zuletzt vom System zwischengespeicherten Position (`LocationManager.getLastKnownLocation`,
     kann je nach Gerät veraltet sein) bei jeder Wetter-Aktualisierung aktiv eine frische
     Position an (`LocationManager.getCurrentLocation`, API 30+, seit minSdk 33 immer verfügbar)
     — mit COARSE-tauglichen Providern (Netzwerk vor GPS) und 8-Sekunden-Timeout, danach
     Fallback auf die letzte bekannte Position. Gilt nur für den Hauptort, nicht für zusätzliche
     Orte (die haben feste Koordinaten).
   - **Wetter ist ein bewusstes Opt-in über das 3-Punkte-Menü des Wetter-Screens** (Eintrag
     "Wetter" mit Schalter, standardmäßig **aus** — zusammen mit "Ort" von dort aus dem
     ursprünglichen Wecker-Einstellungen-Screen in den Wetter-Screen selbst verschoben): ist es
     aktiviert, erscheint oben in der Titelleiste der Alarmliste ein Wettersymbol; Antippen
     öffnet den **Wetter-Screen** (kein Dialog) mit allem, was der kostenlose
     OpenWeatherMap-"Current
     Weather Data"-Endpoint hergibt (Temperatur, gefühlte Temperatur, Tageshöchst-/-tiefstwert,
     Beschreibung, Luftfeuchtigkeit, Wind, Luftdruck, Sonnenauf-/-untergang), darunter eine
     **8-Tage-Vorhersage** (heute + 7 Tage: Min/Max-Temperatur, Symbol, Beschreibung,
     Niederschlagswahrscheinlichkeit) über den OpenWeatherMap-"One Call API 4.0"-Endpoint
     (`/data/4.0/onecall/timeline/1day`, "1-day timeline" statt des alten kombinierten
     `/onecall`-Response). OpenWeatherMap hat die vormalige "One Call API 3.0" im Herbst 2026
     durch 4.0 ersetzt — 3.0-Zugriffsschlüssel liefern seitdem `401`, ebenso 4.0, solange im
     OpenWeatherMap-Konto nicht das "One Call by Call"-Abo (= 4.0) aktiviert ist; altes 3.0-Abo
     und neues 4.0-Abo sind getrennte, sich gegenseitig ausschließende Freischaltungen. One Call
     4.0 ist weiterhin im kostenlosen Kontingent enthalten (1.000 Abrufe/Tag), verlangt aber wie
     zuvor 3.0 auf openweathermap.org einmalig eine Abo-Aktivierung mit Zahlungsmethode (keine
     Belastung innerhalb des Kontingents) — ohne diese Aktivierung bleibt die 8-Tage-Vorhersage
     leer, während aktuelles Wetter/Uhr-Widget (reiner "Current Weather Data"-Endpoint) unberührt
     weiterlaufen. Bewusst weiterhin ohne Wetterkarte, Luftqualität oder Pollenwerte (eigene,
     hier nicht benötigte Endpoints). Ist Wetter deaktiviert, bleibt das Symbol ausgeblendet und
     es wird kein Netzwerk-Request ausgelöst; derselbe Schalter steuert auch, ob das Uhr-Widget
     die Temperatur anzeigt (siehe unten). Tippen auf die Wetter-Tap-Zone des Uhr-Widgets öffnet
     den Wetter-Screen direkt als Startziel der App (`EXTRA_OPEN_WEATHER`).
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
     (Schichten aus Rotationen + freie Termine + Geburtstage aus den Geräte-Kontakten), je
     Zeile farbiger Punkt passend zur Schicht-/Termin-/Geburtstags-Farbe, Tag/Uhrzeit und
     Bezeichnung. Tippen öffnet die App direkt in der Kalender-Monatsansicht. Geburtstage
     fließen direkt in `CalendarRepository.buildAgenda()` ein (gemeinsame Grundlage für
     dieses Widget und die Agenda-Zeilen im Uhr-Widget), daher profitiert auch Letzteres
     automatisch mit.
   - **Untere Tab-Leiste** (`NocturnBottomBar`, Wecker/Wetter/Kalender): zusätzlich zum Tippen
     lässt sich per Wischen direkt auf der Leiste selbst zum vorherigen/nächsten Tab wechseln
     — bewusst nur auf der Leiste, damit Wischen im jeweiligen Bildschirminhalt (z. B. der
     Monatswechsel in der Kalender-Monatsansicht) davon unberührt bleibt.

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
     - **Stoppuhr & Timer statt Wetter-/Kalender-Icon in der Wecker-Topbar**: Da Wetter
       und Kalender bereits über die untere Tab-Leiste (`NocturnBottomBar`) erreichbar sind,
       zeigt die Titelleiste der Alarmliste stattdessen zwei eigene Symbole für **Stoppuhr**
       und **Timer** (neue Packages `stopwatch`/`timer`) — analog zu den entsprechenden Tabs
       der Google-Uhr-App. Der **Timer** läuft über `TimerService` (Vordergrunddienst,
       tickende Benachrichtigung mit Pause/+1-Min./Abbrechen-Aktionen) und einen exakten
       `AlarmManager`-Alarm für den tatsächlichen Ablauf (`TimerCompletionReceiver`) — eine
       reine `delay()`-Schleife würde Doze/Prozessabbruch nicht zuverlässig überleben.
       `TimerActivity` registriert zusätzlich Androids systemweite `SET_TIMER`-Aktion (dieselbe
       Standard-Aktion, über die z. B. Google Assistant "stelle einen Timer über 10 Minuten"
       Timer-Apps anspricht), inkl. `SKIP_UI`-Unterstützung für den sofortigen Start ohne
       App-Oberfläche. Die **Stoppuhr** (`StopwatchRepository`, SharedPreferences-basiert,
       bewusst ohne eigenen Foreground-Service, da es keinen festen Zeitpunkt gibt, der das
       rechtfertigen würde) unterstützt Start/Stopp/Runde/Zurücksetzen; anders als beim Timer
       gibt es dafür keine Android-Systemaktion — `SET_TIMER` kennt nur Timer, keine Stoppuhr.
     - **Sprachbefehle für Timer/Stoppuhr/einmaligen Wecker** (Mikrofon-Symbol in der
       Wecker-Titelleiste, neues Package `voice`): Androids Board-Mittel
       `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` liefert die Spracherkennung (Sprachdaten
       verlassen das Gerät nicht über diesen Systemaufruf hinaus, kein eigener Server/LLM-
       Aufruf); der erkannte Text wird danach rein lokal/regelbasiert per Regex
       (`VoiceCommandParser`) ausgewertet — "stelle einen Timer auf 10 Minuten", "starte/
       stoppe/setze die Stoppuhr zurück", "wecke mich um 6 Uhr" (ergibt denselben
       einmaligen Alarm wie `repeatDays = 0` im normalen Anlegen-Dialog, klingelt also
       automatisch heute oder morgen, je nachdem ob die Uhrzeit schon vorbei ist).
       `VoiceCommandExecutor` führt den erkannten Befehl aus (Timer-Start-Intent an
       `TimerService`, `StopwatchRepository`, `AlarmRepository`/`AlarmScheduler`) und liefert
       eine kurze Text-Rückmeldung als Toast. Nicht erkannte Sätze lösen keine Aktion aus,
       sondern zeigen den unverstandenen Text zur Kontrolle an.
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
    - **"Standard-Apps öffnen"-Button im Einstellungen-Screen**: Android selbst hat (anders
      als für Browser/Telefon/SMS) keine generische RoleManager-Rolle für Wecker/Uhr/Kalender
      — ob eine eigene Auswahlseite dafür existiert, hängt vom Hersteller/der Android-Version
      ab. Der Button öffnet direkt `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` (API 31+,
      unterhalb von minSdk 33 also immer verfügbar), mit Fallback auf die normale App-Info-
      Seite der App, falls das Gerät diese Einstellungsseite nicht kennt. Unabhängig davon
      merkt sich Android bei den oben genannten Intent-Filtern automatisch eine vom Nutzer per
      "Immer"-Auswahl im System-Dialog getroffene Standard-App-Entscheidung (z. B. beim ersten
      "Wecker stellen" per Sprachassistent, wenn mehrere Wecker-Apps installiert sind).

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
- **Room-Datenbank-Migrationen (ab jetzt Pflicht)**: `NocturnDatabase` hat jetzt
  reale Nutzdaten auf installierten Geräten (Alarme, Jobs, Schichten, Rotationen,
  Kalendereinträge, Aufgaben, Zugangsdaten-Referenzen, ...). `DatabaseModule.kt` nutzt daher
  bewusst **kein** `fallbackToDestructiveMigration()` mehr (das hätte bei jedem App-Update mit
  Schema-Änderung alle Nutzdaten gelöscht — ist in früheren Entwicklungs-Sessions mehrfach
  passiert, solange es noch keine echten Bestandsdaten gab). Stattdessen nur noch
  `fallbackToDestructiveMigrationOnDowngrade()` (unkritisch, betrifft nur das Draufinstallieren
  einer älteren Debug-Version). **Jede künftige Änderung an `Entities.kt` oder Erhöhung von
  `NocturnDatabase.version` muss eine echte `Migration(alt, neu)` mit ALTER/CREATE-TABLE-SQL in
  `DatabaseModule.kt` ergänzen**, sonst crasht die App beim nächsten Update statt die Daten zu
  verlieren — beides vermeiden, indem die Migration gleich mitgeliefert wird. `exportSchema =
  true` in `NocturnDatabase.kt`, Schema-Snapshots landen unter `app/schemas/` (Room-Build-Zeit-
  Ausgabe, Grundlage für Migrationstests). SharedPreferences-basierte Einstellungen (Wetter-
  Opt-in, manueller Ort, verschlüsselte Nextcloud-/ioBroker-/Lethe-Media-Zugangsdaten) sind
  davon unberührt und überleben App-Updates bereits automatisch, da Android das App-
  Datenverzeichnis bei einem Update (anders als bei Deinstallation) nicht anfasst.
- Health Connect: offizielle Android Health Connect API.
- Bewegungserkennung ohne Health Connect: `SensorManager` mit `TYPE_ACCELEROMETER` /
  `TYPE_GYROSCOPE`, ggf. Schrittzähler-Sensor `TYPE_STEP_COUNTER`/`TYPE_STEP_DETECTOR`.
- Alarm-Zuverlässigkeit: `AlarmManager` (exact alarms) + Foreground Service während der
  Überwachungsphase (Sensorik darf nicht durch Doze/Battery-Optimizations gekillt werden).
- **Lautstärke-Boost für leise Wecktöne** (Package `sound`, `LoudnessBooster`): `MediaPlayer.setVolume()`
  kann einen leise aufgenommenen Ton nur bis zum Aufnahmepegel abschwächen, nie darüber hinaus
  verstärken — ein leiser Ton bliebe also auch bei voller Lautstärke zu leise zum Aufwecken. Pro
  Weckton-URI wird daher einmalig grob die durchschnittliche Lautheit geschätzt (RMS über die
  ersten ~3 Sekunden dekodierter PCM-Samples via `MediaExtractor`/`MediaCodec`, keine echte
  LUFS-Messung, aber für die Boost-Entscheidung ausreichend, Ergebnis pro URI gecacht). Liegt sie
  unter einem Schwellwert, wird ein `android.media.audiofx.LoudnessEnhancer` auf die Audio-Session
  von `AlarmRingingService`/`EmergencyAlarmService` gelegt, mit mind. 3 dB und max. 15 dB Boost
  (Deckel gegen Verzerrung/Rauschen bei nahezu stillen Clips).
- Kalendersync: Google Calendar API (OAuth, geplant) + eigener, schlanker CalDAV-Client für
  Nextcloud (umgesetzt, Package `sync`) — bewusst ohne dav4jvm/biweekly o. Ä., sondern mit
  OkHttp (WebDAV PROPFIND/REPORT/PUT/DELETE) und handgerolltem ICS-Reader/Writer (RFC-5545-
  Subset), passend zur FOSS-Minimalismus-Leitlinie bei einer klar begrenzten Teilmenge des
  Formats. Nextcloud-App-Passwort AES/GCM-verschlüsselt mit einem Android-Keystore-Schlüssel
  abgelegt (keine zusätzliche Security-Crypto-Abhängigkeit nötig). Periodischer
  Hintergrund-Sync über `androidx.work` (Hilt-WorkManager-Integration).
- **Schichtplan-Sync mit der geforkten Nextcloud-Calendar-App** (`NocturnScheduleSyncRepository`,
  Package `sync`): zusätzlich zum reinen CalDAV-Termin-/Schicht-Export synchronisiert NocTurn
  Jobs/Schichten/Rotations-Muster/Tageseinträge bidirektional mit der NocTurn-Schedule-REST-API
  der geforkten Calendar-App (`Nocturn-Kalender/`, siehe "Nextcloud-Kalender-App" unten) — damit
  zeigt deren eigene Vue-Seitenleiste denselben Schichtplan und Änderungen dort fließen zurück
  nach NocTurn. Opt-in über einen dritten Schalter im Nextcloud-Sync-Screen
  (`CalDavAccountEntity.syncNocturnSchedule`, Default aus), nutzt dasselbe bereits verbundene
  Nextcloud-Konto (kein zweiter Login). `NocturnSyncMappingEntity` hält pro Datensatz die
  lokal<->Server-uid-Zuordnung samt Last-Write-Wins-Zeitstempel und Inhalts-Hash (kein Re-Push
  unveränderter Datensätze). Seit Migration `Version6070Date20261023000000` synchronisieren
  dieselben Mechanismen zusätzlich den **Wochenstundenplan** (Schüler/Student-Modus,
  `calendar_noct_lessons`/`NocturnLesson`, pro Unterrichtsstunde Wochentag/Name/Farbe/Zeit/
  Weckzeit-Abstand wie bei Schichten) sowie die **Aufgaben (Tasks)** (`calendar_noct_tasks`/
  `NocturnTask`, Titel/Notiz/Fälligkeitstag/-uhrzeit/Ganztägig/Erledigt-Status) — beide laufen
  nach demselben Push/Pull-Muster wie Jobs/Schichten (eigene `NocturnSyncLocalType`-Werte
  `LESSON`/`TASK`, kein Schema-Zusatz auf Android-Seite nötig, da `LessonEntity`/`TaskEntity`
  schon die nötigen Felder haben und die Server-uid-Zuordnung komplett über
  `NocturnSyncMappingEntity` läuft). In der Vue-Seitenleiste zeigt `NocturnJobItem.vue` bei
  einem Schüler/Student-Job statt Schichten/Rotationen die Unterrichtsstunden gruppiert nach
  Wochentag (`LessonModal.vue`), `NocturnScheduleList.vue` bekommt dafür einen eigenen
  "NocTurn tasks"-Abschnitt mit abhakbarer Liste (`TaskModal.vue`). **Bekannte Einschränkungen**
  (siehe Doc-Kommentar der Klasse): die Server-API kennt pro Rotationstag nur eine Schicht
  (NocTurns neuere Mehrfachbelegung eines Tages wird beim Push auf die erste Schicht reduziert),
  der automatische "Nächste Schicht"-Wecker (`ShiftEntity.latestWakeupHour`) hat kein
  Server-Gegenstück, und Tageseinträge werden nur in einem rollenden Fenster (-14/+180 Tage)
  abgeglichen statt der vollen, ggf. mehrjährigen lokalen Historie — ein anderer Nextcloud-Nutzer
  kann die Rotation nach dem Abgleich von Job/Schichten/Muster aber selbst über die
  Vue-Seitenleiste für einen beliebigen eigenen Zeitraum anwenden. Der Wochenstundenplan erscheint
  bisher nur verwaltbar in der Seitenleiste, nicht als eigene wiederkehrende Einträge im
  FullCalendar-Raster der Nextcloud-App selbst (offener Folgeschritt, analog zum Job-Sharing).
- **Job-Sharing in der geforkten Nextcloud-Calendar-App** (`calendar_noct_job_shares`,
  `NocturnJobShareMapper`/`NocturnJobShare`, Migration `Version6070Date20261016000000`): ein
  Job-Besitzer kann über ein "Teilen"-Icon in der Vue-Seitenleiste (`ShareJobModal.vue`, Nutzer-
  Suche wie beim bereits bestehenden Kalender-Teilen über `principalPropertySearchByDisplaynameOrEmail`,
  bewusst nur einzelne Nextcloud-Nutzer statt Gruppen/Circles) einem anderen Nextcloud-Nutzer
  **READ**- oder **WRITE**-Zugriff auf den kompletten Schichtplan eines einzelnen Jobs (Schichten,
  Rotationen, generierte/manuelle Tageseinträge) geben — ohne die Daten zu duplizieren, der
  Empfänger liest direkt die Zeilen des Besitzers, autorisiert über die Freigabe-Zeile statt über
  Eigentümerschaft. Mit READ sieht der Empfänger den Job unter "Mit mir geteilt" in seiner eigenen
  Seitenleiste (Name + Berechtigungs-Hinweis); mit WRITE kann er zusätzlich einzelne Tageseinträge
  des geteilten Jobs anlegen/ändern (z. B. einen Schichttausch selbst eintragen), über eigene
  `shared/{ownerId}/jobs/{jobUid}/occurrences`-Endpunkte, die den Schreibzugriff bei jedem Aufruf
  neu gegen die Freigabe-Tabelle prüfen. **Bewusste Begrenzung**: WRITE erlaubt nur Tageseinträge,
  nicht das Anlegen/Ändern von Schichten, Rotationen oder des Jobs selbst — das bliebe dem
  Besitzer vorbehalten, um die Berechtigungsfläche klein zu halten. Diese Freigabe ist unabhängig
  vom NocTurn-Android-Sync-Protokoll (siehe oben) und bisher nur über die Nextcloud-eigene
  Vue-Oberfläche nutzbar — die Android-App selbst zeigt noch keine mit ihr geteilten fremden Jobs
  an (offener Folgeschritt).
- Kalender-Teilen (P2P): primär direkte App-zu-App-Synchronisation ohne eigenen
  Smarter-Wecker-Server (z. B. lokal/WLAN oder über vorhandene P2P-Mechanismen).
  Fallback/Hilfsfunktion bei Bedarf als schlanker Endpoint im Lethe-Backend
  (`/opt/lethe/backend`) — nur als Vermittlung/Relay, keine zentrale Kalenderdatenhaltung.
- Wecker-UI: Alarmliste, Zifferblatt-Zeitpicker und Weckton-Auswahl-Screen (Tabs)
  orientieren sich an der Google-Uhr-App, siehe Kernfunktion 9.
- Wetterdaten: OpenWeatherMap "Current Weather Data" (aktuelles Wetter, Topbar-Symbol +
  Uhr-Widget) sowie "One Call API 4.0" für Vorhersagen im Wetter-Screen — 8-Tage-Vorhersage
  über `/data/4.0/onecall/timeline/1day`, zusätzlich eine auf den aktuellen Kalendertag
  gefilterte **stündliche Vorhersage** über `/data/4.0/onecall/timeline/1h` (liefert bis zu
  20 Stunden ab jetzt), als eigener, vertikal scrollbarer Abschnitt (`HourlyForecastCard`,
  feste Maximalhöhe per `heightIn(max=...)` statt `fillMaxSize`/`weight`, da eine verschachtelte
  `LazyColumn` in der äußeren scrollbaren Spalte sonst wegen unbegrenzter Höhenvorgabe abstürzt)
  direkt oberhalb der Tagesliste. Abgelöste Vorgängerversion 3.0 lief über `/data/3.0/onecall`,
  seit Herbst 2026 eigenes, zu 4.0 inkompatibles Abo im OpenWeatherMap-Konto, braucht einmalige
  Abo-Aktivierung mit Zahlungsmethode im OpenWeatherMap-Konto, siehe
  `WeatherRepository`-Doc-Kommentar), API-Key-Handling analog Iosync-Projekt (`/opt/Iosync`):
  Key aus `local.properties` lokal, aus GitHub-Secret `OPENWEATHER_API_KEY` im CI, Einbindung
  als `BuildConfig`-Feld — niemals im Repo.
- Foto-Import/OCR (Kernfunktion 5): Texterkennung on-device über **Tesseract4Android**
  (Apache-2.0), final gewählt gemäß FOSS-Leitlinie gegenüber Googles closed-source ML Kit.
  Wird nicht auf Maven Central, sondern nur über **JitPack** veröffentlicht (Repository in
  `settings.gradle.kts` ergänzt). Die deutschen Trainingsdaten werden nicht ins Repo gelegt,
  sondern beim ersten Foto-Import einmalig von der offiziellen `tesseract-ocr/tessdata`-
  Quelle in den App-internen Speicher nachgeladen — seit der Integration der echten
  Orientierungs-/Schrift-Erkennung (siehe unten) zusätzlich zur Sprachdatei auch `osd.traineddata`
  aus derselben Quelle. **Rotations-robuste Erkennung**: Tesseract liefert bei nicht annähernd
  horizontalem Text (z. B. ein im Hochformat fotografierter, quer beschrifteter Zettel) ohne
  Weiteres nur Zufallsfragmente. `TesseractOcrEngine.recognize()` nutzt daher zuerst einen
  einzelnen Durchlauf mit `PSM_AUTO_OSD` (volle automatische Seitenlayout-Analyse inkl. echter
  Orientierungs- und Schrift-Erkennung über die geladene `osd.traineddata`) statt wie zuvor blind
  alle vier Rotationen durchzuprobieren. Reicht dessen mittlere Tesseract-Konfidenz
  (`meanConfidence()`) nicht (z. B. bei unüblichen Layouts oder falls der `osd.traineddata`-
  Download fehlschlug), fällt die Erkennung auf den bisherigen, robusteren Weg zurück: alle vier
  90°-Rotationen mit festem Einzelblock-Layout (`PSM_SINGLE_BLOCK`) durchprobieren und die
  Variante mit der höchsten Konfidenz behalten. **Grenze bewusst nicht gelöst**: Tesseract ist auf
  gedruckten Text trainiert: handschriftliche Notizen bleiben auch nach dieser Korrektur
  unzuverlässig, da das ein fundamental anderes (und mit on-device-FOSS-Mitteln kaum lösbares)
  Erkennungsproblem ist als Druckschrift-OCR.
- **Lokales Fine-Tuning von `deu.traineddata` für Termin-/Datums-/Uhrzeit-Erkennung**: unter
  `ocr-training/` (komplett `.gitignore`t — Quellcode-Klon + generierte Trainingsdaten gehören
  nicht in den App-Code-Verlauf) liegt eine lokale Trainingsumgebung: ein aus dem offiziellen
  `tesseract-ocr/tesseract`-Quellcode gebauter Satz Trainings-Tools (`text2image`,
  `lstmtraining`, `combine_tessdata`, erfordert `libicu-dev`/`libpango1.0-dev`/`libcairo2-dev`
  zum Bauen, da Debian diese Tools nicht fertig paketiert), die offizielle `deu.traineddata`/
  `osd.traineddata` (Startpunkt) sowie `tesseract-ocr/langdata_lstm` (Sprachressourcen/
  Unicharset). `scripts/train-ocr-model.sh` automatisiert den kompletten Ablauf: Korpus
  (`ocr-training/corpus/appointments.txt`, Termine/Daten/Uhrzeiten/Orte/OCR-Fehlerbilder in
  mehreren Schriftarten/Größen) → synthetische Trainingsbilder (`text2image`) → `.lstmf`-Dateien
  (`tesseract --psm 6 lstm.train`, der in vielen Kurzanleitungen fehlende Zwischenschritt
  zwischen Bild+Box und tatsächlichem LSTM-Training) → Transfer-Learning mit niedriger
  Iterationszahl gegen den aus der offiziellen `deu.traineddata` extrahierten LSTM-Checkpoint
  (`combine_tessdata -e`) → fertiges `deu_appointments.traineddata`.
  **In der App verdrahtet**: Vor der Umstellung wurde das Ergebnis gegen synthetischen
  Termin-Text (auch in einer im Training nicht enthaltenen Schriftart), gegen themenfremden
  deutschen Fließtext (Prüfung auf *catastrophic forgetting*) und gegen echte Fotos getestet —
  keine Regression in Konfidenz/Worttreue gegenüber der offiziellen `deu.traineddata` in allen
  drei Fällen. `TesseractOcrEngine.ensureTrainedDataAvailable()` lädt die Sprachdatei (`deu`)
  daher jetzt von einem selbst gehosteten Download-Endpunkt
  (`https://letheapp.de/downloads/nocturn/ocr/deu_appointments.traineddata`) statt der
  offiziellen Tesseract-Quelle; `osd.traineddata` (Orientierungserkennung, nicht fine-getunt)
  kommt unverändert von dort. Eine lokale Versionsmarkierung (`deu.version` neben der
  zwischengespeicherten `deu.traineddata`, `DEU_MODEL_VERSION`-Konstante) erzwingt bei bereits
  installierten Apps, die noch die alte offizielle Datei zwischengespeichert haben, einmalig
  ein Neuladen. **Weiterhin eine bewusste Grenze**: Handschrift bleibt unabhängig vom
  Fine-Tuning unzuverlässig (siehe oben) — das Fine-Tuning verbessert nur die Erkennung
  gedruckten Texts innerhalb der Termin-Domäne, ohne sie außerhalb zu verschlechtern.
- Berechtigungs-Onboarding (Kernfunktion 10): eigener Onboarding-Screen beim ersten
  Start, der alle benötigten Berechtigungen inkl. Begründung anfragt.
- System-Registrierung (Kernfunktion 11): Manifest-Intent-Filter für
  `SET_ALARM`/`SHOW_ALARMS`/`DISMISS_ALARM`/`SNOOZE_ALARM` sowie die Kategorien
  `APP_CALENDAR`/`APP_CLOCK`.
- Home-Screen-Widgets (Kernfunktion 7): klassische `AppWidgetProvider`/`RemoteViews`
  statt Jetpack Glance, um keine zusätzliche Abhängigkeit einzuführen (FOSS-Minimalismus-
  Leitlinie) — für zwei einfache, überwiegend statische Widgets reicht das. Uhr-Widget
  stößt sich per `AlarmManager.setRepeating` selbst neu an (offizielle
  `updatePeriodMillis`-Updates sind auf 30-Minuten-Minimum begrenzt) — bewusst in zwei
  getrennten Takten: Datum/Uhrzeit jede Minute per günstigem
  `AppWidgetManager.partiallyUpdateAppWidget` (kein Netzwerk-/DB-Zugriff), Wetter und
  Kalender-Agenda nur alle 20 Minuten über den vollen, asynchronen Refresh — sonst hätte
  die Uhrzeit sichtbar nachgehinkt, nur weil sie am selben Takt wie die teureren
  Netzwerk-/DB-Abfragen hing. `android:previewImage` in `clock_widget_info.xml` zeigt im
  Android-Widget-Auswahlmenü einen echten Screenshot des Widgets, da die automatische
  Vorschau-Generierung aus dem Layout (seit Android 12) für dieses Widget leer blieb.
  "nächster Alarm" kommt direkt von `AlarmManager.getNextAlarmClock()` statt aus der eigenen DB, da
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
- **F-Droid-Repo** (`scripts/release-deploy.sh`, Schritt 9): NocTurn wird zusätzlich ins
  bereits bestehende, eigene F-Droid-Repo von Lethe (`https://fdroid.letheapp.de/repo`,
  `/root/my-fdroid-repo` auf dem Hauptserver) eingetragen, statt ein eigenes Repo
  aufzusetzen — ein Nutzer, der dort bereits Lethe bezieht, bekommt NocTurn-Updates über
  dieselbe Quelle. Bei jedem Release (kein Opt-in-Flag nötig, da dieses Skript immer nur
  die eine NocTurn-APK baut) wird die frisch deployte APK als `de.letheapp.nocturn_<versionCode>.apk`
  nach `/root/my-fdroid-repo/repo/` kopiert, die Metadata-Datei
  `/root/my-fdroid-repo/metadata/de.letheapp.nocturn.yml` angelegt bzw. per `CurrentVersion`/
  `CurrentVersionCode` aktuell gehalten (`AutoUpdateMode`/`UpdateCheckMode` bewusst auf `None`,
  da die APK nicht nach F-Droid-Signing-Konvention im Repo liegt — ohne das manuelle
  Nachziehen von `CurrentVersion`/`CurrentVersionCode` würde der F-Droid-Client trotz neuer
  APK im Index weiter die beim Erstanlegen eingetragene Version als "aktuell" anzeigen), und
  abschließend per SSH `fdroid update --clean` ausgeführt. Älteren APKs im Repo-Ordner werden
  dabei bewusst nicht gelöscht (gleiches Verhalten wie beim bestehenden Lethe-Media-Player-
  Eintrag) — F-Droid-Clients können so auch ältere Versionen weiterhin installieren. Derselbe
  Mechanismus (Helper-Funktion `fdroid_sync_metadata`) existiert gespiegelt in
  `/opt/letheapp/push-and-deploy.sh` für den Lethe Media Player (`com.Lethe.mediaplayer`,
  hinter dem bestehenden `--fdroid`-Flag), damit beide Begleit-Apps zuverlässig eine aktuelle
  "Current"-Version im selben Repo zeigen.
- **Push-Deploy der Nextcloud-Kalender-App auf den Test-Nextcloud-Server** (`scripts/
  release-deploy.sh`, Schritt 10, optional): Nextcloud hat **keinen** per-App-Update-Feed
  (kein `<releases>`-Tag in `info.xml`, keine pro-App `appstoreurl`) — das native
  Update-System ist ein einziger, instanzweiter Appstore-Endpunkt (`config.php`
  `'appstoreurl'`, Default `https://apps.nextcloud.com/api/v1`), der für alle installierten
  Apps gleichzeitig gilt; ein eigener Mirror dieses Formats müsste zwangsläufig auch alle
  anderen Apps der Instanz mitbedienen und ist für eine einzelne private App daher nicht
  sinnvoll. Stattdessen baut das Skript die App (`npm run build` + `composer install
  --no-dev`) und deployt sie bei jedem Release automatisch per `scp`/`ssh` (Zugangsdaten in
  `.server-credentials`, Variablen `NEXTCLOUD_SERVER_*`/`NEXTCLOUD_APP_DIR`) nach
  `/var/www/nextcloud/apps/nocturnkalender/` auf dem Test-Nextcloud-Server
  (`thestealth.de`) — Wartungsmodus kurz an/aus für einen sauberen Cache-Rebuild, altes
  App-Verzeichnis vorher als `.old`-Backup gesichert. Rein optional (übersprungen, wenn die
  `NEXTCLOUD_SERVER_*`-Variablen fehlen), da dieser Server kein offizielles Release-Ziel wie
  letheapp.de/F-Droid ist, sondern nur der eigene Test-Nextcloud des Entwicklers.
- **Nextcloud-Kalender-App ("NocTurn Kalender")** (`Nocturn-Kalender/`): vollständiger Fork
  von `nextcloud/calendar`, der die unter "Schichtplan-Sync"/"Job-Sharing" oben beschriebene
  NocTurn-eigene REST-API und Vue-Seitenleiste ergänzt. Angezeigter App-Name ist **"NocTurn
  Kalender"** (`appinfo/info.xml` `<name>`, sowohl App- als auch Navigationseintrag). **Eigene,
  vom Original getrennte App-`<id>` `nocturnkalender`** (ebenso PHP-Namespace
  `OCA\NocturnKalender`, Route `nocturnkalender.view.index`, npm-Paketname `nocturnkalender`,
  JS-Bundle-Präfix, Composer-Autoloader-Suffix, Capabilities-Schlüssel, Reference-Provider-ID,
  UserMigration-ID) — bewusste Kurskorrektur gegenüber einer früheren Entscheidung, die die ID
  noch absichtlich auf `calendar` beließ: eine identische ID hätte beim Enable neben der
  eingebauten Nextcloud-Calendar-App zu PHP-Klassenkollisionen (doppelte `OCA\Calendar`-Klassen)
  geführt und die Original-App faktisch überschrieben/ersetzt statt eigenständig daneben zu
  existieren. Der Android-Client (`NocturnScheduleApiClient.kt`) spricht entsprechend
  `/apps/nocturnkalender/v1/nocturn/...` an. **Bekannte Grenze**: Enthält die Fork-eigenen
  Migrationen (Appointments/Reminder-Tabellen, ererbt von `nextcloud/calendar`) `CREATE TABLE`-
  Schritte für Tabellennamen, die die eingebaute Calendar-App bei vorheriger Nutzung auf
  demselben Server bereits angelegt hat, kann das Enable dieser App unter der neuen ID an
  "Tabelle existiert bereits" scheitern — nur relevant, wenn beide Apps je auf derselben
  Nextcloud-Instanz aktiv waren/sind, nicht bei einer Neuinstallation. **CI/Release**:
  `build-apk.yml` baut diese App als eigenen Job (`nextcloud-kalender`, Node 24 + PHP 8.2,
  `npm run build` + `composer install --no-dev`) parallel zu den Android-APKs und packt sie als
  `nocturn-kalender-<Version>.tar.gz`; ein dritter Job `release` lädt alle drei Artefakte
  (Release-APK, WearOS-APK, Nextcloud-Paket) herunter und hängt sie gemeinsam an dasselbe
  GitHub Release — die Nextcloud-App landet damit bei jedem Release automatisch als
  Installationspaket neben der APK, ohne eigenen Release-Lauf. **Behobener Rename-Bug**: Die
  Umbenennung von `calendar` auf `nocturnkalender` hatte in `templates/main.php` und den drei
  `templates/appointments/*.php`-Dateien (`script()`/`style()`-Aufrufe) sowie in **~40
  `loadState('calendar', ...)`-Aufrufen und dem `linkTo('calendar', 'js/')`-Webpack-Public-Path
  in `src/`** nicht gegriffen — dadurch lud die App beim Öffnen weiterhin das Script-/
  Style-Bundle und sämtliche Lazy-Chunks der ANDEREN, noch installierten `calendar`-App und
  fand keine zu sich selbst passenden Initial-State-Werte, wodurch die Seite komplett leer
  blieb (Vue mountete nichts in `#content.app-nocturnkalender`). Zusätzlich fehlte in
  `appinfo/info.xml`s `<background-jobs>`-Eintrag und den Appointment-Templates das
  `OCA\Calendar\...` → `OCA\NocturnKalender\...`-Update. Alle Fundstellen sind korrigiert;
  `t('calendar', ...)`/`n('calendar', ...)`-Übersetzungsdomänen wurden ebenfalls global auf
  `nocturnkalender` umgestellt. **Zweiter, tatsächlich funktionaler Übersetzungs-Bug** (fiel erst
  nach der Installation auf einer echten Nextcloud-Instanz auf): Die generierten `l10n/*.js`-
  Dateien registrieren ihre Übersetzungen zur Laufzeit selbst per `OC.L10N.register("<appid>",
  {...})` — dieser String blieb beim Rename auf allen 92 Sprachdateien hart auf `"calendar"`
  stehen. Nextcloud lädt für die aufrufende App zwar korrekt `apps/nocturnkalender/l10n/de.js`,
  das Skript registrierte seine Übersetzungen darin aber unter dem Schlüssel `"calendar"` statt
  `"nocturnkalender"` — `t('nocturnkalender', text)` fand im globalen Übersetzungs-Registry also
  nichts und fiel auf den unübersetzten (englischen) Originaltext zurück. Alle `l10n/*.js`-Dateien
  sind jetzt auf den korrekten App-Namen umgestellt (reiner Registrierungs-String, die `l10n/*.json`-
  Dateien selbst brauchen keine App-ID und waren nie betroffen).

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
