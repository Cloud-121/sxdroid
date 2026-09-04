package io.github.sxdroid.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sxdroid.commands.Command
import io.github.sxdroid.commands.CommandRanker
import io.github.sxdroid.commands.CommandRegistry
import io.github.sxdroid.commands.DeviceControl
import io.github.sxdroid.commands.DeviceControlCommand
import io.github.sxdroid.commands.MenuCommand
import io.github.sxdroid.commands.LaunchApplicationCommand
import io.github.sxdroid.commands.OpenIntentCommand
import io.github.sxdroid.commands.SettingCommand
import io.github.sxdroid.config.FavoriteApps
import io.github.sxdroid.config.HomeSettings
import io.github.sxdroid.config.LauncherPreferences
import io.github.sxdroid.config.SettingOption
import io.github.sxdroid.menu.CommandMenu
import io.github.sxdroid.menu.MenuNavigator
import io.github.sxdroid.system.DeviceStatus
import io.github.sxdroid.system.AndroidActions
import io.github.sxdroid.system.FlashlightController
import io.github.sxdroid.system.TorchResult
import io.github.sxdroid.system.VolumeControls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LauncherState(
    val menuVisible: Boolean = false,
    val menuId: String = "root",
    val title: String = "home",
    val query: String = "",
    val commands: List<Command> = emptyList(),
    val selectedIndex: Int = 0,
    val loading: Boolean = true,
    val message: String? = null,
    val contextIndex: Int? = null,
    val actionMenuVisible: Boolean = false,
    val settings: HomeSettings = HomeSettings(),
    val requestCameraPermission: Boolean = false,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val registry = CommandRegistry(application)
    private val preferences = LauncherPreferences(application)
    private val flashlight = FlashlightController(application)
    private var favorites = preferences.loadFavorites()
    private val menus = mutableMapOf<String, CommandMenu>()
    private lateinit var navigator: MenuNavigator
    private var applications: List<Command> = emptyList()
    private val _state = MutableStateFlow(LauncherState(settings = preferences.loadSettings()))
    val state: StateFlow<LauncherState> = _state.asStateFlow()
    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status.asStateFlow()
    var searchFocused = false
    val menuVisible: Boolean get() = _state.value.menuVisible

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshApplications()
        }
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateBattery(intent)
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateNetwork()
        override fun onLost(network: Network) = updateNetwork()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = updateNetwork()
    }
    private var packageReceiverRegistered = false
    private var batteryReceiverRegistered = false
    private var networkCallbackRegistered = false

    init {
        buildMenus()
        registerReceivers()
        refreshApplications()
    }

    private fun buildMenus() {
        val system = CommandMenu("system", "System", listOf(
            OpenIntentCommand("system.settings", "Settings", "Android settings", listOf("system", "preferences"), Intent(Settings.ACTION_SETTINGS)),
            OpenIntentCommand("system.wifi", "Wi-Fi", "Wi-Fi settings", listOf("wireless", "network"), Intent(Settings.ACTION_WIFI_SETTINGS)),
            OpenIntentCommand("system.bluetooth", "Bluetooth", "Bluetooth settings", listOf("wireless", "devices"), Intent(Settings.ACTION_BLUETOOTH_SETTINGS)),
            OpenIntentCommand("system.display", "Display", "Display settings", listOf("screen", "brightness"), Intent(Settings.ACTION_DISPLAY_SETTINGS)),
            OpenIntentCommand("system.sound", "Sound", "Sound settings", listOf("audio", "volume"), Intent(Settings.ACTION_SOUND_SETTINGS)),
        ))
        val apps = CommandMenu("apps", "Apps", listOf(favoritesMenuCommand()))
        val favorites = CommandMenu("favorites", "Favorites", emptyList())
        val configuration = CommandMenu("configuration", "Configuration", configurationCommands())
        val controls = CommandMenu("controls", "Controls", DeviceControl.entries.map(::DeviceControlCommand))
        val root = CommandMenu("root", "Menu", registry.builtIns())
        menus[root.id] = root
        menus[apps.id] = apps
        menus[favorites.id] = favorites
        menus[configuration.id] = configuration
        menus[controls.id] = controls
        menus[system.id] = system
        navigator = MenuNavigator(root, menus)
    }

    private fun registerReceivers() {
        val context = getApplication<Application>()
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(packageReceiver, packageFilter)
            }
            packageReceiverRegistered = true

            val batteryIntent = if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }
            batteryReceiverRegistered = true
            batteryIntent?.let(::updateBattery)

            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            if (connectivity != null) {
                connectivity.registerDefaultNetworkCallback(networkCallback)
                networkCallbackRegistered = true
            }
        } catch (_: SecurityException) {
            // A launcher must still render if a device policy blocks a status receiver.
        } catch (_: IllegalArgumentException) {
            // Avoid failing startup on devices with an unavailable system service.
        }
        updateNetwork()
    }

    private fun refreshApplications() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true)
        applications = registry.installedApplications()
        menus["apps"] = menus.getValue("apps").copy(commands = listOf(favoritesMenuCommand()) + applications)
        updateFavoritesMenu()
        publish(loading = false)
    }

    private fun favoritesMenuCommand() = MenuCommand(
        "menu.favorites",
        "Favorites",
        "Favorite applications",
        listOf("starred", "apps"),
        "favorites",
    )

    private fun configurationCommands(): List<Command> = SettingOption.entries.map { option ->
        SettingCommand(option, _state.value.settings.isEnabled(option))
    }

    private fun updateFavoritesMenu() {
        menus["favorites"] = menus.getValue("favorites").copy(
            commands = applications.filter { it.id in favorites },
        )
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query, selectedIndex = 0, message = null)
        publish()
    }

    fun deleteSearchCharacter() {
        if (_state.value.query.isNotEmpty()) setQuery(_state.value.query.dropLast(1)) else back()
    }

    fun openBrightnessControls(increase: Boolean) {
        val direction = if (increase) "increase" else "decrease"
        AndroidActions.openDisplayControls(getApplication()).fold(
            onSuccess = { _state.value = _state.value.copy(message = "Use Display settings to $direction brightness") },
            onFailure = { _state.value = _state.value.copy(message = "Display controls are unavailable") },
        )
    }

    fun openLockFallback() = openFallback("Lock is unavailable to an ordinary launcher; opening Security settings") {
        AndroidActions.openSecuritySettings(getApplication())
    }

    fun openRotateFallback() = openFallback("Rotation is unavailable to an ordinary launcher; opening Display settings") {
        AndroidActions.openDisplayControls(getApplication())
    }

    fun reportGlobalWindowAction(action: String) {
        _state.value = _state.value.copy(message = "$action window is unavailable to an ordinary Android launcher")
    }

    private fun openFallback(message: String, action: () -> Result<Unit>) {
        action().fold(
            onSuccess = { _state.value = _state.value.copy(message = message) },
            onFailure = { _state.value = _state.value.copy(message = "Android settings fallback is unavailable") },
        )
    }

    fun openApplicationDetails(index: Int) {
        val command = _state.value.commands.getOrNull(index) as? LaunchApplicationCommand ?: return
        AndroidActions.openApplicationDetails(getApplication(), command.packageName)
            .onFailure { _state.value = _state.value.copy(message = "Application details are unavailable") }
    }

    fun isApplication(index: Int): Boolean = _state.value.commands.getOrNull(index) is LaunchApplicationCommand

    fun isFavorite(index: Int): Boolean = _state.value.commands.getOrNull(index)?.let { it.id in favorites } == true

    fun toggleFavorite(index: Int) {
        val command = _state.value.commands.getOrNull(index) as? LaunchApplicationCommand ?: return
        favorites = favorites.toggle(command.id)
        preferences.saveFavorites(favorites)
        updateFavoritesMenu()
        _state.value = _state.value.copy(
            contextIndex = null,
            message = if (command.id in favorites) "Added ${command.name} to favorites" else "Removed ${command.name} from favorites",
        )
        publish()
    }

    fun openSelectedContext() {
        requestContext(_state.value.selectedIndex)
    }

    fun requestContext(index: Int) {
        if (_state.value.menuVisible && index in _state.value.commands.indices) {
            _state.value = _state.value.copy(contextIndex = index)
        }
    }

    fun dismissContext() { _state.value = _state.value.copy(contextIndex = null) }

    fun showActionMenu() { _state.value = _state.value.copy(actionMenuVisible = true) }
    fun dismissActionMenu() { _state.value = _state.value.copy(actionMenuVisible = false) }

    fun closeAllMenus(): Boolean {
        val hadMenu = _state.value.menuVisible
        val hadQuery = _state.value.query.isNotEmpty()
        val hadDialogs = _state.value.contextIndex != null || _state.value.actionMenuVisible
        val closedMenus = navigator.closeAll()
        val consumed = hadMenu || hadQuery || hadDialogs || closedMenus
        if (consumed) {
            _state.value = _state.value.copy(
                menuVisible = false,
                query = "",
                selectedIndex = 0,
                message = null,
                contextIndex = null,
                actionMenuVisible = false,
                requestCameraPermission = false,
            )
            publish()
        }
        return consumed
    }

    fun showMenu() {
        navigator.closeAll()
        _state.value = _state.value.copy(
            menuVisible = true,
            query = "",
            selectedIndex = 0,
            message = null,
            contextIndex = null,
            actionMenuVisible = false,
            requestCameraPermission = false,
        )
        publish()
    }

    fun move(delta: Int) {
        if (!_state.value.menuVisible) return
        val commands = _state.value.commands
        if (commands.isEmpty()) return
        val index = (_state.value.selectedIndex + delta).floorMod(commands.size)
        _state.value = _state.value.copy(selectedIndex = index)
    }

    fun highlight(index: Int) {
        if (_state.value.menuVisible && index in _state.value.commands.indices) _state.value = _state.value.copy(selectedIndex = index)
    }

    fun select(index: Int = _state.value.selectedIndex) {
        if (!_state.value.menuVisible) return
        val command = _state.value.commands.getOrNull(index) ?: return
        if (command is MenuCommand) {
            navigator.enter(command.menuId)
            _state.value = _state.value.copy(query = "", selectedIndex = 0, message = null)
            publish()
            return
        }
        if (command is SettingCommand) {
            toggleSetting(command.option)
            return
        }
        if (command is DeviceControlCommand) {
            runDeviceControl(command.control)
            return
        }
        viewModelScope.launch {
            runCatching { command.execute(getApplication()) }
                .onFailure { _state.value = _state.value.copy(message = "Unable to open ${command.name}") }
        }
    }

    private fun toggleSetting(option: SettingOption) {
        val settings = _state.value.settings.toggle(option)
        preferences.saveSettings(settings)
        _state.value = _state.value.copy(settings = settings, message = "${option.title}: ${if (settings.isEnabled(option)) "on" else "off"}")
        menus["configuration"] = menus.getValue("configuration").copy(commands = configurationCommands())
        publish()
    }

    private fun runDeviceControl(control: DeviceControl) {
        when (control) {
            DeviceControl.FLASHLIGHT -> handleTorchResult(flashlight.toggle())
            DeviceControl.MEDIA_VOLUME_UP -> adjustVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, "Media volume raised")
            DeviceControl.MEDIA_VOLUME_DOWN -> adjustVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, "Media volume lowered")
            DeviceControl.RING_VOLUME_UP -> adjustVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_RAISE, "Ring volume raised")
            DeviceControl.RING_VOLUME_DOWN -> adjustVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_LOWER, "Ring volume lowered")
        }
    }

    private fun adjustVolume(stream: Int, direction: Int, successMessage: String) {
        VolumeControls.adjust(getApplication(), stream, direction).fold(
            onSuccess = { _state.value = _state.value.copy(message = successMessage) },
            onFailure = { _state.value = _state.value.copy(message = "Volume control is unavailable") },
        )
    }

    private fun handleTorchResult(result: TorchResult) {
        _state.value = when (result) {
            is TorchResult.Changed -> _state.value.copy(message = "Flashlight ${if (result.enabled) "on" else "off"}")
            TorchResult.PermissionRequired -> _state.value.copy(requestCameraPermission = true, message = "Camera permission is needed for the flashlight")
            TorchResult.Unavailable -> _state.value.copy(message = "Flashlight is unavailable on this device")
            TorchResult.Error -> _state.value.copy(message = "Unable to change the flashlight")
        }
    }

    fun onCameraPermissionResult(granted: Boolean) {
        _state.value = _state.value.copy(requestCameraPermission = false)
        if (granted) handleTorchResult(flashlight.toggle())
        else _state.value = _state.value.copy(message = "Camera permission denied; flashlight unavailable")
    }

    /** Returns true when launcher navigation consumed the back press. */
    fun back(): Boolean {
        if (!_state.value.menuVisible) return false
        if (_state.value.query.isNotEmpty()) {
            setQuery("")
            return true
        }
        if (navigator.back()) {
            _state.value = _state.value.copy(selectedIndex = 0, message = null)
            publish()
            return true
        }
        return closeAllMenus()
    }

    private fun publish(loading: Boolean = _state.value.loading) {
        val current = navigator.current
        val commands = CommandRanker.rank(current.commands, _state.value.query)
        _state.value = _state.value.copy(
            menuId = current.id,
            title = current.title,
            commands = commands,
            selectedIndex = 0.coerceAtMost((commands.size - 1).coerceAtLeast(0)),
            loading = loading,
        )
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        _status.value = _status.value.copy(
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }

    private fun updateNetwork() {
        val connectivity = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivity?.activeNetwork
        val capabilities = connectivity?.getNetworkCapabilities(activeNetwork)
        val network = when {
            capabilities == null -> "network off"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "network ?"
        }
        _status.value = _status.value.copy(network = network)
    }

    override fun onCleared() {
        val context = getApplication<Application>()
        if (packageReceiverRegistered) {
            runCatching { context.unregisterReceiver(packageReceiver) }
            packageReceiverRegistered = false
        }
        if (batteryReceiverRegistered) {
            runCatching { context.unregisterReceiver(batteryReceiver) }
            batteryReceiverRegistered = false
        }
        if (networkCallbackRegistered) {
            context.getSystemService(ConnectivityManager::class.java)?.let { connectivity ->
                runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
            }
            networkCallbackRegistered = false
        }
        super.onCleared()
    }
}

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
