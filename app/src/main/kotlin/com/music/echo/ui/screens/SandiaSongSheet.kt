package echo.music.iad1tya.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.music.iad1tya.LocalDatabase
import echo.music.iad1tya.LocalPlayerConnection
import echo.music.iad1tya.db.entities.Playlist
import echo.music.iad1tya.db.entities.PlaylistEntity
import echo.music.iad1tya.db.entities.PlaylistSongMap
import echo.music.iad1tya.extensions.toMediaItem
import echo.music.iad1tya.models.MediaMetadata
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandiaSongSheet(metadata: MediaMetadata, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val database = LocalDatabase.current
  val playerConnection = LocalPlayerConnection.current
  val scope = rememberCoroutineScope()
  val playlists by database.playlistsByCreateDateAsc().collectAsState(initial = emptyList())
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var picking by remember { mutableStateOf(false) }
  var creating by remember { mutableStateOf(false) }
  var newName by remember { mutableStateOf("") }

  fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

  fun addTo(playlist: Playlist) {
    scope.launch(Dispatchers.IO) {
      val already =
        database.playlistSongs(playlist.id).first().any { it.song.song.id == metadata.id }
      if (!already) {
        database.query {
          insert(metadata)
          addSongToPlaylist(playlist, listOf(metadata.id))
        }
      }
      withContext(Dispatchers.Main) {
        toast(if (already) "Already in ${playlist.playlist.name}" else "Added to ${playlist.playlist.name}")
        onDismiss()
      }
    }
  }

  fun createAndAdd(name: String) {
    scope.launch(Dispatchers.IO) {
      val entity =
        PlaylistEntity(name = name.trim(), bookmarkedAt = LocalDateTime.now(), isLocal = true)
      database.query {
        insert(entity)
        insert(metadata)
        insert(PlaylistSongMap(songId = metadata.id, playlistId = entity.id, position = 0))
      }
      withContext(Dispatchers.Main) {
        toast("Added to ${entity.name}")
        onDismiss()
      }
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Sandia.Surface,
    contentColor = Sandia.TextPrimary,
  ) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SandiaCover(metadata.thumbnailUrl, Modifier.size(52.dp), 10.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
          Text(
            metadata.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            metadata.artists.joinToString { it.name },
            color = Sandia.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Spacer(Modifier.height(8.dp))

      if (creating) {
        TextField(
          value = newName,
          onValueChange = { newName = it },
          singleLine = true,
          placeholder = { Text("Playlist name") },
          colors =
            TextFieldDefaults.colors(
              focusedContainerColor = Sandia.SurfaceHigh,
              unfocusedContainerColor = Sandia.SurfaceHigh,
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
              cursorColor = Sandia.Accent,
            ),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        TextButton(
          onClick = { if (newName.isNotBlank()) createAndAdd(newName) },
          modifier = Modifier.padding(horizontal = 12.dp),
        ) {
          Text("Create and add", color = Sandia.Accent)
        }
      } else if (picking) {
        SheetAction(Icons.Rounded.Add, "New playlist") { creating = true }
        playlists
          .filter { it.playlist.id != SANDIA_LIKED_ID }
          .forEach { p -> SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, p.playlist.name) { addTo(p) } }
      } else {
        SheetAction(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next") {
          playerConnection?.playNext(metadata.toMediaItem())
          toast("Will play next")
          onDismiss()
        }
        SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") {
          playerConnection?.addToQueue(metadata.toMediaItem())
          toast("Added to queue")
          onDismiss()
        }
        SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { picking = true }
      }
    }
  }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, null, tint = Sandia.TextSecondary, modifier = Modifier.size(24.dp))
    Text(label, fontSize = 16.sp, modifier = Modifier.padding(start = 18.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}
