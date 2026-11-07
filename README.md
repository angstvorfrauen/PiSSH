# PiSSH

Dieses Projekt baut auf dem bereitgestellten PiSSH-Projekt auf. Das Paket wurde auf `com.xeuka.pissh` vereinheitlicht und die Gradle-Konfiguration auf AGP 8.13.2 / Kotlin 2.2.20 / Gradle 8.13 ausgerichtet.

## Enthalten

- Jetpack Compose / Material 3 Oberfläche mit Files, Editor, Terminal und Settings.
- Light / Dark / System Theme.
- SSH-Verbindung mit JSch, KeepAlive (konfigurierbar) und SFTP.
- Foreground-Service mit permanenter Verbindungshinweis-Benachrichtigung.
- Wiederverbindungsversuche mit exponentiellem Backoff, solange der Service und der App-Prozess laufen.
- SFTP-Dateiliste, Ordnernavigation, Upload, Download, Erstellen, Umbenennen, Löschen, Kopieren, Verschieben, Sortierung, Namensfilter und chmod.
- Textdatei-Editor mit Tabs, Zeilennummern, Suche/Ersetzen, Syntax-Farbgebung (heuristisch), acht Farbschemata, Schriftgröße und SFTP-Speichern mit temporärer Datei/Backup.
- Interaktiver SSH-Shell-Kanal, der `tmux new-session -A -s PiSSH` startet und die Sitzung beim Trennen des Clients auf dem Pi bestehen lässt.

## Voraussetzungen / Build

- Android Studio mit JDK 17
- Android SDK Platform 36
- Android Gradle Plugin 8.13.2 / Gradle 8.13
- Raspberry Pi mit aktiviertem SSH
- Für das persistente Terminal: `tmux` auf dem Raspberry Pi installieren, z. B. `sudo apt update && sudo apt install tmux`

Build in Android Studio: Projekt öffnen, Gradle Sync ausführen und `app` als Debug-App starten.

## Wichtige Einschränkungen

- Ein vollständiger Gradle-Build konnte in der Erstellungsumgebung nicht ausgeführt werden: Der Gradle-Wrapper konnte `services.gradle.org` wegen fehlender Netzwerk-DNS-Auflösung nicht erreichen. Es wird daher kein erfolgreicher Build behauptet.
- Die SSH-Host-Key-Prüfung ist in dieser Version noch deaktiviert (`StrictHostKeyChecking=no`). Nur in vertrauenswürdigen Netzwerken verwenden; vor einem öffentlichen Release muss Host-Key-Pinning / `known_hosts` implementiert werden.
- Das Passwort wird nur im Arbeitsspeicher für Wiederverbindungsversuche gehalten und nicht dauerhaft gespeichert.
- Der Android-Foreground-Service ist keine Garantie für eine unbegrenzt laufende Verbindung. Android-Hintergrundlimits, Energiesparmodi und insbesondere die Data-Sync-FGS-Zeitlimits neuer Android-Versionen können ihn beenden. Nach Prozessende sind gespeicherte SSH-Zugangsdaten nicht verfügbar.
- `tmux` wird nicht automatisch mit Root-Rechten installiert. Es muss auf dem Raspberry Pi vorhanden sein.
- ANSI-Escape-Sequenzen werden im Terminal-Ausgabefeld noch nicht vollständig in farbige Compose-Spans umgesetzt; Terminalgröße wird aktuell mit 100x32 initialisiert.
- Die Syntaxhervorhebung ist eine leichte Regex-basierte Basis, kein vollwertiger Language Server. Sehr große Dateien über 5 MiB werden zum Schutz vor Speicherproblemen abgelehnt.
- Noch nicht enthalten: echte Mehrfachauswahl, rekursive Inhalts-/Dateisuche mit Trefferliste, Save-As-Dialog, Undo/Redo, Code-Folding, vollständige Sprachgrammatiken, automatische Editor-Speicherung und geräteübergreifende Tablet-Speziallayouts.

## Sicherheit

- Keine Zugangsdaten sind im Quellcode fest hinterlegt.
- Die App speichert Host, Port und Benutzername lokal; das Passwort bleibt flüchtig im Speicher.
- Vor produktivem Einsatz Host-Key-Prüfung ergänzen und auf sichere SSH-Passwort-/Key-Verwaltung umstellen.
