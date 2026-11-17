package echo.music.iad1tya.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import echo.music.iad1tya.LocalDatabase
import echo.music.iad1tya.LocalPlayerConnection
import echo.music.iad1tya.extensions.toMediaItem
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.models.toMediaMetadata
import echo.music.iad1tya.playback.queues.ListQueue
import kotlinx.coroutines.flow.flowOf

@Composable
fun SandiaPlaylistScreen(navController: NavController, playlistId: String) {
  val database = LocalDatabase.current
  val playerConnection = LocalPlayerConnection.current
  val playlist by database.playlist(playlistId).collectAsState(initial = null)
  val songs by database.playlistSongs(playlistId).collectAsState(initial = emptyList())
  val current by
    (playerConnection?.mediaMetadata ?: flowOf<MediaMetadata?>(null)).collectAsState(null)
  var sheetSong by remember { mutableStateOf<MediaMetadata?>(null) }

  val isLiked = playlistId == SANDIA_LIKED_ID
  val title = if (isLiked) "Liked Songs" else playlist?.playlist?.name ?: "Playlist"

  fun play(list: List<androidx.media3.common.MediaItem>, startIndex: Int = 0) {
    if (list.isEmpty()) return
    playerConnection?.playQueue(ListQueue(title = title, items = list, startIndex = startIndex))
  }

  SandiaScaffold(navController) {
    LazyColumn(Modifier.fillMaxWidth()) {
      item {
        Column(Modifier.fillMaxWidth()) {
          Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = { navController.navigateUp() }) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Sandia.TextPrimary)
            }
          }
          Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (isLiked) {
              SandiaLikedCover(Modifier.size(168.dp), 18.dp)
            } else {
              SandiaCover(
                playlist?.playlist?.thumbnailUrl ?: playlist?.thumbnails?.firstOrNull(),
                Modifier.size(168.dp),
                18.dp,
                sizePx = 544,
              )
            }
          }
          Text(
            title,
            color = Sandia.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp),
          )
          Text(
            "${songs.size} songs",
            color = Sandia.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
          )
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Button(
              onClick = { play(songs.map { it.song.toMediaItem() }) },
              enabled = songs.isNotEmpty(),
              modifier = Modifier.weight(1f),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = Sandia.Accent,
                  contentColor = Sandia.OnAccent,
                ),
            ) {
              Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
              Spacer(Modifier.size(6.dp))
              Text("Play")
            }
            OutlinedButton(
              onClick = { play(songs.shuffled().map { it.song.toMediaItem() }) },
              enabled = songs.isNotEmpty(),
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(20.dp))
              Spacer(Modifier.size(6.dp))
              Text("Shuffle")
            }
          }
          Spacer(Modifier.height(4.dp))
        }
      }

      itemsIndexed(songs) { index, item ->
        val s = item.song
        SandiaSongRow(
          title = s.song.title,
          artist = s.artists.joinToString { it.name },
          thumbnailUrl = s.song.thumbnailUrl,
          playing = current?.id == s.song.id,
          onClick = { play(songs.map { it.song.toMediaItem() }, index) },
          onTrailing = { sheetSong = s.toMediaMetadata() },
        )
      }
    }
    sheetSong?.let { SandiaSongSheet(it) { sheetSong = null } }
  }
}
