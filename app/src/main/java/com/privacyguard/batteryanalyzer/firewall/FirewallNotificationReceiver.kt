package com.privacyguard.batteryanalyzer.firewall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FirewallNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISABLE_FIREWALL) {
            CoroutineScope(Dispatchers.IO).launch {
                val controller = FirewallControllerProvider.get(context.applicationContext)
                controller.disableFirewall()
            }
        }
    }

    companion object {
        const val ACTION_DISABLE_FIREWALL = "com.privacyguard.batteryanalyzer.firewall.DISABLE"
    }
}
