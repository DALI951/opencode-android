package ai.opencode.android.data.api

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val apkUrl: String,
    val releaseNotes: String
)

class UpdateChecker(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/DALI951/opencode-android/releases/latest")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val release = json.parseToJsonElement(body).jsonObject

            val tagName = release["tag_name"]?.jsonPrimitive?.content ?: return@withContext null
            val latestVersion = tagName.removePrefix("v")

            if (compareVersions(latestVersion, currentVersion) <= 0) {
                return@withContext null
            }

            val assets = release["assets"]?.jsonArray ?: return@withContext null
            val apkAsset = assets.firstOrNull {
                it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true
            } ?: return@withContext null

            val apkUrl = apkAsset.jsonObject["browser_download_url"]?.jsonPrimitive?.content ?: return@withContext null
            val bodyText = release["body"]?.jsonPrimitive?.content ?: ""

            UpdateInfo(
                versionName = latestVersion,
                tagName = tagName,
                apkUrl = apkUrl,
                releaseNotes = bodyText
            )
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Failed to check for update", e)
            null
        }
    }

    suspend fun downloadAndInstall(updateInfo: UpdateInfo): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(updateInfo.apkUrl)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Download failed: ${response.code}"))

            val apkDir = File(context.cacheDir, "updates")
            apkDir.mkdirs()
            val apkFile = File(apkDir, "opencode-android-v${updateInfo.versionName}.apk")

            response.body?.byteStream()?.use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    ),
                    "application/vnd.android.package-archive"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Failed to download/install", e)
            Result.failure(e)
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val partsA = a.split(".").mapNotNull { it.toIntOrNull() }
        val partsB = b.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val numA = partsA.getOrElse(i) { 0 }
            val numB = partsB.getOrElse(i) { 0 }
            if (numA != numB) return numA.compareTo(numB)
        }
        return 0
    }
}
