package com.wakemove.android.update

import com.wakemove.android.i18n.tr

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.wakemove.android.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppUpdateManager(
    context: Context,
    private val scope: CoroutineScope,
    private val repository: UpdateRepository = GitHubUpdateRepository(),
) {
    private val applicationContext = context.applicationContext
    private val legacyDownloadManager =
        applicationContext.getSystemService(DownloadManager::class.java)
    private val preferences = applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val _state = MutableStateFlow(AppUpdateUiState())
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()
    private val checkedThisSession = AtomicBoolean(false)
    private var downloadJob: Job? = null

    init {
        restoreDownloadIfPresent()
    }

    fun checkForUpdate(manual: Boolean) {
        if (manual) checkedThisSession.set(true)
        if (!manual && !checkedThisSession.compareAndSet(false, true)) return
        if (_state.value.phase == AppUpdatePhase.DOWNLOADING) return
        scope.launch {
            _state.value = AppUpdateUiState(
                phase = AppUpdatePhase.CHECKING,
                showDialog = manual,
            )
            runCatching {
                withContext(Dispatchers.IO) { repository.latestRelease() }
            }.onSuccess { info ->
                val newer = info.versionCode > BuildConfig.VERSION_CODE
                val ignored = preferences.getString(KEY_IGNORED_VERSION, null)
                _state.value = if (newer && (manual || ignored != info.versionName)) {
                    AppUpdateUiState(
                        phase = AppUpdatePhase.AVAILABLE,
                        info = info,
                        showDialog = true,
                    )
                } else if (newer) {
                    AppUpdateUiState(
                        phase = AppUpdatePhase.IDLE,
                        info = info,
                        showDialog = false,
                        message = tr("已忽略 WakeMove v${info.versionName}"),
                    )
                } else {
                    AppUpdateUiState(
                        phase = AppUpdatePhase.UP_TO_DATE,
                        showDialog = manual,
                        message = tr("当前已是最新版本"),
                    )
                }
            }.onFailure { error ->
                _state.value = AppUpdateUiState(
                    phase = if (manual) AppUpdatePhase.ERROR else AppUpdatePhase.IDLE,
                    showDialog = manual,
                    message = error.message ?: tr("检查更新失败，请稍后重试"),
                )
            }
        }
    }

    fun showAvailableUpdate() {
        val current = _state.value
        if (current.phase == AppUpdatePhase.AVAILABLE) {
            _state.value = current.copy(showDialog = true)
        } else {
            checkForUpdate(manual = true)
        }
    }

    fun dismissDialog() {
        _state.value = _state.value.copy(showDialog = false)
    }

    fun ignoreCurrentVersion() {
        val current = _state.value
        current.info?.let { info ->
            preferences.edit().putString(KEY_IGNORED_VERSION, info.versionName).apply()
        }
        _state.value = AppUpdateUiState(
            phase = AppUpdatePhase.IDLE,
            message = current.info?.let { tr("已忽略 WakeMove v${it.versionName}") },
        )
    }

    fun downloadUpdate() {
        val info = _state.value.info ?: return
        if (_state.value.phase == AppUpdatePhase.DOWNLOADING) return
        downloadJob?.cancel()
        savePendingDownload(info)
        _state.value = AppUpdateUiState(
            phase = AppUpdatePhase.DOWNLOADING,
            info = info,
            progressPercent = null,
            showDialog = true,
        )
        downloadJob = scope.launch(Dispatchers.IO) {
            runCatching { downloadPackage(info) }
                .onSuccess { file ->
                    if (!verifyDownloadedPackage(file, info.sha256)) {
                        file.delete()
                        withContext(Dispatchers.Main) {
                            _state.value = AppUpdateUiState(
                                phase = AppUpdatePhase.ERROR,
                                info = info,
                                showDialog = true,
                                message = tr("安装包校验失败，已阻止安装，请重新下载"),
                            )
                        }
                        clearSavedDownload()
                        return@onSuccess
                    }
                    withContext(Dispatchers.Main) {
                        _state.value = AppUpdateUiState(
                            phase = AppUpdatePhase.READY_TO_INSTALL,
                            info = info,
                            progressPercent = 100,
                            showDialog = true,
                        )
                    }
                }
                .onFailure {
                    updatePartFile(info).delete()
                    withContext(Dispatchers.Main) {
                        _state.value = AppUpdateUiState(
                            phase = AppUpdatePhase.ERROR,
                            info = info,
                            showDialog = true,
                            message = tr("下载没有完成，请切换网络或连接 Wi-Fi 后重试"),
                        )
                    }
                    clearSavedDownload()
                }
        }
    }

    fun installDownloadedUpdate() {
        val current = _state.value
        val info = current.info ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !applicationContext.packageManager.canRequestPackageInstalls()
        ) {
            _state.value = current.copy(
                phase = AppUpdatePhase.INSTALL_PERMISSION_REQUIRED,
                showDialog = true,
                message = tr("请允许 WakeMove 安装更新，返回后会继续安装"),
            )
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${applicationContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            applicationContext.startActivity(settingsIntent)
            return
        }
        openSystemInstaller(info)
    }

    fun continueInstallationIfPossible() {
        val current = _state.value
        if (current.phase != AppUpdatePhase.INSTALL_PERMISSION_REQUIRED) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            applicationContext.packageManager.canRequestPackageInstalls()
        ) {
            val info = current.info ?: return
            openSystemInstaller(info)
        }
    }

    private fun openSystemInstaller(info: AppUpdateInfo) {
        val file = updateFile(info)
        if (!file.isFile) {
            _state.value = AppUpdateUiState(
                phase = AppUpdatePhase.ERROR,
                info = info,
                showDialog = true,
                message = tr("安装包不存在，请重新下载"),
            )
            clearSavedDownload()
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(
                applicationContext,
                "${applicationContext.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            applicationContext.startActivity(intent)
            _state.value = _state.value.copy(showDialog = false)
        }.onFailure {
            _state.value = _state.value.copy(
                phase = AppUpdatePhase.ERROR,
                showDialog = true,
                message = tr("无法打开系统安装器，请从下载通知中安装"),
            )
        }
    }

    private suspend fun downloadPackage(info: AppUpdateInfo): File {
        val target = updateFile(info)
        val part = updatePartFile(info)
        target.parentFile?.mkdirs()
        target.delete()
        val candidates = downloadCandidates(info)
        var lastError: Throwable? = null
        for (url in candidates) {
            part.delete()
            try {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(progressPercent = null)
                }
                downloadUrl(url, part)
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                return target
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException(tr("没有可用的更新下载地址"))
    }

    internal fun downloadCandidates(info: AppUpdateInfo): List<String> =
        listOfNotNull(info.fallbackDownloadUrl, info.downloadUrl).distinct()

    private suspend fun downloadUrl(url: String, destination: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = DOWNLOAD_READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", APK_MIME_TYPE)
            connection.setRequestProperty("User-Agent", "WakeMove-Android-Updater")
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) error(tr("下载服务返回 $responseCode"))
            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L
            var lastProgress = -1
            connection.inputStream.buffered().use { input ->
                FileOutputStream(destination).buffered().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val progress = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 99)
                        } else {
                            null
                        }
                        if (progress != null && progress != lastProgress) {
                            lastProgress = progress
                            withContext(Dispatchers.Main) {
                                _state.value = _state.value.copy(progressPercent = progress)
                            }
                        }
                    }
                }
            }
            if (downloadedBytes <= 0L || (totalBytes > 0 && downloadedBytes != totalBytes)) {
                error(tr("安装包下载不完整"))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun restoreDownloadIfPresent() {
        val legacyDownloadId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (legacyDownloadId >= 0) {
            legacyDownloadManager.remove(legacyDownloadId)
            preferences.edit().remove(KEY_DOWNLOAD_ID).apply()
        }
        val version = preferences.getString(KEY_DOWNLOAD_VERSION, null) ?: return
        val versionCode = preferences.getInt(KEY_DOWNLOAD_VERSION_CODE, -1)
        val info = AppUpdateInfo(
            versionCode = versionCode,
            versionName = version,
            downloadUrl = preferences.getString(KEY_DOWNLOAD_URL, null).orEmpty(),
            releaseUrl = preferences.getString(KEY_RELEASE_URL, null).orEmpty(),
            releaseNotes = preferences.getString(KEY_RELEASE_NOTES, null).orEmpty(),
            sha256 = preferences.getString(KEY_DOWNLOAD_SHA256, null).orEmpty(),
            fallbackDownloadUrl = preferences.getString(KEY_FALLBACK_DOWNLOAD_URL, null),
        )
        if (versionCode <= BuildConfig.VERSION_CODE) {
            updateFile(info).delete()
            updatePartFile(info).delete()
            clearSavedDownload()
            return
        }
        if (updateFile(info).isFile) {
            _state.value = AppUpdateUiState(
                phase = AppUpdatePhase.READY_TO_INSTALL,
                info = info,
                progressPercent = 100,
                showDialog = false,
            )
        } else {
            updatePartFile(info).delete()
            _state.value = AppUpdateUiState(
                phase = AppUpdatePhase.AVAILABLE,
                info = info,
                showDialog = false,
                message = tr("上次下载没有完成，点击后可重新下载"),
            )
        }
    }

    private fun savePendingDownload(info: AppUpdateInfo) {
        preferences.edit()
            .remove(KEY_DOWNLOAD_ID)
            .putString(KEY_DOWNLOAD_VERSION, info.versionName)
            .putInt(KEY_DOWNLOAD_VERSION_CODE, info.versionCode)
            .putString(KEY_DOWNLOAD_URL, info.downloadUrl)
            .putString(KEY_FALLBACK_DOWNLOAD_URL, info.fallbackDownloadUrl)
            .putString(KEY_RELEASE_URL, info.releaseUrl)
            .putString(KEY_RELEASE_NOTES, info.releaseNotes)
            .putString(KEY_DOWNLOAD_SHA256, info.sha256)
            .apply()
    }

    private fun clearSavedDownload() {
        preferences.edit()
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_DOWNLOAD_VERSION)
            .remove(KEY_DOWNLOAD_VERSION_CODE)
            .remove(KEY_DOWNLOAD_URL)
            .remove(KEY_FALLBACK_DOWNLOAD_URL)
            .remove(KEY_RELEASE_URL)
            .remove(KEY_RELEASE_NOTES)
            .remove(KEY_DOWNLOAD_SHA256)
            .apply()
    }

    private fun updateFile(info: AppUpdateInfo): File =
        File(updateDirectory(), "WakeMove-v${info.versionName}.apk")

    private fun updatePartFile(info: AppUpdateInfo): File =
        File(updateDirectory(), "WakeMove-v${info.versionName}.apk.part")

    private fun updateDirectory(): File = File(applicationContext.filesDir, "updates")

    private fun verifyDownloadedPackage(file: File, expectedSha256: String): Boolean {
        if (!expectedSha256.matches(Regex("[a-fA-F0-9]{64}"))) return false
        if (!file.isFile) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }

    companion object {
        internal const val PREFERENCES_NAME = "wakemove_update_preferences"
        internal const val KEY_IGNORED_VERSION = "ignored_version"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_DOWNLOAD_VERSION = "download_version"
        private const val KEY_DOWNLOAD_VERSION_CODE = "download_version_code"
        private const val KEY_DOWNLOAD_URL = "download_url"
        private const val KEY_FALLBACK_DOWNLOAD_URL = "fallback_download_url"
        private const val KEY_RELEASE_URL = "release_url"
        private const val KEY_RELEASE_NOTES = "release_notes"
        private const val KEY_DOWNLOAD_SHA256 = "download_sha256"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val DOWNLOAD_CONNECT_TIMEOUT_MILLIS = 15_000
        private const val DOWNLOAD_READ_TIMEOUT_MILLIS = 35_000
        private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
    }
}
