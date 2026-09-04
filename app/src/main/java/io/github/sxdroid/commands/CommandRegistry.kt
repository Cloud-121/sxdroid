package io.github.sxdroid.commands

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal fun builtInMenuCommands(): List<Command> = listOf(
    MenuCommand("menu.favorites", "Favorites", "Favorite applications", listOf("starred", "apps"), "favorites"),
    MenuCommand("menu.apps", "Apps", "Installed launchable applications", listOf("applications", "launch"), "apps"),
    MenuCommand("menu.configuration", "Configuration", "Launcher appearance settings", listOf("settings", "home", "overlay"), "configuration"),
    MenuCommand("menu.controls", "Controls", "Flashlight and volume controls", listOf("torch", "audio", "volume"), "controls"),
    MenuCommand("menu.system", "System", "Settings and device controls", listOf("settings", "wifi", "bluetooth"), "system"),
)

class CommandRegistry(private val context: Context) {
    private val packageManager get() = context.packageManager

    fun builtIns(): List<Command> = builtInMenuCommands()

    suspend fun installedApplications(): List<Command> = withContext(Dispatchers.Default) {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0))
        } else {
            packageManager.queryIntentActivities(query, 0)
        }
        activities.asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map { info ->
                val activity = info.activityInfo
                val label = info.loadLabel(packageManager).toString().ifBlank { activity.packageName }
                LaunchApplicationCommand(
                    id = "app:${activity.packageName}/${activity.name}",
                    name = label,
                    description = activity.packageName,
                    keywords = listOf(activity.packageName, label.lowercase(Locale.ROOT)),
                    packageName = activity.packageName,
                    activityName = activity.name,
                )
            }
            .distinctBy { it.id }
            .sortedWith(compareBy<Command> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })
            .toList()
    }
}
