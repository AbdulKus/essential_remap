package com.abdulkus.essentialremap.platform

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicReference

class TorchController(context: Context) {
    private val cameraManager = context.applicationContext.getSystemService(CameraManager::class.java)
    private val torchCameraId = runCatching {
        cameraManager.cameraIdList.firstOrNull { cameraId ->
            cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()
    private val enabled = AtomicReference<Boolean?>(null)

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, isEnabled: Boolean) {
            if (cameraId == torchCameraId) enabled.set(isEnabled)
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchCameraId) enabled.set(null)
        }
    }

    init {
        runCatching {
            @Suppress("DEPRECATION")
            cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        }
    }

    fun toggle(): Result<Boolean> {
        val cameraId = torchCameraId
            ?: return Result.failure(IllegalStateException("This device has no available flashlight"))
        val target = enabled.get() != true
        return runCatching {
            cameraManager.setTorchMode(cameraId, target)
            enabled.set(target)
            target
        }
    }
}
