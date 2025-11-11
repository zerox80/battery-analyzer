package com.example.batteryanalyzer.firewall

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.batteryanalyzer.MainActivity
import com.example.batteryanalyzer.R
import java.io.IOException
import java.io.InputStream

class VpnFirewallService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var drainThread: Thread? = null
    private var inputStream: InputStream? = null
    private var blockedPackages: Set<String> = emptySet()


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START_OR_UPDATE) {
            val blocking = intent.getBooleanExtra(EXTRA_BLOCKING, false)
            blockedPackages = intent.getStringArrayListExtra(EXTRA_BLOCK_LIST)?.toSet() ?: emptySet()
            startForeground(NOTIFICATION_ID, buildNotification(blocking))
            updateVpn(blocking)
            return START_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }

    private fun buildNotification(isBlocking: Boolean): Notification {
        val channelId = FIREWALL_CHANNEL_ID
        val titleRes = if (isBlocking) {
            R.string.firewall_notification_title_blocking
        } else {
            R.string.firewall_notification_title_allowing
        }
        val textRes = if (isBlocking) {
            R.string.firewall_notification_text_blocking
        } else {
            R.string.firewall_notification_text_allowing
        }

        val disableIntent = Intent(this, FirewallNotificationReceiver::class.java).apply {
            action = FirewallNotificationReceiver.ACTION_DISABLE_FIREWALL
        }
        val disablePendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            disableIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(getString(titleRes))
            .setContentText(getString(textRes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(
                NotificationCompat.Action(
                    0,
                    getString(R.string.firewall_notification_action_disable),
                    disablePendingIntent
                )
            )
            .build()
    }

    private fun updateVpn(blocking: Boolean) {
        if (blocking) {
            if (blockedPackages.isEmpty()) {
                stopVpn()
            } else {
                startVpn()
            }
        } else {
            stopVpn()
        }
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        val builder = Builder()
            .setSession(getString(R.string.firewall_vpn_session_name))

        // Assign a virtual local address to the VPN interface.
        builder.addAddress("10.0.0.2", 32)
        builder.addRoute("0.0.0.0", 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            builder.addAddress("fd00:1:fd00::1", 128)
            builder.addRoute("::", 0)
        }

        if (blockedPackages.isEmpty()) {
            stopVpn()
            return
        }

        blockedPackages.forEach { pkg ->
            if (pkg != packageName) {
                runCatching { builder.addAllowedApplication(pkg) }
                    .onFailure { Log.w(TAG, "Unable to include $pkg in VPN", it) }
            }
        }

        vpnInterface = builder.establish()
        vpnInterface?.let { descriptor ->
            inputStream = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            drainPackets()
        }
    }

    private fun stopVpn() {
        drainThread?.interrupt()
        drainThread = null

        try {
            inputStream?.close()
        } catch (_: IOException) {
        }
        inputStream = null

        try {
            vpnInterface?.close()
        } catch (_: IOException) {
        }
        vpnInterface = null
    }

    private fun drainPackets() {
        val stream = inputStream ?: return
        drainThread?.interrupt()
        drainThread = Thread {
            val buffer = ByteArray(32767)
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val read = stream.read(buffer)
                    if (read <= 0) {
                        Thread.sleep(50)
                    }
                } catch (_: IOException) {
                    break
                } catch (_: InterruptedException) {
                    break
                }
            }
            try {
                stream.close()
            } catch (_: IOException) {
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    companion object {
        const val ACTION_START_OR_UPDATE = "com.example.batteryanalyzer.firewall.START"
        const val ACTION_STOP = "com.example.batteryanalyzer.firewall.STOP"
        const val EXTRA_BLOCKING = "extra_blocking"
        const val EXTRA_BLOCK_LIST = "extra_block_list"
        const val FIREWALL_CHANNEL_ID = "firewall_channel"
        private const val NOTIFICATION_ID = 1011
        private const val TAG = "VpnFirewallService"
    }
}
