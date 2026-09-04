package io.github.sxdroid.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager

sealed interface TorchResult {
    data class Changed(val enabled: Boolean) : TorchResult
    data object PermissionRequired : TorchResult
    data object Unavailable : TorchResult
    data object Error : TorchResult
}

class FlashlightController(private val context: Context) {
    private var enabled = false

    fun toggle(): TorchResult {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return TorchResult.PermissionRequired
        }
        val cameraManager = context.getSystemService(CameraManager::class.java) ?: return TorchResult.Unavailable
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return TorchResult.Unavailable
            val next = !enabled
            cameraManager.setTorchMode(cameraId, next)
            enabled = next
            TorchResult.Changed(enabled)
        } catch (_: SecurityException) {
            TorchResult.PermissionRequired
        } catch (_: Exception) {
            TorchResult.Error
        }
    }
}

object VolumeControls {
    fun adjust(context: Context, stream: Int, direction: Int): Result<Unit> = runCatching {
        val audio = requireNotNull(context.getSystemService(AudioManager::class.java))
        audio.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)
    }
}
