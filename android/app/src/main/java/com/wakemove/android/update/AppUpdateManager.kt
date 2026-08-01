package com.wakemove.android.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.wakemove.android.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val downloadManager = applicationContext.getSystemService(DownloadManager::class.java)
    private val preferences = applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val _state = MutableStateFlow(AppUpdateUiState())
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()
    private val checkedThisSession = AtomicBoolean(false)
    private var downloadObserver: Job? = null

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
                        message = "已忽略 WakeMove v${info.versionName}",
                    )
                } else {
                    AppUpdateUiState(
                        phase = AppUpdatePhase.UP_TO_DATE,
                        showDialog = manual,
                        message = "当前已是最新版本",
                    )
                }
            }.onFailure { error ->
                _state.value = AppUpdateUiState(
                    phase = if (manual) AppUpdatePhase.ERROR else AppUpdatePhase.IDLE,
                    showDialog = manual,
                    message = error.message ?: "检查更新失败，请稍后重试",
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
            message = current.info?.let { "已忽略 WakeMove v${it.versionName}" },
        )
    }

    fun downloadUpdate() {
        val info = _state.value.info ?: return
        if (_state.value.phase == AppUpdatePhase.DOWNLOADING) return
        runCatching {
            val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
                .setTitle("WakeMove v${info.versionName}")
                .setDescription("正在下载安装包")
                .setMimeType(APK_MIME_TYPE)
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(
                    applicationContext,
                    Environment.DIRECTORY_DOWNLOADS,
                    "WakeMove-v${info.versionName}.apk",
                )
            val downloadId = downloadManager.enqueue(request)
            preferences.edit()
                .putLong(KEY_DOWNLOAD_ID, downloadId)
                .putString(KEY_DOWNLOAD_VERSION, info.versionName)
                .putInt(KEY_DOWNLOAD_VERSION_CODE, info.versionCode)
                .putString(KEY_DOWNLOAD_URL, info.downloadUrl)
                .putString(KEY_RELEASE_URL, info.releaseUrl)
                .putString(KEY_RELEASE_NOTES, info.releaseNotes)
                .putString(KEY_DOWNLOAD_SHA256, info.sha256)
                .apply()
            _state.value = AppUpdateUiState(
                phase = AppUpdatePhase.DOWNLOADING,
                info = info,
                progressPercent = 0,
                showDialog = true,
                downloadId = downloadId,
            )
            observeDownload(downloadId, info)
        }.onFailure {
            _state.value = AppUpdateUiState(
                phase = AppUpdatePhase.ERROR,
                info = info,
                showDialog = true,
                message = "无法开始下载，请稍后重试",
            )
        }
    }

    fun installDownloadedUpdate() {
        val current = _state.value
        val downloadId = current.downloadId ?: return
        val info = current.info ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !applicationContext.packageManager.canRequestPackageInstalls()
        ) {
            _state.value = current.copy(
                phase = AppUpdatePhase.INSTALL_PERMISSION_REQUIRED,
                showDialog = true,
                message = "请允许 WakeMove 安装更新，返回后会继续安装",
            )
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${applicationContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            applicationContext.startActivity(settingsIntent)
            return
        }
        openSystemInstaller(downloadId, info)
    }

    fun continueInstallationIfPossible() {
        val current = _state.value
        if (current.phase != AppUpdatePhase.INSTALL_PERMISSION_REQUIRED) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            applicationContext.packageManager.canRequestPackageInstalls()
        ) {
            val downloadId = current.downloadId ?: return
            val info = current.info ?: return
            openSystemInstaller(downloadId, info)
        }
    }

    private fun openSystemInstaller(downloadId: Long, info: AppUpdateInfo) {
        val uri = downloadManager.getUriForDownloadedFile(downloadId)
        if (uri == null) {
            _state.value = AppUpdateUiState(
                phase = AppUpdatePhase.ERROR,
                info = info,
                showDialog = true,
                message = "安装包不存在，请重新下载",
            )
            clearSavedDownload()
            return
        }
        runCatching {
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
                message = "无法打开系统安装器，请从下载通知中安装",
            )
        }
    }

    private fun observeDownload(downloadId: Long, info: AppUpdateInfo) {
        downloadObserver?.cancel()
        downloadObserver = scope.launch(Dispatchers.IO) {
            while (true) {
                val snapshot = queryDownload(downloadId) ?: run {
                    withContext(Dispatchers.Main) {
                        _state.value = AppUpdateUiState(
                            phase = AppUpdatePhase.ERROR,
                            info = info,
                            showDialog = true,
                            message = "下载任务已被系统移除，请重新下载",
                        )
                    }
                    clearSavedDownload()
                    return@launch
                }
                when (snapshot.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        if (!verifyDownloadedPackage(downloadId, info.sha256)) {
                            downloadManager.remove(downloadId)
                            withContext(Dispatchers.Main) {
                                _state.value = AppUpdateUiState(
                                    phase = AppUpdatePhase.ERROR,
                                    info = info,
                                    showDialog = true,
                                    message = "安装包校验失败，已阻止安装，请重新下载",
                                )
                            }
                            clearSavedDownload()
                            return@launch
                        }
                        withContext(Dispatchers.Main) {
                            _state.value = AppUpdateUiState(
                                phase = AppUpdatePhase.READY_TO_INSTALL,
                                info = info,
                                progressPercent = 100,
                                showDialog = true,
                                downloadId = downloadId,
                            )
                        }
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        withContext(Dispatchers.Main) {
                            _state.value = AppUpdateUiState(
                                phase = AppUpdatePhase.ERROR,
                                info = info,
                                showDialog = true,
                                message = "下载失败，请检查网络和存储空间后重试",
                            )
                        }
                        clearSavedDownload()
                        return@launch
                    }
                    else -> withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(
                            phase = AppUpdatePhase.DOWNLOADING,
                            progressPercent = snapshot.progressPercent,
                            showDialog = _state.value.showDialog,
                        )
                    }
                }
                delay(DOWNLOAD_POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun queryDownload(downloadId: Long): DownloadSnapshot? {
        return downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
                )
                val downloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                )
                val total = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                )
                DownloadSnapshot(
                    status = status,
                    progressPercent = if (total > 0) {
                        ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                    } else {
                        null
                    },
                )
            }
    }

    private fun restoreDownloadIfPresent() {
        val downloadId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId < 0) return
        val version = preferences.getString(KEY_DOWNLOAD_VERSION, null) ?: return
        val versionCode = preferences.getInt(KEY_DOWNLOAD_VERSION_CODE, -1)
        if (versionCode <= BuildConfig.VERSION_CODE) {
            downloadManager.remove(downloadId)
            clearSavedDownload()
            return
        }
        val info = AppUpdateInfo(
            versionCode = versionCode,
            versionName = version,
            downloadUrl = preferences.getString(KEY_DOWNLOAD_URL, null).orEmpty(),
            releaseUrl = preferences.getString(KEY_RELEASE_URL, null).orEmpty(),
            releaseNotes = preferences.getString(KEY_RELEASE_NOTES, null).orEmpty(),
            sha256 = preferences.getString(KEY_DOWNLOAD_SHA256, null).orEmpty(),
        )
        val snapshot = queryDownload(downloadId)
        when (snapshot?.status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                _state.value = AppUpdateUiState(
                    phase = AppUpdatePhase.DOWNLOADING,
                    info = info,
                    progressPercent = 100,
                    showDialog = false,
                    downloadId = downloadId,
                )
                observeDownload(downloadId, info)
            }
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_PAUSED,
            DownloadManager.STATUS_RUNNING,
            -> {
                _state.value = AppUpdateUiState(
                    phase = AppUpdatePhase.DOWNLOADING,
                    info = info,
                    progressPercent = snapshot.progressPercent,
                    showDialog = false,
                    downloadId = downloadId,
                )
                observeDownload(downloadId, info)
            }
            else -> clearSavedDownload()
        }
    }

    private fun clearSavedDownload() {
        preferences.edit()
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_DOWNLOAD_VERSION)
            .remove(KEY_DOWNLOAD_VERSION_CODE)
            .remove(KEY_DOWNLOAD_URL)
            .remove(KEY_RELEASE_URL)
            .remove(KEY_RELEASE_NOTES)
            .remove(KEY_DOWNLOAD_SHA256)
            .apply()
    }

    private fun verifyDownloadedPackage(downloadId: Long, expectedSha256: String): Boolean {
        if (!expectedSha256.matches(Regex("[a-fA-F0-9]{64}"))) return false
        val localUri = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return false
                cursor.getString(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI),
                )
            }
            ?: return false
        val file = Uri.parse(localUri).path?.let(::File) ?: return false
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

    private data class DownloadSnapshot(
        val status: Int,
        val progressPercent: Int?,
    )

    companion object {
        internal const val PREFERENCES_NAME = "wakemove_update_preferences"
        internal const val KEY_IGNORED_VERSION = "ignored_version"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_DOWNLOAD_VERSION = "download_version"
        private const val KEY_DOWNLOAD_VERSION_CODE = "download_version_code"
        private const val KEY_DOWNLOAD_URL = "download_url"
        private const val KEY_RELEASE_URL = "release_url"
        private const val KEY_RELEASE_NOTES = "release_notes"
        private const val KEY_DOWNLOAD_SHA256 = "download_sha256"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val DOWNLOAD_POLL_INTERVAL_MILLIS = 700L
    }
}
