package io.github.sxdroid.config

import android.content.Context

data class HomeSettings(
    val showClock: Boolean = false,
    val showDate: Boolean = false,
    val showBattery: Boolean = false,
    val showNetwork: Boolean = false,
    val groupAppsByLetter: Boolean = true,
) {
    fun toggle(option: SettingOption): HomeSettings = when (option) {
        SettingOption.CLOCK -> copy(showClock = !showClock)
        SettingOption.DATE -> copy(showDate = !showDate)
        SettingOption.BATTERY -> copy(showBattery = !showBattery)
        SettingOption.NETWORK -> copy(showNetwork = !showNetwork)
        SettingOption.APP_LETTERS -> copy(groupAppsByLetter = !groupAppsByLetter)
    }

    fun isEnabled(option: SettingOption): Boolean = when (option) {
        SettingOption.CLOCK -> showClock
        SettingOption.DATE -> showDate
        SettingOption.BATTERY -> showBattery
        SettingOption.NETWORK -> showNetwork
        SettingOption.APP_LETTERS -> groupAppsByLetter
    }
}

enum class SettingOption(val commandId: String, val title: String) {
    CLOCK("configuration.clock", "Centered clock"),
    DATE("configuration.date", "Date"),
    BATTERY("configuration.battery", "Battery"),
    NETWORK("configuration.network", "Network"),
    APP_LETTERS("configuration.app_letters", "Group Apps by letter"),
}

data class FavoriteApps(val appIds: Set<String> = emptySet()) {
    operator fun contains(appId: String): Boolean = appId in appIds

    fun toggle(appId: String): FavoriteApps = FavoriteApps(
        if (appId in appIds) appIds - appId else appIds + appId,
    )
}

class LauncherPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun loadSettings() = HomeSettings(
        showClock = preferences.getBoolean(KEY_CLOCK, false),
        showDate = preferences.getBoolean(KEY_DATE, false),
        showBattery = preferences.getBoolean(KEY_BATTERY, false),
        showNetwork = preferences.getBoolean(KEY_NETWORK, false),
        groupAppsByLetter = preferences.getBoolean(KEY_GROUP_APPS, true),
    )

    fun saveSettings(settings: HomeSettings) {
        preferences.edit()
            .putBoolean(KEY_CLOCK, settings.showClock)
            .putBoolean(KEY_DATE, settings.showDate)
            .putBoolean(KEY_BATTERY, settings.showBattery)
            .putBoolean(KEY_NETWORK, settings.showNetwork)
            .putBoolean(KEY_GROUP_APPS, settings.groupAppsByLetter)
            .apply()
    }

    fun loadFavorites() = FavoriteApps(
        preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet(),
    )

    fun saveFavorites(favorites: FavoriteApps) {
        preferences.edit().putStringSet(KEY_FAVORITES, favorites.appIds).apply()
    }

    private companion object {
        const val FILE_NAME = "launcher_preferences"
        const val KEY_CLOCK = "home_clock"
        const val KEY_DATE = "home_date"
        const val KEY_BATTERY = "home_battery"
        const val KEY_NETWORK = "home_network"
        const val KEY_GROUP_APPS = "group_apps_by_letter"
        const val KEY_FAVORITES = "favorite_apps"
    }
}
