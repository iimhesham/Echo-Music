package echo.music.iad1tya.spotifyimport

import android.content.Context
import android.net.Uri
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.models.toMediaMetadata
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger

/**
 * One-time import of Liked Songs from Spotify's official "Download your data" export.
 * Reads YourLibrary.json -> "tracks": [{artist, album, track, uri}].
 * No Spotify login, no cookies, no unofficial API calls: zero risk for the Spotify account.
 */
@Serializable data class ExportTrack(val artist: String = "", val album: String = "", val track: String = "", val uri: String = "")
@Serializable data class ExportLibrary(val tracks: List<ExportTrack> = emptyList())

data class ImportResult(val total: Int, val imported: Int, val failed: List<ExportTrack>)

@Singleton
class SpotifyExportImporter @Inject constructor(
  @ApplicationContext private val context: Context,
  private val spotifyImportRepository: SpotifyImportRepository,
) {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  suspend fun import(file: Uri, onProgress: (done: Int, total: Int) -> Unit): ImportResult =
    withContext(Dispatchers.IO) {
      val bytes = context.contentResolver.openInputStream(file)!!.use { it.readBytes() }
      val text = readLibraryJson(bytes)
      val tracks = json.decodeFromString<ExportLibrary>(text).tracks
        .filter { it.track.isNotBlank() }
        .distinctBy { it.uri.ifBlank { it.track + it.artist } }

      val done = AtomicInteger(0)
      val gate = Semaphore(2) // gentle on YouTube: 2 searches at a time
      val failed = java.util.Collections.synchronizedList(mutableListOf<ExportTrack>())

      val matched =
        coroutineScope {
          tracks.map { track ->
            async {
              gate.withPermit {
                val metadata =
                  try {
                    matchTrack(track)
                  } catch (error: CancellationException) {
                    throw error
                  } catch (_: Throwable) {
                    null
                  }
                if (metadata == null) failed += track
                onProgress(done.incrementAndGet(), tracks.size)
                delay(150)
                metadata
              }
            }
          }.awaitAll()
        }

      val importedTracks = matched.filterNotNull()
      spotifyImportRepository.addExportMatchesToLikedSongs(importedTracks)
      ImportResult(tracks.size, importedTracks.size, failed.toList())
    }

  /** Accepts either YourLibrary.json itself or the whole ZIP that Spotify emails you. */
  private fun readLibraryJson(bytes: ByteArray): String {
    val isZip = bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
    if (!isZip) return bytes.toString(Charsets.UTF_8)
    java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
      var entry = zip.nextEntry
      while (entry != null) {
        if (entry.name.endsWith("YourLibrary.json", ignoreCase = true)) {
          return zip.readBytes().toString(Charsets.UTF_8)
        }
        entry = zip.nextEntry
      }
    }
    error("YourLibrary.json not found inside the ZIP")
  }

  private suspend fun matchTrack(t: ExportTrack): MediaMetadata? {
    val queries =
      listOf(
        "${t.track} ${t.artist}",
        "${t.artist} ${t.track}",
        t.track,
      ).map(String::trim).filter(String::isNotBlank).distinct()
    val candidatesById = LinkedHashMap<String, SongItem>()

    for (query in queries) {
      val result =
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrElse { error ->
          if (error is CancellationException) throw error
          null
        } ?: continue

      result.items.filterIsInstance<SongItem>().forEach { candidatesById.putIfAbsent(it.id, it) }
      val best = candidatesById.values.maxByOrNull { score(t, it) }
      if (best != null && score(t, best) >= 0.97) return best.toMediaMetadata()
    }

    val best = candidatesById.values.maxByOrNull { score(t, it) } ?: return null
    if (score(t, best) < MIN_SCORE) return null
    return best.toMediaMetadata()
  }

  private fun norm(s: String) =
    s.lowercase().replace(Regex("\\(.*?\\)|\\[.*?]|feat\\.?.*|[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

  private fun score(t: ExportTrack, c: SongItem): Double {
    val title = norm(t.track); val cTitle = norm(c.title)
    val titleScore = when {
      title == cTitle -> 1.0
      cTitle.contains(title) || title.contains(cTitle) -> 0.7
      else -> 0.0
    }
    val artist = norm(t.artist)
    val artistScore = if (c.artists.any { norm(it.name).let { a -> a.contains(artist) || artist.contains(a) } }) 1.0 else 0.0
    return titleScore * 0.65 + artistScore * 0.35
  }

  private companion object { const val MIN_SCORE = 0.7 }
}
