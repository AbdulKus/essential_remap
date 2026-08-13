package com.abdulkus.essentialremap

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ScreenOffKeyAccess {
    const val BLOCK_SETTING = "nt_block_essential_key"

    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = mutableChanges.asSharedFlow()

    fun isGranted(context: Context): Boolean =
        context.checkSelfPermission(READ_LOGS_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun notifyChanged() {
        mutableChanges.tryEmit(Unit)
    }

    private const val READ_LOGS_PERMISSION = "android.permission.READ_LOGS"
}
