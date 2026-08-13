package com.abdulkus.essentialremap.platform

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.abdulkus.essentialremap.KeyAccessibilityService

data class AccessibilityStatus(
    val serviceEnabled: Boolean,
    val competingKeyServices: List<String>,
)

class AccessibilityStatusReader(private val context: Context) {
    fun read(): AccessibilityStatus {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val ownClassName = KeyAccessibilityService::class.java.name
        val ownPackage = context.packageName
        val ownEnabled = services.any {
            it.resolveInfo.serviceInfo.packageName == ownPackage &&
                it.resolveInfo.serviceInfo.name == ownClassName
        }
        val competing = services.filter { service ->
            val isOurs = service.resolveInfo.serviceInfo.packageName == ownPackage &&
                service.resolveInfo.serviceInfo.name == ownClassName
            !isOurs && service.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0
        }.map { service ->
            service.resolveInfo.loadLabel(context.packageManager).toString()
        }.distinct()
        return AccessibilityStatus(ownEnabled, competing)
    }
}
