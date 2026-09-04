package io.github.sxdroid

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.sxdroid.config.LauncherConfig
import io.github.sxdroid.input.KeyMapper
import io.github.sxdroid.input.LauncherAction
import io.github.sxdroid.input.PressKind
import io.github.sxdroid.input.DualVolumeController
import io.github.sxdroid.input.VolumeKey
import io.github.sxdroid.input.EdgeGestureAction
import io.github.sxdroid.input.EdgeGestureClassifier
import io.github.sxdroid.input.GestureBounds
import io.github.sxdroid.input.GesturePoint
import io.github.sxdroid.launcher.LauncherViewModel
import io.github.sxdroid.system.DeviceStatus
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.isSystemInDarkTheme

class MainActivity : ComponentActivity() {
    private val launcher: LauncherViewModel by viewModels()
    private val keyMapper = KeyMapper(LauncherConfig().keyBindings)
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val volumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (volumeKey && hasWindowFocus() && !destroyed) {
            val key = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) VolumeKey.UP else VolumeKey.DOWN
            if (event.action == KeyEvent.ACTION_DOWN) {
                performVolumeActions(volumeController.onDown(key, SystemClock.uptimeMillis(), event.isLongPress))
            } else if (event.action == KeyEvent.ACTION_UP) {
                performVolumeActions(volumeController.onUp(key, SystemClock.uptimeMillis()))
            }
            scheduleVolumeTimeout()
            return true // Never hand focused-launcher navigation volume keys to the system stream.
        }
        if (event.action == KeyEvent.ACTION_DOWN && !launcher.searchFocused &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            perform(keyMapper.actionFor(event.keyCode, PressKind.SHORT))
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun perform(action: LauncherAction) {
        when (action) {
            LauncherAction.PREVIOUS -> launcher.move(-1)
            LauncherAction.NEXT -> launcher.move(1)
            LauncherAction.SELECT -> launcher.select()
            LauncherAction.CONTEXT -> launcher.openSelectedContext()
            LauncherAction.BACK -> launcher.back()
            LauncherAction.NONE -> Unit
        }
    }

    private fun performVolumeActions(actions: List<LauncherAction>) = actions.forEach(::perform)

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
    val time by minuteClock()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val classifier = remember { EdgeGestureClassifier(LauncherConfig().edgeGestures) }
    var showGestureHelp by remember { mutableStateOf(false) }
    var showKeyboardRequest by remember { mutableStateOf(false) }
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
                EdgeGestureAction.SHOW_MENU -> { viewModel.resetToTopLevel(); showKeyboardRequest = true }
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
                EdgeGestureAction.OPEN_CONTEXT -> viewModel.requestContext(state.selectedIndex)
                EdgeGestureAction.OPEN_ACTION_MENU -> viewModel.showActionMenu()
                null -> Unit
            }
        }
    }
    Column(gestureModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        StatusHeader(time, status)
        Text(
            text = "[${state.title}] ${if (state.loading) "loading" else "${state.commands.size} commands"}",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { viewModel.searchFocused = it.isFocused }
                .semantics { contentDescription = "Search commands" },
            singleLine = true,
            label = { Text("search") },
            placeholder = { Text("type to filter") },
        )
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            itemsIndexed(state.commands, key = { _, command -> command.id }) { index, command ->
                val selected = index == state.selectedIndex
                CommandRow(
                    selected = selected,
                    name = command.name,
                    description = command.description,
                    onClick = { viewModel.select(index) },
                    onLongClick = { viewModel.highlight(index); viewModel.requestContext(index) },
                )
            }
        }
        Text("vol up/down: prev/next  both: open  hold up: context  back: up", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        TextButton(
            onClick = { showGestureHelp = true },
            modifier = Modifier.semantics { contentDescription = "Open gesture help" },
        ) { Text("[?] gesture map", fontSize = 12.sp) }
    }
    state.contextIndex?.let { index ->
        state.commands.getOrNull(index)?.let { command ->
            ActionMenuDialog(
                name = command.name,
                description = command.description,
                isApplication = viewModel.isApplication(index),
                onDismiss = viewModel::dismissContext,
                onOpen = { viewModel.dismissContext(); viewModel.select(index) },
                onAppInfo = { viewModel.dismissContext(); viewModel.openApplicationDetails(index) },
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
    if (showGestureHelp) GestureHelpDialog(onDismiss = { showGestureHelp = false })
}

@Composable
private fun ActionMenuDialog(name: String, description: String, isApplication: Boolean, onDismiss: () -> Unit, onOpen: () -> Unit, onAppInfo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = { Text(description) },
        confirmButton = { TextButton(onClick = onOpen) { Text("Open") } },
        dismissButton = {
            Row {
                if (isApplication) TextButton(onClick = onAppInfo) { Text("App info") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
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
private fun GestureHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SxDroid gesture map") },
        text = {
            Text(
                "top: left/right brightness; up close; down show\n" +
                    "left: up/down previous/next; right previous; left back\n" +
                    "right: up/down volume up/down; left next; right select\n" +
                    "bottom long: left Backspace; right Enter/select\n" +
                    "bottom vertical: action menu\n" +
                    "bottom-left diagonal: Lock fallback; bottom-right: Rotate fallback\n" +
                    "hold: action menu",
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun StatusHeader(time: String, status: DeviceStatus) {
    val battery = status.batteryPercent?.let { "$it%${if (status.charging) "+" else ""}" } ?: "battery ?"
    Row(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$time, $battery, ${status.network}" },
    ) {
        Text(time, fontWeight = FontWeight.Bold)
        Text("  $battery  ${status.network}", modifier = Modifier.weight(1f))
    }
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
private fun minuteClock(): androidx.compose.runtime.State<String> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val value = remember { mutableStateOf(formatTime()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            active = event != Lifecycle.Event.ON_STOP && event != Lifecycle.Event.ON_DESTROY
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active) {
        while (active) {
            value.value = formatTime()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 10L)
        }
    }
    return value
}

private fun formatTime(): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())

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
        Surface(color = MaterialTheme.colorScheme.surface, content = content)
    }
}
