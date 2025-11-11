# Battery Analyzer

Battery Analyzer helps Android power users reclaim battery life by spotlighting neglected apps and automating blocklists.

## Features
- Tracks recent, rare, and disabled apps from Android usage events in near real time.
- Predicts when unused packages should be paused and nudges you with contextual notifications.
- Syncs a VPN-based firewall to block stale packages and automatically reactivates on schedule.
- Optional traffic sampler chart keeps tabs on data usage per package.

## Getting Started
1. Install the project with Android Studio Giraffe+ and open the `app` module.
2. Connect a device or emulator running Android 9 (API 28) or newer and tap **Run**.
3. On first launch grant Usage Access and VPN permissions so analytics and firewall policies can operate.

## Configuration
- Tune automation thresholds via the in-app allow-duration slider; the backend aligns disable and warning windows automatically.
- Toggle manual unblock mode when you need temporary exceptions; the firewall will honour your cooldowns.

## Support
Issues and ideas are welcome; open an issue or start a discussion to shape upcoming releases.
