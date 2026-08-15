package com.abdulkus.essentialremap.update

object UpdatePolicy {
    fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = parseVersion(remote) ?: return false
        val currentParts = parseVersion(current) ?: return false
        val maxSize = maxOf(remoteParts.size, currentParts.size)
        for (index in 0 until maxSize) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (remotePart != currentPart) return remotePart > currentPart
        }
        return false
    }

    /** Show on the 2nd configured launch, then every 5 launches: 2, 7, 12, 17... */
    fun shouldShowSupportPrompt(configuredLaunchCount: Long): Boolean =
        configuredLaunchCount >= 2L && (configuredLaunchCount - 2L) % 5L == 0L

    private fun parseVersion(raw: String): List<Int>? {
        val normalized = raw.trim().removePrefix("v").removePrefix("V").substringBefore('-')
        if (normalized.isBlank()) return null
        val parts = normalized.split('.')
        if (parts.isEmpty()) return null
        return parts.map { it.toIntOrNull() ?: return null }
    }
}
