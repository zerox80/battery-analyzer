package com.privacyguard.batteryanalyzer.firewall

import android.content.Context
import java.util.concurrent.atomic.AtomicReference

object FirewallControllerProvider {

    private val reference = AtomicReference<FirewallController?>()

    fun get(context: Context): FirewallController {
        reference.get()?.let { return it }
        synchronized(this) {
            reference.get()?.let { return it }
            val prefs = FirewallPreferencesDataSource(context.applicationContext)
            val controller = FirewallController(context.applicationContext, prefs)
            reference.set(controller)
            return controller
        }
    }
}
