package io.github.sxdroid

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.ExperimentalComposeUiApi
import io.github.sxdroid.config.LauncherConfig
import io.github.sxdroid.input.LauncherAction
import io.github.sxdroid.input.DualVolumeController
import io.github.sxdroid.input.VolumeKey
import io.github.sxdroid.input.EdgeGestureAction
import io.github.sxdroid.input.EdgeGestureClassifier
import io.github.sxdroid.input.GestureBounds
import io.github.sxdroid.input.GesturePoint
import io.github.sxdroid.launcher.LauncherViewModel
import io.github.sxdroid.config.HomeSettings
import io.github.sxdroid.system.DeviceStatus
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val launcher: LauncherViewModel by viewModels()
    private val volumeController = DualVolumeController()
    private val keyHandler = Handler(Looper.getMainLooper())
    private var destroyed = false
    private val volumeTimeout = Runnable {
        if (!destroyed) performVolumeActions(volumeController.onTime(SystemClock.uptimeMillis()))
        scheduleVolumeTimeout()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!launcher.back()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        setContent { SxDroidTheme { LauncherScreen(launcher) } }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val volumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (volumeKey && !destroyed) {
            val key = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) VolumeKey.UP else VolumeKey.DOWN
            if (event.action == KeyEvent.ACTION_DOWN) {
                performVolumeActions(volumeController.onDown(key, SystemClock.uptimeMillis(), event.isLongPress))
            } else if (event.action == KeyEvent.ACTION_UP) {
                performVolumeActions(volumeController.onUp(key, SystemClock.uptimeMillis()))
            }
            scheduleVolumeTimeout()
            return true // Never hand focused-launcher navigation volume keys to the system stream.
        }
        val action = when (event.keyCode) {
            KeyEvent.KEYCODE_MENU -> null
            KeyEvent.KEYCODE_DPAD_UP -> LauncherAction.PREVIOUS
            KeyEvent.KEYCODE_DPAD_DOWN -> LauncherAction.NEXT
            KeyEvent.KEYCODE_DPAD_LEFT -> LauncherAction.BACK
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> LauncherAction.SELECT
            else -> null
        }
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_DOWN) launcher.showMenu()
            return true
        }
        if (action != null && !launcher.searchFocused) {
            if (event.action == KeyEvent.ACTION_DOWN) perform(action)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun perform(action: LauncherAction, opensMenuWhenClosed: Boolean = true) {
        if (!launcher.menuVisible && opensMenuWhenClosed) {
            launcher.showMenu()
            if (action == LauncherAction.SELECT || action == LauncherAction.CONTEXT || action == LauncherAction.BACK) return
        }
        when (action) {
            LauncherAction.PREVIOUS -> launcher.move(-1)
            LauncherAction.NEXT -> launcher.move(1)
            LauncherAction.SELECT -> launcher.select()
            LauncherAction.CONTEXT -> launcher.openSelectedContext()
            LauncherAction.BACK -> launcher.back()
            LauncherAction.NONE -> Unit
        }
    }

    private fun performVolumeActions(actions: List<LauncherAction>) {
        if (actions.isEmpty()) return
        if (!launcher.menuVisible) launcher.showMenu()
        actions.forEach { perform(it, opensMenuWhenClosed = false) }
    }

    private fun scheduleVolumeTimeout() {
        keyHandler.removeCallbacks(volumeTimeout)
        volumeController.nextDeadlineMillis()?.let { keyHandler.postDelayed(volumeTimeout, (it - SystemClock.uptimeMillis()).coerceAtLeast(0L)) }
    }

    override fun onDestroy() {
        destroyed = true
        keyHandler.removeCallbacks(volumeTimeout)
        volumeController.clear()
        super.onDestroy()
    }

}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun LauncherScreen(viewModel: LauncherViewModel) {
    val state by viewModel.state.collectAsState()
    val status by viewModel.status.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val classifier = remember { EdgeGestureClassifier(LauncherConfig().edgeGestures) }
    var showKeyboardRequest by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val selectedIndex by rememberUpdatedState(state.selectedIndex)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onCameraPermissionResult(it)
    }
    LaunchedEffect(state.requestCameraPermission) {
        if (state.requestCameraPermission) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(showKeyboardRequest) {
        if (showKeyboardRequest) {
            focusRequester.requestFocus()
            androidx.compose.runtime.withFrameNanos { }
            keyboard?.show()
            showKeyboardRequest = false
        }
    }
    val gestureModifier = Modifier.pointerInput(classifier, viewModel.searchFocused) {
        awaitEachGesture {
            val start = awaitFirstDown(requireUnconsumed = false)
            var end = start
            var pointerCount = 1
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                pointerCount = maxOf(pointerCount, event.changes.size)
                event.changes.firstOrNull { it.id == start.id }?.let { end = it }
            } while (event.changes.any { it.pressed })
            when (classifier.classify(
                GesturePoint(start.position.x, start.position.y),
                GesturePoint(end.position.x, end.position.y),
                GestureBounds(size.width.toFloat(), size.height.toFloat()),
                durationMillis = end.uptimeMillis - start.uptimeMillis,
                pointerCount = pointerCount,
            )) {
                EdgeGestureAction.SHOW_MENU -> viewModel.showMenu()
                EdgeGestureAction.CLOSE_MENUS -> { viewModel.closeAllMenus(); focusRequester.freeFocus(); keyboard?.hide() }
                EdgeGestureAction.SELECT, EdgeGestureAction.RIGHT_KEY -> viewModel.select()
                EdgeGestureAction.BACKSPACE -> viewModel.deleteSearchCharacter()
                EdgeGestureAction.NEXT, EdgeGestureAction.VOLUME_DOWN -> viewModel.move(1)
                EdgeGestureAction.PREVIOUS, EdgeGestureAction.VOLUME_UP -> viewModel.move(-1)
                EdgeGestureAction.BACK -> viewModel.back()
                EdgeGestureAction.BRIGHTNESS_UP -> viewModel.openBrightnessControls(increase = true)
                EdgeGestureAction.BRIGHTNESS_DOWN -> viewModel.openBrightnessControls(increase = false)
                EdgeGestureAction.LOCK_FALLBACK -> viewModel.openLockFallback()
                EdgeGestureAction.ROTATE_FALLBACK -> viewModel.openRotateFallback()
                EdgeGestureAction.OPEN_CONTEXT -> viewModel.requestContext(selectedIndex)
                EdgeGestureAction.OPEN_ACTION_MENU -> viewModel.showActionMenu()
                null -> Unit
            }
        }
    }
    Box(gestureModifier.fillMaxSize()) {
        if (!state.menuVisible) HomeOverlay(state.settings, status)
        if (state.menuVisible) {
            LaunchedEffect(state.menuId, state.selectedIndex, state.menuVisible) {
                if (state.selectedIndex in state.commands.indices) listState.scrollToItem(state.selectedIndex)
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = state.title,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { viewModel.searchFocused = it.isFocused }
                            .semantics { contentDescription = "Search commands" },
                        singleLine = true,
                        placeholder = { Text("search") },
                    )
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        itemsIndexed(state.commands, key = { _, command -> "${state.menuId}:${command.id}" }) { index, command ->
                            val selected = index == state.selectedIndex
                            CommandRow(
                                selected = selected,
                                name = command.name,
                                description = command.description,
                                onClick = { viewModel.highlight(index); viewModel.select(index) },
                                onLongClick = { viewModel.highlight(index); viewModel.requestContext(index) },
                            )
                        }
                    }
                }
            }
        }
    }
    state.contextIndex?.let { index ->
        state.commands.getOrNull(index)?.let { command ->
            ActionMenuDialog(
                name = command.name,
                description = command.description,
                isApplication = viewModel.isApplication(index),
                isFavorite = viewModel.isFavorite(index),
                onDismiss = viewModel::dismissContext,
                onOpen = { viewModel.dismissContext(); viewModel.select(index) },
                onAppInfo = { viewModel.dismissContext(); viewModel.openApplicationDetails(index) },
                onToggleFavorite = { viewModel.toggleFavorite(index) },
            )
        }
    }
    if (state.actionMenuVisible) FourActionDialog(
        onDismiss = viewModel::dismissActionMenu,
        onClose = { viewModel.dismissActionMenu(); viewModel.reportGlobalWindowAction("Close") },
        onKill = { viewModel.dismissActionMenu(); viewModel.reportGlobalWindowAction("Kill") },
        onHideKeyboard = { viewModel.dismissActionMenu(); focusRequester.freeFocus(); keyboard?.hide() },
        onShowKeyboard = { viewModel.dismissActionMenu(); showKeyboardRequest = true },
    )
}

@Composable
private fun ActionMenuDialog(
    name: String,
    description: String,
    isApplication: Boolean,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onAppInfo: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = { Text(description) },
        confirmButton = { TextButton(onClick = onOpen) { Text("Open") } },
        dismissButton = {
            Column {
                if (isApplication) TextButton(onClick = onToggleFavorite) { Text(if (isFavorite) "Remove favorite" else "Add favorite") }
                if (isApplication) TextButton(onClick = onAppInfo) { Text("App info") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun HomeOverlay(settings: HomeSettings, status: DeviceStatus) {
    if (!settings.showClock && !settings.showDate && !settings.showBattery && !settings.showNetwork) return
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val locale = Locale.getDefault()
    val lines = buildList {
        if (settings.showClock) add(SimpleDateFormat("HH:mm", locale).format(Date(now)))
        if (settings.showDate) add(SimpleDateFormat("EEE, MMM d", locale).format(Date(now)))
        if (settings.showBattery) {
            add(status.batteryPercent?.let { "battery $it%${if (status.charging) " charging" else ""}" } ?: "battery ?")
        }
        if (settings.showNetwork) add(status.network)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.background(Color.Black.copy(alpha = 0.32f)).padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            lines.forEachIndexed { index, line ->
                Text(
                    text = line,
                    color = Color.White,
                    fontSize = if (settings.showClock && index == 0) 36.sp else 14.sp,
                    fontWeight = if (settings.showClock && index == 0) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun FourActionDialog(
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    onKill: () -> Unit,
    onHideKeyboard: () -> Unit,
    onShowKeyboard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Window actions") },
        text = { Text("Global window control is unavailable to an ordinary Android launcher.") },
        confirmButton = {
            Column {
                TextButton(onClick = onClose) { Text("Close window") }
                TextButton(onClick = onKill) { Text("Kill window") }
                TextButton(onClick = onHideKeyboard) { Text("Hide keyboard") }
                TextButton(onClick = onShowKeyboard) { Text("Show keyboard") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CommandRow(selected: Boolean, name: String, description: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val marker = if (selected) ">" else " "
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { this.selected = selected; role = Role.Button; contentDescription = "$name, $description" }
            .padding(horizontal = 6.dp, vertical = 9.dp),
    ) {
        Text(marker, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
        Column {
            Text(name, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SxDroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        typography = androidx.compose.material3.Typography().let { it.copy(
            bodyLarge = it.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            bodyMedium = it.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            titleMedium = it.titleMedium.copy(fontFamily = FontFamily.Monospace),
        ) },
    ) {
        content()
    }
}
