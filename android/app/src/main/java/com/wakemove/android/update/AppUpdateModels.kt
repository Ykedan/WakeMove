package com.wakemove.android.update

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val releaseNotes: String,
    val sha256: String,
)

enum class AppUpdatePhase {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    INSTALL_PERMISSION_REQUIRED,
    ERROR,
}

data class AppUpdateUiState(
    val phase: AppUpdatePhase = AppUpdatePhase.IDLE,
    val info: AppUpdateInfo? = null,
    val progressPercent: Int? = null,
    val showDialog: Boolean = false,
    val message: String? = null,
    val downloadId: Long? = null,
)

internal fun isVersionNewer(latest: String, current: String): Boolean {
    val latestParts = versionParts(latest)
    val currentParts = versionParts(current)
    val size = maxOf(latestParts.size, currentParts.size)
    repeat(size) { index ->
        val latestPart = latestParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (latestPart != currentPart) return latestPart > currentPart
    }
    return false
}

private fun versionParts(version: String): List<Int> = version
    .trim()
    .removePrefix("v")
    .removePrefix("V")
    .substringBefore('-')
    .split('.')
    .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
