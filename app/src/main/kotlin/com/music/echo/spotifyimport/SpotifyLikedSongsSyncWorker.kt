/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package echo.music.iad1tya.spotifyimport

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Periodically refreshes the user's Spotify Liked Songs mirror after the first
 * successful import. It uses the existing private Spotify session integration.
 */
class SpotifyLikedSongsSyncWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface Dependencies {
    fun spotifyImportRepository(): SpotifyImportRepository
  }

  override suspend fun doWork(): Result {
    val repository =
      EntryPointAccessors.fromApplication(
        applicationContext,
        Dependencies::class.java,
      ).spotifyImportRepository()

    return try {
      // Do not create an initial 1,194-track import in the background.
      // The user starts that explicitly; periodic sync begins after it is imported.
      if (repository.lastLikedSongsSyncAt() == null) return Result.success()

      val session = repository.restoreSession()
      if (!session.isAuthenticated) {
        Timber.w("Spotify background sync skipped: session is not authenticated")
        return Result.success()
      }

      repository.syncPlaylistFast("SPOTIFY_LIKED_SONGS")
      Result.success()
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      Timber.e(error, "Spotify Liked Songs background sync failed")
      Result.retry()
    }
  }

  companion object {
    private const val WORK_NAME = "SpotifyLikedSongsPeriodicSync"

    fun cancel(context: Context) {
      WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun schedule(context: Context) {
      val request =
        PeriodicWorkRequestBuilder<SpotifyLikedSongsSyncWorker>(12, TimeUnit.HOURS)
          .setConstraints(
            Constraints.Builder()
              .setRequiredNetworkType(NetworkType.CONNECTED)
              .build(),
          )
          .build()

      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
      )
    }
  }
}
