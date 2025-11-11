package com.privacyguard.batteryanalyzer.di

import android.content.Context
import com.privacyguard.batteryanalyzer.data.local.AppDatabase
import com.privacyguard.batteryanalyzer.data.repository.UsageRepository
import com.privacyguard.batteryanalyzer.domain.ApplicationManager
import com.privacyguard.batteryanalyzer.domain.UsageAnalyzer
import com.privacyguard.batteryanalyzer.domain.UsagePolicy
import com.privacyguard.batteryanalyzer.firewall.FirewallController
import com.privacyguard.batteryanalyzer.firewall.FirewallPreferencesDataSource
import com.privacyguard.batteryanalyzer.settings.SettingsPreferencesDataSource

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    private val database: AppDatabase = AppDatabase.getInstance(appContext)
    private val trackedAppDao = database.trackedAppDao()

    private val usagePolicy: UsagePolicy = UsagePolicy()

    val usageAnalyzer: UsageAnalyzer = UsageAnalyzer(appContext, trackedAppDao, usagePolicy)
    val applicationManager: ApplicationManager = ApplicationManager(appContext, trackedAppDao)
    val usageRepository: UsageRepository = UsageRepository(trackedAppDao)

    private val firewallPreferences: FirewallPreferencesDataSource = FirewallPreferencesDataSource(appContext)
    val firewallController: FirewallController = FirewallController(appContext, firewallPreferences)

    val settingsPreferences: SettingsPreferencesDataSource = SettingsPreferencesDataSource(appContext)
}
