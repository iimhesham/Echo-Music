package echo.music.iad1tya.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import echo.music.iad1tya.LocalPlayerConnection
import echo.music.iad1tya.extensions.toMediaItem
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.models.toMediaMetadata
import echo.music.iad1tya.playback.queues.ListQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** Search any song on YouTube Music, independent of Spotify. */
@Composable
fun SandiaSearchScreen(navController: NavController) {
  val playerConnection = LocalPlayerConnection.current
  val current by
    (playerConnection?.mediaMetadata ?: flowOf<MediaMetadata?>(null)).collectAsState(null)

  var query by rememberSaveable { mutableStateOf("") }
  var results by remember { mutableStateOf<List<SongItem>>(emptyList()) }
  var loading by remember { mutableStateOf(false) }
  var failed by remember { mutableStateOf(false) }
  var sheetSong by remember { mutableStateOf<MediaMetadata?>(null) }
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  LaunchedEffect(query) {
    val q = query.trim()
    if (q.length < 2) {
      results = emptyList()
      loading = false
      failed = false
      return@LaunchedEffect
    }
    delay(450)
    loading = true
    failed = false
    val items =
      withContext(Dispatchers.IO) {
        YouTube.search(q, YouTube.SearchFilter.FILTER_SONG)
          .getOrNull()
          ?.items
          ?.filterIsInstance<SongItem>()
      }
    if (items == null) failed = true else results = items
    loading = false
  }

  SandiaScaffold(navController) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
      Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = { navController.navigateUp() }) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Sandia.TextPrimary)
        }
        TextField(
          value = query,
          onValueChange = { query = it },
          singleLine = true,
          placeholder = { Text("Search songs", color = Sandia.TextSecondary) },
          leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Sandia.TextSecondary) },
          trailingIcon = {
            if (query.isNotEmpty()) {
              IconButton(onClick = { query = "" }) {
                Icon(Icons.Rounded.Close, null, tint = Sandia.TextSecondary)
              }
            }
          },
          shape = RoundedCornerShape(28.dp),
          colors =
            TextFieldDefaults.colors(
              focusedContainerColor = Sandia.SurfaceHigh,
              unfocusedContainerColor = Sandia.SurfaceHigh,
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
              cursorColor = Sandia.Accent,
              focusedTextColor = Sandia.TextPrimary,
              unfocusedTextColor = Sandia.TextPrimary,
            ),
          modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
      }

      Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(Modifier.fillMaxWidth()) {
          itemsIndexed(results) { index, item ->
            SandiaSongRow(
              title = item.title,
              artist = item.artists.joinToString { it.name },
              thumbnailUrl = item.thumbnail,
              playing = current?.id == item.id,
              onClick = {
                playerConnection?.playQueue(
                  ListQueue(
                    title = "Search",
                    items = results.map { it.toMediaMetadata().toMediaItem() },
                    startIndex = index,
                  )
                )
              },
              onTrailing = { sheetSong = item.toMediaMetadata() },
            )
          }
        }
        if (loading) {
          CircularProgressIndicator(
            color = Sandia.Accent,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp).size(28.dp),
          )
        } else if (failed) {
          Text(
            "Search failed. Check your connection and try again.",
            color = Sandia.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(24.dp),
          )
        } else if (query.trim().length >= 2 && results.isEmpty()) {
          Text(
            "No songs found.",
            color = Sandia.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(24.dp),
          )
        }
      }
    }
    sheetSong?.let { SandiaSongSheet(it) { sheetSong = null } }
  }
}
