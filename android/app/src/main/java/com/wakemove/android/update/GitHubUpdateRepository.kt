package com.wakemove.android.update

import com.wakemove.android.i18n.tr
import com.wakemove.android.i18n.WakeMoveLocale

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import org.json.JSONObject

fun interface UpdateRepository {
    suspend fun latestRelease(): AppUpdateInfo
}

class GitHubUpdateRepository : UpdateRepository {
    override suspend fun latestRelease(): AppUpdateInfo {
        val connection = URL(UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "WakeMove-Android-Updater")
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw UpdateCheckException(tr("版本服务暂时不可用（$responseCode）"))
            }
            parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (error: UpdateCheckException) {
            throw error
        } catch (_: Exception) {
            throw UpdateCheckException(tr("无法连接版本服务，请检查网络后重试"))
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseRelease(json: String): AppUpdateInfo {
        val release = JSONObject(json)
        val versionCode = release.optInt("versionCode", -1)
        val versionName = release.optString("versionName").removePrefix("v")
        val releaseUrl = release.optString("releaseUrl")
        val downloadUrl = release.optString("downloadUrl")
        val fallbackDownloadUrl = release.optString("fallbackDownloadUrl").ifBlank { null }
        val sha256 = release.optString("sha256").lowercase()
        if (versionCode < 1 || versionName.isBlank() || releaseUrl.isBlank() ||
            downloadUrl.isBlank() || !sha256.matches(Regex("[a-f0-9]{64}"))
        ) {
            throw UpdateCheckException(tr("最新版本信息不完整"))
        }
        requireTrustedGitHubUrl(releaseUrl)
        requireTrustedGitHubUrl(downloadUrl)
        fallbackDownloadUrl?.let(::requireTrustedGitHubUrl)
        return AppUpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            downloadUrl = downloadUrl,
            releaseUrl = releaseUrl,
            releaseNotes = if (WakeMoveLocale.isEnglish()) {
                release.optString("releaseNotesEn").trim()
                    .ifBlank { release.optString("releaseNotes").trim() }
            } else {
                release.optString("releaseNotes").trim()
            }.ifBlank { tr("可靠性与体验改进。") },
            sha256 = sha256,
            fallbackDownloadUrl = fallbackDownloadUrl,
        )
    }

    private fun requireTrustedGitHubUrl(value: String) {
        val uri = runCatching { URI(value) }.getOrNull()
        if (uri?.scheme != "https" || uri.host?.lowercase() !in TRUSTED_HOSTS) {
            throw UpdateCheckException(tr("版本下载地址未通过安全校验"))
        }
    }

    private companion object {
        const val UPDATE_MANIFEST_URL = "https://ykedan.github.io/WakeMove/update.json"
        const val TIMEOUT_MILLIS = 12_000
        val TRUSTED_HOSTS = setOf("github.com", "www.github.com", "ykedan.github.io")
    }
}

class UpdateCheckException(message: String) : Exception(message)
