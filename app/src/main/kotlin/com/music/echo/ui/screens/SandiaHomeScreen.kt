package echo.music.iad1tya.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import echo.music.iad1tya.LocalDatabase
import echo.music.iad1tya.LocalPlayerConnection
import echo.music.iad1tya.extensions.toMediaItem
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.playback.queues.ListQueue
import echo.music.iad1tya.playback.queues.YouTubeQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Sandía's only screen: a plain text list of Liked Songs, shuffle, suggestions and a tiny player bar. */
@Composable
fun SandiaHomeScreen(navController: NavController) {
  val database = LocalDatabase.current
  val playerConnection = LocalPlayerConnection.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val songs by database.playlistSongs("SPOTIFY_LIKED_SONGS").collectAsState(initial = emptyList())
  val mediaMetadata by
    (playerConnection?.mediaMetadata ?: flowOf<MediaMetadata?>(null)).collectAsState(null)
  val isPlaying by (playerConnection?.isEffectivelyPlaying ?: flowOf(false)).collectAsState(false)

  var updateText by remember { mutableStateOf("") }
  var pendingUpdate by remember { mutableStateOf<SandiaUpdate?>(null) }
  var busy by remember { mutableStateOf(false) }

  fun checkOrInstall() {
    if (busy) return
    val pending = pendingUpdate
    scope.launch {
      busy = true
      try {
        if (pending == null) {
          updateText = "Checking for updates..."
          val found = SandiaUpdater.check(context)
          pendingUpdate = found
          updateText =
            if (found == null) "You have the latest version."
            else "New version available (${found.tag}). Tap Install update."
        } else {
          updateText = "Downloading 0%"
          val file = SandiaUpdater.download(context, pending) { updateText = "Downloading $it%" }
          updateText = "Opening the installer..."
          SandiaUpdater.install(context, file)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        updateText = "Update failed: ${e.message}"
      } finally {
        busy = false
      }
    }
  }

  Column(Modifier.fillMaxSize().systemBarsPadding()) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "Sandía",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = { navController.navigate("settings/spotify_import") }) {
        Text("Spotify")
      }
      TextButton(onClick = { navController.navigate("settings/spotify_file_import") }) {
        Text("File")
      }
      TextButton(onClick = { checkOrInstall() }, enabled = !busy) {
        Text(if (pendingUpdate != null) "Install update" else "Update")
      }
    }

    if (updateText.isNotEmpty()) {
      Text(
        updateText,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
    }

    Row(
      Modifier.fillMaxWidth().padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Button(
        modifier = Modifier.weight(1f),
        enabled = songs.isNotEmpty(),
        onClick = {
          // Random order, only from the songs in this playlist.
          playerConnection?.playQueue(
            ListQueue(title = "Liked Songs", items = songs.shuffled().map { it.song.toMediaItem() })
          )
        },
      ) {
        Text("Shuffle")
      }
      OutlinedButton(
        modifier = Modifier.weight(1f),
        onClick = {
          val current = mediaMetadata
          if (current == null) {
            Toast.makeText(context, "Play a song first", Toast.LENGTH_SHORT).show()
          } else {
            playerConnection?.playQueue(YouTubeQueue.radio(current))
          }
        },
      ) {
        Text("Suggest songs")
      }
    }

    if (songs.isEmpty()) {
      Text(
        "No songs yet. Tap Spotify (top) to connect and import your Liked Songs.",
        modifier = Modifier.padding(16.dp),
      )
    }

    LazyColumn(Modifier.weight(1f)) {
      itemsIndexed(songs) { index, item ->
        val playing = mediaMetadata?.id == item.song.song.id
        Column(
          Modifier.fillMaxWidth()
            .clickable {
              playerConnection?.playQueue(
                ListQueue(
                  title = "Liked Songs",
                  items = songs.map { it.song.toMediaItem() },
                  startIndex = index,
                )
              )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
          Text(
            item.song.song.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
            color =
              if (playing) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurface,
          )
          Text(
            item.song.artists.joinToString { it.name },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    mediaMetadata?.let { current ->
      Row(
        Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.surfaceContainer)
          .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            current.artists.joinToString { it.name },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        TextButton(onClick = { playerConnection?.seekToPrevious() }) { Text("⏮") }
        TextButton(onClick = { playerConnection?.togglePlayPause() }) {
          Text(if (isPlaying) "⏸" else "▶")
        }
        TextButton(onClick = { playerConnection?.seekToNext() }) { Text("⏭") }
      }
    }
  }
}
