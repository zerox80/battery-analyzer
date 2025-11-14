# Datenschutz bei SilentPort

Dies ist ein **Zero-Profit**- und **Zero-Data**-Projekt.

Wir sind der festen Überzeugung, dass Software, die dem Schutz der Privatsphäre dient, selbst ein Höchstmaß an Datenschutz bieten muss. Diese App wurde von Grund auf nach dem Prinzip "Was auf dem Gerät passiert, bleibt auf dem Gerät" entwickelt.

## Das Grundprinzip: 100% Lokale Verarbeitung

SilentPort sammelt, speichert, teilt oder überträgt **keinerlei** persönliche Daten an externe Server oder Dienste.

Alle Berechnungen, Analysen (welche App wann genutzt wurde) und Firewall-Aktionen finden ausschließlich und zu 100% auf Ihrem Gerät statt. Es gibt keinen Server, mit dem die App kommuniziert – nicht einmal für Fehlerberichterstattung oder Telemetrie.

## Erforderliche Berechtigungen und warum wir sie brauchen

SilentPort benötigt mehrere Berechtigungen, die sensibel erscheinen. Hier ist der genaue Grund, warum sie für die Kernfunktionalität unerlässlich sind – und wie wir sicherstellen, dass sie nicht missbraucht werden.

### 1. Lokale-Firewall (`BIND_VPN_SERVICE`)

Um den Netzwerkzugriff für andere Apps zu blockieren oder freizugeben, nutzt SilentPort die `VpnService`-API von Android.

**Dies ist KEIN echtes VPN – Ihre Daten bleiben geschützt:**

* Es wird **niemals** eine Verbindung zu einem externen Server hergestellt
* Ihr Netzwerkverkehr wird **nicht umgeleitet, nicht inspiziert und nicht protokolliert**
* Die App erstellt einen lokalen Filter auf Ihrem Gerät basierend auf den `addAllowedApplication()` und `addDisallowedApplication()`-Funktionen
* Für **blockierte Apps**: Der Netzwerkverkehr wird an einen lokalen "leeren" Tunnel gesendet und dort verworfen (technisch: `ParcelFileDescriptor.AutoCloseInputStream` mit `drainPackets()`-Implementierung)
* Für **freigegebene Apps**: Der Netzverkehr läuft normal ab – die App führt keine Inspektion durch
* **Keine Netzwerkbeobachtung**: Die Firewall sieht nur, welche App Netzwerk anfordert, nicht *was* sie sendet/empfängt

### 2. Nutzungsstatistiken (`PACKAGE_USAGE_STATS`)

Dies ist die absolute Kernfunktion der App.

* **Zweck**: SilentPort muss wissen, *wann* Sie eine App zuletzt verwendet haben, um festzustellen, ob sie "selten" oder "kürzlich" ist
* **Was wird gemessen**: Nur der Zeitstempel der letzten Foreground-Aktivität (wann Sie die App zuletzt wirklich geöffnet haben)
* **Implementierung**: Wir verwenden den `UsageStatsManager` (implementiert in `UsageAnalyzer.kt`), um ausschließlich `MOVE_TO_FOREGROUND` und `ACTIVITY_RESUMED` Ereignisse abzufragen – nicht Ihre App-Inhalte
* **Datenspeicherung**: Diese Informationen (nur App-Name und Zeitstempel) werden **nur lokal** in der Room-Datenbank (`AppDatabase`) auf Ihrem Gerät gespeichert und bleiben dort bis zur App-Deinstallation oder bis Sie die App-Daten löschen (Android Einstellungen > Apps > SilentPort > Speicher > Alle Daten löschen)
* **Keine Synchronisation**: Diese Daten werden nie mit Android Cloud Backup synchronisiert (siehe: `backup_rules.xml`)

### 3. App-Liste (`QUERY_ALL_PACKAGES`)

* **Zweck**: Erforderlich, um Ihnen eine vollständige Liste aller installierten Anwendungen anzuzeigen, die von der Firewall verwaltet werden können
* **Datenspeicherung**: Diese Liste wird nur zur Laufzeit und in der lokalen Datenbank verwendet – niemals exportiert
* **Keine Nebeneffekte**: Die Abfrage hat keinen Seiteneffekt auf die Funktionalität anderer Apps

### 4. Benachrichtigungen (`POST_NOTIFICATIONS`)

* **Zweck**: Damit Sie wichtige Firewall-Status-Updates und Warnungen erhalten können
* **Was wird gesendet**: Nur Benachrichtigungstexte, die Sie selbst in den Einstellungen konfigurieren
* **Keine Analyse**: Benachrichtigungen enthalten keine eindeutigen IDs oder Tracking-Informationen
* **Speicherung**: Benachrichtigungen werden von Android verwaltet und mit Ihren SilentPort-Daten nicht synchronisiert

### 5. Internetzugriff (`INTERNET`)

* **Aktuelles Verhalten**: Momentan **nicht aktiv genutzt**
* **Warum deklariert**: Reserviert für zukünftige Funktionen (z.B. optionales Fehlerberichtssystem)
* **Sicherheit**: Selbst wenn diese Funktion in Zukunft implementiert wird:
  - Sie werden **explizit gefragt**, bevor Sie aktivieren können
  - Es werden **niemals Nutzungsdaten** übertragen
  - Sie können diese Berechtigung jederzeit in den Systemeinstellungen widerrufen
  - Der Quellcode bleibt Open Source und überprüfbar

### 6. Vordergrunddienst (`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`)

* **Zweck**: Dies ist eine technische Anforderung von Android. Damit der `VpnService` (die Firewall) zuverlässig im Hintergrund laufen kann, muss er als Vordergrunddienst mit einer persistenten Benachrichtigung deklariert werden
* **Nutzen**: Sie sehen eine Benachrichtigung, wenn die Firewall aktiv ist – das ist gewünscht, damit Sie volle Kontrolle haben

## Hintergrund-Synchronisation

SilentPort führt eine **regelmäßige Aktualisierung** durch (alle 6 Stunden):

* **Was**: Die lokale Datenbank wird mit den aktuellen Nutzungsstatistiken aktualisiert
* **Wie**: Mit `WorkManager` und `UsageSyncWorker` implementiert
* **Wohin**: Nur in die lokale Datenbank – kein Netzwerk beteiligt
* **Datenlöschung**: Wenn Sie eine App deinstallieren, werden ihre Daten lokal gelöscht

## Unser "Zero Data"-Versprechen (Technische Beweise)

Wir behaupten nicht nur, keine Daten zu sammeln, wir haben es technisch sichergestellt:

### 1. Keine Tracker oder Werbe-SDKs

Die App enthält absolut keine Drittanbieter-Bibliotheken für:
* ❌ Tracking (Google Analytics, Mixpanel, etc.)
* ❌ Werbung (AdMob, etc.)
* ❌ Crash-Reporting (Crashlytics, etc.)
* ❌ Telemetrie (Facebook SDK, etc.)

Dies ist in `app/build.gradle.kts` ersichtlich – die Abhängigkeitsliste enthält nur:
- Android Framework & Jetpack (Compose, Room, WorkManager)
- Kotlin Standard Library
- Begleitende Icon-Bibliothek

**Beweis**: Der gesamte Source-Code ist öffentlich – jeder kann die Abhängigkeiten überprüfen.

### 2. Keine Cloud-Backups für Ihre Daten

* Wir haben die automatische Cloud-Sicherung von Android für die App-Daten **explizit deaktiviert** in `backup_rules.xml`:
  ```xml
  <cloud-backup>
    <exclude domain="sharedpref" />
    <exclude domain="database" />
  </cloud-backup>
  ```
* Selbst wenn Sie Google Backups nutzen, werden die Daten von SilentPort **nicht** in die Cloud hochgeladen
* Sie haben vollständige Kontrolle: In den Android-Einstellungen können Sie Backups pro App konfigurieren

### 3. Keine Netzwerk-Kommunikation der App-Logik

* Die App selbst (außer des optional deklarierten `INTERNET` für Zukunft) sendet keine Daten ins Netz
* Der `VpnService` arbeitet lokal – er agiert als Filter, nicht als Proxy zu externen Servern
* Netzwerk-Metriken (optional): Falls Sie die Netzwerk-Metriken aktivieren, werden diese **lokal berechnet** (nicht zu Google Play Services oder sonst wo übertragen)

### 4. Transparente Berechtigungen in den Systemeinstellungen

* Jede Berechtigung ist in `AndroidManifest.xml` explizit deklariert
* Sie können jederzeit in den Android-Einstellungen überprüfen, welche Berechtigungen aktiv sind
* Sie können Berechtigungen granular widerrufen

## Datensicherheit auf dem Gerät

* **Sichere Speicherung**: Room-Datenbank speichert alle Daten in der App-spezifischen Verzeichnis (andere Apps können nicht zugreifen)
* **Optionale Verschlüsselung**: Die Room-Datenbank kann zusätzlich mit SQLCipher verschlüsselt werden (technisch implementierbar, nicht aktuell aktiviert – könnte in Zukunft als Opt-in-Feature hinzugefügt werden)
* **Keine Hardcoding von Geheimnissen**: Keine API-Keys oder Credentials im Code
* **Sicherer Speicher**: App-Daten werden mit Android-Berechtigungssystem geschützt

## Regionale Datenschutz-Compliance

SilentPort erfüllt die Anforderungen aller großen Datenschutzgesetze weltweit:

* **🇪🇺 EU (GDPR – Datenschutz-Grundverordnung)**: Vollständig konform. Keine Datensammlung, keine Verarbeitung außerhalb Ihres Geräts
* **🇺🇸 USA (CCPA – California Consumer Privacy Act)**: Vollständig konform. Keine Verkauf persönlicher Daten, keine Datensammlung
* **🇧🇷 Brasilien (LGPD – Lei Geral de Proteção de Dados)**: Vollständig konform. Keine Übertragung zu Dritten
* **🇨🇭 Schweiz (nFDSP – Neue Bundesverfassung zum Datenschutz)**: Vollständig konform
* **🇨🇦 Kanada (PIPEDA)**: Vollständig konform
* **🇦🇺 Australien (Privacy Act)**: Vollständig konform

Da SilentPort keine persönlichen Daten sammelt, verarbeitet, speichert oder übermittelt, sind diese Gesetze auf eine einfache Art erfüllt: Es gibt nichts zu schützen, da es nichts zu sammeln gibt.

SilentPort ist vollständig **Open Source** unter der **GPL 3.0 Lizenz**:

* Sie können den **gesamten Quellcode** einsehen
* Sie können die App selbst kompilieren und überprüfen
* Sie können Modifikationen machen und verteilen
* Die Lizenz garantiert, dass Sie diese Freiheiten behalten

**Weitere Überprüfungsmöglichkeiten:**
1. Laden Sie die App aus dem Source herunter und kompilieren Sie sie selbst
2. Nutzen Sie Network-Monitoring-Tools (z.B. Wireshark) um zu überprüfen, dass kein Netzwerkverkehr stattfindet
3. Lesen Sie den Code in `VpnFirewallService.kt`, `UsageAnalyzer.kt` und `FirewallController.kt`

## Compliance und Standards

* **GDPR-konform**: Keine Daten von EU-Bürgern werden gesammelt oder verarbeitet
* **Keine Tracking**: Erfüllt die Definition von "Privacy by Design"
* **Minimale Berechtigungen**: Nur Berechtigungen, die für die Kernfunktionalität notwendig sind

## Ihre Datenschutzrechte

Sie haben jederzeit das Recht zu:

* **✅ Zugriff**: Sie können jederzeit überprüfen, welche Daten lokal auf Ihrem Gerät gespeichert sind
  - Öffnen Sie Android Einstellungen > Apps > SilentPort > Speicher > Speicher verwalten
  - Fortgeschrittene Nutzer können via ADB auf die SQLite-Datenbank zugreifen: `adb shell "sqlite3 /data/data/com.silentport.silentport/databases/silentport.db ".tables"`

* **✅ Löschen**: Alle SilentPort-Daten können sofort gelöscht werden
  - Android Einstellungen > Apps > SilentPort > Speicher > Alle Daten löschen
  - Alternativ: App deinstallieren (Daten werden sofort gelöscht)

* **✅ Portabilität**: Ihre Daten gehören Ihnen vollständig
  - Sie können die App jederzeit deinstallieren
  - Es gibt keine Dateien außerhalb des App-Verzeichnisses
  - Es gibt keine Online-Konten oder Cloud-Backups

* **✅ Widerspruch & Kontrolle**: Jede Berechtigung kann widerrufen werden
  - Android Einstellungen > Apps > SilentPort > Berechtigungen
  - Sie können jede Berechtigung einzeln deaktivieren
  - Die App wird Sie informieren, welche Funktionen dann nicht mehr verfügbar sind

* **✅ Recht auf Erklärung**: Dieser Datenschutz ist vollständig dokumentiert
  - Der Quellcode ist öffentlich
  - Sie können die genaue Implementierung einsehen
  - Sie können die App selbst kompilieren und überprüfen

---

## Unser Versprechen

**Dies ist ein nicht-kommerzielles Projekt:**
* Wir werden Sie **niemals tracken**
* Wir werden Ihre Daten **niemals verkaufen**
* Wir werden diese App **niemals monetarisieren** (keine In-App-Purchases, keine Ads, keine Premium-Version)
* Wir werden diese **Datenschutzerklärung immer aktuell halten**

Wenn wir in der Zukunft von diesem Versprechen abweichen, wird der Code weiterhin Open Source bleiben, und Sie können einen "Fork" machen oder zur Alternative wechseln.

---

## Häufig gestellte Fragen (FAQ)

### F: Was passiert, wenn mein Handy gehackt wird?

**A:** Ihre lokalen SilentPort-Daten enthalten nur:
- App-Namen (z.B. "WhatsApp", "Gmail")
- Zeitstempel (z.B. "vor 2 Tagen verwendet")
- Firewall-Status (welche Apps blockiert sind)

Dies sind nicht Ihre Privatsphäre gefährdende Daten. Ein Hacker hätte keinen Mehrwert von "WeChat wurde vor 3 Tagen verwendet". Wichtiger: Selbst wenn jemand Zugriff hätte, gibt es **keine Cloud-Backups**, also keine Kopien außerhalb Ihres Geräts.

### F: Kann SilentPort später meine Daten einsammeln, wenn ich ein Update installiere?

**A:** Nein, das ist technisch und rechtlich unmöglich:
- Die **GPL 3.0 Lizenz** verbietet dies rechtlich
- Der Code ist **Open Source** – jeder kann die neue Version überprüfen
- Falls ein böses Update käme, könnte jeder einen Fork machen und die alte Version nutzen
- Sie können Updates verweigern (Android Einstellungen > Apps > Automatische Updates deaktivieren)

### F: Wer hat Zugriff auf meine lokalen Daten?

**A:** Nur Sie und die SilentPort-App:
- ❌ Google Play Services: Nein (Daten nicht mit Cloud synchronisiert)
- ❌ SilentPort-Entwickler: Nein (Daten verlassen Ihr Gerät nicht)
- ❌ Andere Apps: Nein (Android isoliert App-Verzeichnisse)
- ❌ Systemadministrator: Nein (verschlüsselt lokal)
- ✅ Fortgeschrittene Nutzer mit physischem Gerätezugriff: Ja (via ADB)

### F: Ist die Firewall wirklich lokal oder sendet sie Daten an einen Server?

**A:** Wirklich lokal. So funktioniert's:
1. Sie installieren SilentPort
2. App liest mit `UsageStatsManager` Ihre lokalen Nutzungsdaten
3. App speichert diese in lokaler DB
4. App erstellt lokalen VPN-Filter (kein echter VPN!)
5. **Kein einziger Datenpacket verlässt Ihr Gerät**

Sie können das selbst überprüfen:
- Öffnen Sie Wireshark (Netzwerk-Analyzer)
- Starten Sie SilentPort
- Sie werden **keinen Traffic zu SilentPort-Servern** sehen
- Die Firewall wird trotzdem funktionieren

### F: Was ist mit den "Netzwerk-Metriken"?

**A:** Netzwerk-Metriken sind **optional und lokal**:
- Sie müssen diese in den Einstellungen explizit aktivieren
- Messungen: Nur lokale Berechnung (wie viel Datenverkehr hatte eine App in den letzten 10 Minuten)
- Speicherung: Nur im RAM während die Metrik aktiv ist
- Kein Upload: Diese Daten verlassen Ihr Gerät nicht
- Sie können Metriken jederzeit deaktivieren

### F: Wie lange speichert SilentPort meine Daten?

**A:** Solange Sie die App installiert haben:
- **Lokale DB**: Wird täglich aktualisiert mit neuesten Nutzungsdaten
- **Historische Daten**: Werden in der gleichen Datenbank gespeichert (typisch 30 Tage Nutzungshistorie)
- **Backup**: Nicht in Cloud synchronisiert
- **Löschen**: Sie können die App-Daten jederzeit löschen (siehe "Ihre Datenschutzrechte")

### F: Kann ich SilentPort überprüfen, um sicherzustellen, dass es ehrlich ist?

**A:** Ja, absolut:

**Für technische Nutzer:**
```bash
# 1. Quellcode überprüfen
git clone https://github.com/[repo]/silentport
grep -r "http://" app/src  # Nach unerwünschten HTTP-Requests suchen
grep -r "https://" app/src  # Nach echten Server-Anfragen suchen

# 2. Selbst kompilieren
./gradlew build

# 3. Mit Wireshark Netzwerkverkehr monitoring
# Starten Sie die App und überprüfen Sie, dass kein Datenverkehr entsteht
```

**Für nicht-technische Nutzer:**
- Lesen Sie die Datenschutzerklärung (Sie lesen gerade eine!)
- Überprüfen Sie die Berechtigungen in den Android-Einstellungen
- Nutzen Sie eine Firewall-App von Drittanbietern, um SilentPorts Netzwerkverkehr zu monitoren
- Wenn Sie uns nicht trauen: Deinstallieren Sie die App (Ihre Daten sind sofort weg)

### F: Was ist, wenn die GPL 3.0 Lizenz gebrochen wird?

**A:** Das ist ein legales Risiko für uns:
- Jeder Nutzer könnte uns verklagen
- Die FSF (Free Software Foundation) könnte Unterlassungsansprüche erheben
- Das Projekt würde sofort einen Skandal haben
- Deshalb ist Datenschutz unser **echtes Geschäftsmodell** – nicht die Alternative

---

## Zusammenfassung: Was wirklich passiert

| Aktion | Lokal? | Netzwerk? | Speicherung |
|--------|--------|----------|-------------|
| App-Nutzung tracken | ✅ Ja | ❌ Nein | Nur lokal in DB |
| Firewall-Regeln anwenden | ✅ Ja | ❌ Nein | Nur lokal in Prefs |
| Netzwerk-Metriken (optional) | ✅ Ja | ❌ Nein | Nur lokal in Memory |
| Benachrichtigungen | ✅ Ja | ❌ Nein | System-Benachrichtigungen |
| Cloud-Backup | ❌ Nein | ❌ Nein | Explizit deaktiviert |
| Telemetrie | ❌ Nein | ❌ Nein | Gar nicht implementiert |

## Fragen und Kontakt

Falls Sie noch Fragen zur Sicherheit oder zum Datenschutz haben:

### 🔍 Selbst überprüfen:

1. **Quellcode lesen** – Alles ist auf GitHub öffentlich
   - Kritische Dateien: `VpnFirewallService.kt`, `UsageAnalyzer.kt`, `FirewallController.kt`
   - Suchen Sie nach HTTP/HTTPS Anfragen – Sie werden keine finden

2. **Netzwerkverkehr monitoren** – Nutzen Sie Wireshark oder eine Firewall-App
   - Starten Sie SilentPort
   - Überprüfen Sie, dass KEINE Daten zu externen Servern gesendet werden
   - Überprüfen Sie die IP-Adressen, an die verbunden wird (sollte nur lokal sein)

3. **Android-Berechtigungen überprüfen** – Android-Einstellungen
   - Android Einstellungen > Apps > SilentPort > Berechtigungen
   - Sie sehen genau, welche Berechtigungen aktiv sind
   - Sie können diese einzeln widerrufen

4. **App-Daten einsehen** – Für fortgeschrittene Nutzer
   ```bash
   adb shell "sqlite3 /data/data/com.silentport.silentport/databases/silentport.db '.tables'"
   ```
   Dies zeigt Ihnen alle Tabellen und deren Inhalt

### 📧 Kontakt & Feedback:

- Haben Sie einen Datenschutz-Bedenken? Öffnen Sie ein GitHub Issue
- Haben Sie einen Verbesserungsvorschlag? Machen Sie einen Pull Request
- Haben Sie einen Sicherheitsfund? Bitte melden Sie diesen verantwortungsvoll

---

**Unser finales Versprechen: Wir lesen nur die minimal notwendigen Daten (App-Liste, letzter Zeitstempel), um die Kernfunktion zu erfüllen. Alle diese Daten verlassen niemals Ihr Gerät. Und das können Sie selbst überprüfen.**
