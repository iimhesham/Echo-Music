package echo.music.iad1tya.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import echo.music.iad1tya.LocalDatabase
import echo.music.iad1tya.LocalPlayerConnection
import echo.music.iad1tya.extensions.toMediaItem
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.playback.queues.ListQueue
import echo.music.iad1tya.playback.queues.YouTubeQueue
import echo.music.iad1tya.spotifyimport.SpotifyImportSourceType
import echo.music.iad1tya.spotifyimport.SpotifyImportUiState
import echo.music.iad1tya.spotifyimport.SpotifyImportViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val SandiaBlack = Color(0xFF000000)
private val SandiaSurface = Color(0xFF141414)
private val SandiaSurfaceRaised = Color(0xFF1C1718)
private val SandiaPink = Color(0xFFF6B5B0)
private val SandiaMuted = Color(0xFFB9AEB0)
private val SandiaGreen = Color(0xFF1DB954)

@Composable
fun SandiaHomeScreen(navController: NavController) {
  val database = LocalDatabase.current
  val playerConnection = LocalPlayerConnection.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val spotifyViewModel: SpotifyImportViewModel = hiltViewModel()
  val spotifyState by spotifyViewModel.uiState.collectAsState()

  val songs by database.playlistSongs("SPOTIFY_LIKED_SONGS").collectAsState(initial = emptyList())
  val mediaMetadata by
    (playerConnection?.mediaMetadata ?: flowOf<MediaMetadata?>(null)).collectAsState(null)
  val isPlaying by (playerConnection?.isEffectivelyPlaying ?: flowOf(false)).collectAsState(false)

  var selectedTab by rememberSaveable { mutableStateOf("Playlists") }
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
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        updateText = "Update failed: ${error.message}"
      } finally {
        busy = false
      }
    }
  }

  val likedSource = spotifyState.sources.firstOrNull { it.type == SpotifyImportSourceType.LIKED_SONGS }
  val spotifyPlaylists = spotifyState.sources.filter { it.type == SpotifyImportSourceType.PLAYLIST }

  Column(
    modifier = Modifier.fillMaxSize().background(SandiaBlack).systemBarsPadding(),
  ) {
    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            "Sandía",
            modifier = Modifier.weight(1f),
            color = SandiaPink,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
          )
          TextButton(onClick = { navController.navigate("settings/spotify_import") }) {
            Text("Spotify", color = SandiaPink)
          }
          TextButton(onClick = { navController.navigate("settings/spotify_file_import") }) {
            Text("File", color = SandiaPink)
          }
          TextButton(onClick = { checkOrInstall() }, enabled = !busy) {
            Text(if (pendingUpdate != null) "Install" else "Update", color = SandiaPink)
          }
        }
      }

      item {
        SpotifyAccountCard(
          state = spotifyState,
          onConnect = { navController.navigate("settings/spotify_import") },
          onSync = { spotifyViewModel.syncLikedSongs() },
          onRefresh = { spotifyViewModel.loadSources() },
        )
      }

      if (updateText.isNotBlank()) {
        item {
          Text(
            updateText,
            color = SandiaMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
          )
        }
      }

      spotifyState.errorMessage?.let { error ->
        item {
          Text(
            error,
            color = Color(0xFFFF7777),
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
            modifier = Modifier.weight(1f).height(48.dp),
            enabled = songs.isNotEmpty(),
            onClick = {
              playerConnection?.playQueue(
                ListQueue(title = "Liked Songs", items = songs.shuffled().map { it.song.toMediaItem() })
              )
            },
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = SandiaPink,
              contentColor = Color(0xFF241719),
              disabledContainerColor = SandiaSurface,
              disabledContentColor = SandiaMuted,
            ),
          ) {
            Text("Shuffle", fontWeight = FontWeight.SemiBold)
          }
          OutlinedButton(
            modifier = Modifier.weight(1f).height(48.dp),
            onClick = {
              val current = mediaMetadata
              if (current == null) {
                Toast.makeText(context, "Play a song first", Toast.LENGTH_SHORT).show()
              } else {
                playerConnection?.playQueue(YouTubeQueue.radio(current))
              }
            },
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, SandiaMuted.copy(alpha = 0.35f)),
          ) {
            Text("Suggest songs", color = SandiaMuted)
          }
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          SandiaTab(
            title = "Playlists",
            selected = selectedTab == "Playlists",
            modifier = Modifier.weight(1f),
            onClick = { selectedTab = "Playlists" },
          )
          SandiaTab(
            title = "Songs",
            selected = selectedTab == "Songs",
            modifier = Modifier.weight(1f),
            onClick = { selectedTab = "Songs" },
          )
        }
      }

      if (selectedTab == "Playlists") {
        item {
          Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              "Spotify Playlists",
              color = Color.White,
              fontSize = 22.sp,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.weight(1f),
            )
            Text(
              "${spotifyPlaylists.size} playlists",
              color = SandiaMuted,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }

        if (likedSource != null || songs.isNotEmpty()) {
          item {
            SandiaPlaylistRow(
              title = "Spotify Liked Songs",
              subtitle = "Spotify",
              trackCount = likedSource?.trackCount ?: songs.size,
              imageUrl = null,
              isLikedSongs = true,
              onClick = {
                if (songs.isNotEmpty()) {
                  navController.navigate("local_playlist/SPOTIFY_LIKED_SONGS")
                } else {
                  navController.navigate("settings/spotify_import")
                }
              },
            )
          }
        }

        items(spotifyPlaylists, key = { it.id }) { source ->
          SandiaPlaylistRow(
            title = source.title,
            subtitle = source.subtitle.ifBlank { "Spotify" },
            trackCount = source.trackCount,
            imageUrl = source.thumbnailUrl,
            isLikedSongs = false,
            onClick = {
              val playlistId = source.id.removePrefix("playlist:")
              navController.navigate(
                "spotify_playlist/$playlistId?title=${Uri.encode(source.title)}&cover=${Uri.encode(source.thumbnailUrl.orEmpty())}"
              )
            },
          )
        }

        if (spotifyState.isLoading && spotifyState.sources.isEmpty()) {
          item { Text("Loading your Spotify library...", color = SandiaMuted) }
        } else if (!spotifyState.isAuthenticated && songs.isEmpty()) {
          item {
            Text(
              "Connect Spotify to see your playlists and import your Liked Songs.",
              color = SandiaMuted,
              modifier = Modifier.padding(vertical = 12.dp),
            )
          }
        }
      } else {
        item {
          Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              "Liked Songs",
              color = Color.White,
              fontSize = 22.sp,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.weight(1f),
            )
            Text("${songs.size} songs", color = SandiaMuted)
          }
        }

        if (songs.isEmpty()) {
          item {
            Text(
              "No imported songs yet. Connect Spotify or import your Spotify data file.",
              color = SandiaMuted,
              modifier = Modifier.padding(vertical = 12.dp),
            )
          }
        } else {
          itemsIndexed(songs, key = { _, item -> item.song.song.id }) { index, item ->
            val playing = mediaMetadata?.id == item.song.song.id
            Row(
              modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (playing) SandiaSurfaceRaised else Color.Transparent)
                .clickable {
                  playerConnection?.playQueue(
                    ListQueue(
                      title = "Liked Songs",
                      items = songs.map { it.song.toMediaItem() },
                      startIndex = index,
                    )
                  )
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              AsyncImage(
                model = item.song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(9.dp)),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  item.song.song.title,
                  color = if (playing) SandiaPink else Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                  item.song.artists.joinToString { it.name },
                  color = SandiaMuted,
                  style = MaterialTheme.typography.bodySmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }

      item { Spacer(Modifier.height(4.dp)) }
    }

    mediaMetadata?.let { current ->
      SandiaMiniPlayer(
        metadata = current,
        isPlaying = isPlaying,
        onPrevious = { playerConnection?.seekToPrevious() },
        onToggle = { playerConnection?.togglePlayPause() },
        onNext = { playerConnection?.seekToNext() },
      )
    }
  }
}

@Composable
private fun SpotifyAccountCard(
  state: SpotifyImportUiState,
  onConnect: () -> Unit,
  onSync: () -> Unit,
  onRefresh: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(22.dp))
      .background(SandiaSurface)
      .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(22.dp))
      .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier = Modifier.size(54.dp).clip(CircleShape).background(SandiaSurfaceRaised),
      contentAlignment = Alignment.Center,
    ) {
      if (!state.accountAvatarUrl.isNullOrBlank()) {
        AsyncImage(
          model = state.accountAvatarUrl,
          contentDescription = "Spotify profile picture",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
      } else {
        Text("S", color = SandiaPink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
      }
    }

    Column(modifier = Modifier.weight(1f)) {
      Text(
        if (state.isAuthenticated) state.accountName.ifBlank { "Spotify account" } else "Spotify",
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (state.isAuthenticated) SandiaGreen else SandiaMuted))
        Text(
          if (state.isAuthenticated) "Spotify Connected" else "Not connected",
          color = SandiaMuted,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(start = 6.dp),
        )
      }
      if (state.lastSyncAt != null) {
        Text(
          "Last sync: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(state.lastSyncAt)),
          color = SandiaMuted.copy(alpha = 0.8f),
          style = MaterialTheme.typography.labelSmall,
        )
      }
    }

    if (state.isAuthenticated) {
      TextButton(onClick = onSync, enabled = !state.isLoading) {
        Text(if (state.isLoading) "Syncing" else "Sync", color = SandiaPink)
      }
      TextButton(onClick = onRefresh, enabled = !state.isLoading) {
        Text("↻", color = SandiaPink, fontSize = 20.sp)
      }
    } else {
      TextButton(onClick = onConnect) {
        Text("Connect", color = SandiaPink)
      }
    }
  }
}

@Composable
private fun SandiaTab(
  title: String,
  selected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(24.dp))
      .background(if (selected) SandiaSurfaceRaised else SandiaSurface)
      .clickable(onClick = onClick)
      .padding(vertical = 11.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      title,
      color = if (selected) Color.White else SandiaMuted,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
  }
}

@Composable
private fun SandiaPlaylistRow(
  title: String,
  subtitle: String,
  trackCount: Int?,
  imageUrl: String?,
  isLikedSongs: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(SandiaSurface)
      .clickable(onClick = onClick)
      .padding(10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(13.dp),
  ) {
    Box(
      modifier = Modifier.size(66.dp).clip(RoundedCornerShape(10.dp))
        .background(SandiaSurfaceRaised),
      contentAlignment = Alignment.Center,
    ) {
      if (!imageUrl.isNullOrBlank()) {
        AsyncImage(
          model = imageUrl,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Text(
          if (isLikedSongs) "♥" else "♫",
          color = if (isLikedSongs) SandiaPink else SandiaMuted,
          fontSize = 34.sp,
          fontWeight = FontWeight.Light,
        )
      }
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        title,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        buildString {
          append(subtitle)
          if (trackCount != null) append(" · $trackCount songs")
        },
        color = SandiaMuted,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text("›", color = SandiaMuted, fontSize = 25.sp)
  }
}

@Composable
private fun SandiaMiniPlayer(
  metadata: MediaMetadata,
  isPlaying: Boolean,
  onPrevious: () -> Unit,
  onToggle: () -> Unit,
  onNext: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth()
      .background(SandiaSurfaceRaised)
      .padding(horizontal = 14.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    AsyncImage(
      model = metadata.thumbnailUrl,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        metadata.title,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontWeight = FontWeight.Medium,
      )
      Text(
        metadata.artists.joinToString { it.name },
        color = SandiaMuted,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    TextButton(onClick = onPrevious) { Text("◀|", color = SandiaPink) }
    TextButton(onClick = onToggle) {
      Text(if (isPlaying) "Ⅱ" else "▶", color = SandiaPink, fontSize = 18.sp)
    }
    TextButton(onClick = onNext) { Text("|▶", color = SandiaPink) }
  }
}
