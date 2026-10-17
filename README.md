# Attack Shark X11 Control - Android (inoffiziell)

Native Android-App (Kotlin + Jetpack Compose) für Tablet und Handy, die die
**Attack Shark X11 / X11 SE** über **USB-OTG** konfiguriert - DPI-Stufen,
RGB-Beleuchtung, Abtastrate, Tastenbelegung, Akkustand. Gleiches Protokoll
wie die Linux/Bazzite-Version dieses Projekts, nur über Androids
USB-Host-API (`UsbManager`/`UsbDeviceConnection`) statt `/dev/hidraw`.

## Wichtige Voraussetzung: USB-Host-Modus (OTG)

Dein Tablet/Handy muss **USB-Host-Modus** unterstützen (fast jedes
Android-Gerät der letzten Jahre kann das). Du brauchst außerdem:

- Beim **Kabelbetrieb**: ein USB-OTG-Adapter (USB-C/Micro-USB → USB-A),
  Maus direkt dranstecken.
- Beim **Funkbetrieb**: den 2,4-GHz-Dongle genauso über den OTG-Adapter
  anschließen (er wird vom System als eigenes USB-Gerät erkannt).

Ohne OTG-Unterstützung geht es grundsätzlich nicht - das ist eine
Hardware-Einschränkung von Android, keine Einschränkung dieser App.

## Bauen

### Mit Android Studio (einfachster Weg)

1. Android Studio installieren (auf Bazzite z.B. als Flatpak:
   `flatpak install flathub com.google.AndroidStudio`).
2. Diesen Ordner (`android/`) als Projekt öffnen.
3. Studio lädt beim ersten Öffnen automatisch den Gradle-Wrapper +ladet
   Abhängigkeiten herunter (Internetzugang nötig).
4. „Run" auf ein per USB angeschlossenes/ADB-verbundenes Gerät oder
   „Build > Build APK(s)" für eine installierbare `.apk`.

### Über die Kommandozeile (ohne Android Studio)

Voraussetzung: JDK 17 + Android SDK (command line tools) sind installiert.
Auf Bazzite z.B. über eine Distrobox mit den SDK-Kommandozeilen-Tools, oder
per `sdkmanager`, den man separat herunterlädt.

```bash
cd android
# einmalig: Gradle-Wrapper-JAR erzeugen (hier aus Lizenzgründen nicht
# mitgeliefert) - entweder über ein lokal installiertes Gradle:
gradle wrapper --gradle-version 8.7

# danach wie gewohnt:
./gradlew assembleDebug
# APK liegt danach unter:
#   app/build/outputs/apk/debug/app-debug.apk
```

Installation auf dem Gerät per ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

(Oder die APK einfach manuell auf das Tablet/Handy kopieren und dort
installieren - „Installation aus unbekannten Quellen" muss dafür erlaubt
sein, da die App nicht aus einem Store kommt.)

## Bedienung

1. Maus/Dongle per OTG anstecken.
2. Beim ersten Mal fragt Android nach der USB-Berechtigung für die App -
   bestätigen.
3. Einstellungen anpassen, „Übernehmen" drücken zum Senden, „Von Maus
   laden" zum Auslesen der aktuellen Konfiguration.

## Architektur

```
app/src/main/kotlin/de/harukadev/attacksharkx11/
  protocol/      DPI-Tabelle, Report-Builder/-Parser (identisch zur Linux-Version)
  usb/           USB-Host Control-Transfers (SET_REPORT/GET_REPORT über EP0)
  device/        Hochlevel-API (Coroutines-basiert)
  ui/theme/      Material3-Farbschema (dunkel, orange)
  MainActivity.kt Komplette Compose-Oberfläche + USB-Berechtigungs-Handling
```

## Bekannte Einschränkungen

- Gleiche protokollseitigen Einschränkungen wie die Linux-Version (siehe
  deren README): Reverse-Engineering, keine Makros, DPI-Auslesen bei sehr
  hohen Werten kann mehrdeutig sein.
- **Nicht gegen echte Hardware getestet** (kein USB-Gerät in der
  Entwicklungsumgebung verfügbar) - Control-Transfer-Parameter (bmRequestType/
  wValue/wIndex) folgen exakt der USB-HID-Spezifikation für
  Set_Report/Get_Report auf Feature-Reports, sollten also passen. Falls
  beim ersten Test etwas nicht ankommt, bitte melden - dann justiere ich
  gezielt nach.
- Manche Tablets/Handys liefern über den OTG-Port zu wenig Strom für Maus
  *und* Dongle gleichzeitig; bei Verbindungsproblemen ggf. einen
  Y-Adapter mit externer Stromversorgung probieren.

## Lizenz

MIT, wie das Ursprungsprojekt `attack-shark-x11-driver` (© HarukaYamamoto0).
