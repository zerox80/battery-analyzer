## PrivacyGuard Firewall
PrivacyGuard Firewall ist eine Android-App, die ausgehenden Traffic per lokaler VPN-Schnittstelle überwacht und gezielt blockiert. Sie richtet sich an Nutzerinnen und Nutzer, die strom- und datenhungrige Apps temporär oder dauerhaft stummschalten möchten.

### Kernfunktionen
- Adaptive Blocklisten: Automatische Vorschläge aus Nutzungsprofilen kombiniert mit manuellen Allow- und Blocklists.
- Flexible Modi: Sofortiges Blockieren, zeitlich begrenztes Freischalten sowie geplante Reaktivierung per WorkManager.
- Vordergrunddienst: Persistenter VPN-Service mit Benachrichtigungen, Schnellaktion zum Deaktivieren und Logging per Logcat.
- Zustandsverwaltung: DataStore-gestützte FirewallUiState-Streams für Compose-Oberflächen und Widgets.

### Schnellstart
1. Repository klonen und in Android Studio Hedgehog öffnen.
2. App auf ein Gerät mit Android 11+ oder Emulator mit VPN-Unterstützung deployen.
3. Beim ersten Start VPN-Berechtigung erteilen, Blocklisten konfigurieren und Firewall aktivieren.

### Support
Fehler oder Feature-Wünsche bitte als Issue melden. Sicherheitsrelevante Hinweise an security@privacyguard.dev senden.
