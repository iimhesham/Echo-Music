package echo.music.iad1tya.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SandiaUpdate(val versionCode: Int, val tag: String, val apkUrl: String)

/**
 * Minimal in-app updater: reads the latest GitHub Release (tag "sandia-N", versionCode = 200 + N,
 * the same formula the CI build uses), downloads the APK and hands it to Android's package installer.
 * Android always asks the user to confirm the installation.
 */
object SandiaUpdater {
  private const val RELEASE_URL =
    "https://api.github.com/repos/iimhesham/Echo-Music/releases/latest"
  private const val BASE_CODE = 200

  private fun installedVersionCode(context: Context): Int {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return PackageInfoCompat.getLongVersionCode(info).toInt()
  }

  suspend fun check(context: Context): SandiaUpdate? =
    withContext(Dispatchers.IO) {
      val conn =
        (URL(RELEASE_URL).openConnection() as HttpURLConnection).apply {
          connectTimeout = 15_000
          readTimeout = 15_000
          setRequestProperty("Accept", "application/vnd.github+json")
        }
      try {
        if (conn.responseCode != 200) error("GitHub returned HTTP ${conn.responseCode}")
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val tag = json.getString("tag_name")
        val number = tag.removePrefix("sandia-").toIntOrNull() ?: return@withContext null
        val assets = json.getJSONArray("assets")
        var url: String? = null
        for (i in 0 until assets.length()) {
          val asset = assets.getJSONObject(i)
          if (asset.getString("name").endsWith(".apk")) {
            url = asset.getString("browser_download_url")
            break
          }
        }
        if (url == null) return@withContext null
        val code = BASE_CODE + number
        if (code > installedVersionCode(context)) SandiaUpdate(code, tag, url) else null
      } finally {
        conn.disconnect()
      }
    }

  suspend fun download(
    context: Context,
    update: SandiaUpdate,
    onProgress: (Int) -> Unit,
  ): File =
    withContext(Dispatchers.IO) {
      val dir = File(context.cacheDir, "update").apply { mkdirs() }
      val file = File(dir, "Sandia-${update.tag}.apk")
      dir.listFiles()?.filter { it != file }?.forEach { it.delete() }
      val conn =
        (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
          connectTimeout = 15_000
          readTimeout = 30_000
          instanceFollowRedirects = true
        }
      try {
        if (conn.responseCode !in 200..299) error("Download failed: HTTP ${conn.responseCode}")
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
          file.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            var done = 0L
            var lastPercent = -1
            while (true) {
              val read = input.read(buffer)
              if (read < 0) break
              out.write(buffer, 0, read)
              done += read
              if (total > 0) {
                val percent = (done * 100 / total).toInt()
                if (percent != lastPercent) {
                  lastPercent = percent
                  onProgress(percent)
                }
              }
            }
          }
        }
        if (total > 0 && file.length() != total) {
          file.delete()
          error("Incomplete download, please try again")
        }
        file
      } finally {
        conn.disconnect()
      }
    }

  fun install(context: Context, file: File) {
    if (!context.packageManager.canRequestPackageInstalls()) {
      context.startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
      return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    context.startActivity(
      Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
  }
}
