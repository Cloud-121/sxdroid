package io.github.sxdroid.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesTest {
    @Test fun settings_toggle_only_the_selected_option() {
        val settings = HomeSettings().toggle(SettingOption.CLOCK).toggle(SettingOption.NETWORK)

        assertTrue(settings.showClock)
        assertTrue(settings.showNetwork)
        assertFalse(settings.showDate)
        assertFalse(settings.showBattery)
        assertTrue(settings.isEnabled(SettingOption.CLOCK))
    }

    @Test fun favorites_toggle_without_duplicates() {
        val appId = "app:example/.Main"
        val added = FavoriteApps().toggle(appId).toggle(appId).toggle(appId)

        assertTrue(appId in added)
        assertTrue(added.appIds.size == 1)
        assertFalse(appId in added.toggle(appId))
    }
}
