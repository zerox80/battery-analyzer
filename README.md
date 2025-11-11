## PrivacyGuard Firewall
PrivacyGuard Firewall is an Android app that monitors and selectively blocks outgoing traffic via a local VPN interface. It is designed for users who want to temporarily or permanently mute power- and data-hungry apps.

### Core features
- Adaptive block lists: Automatic suggestions from usage profiles combined with manual allow and block lists.
- Flexible modes: Instant blocking, time-limited unblocking, and scheduled reactivation via WorkManager.
- Foreground service: Persistent VPN service with notifications, quick action to disable, and logging via Logcat.
- State management: DataStore-supported FirewallUiState streams for Compose interfaces and widgets.

### Quick start
1. Clone the repository and open it in Android Studio Hedgehog.
2. Deploy the app to a device running Android 11+ or an emulator with VPN support.
3. When starting for the first time, grant VPN permission, configure block lists, and activate the firewall.

### Support
Please report bugs or feature requests as issues. Send security-related information to rujbin@proton.me.


Translated with DeepL.com (free version)
