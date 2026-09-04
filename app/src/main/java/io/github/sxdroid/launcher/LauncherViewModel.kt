package io.github.sxdroid.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sxdroid.commands.Command
import io.github.sxdroid.commands.CommandRanker
import io.github.sxdroid.commands.CommandRegistry
import io.github.sxdroid.commands.MenuCommand
import io.github.sxdroid.commands.LaunchApplicationCommand
import io.github.sxdroid.commands.OpenIntentCommand
import io.github.sxdroid.menu.CommandMenu
import io.github.sxdroid.menu.MenuNavigator
import io.github.sxdroid.system.DeviceStatus
import io.github.sxdroid.system.AndroidActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LauncherState(
    val title: String = "home",
    val query: String = "",
    val commands: List<Command> = emptyList(),
    val selectedIndex: Int = 0,
    val loading: Boolean = true,
    val message: String? = null,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val registry = CommandRegistry(application)
    private val menus = mutableMapOf<String, CommandMenu>()
    private lateinit var navigator: MenuNavigator
    private var applications: List<Command> = emptyList()
    private val _state = MutableStateFlow(LauncherState())
    val state: StateFlow<LauncherState> = _state.asStateFlow()
    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status.asStateFlow()
    var searchFocused = false

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

    init {
        buildMenus()
        registerReceivers()
        refreshApplications()
    }

    private fun buildMenus() {
        val system = CommandMenu("system", "system", listOf(
            OpenIntentCommand("system.settings", "Settings", "Android settings", listOf("system", "preferences"), Intent(Settings.ACTION_SETTINGS)),
            OpenIntentCommand("system.wifi", "Wi-Fi", "Wi-Fi settings", listOf("wireless", "network"), Intent(Settings.ACTION_WIFI_SETTINGS)),
            OpenIntentCommand("system.bluetooth", "Bluetooth", "Bluetooth settings", listOf("wireless", "devices"), Intent(Settings.ACTION_BLUETOOTH_SETTINGS)),
            OpenIntentCommand("system.display", "Display", "Display settings", listOf("screen", "brightness"), Intent(Settings.ACTION_DISPLAY_SETTINGS)),
            OpenIntentCommand("system.sound", "Sound", "Sound settings", listOf("audio", "volume"), Intent(Settings.ACTION_SOUND_SETTINGS)),
        ))
        val root = CommandMenu("root", "home", registry.builtIns())
        menus[root.id] = root
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
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(packageReceiver, packageFilter)
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::updateBattery)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(networkCallback)
        updateNetwork()
    }

    private fun refreshApplications() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true)
        applications = registry.installedApplications()
        val root = menus.getValue("root").copy(commands = registry.builtIns() + applications)
        menus[root.id] = root
        publish(loading = false)
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

    fun openApplicationDetails(index: Int) {
        val command = _state.value.commands.getOrNull(index) as? LaunchApplicationCommand ?: return
        AndroidActions.openApplicationDetails(getApplication(), command.packageName)
            .onFailure { _state.value = _state.value.copy(message = "Application details are unavailable") }
    }

    fun isApplication(index: Int): Boolean = _state.value.commands.getOrNull(index) is LaunchApplicationCommand

    fun closeAllMenus(): Boolean {
        val hadQuery = _state.value.query.isNotEmpty()
        val closedMenus = navigator.closeAll()
        val consumed = hadQuery || closedMenus
        if (consumed) {
            _state.value = _state.value.copy(query = "", selectedIndex = 0, message = null)
            publish()
        }
        return consumed
    }

    fun move(delta: Int) {
        val commands = _state.value.commands
        if (commands.isEmpty()) return
        val index = (_state.value.selectedIndex + delta).floorMod(commands.size)
        _state.value = _state.value.copy(selectedIndex = index)
    }

    fun highlight(index: Int) {
        if (index in _state.value.commands.indices) _state.value = _state.value.copy(selectedIndex = index)
    }

    fun select(index: Int = _state.value.selectedIndex) {
        val command = _state.value.commands.getOrNull(index) ?: return
        if (command is MenuCommand) {
            navigator.enter(command.menuId)
            _state.value = _state.value.copy(query = "", selectedIndex = 0, message = null)
            publish()
            return
        }
        viewModelScope.launch {
            runCatching { command.execute(getApplication()) }
                .onFailure { _state.value = _state.value.copy(message = "Unable to open ${command.name}") }
        }
    }

    /** Returns true when launcher navigation consumed the back press. */
    fun back(): Boolean {
        if (_state.value.query.isNotEmpty()) {
            setQuery("")
            return true
        }
        if (navigator.back()) {
            _state.value = _state.value.copy(selectedIndex = 0, message = null)
            publish()
            return true
        }
        return false
    }

    private fun publish(loading: Boolean = _state.value.loading) {
        val current = navigator.current
        val commands = CommandRanker.rank(current.commands, _state.value.query)
        _state.value = _state.value.copy(title = current.title, commands = commands, selectedIndex = 0.coerceAtMost((commands.size - 1).coerceAtLeast(0)), loading = loading)
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
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
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
        context.unregisterReceiver(packageReceiver)
        context.unregisterReceiver(batteryReceiver)
        context.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        super.onCleared()
    }
}

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
