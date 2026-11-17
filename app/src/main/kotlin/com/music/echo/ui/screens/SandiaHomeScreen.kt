package echo.music.iad1tya.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import echo.music.iad1tya.LocalDatabase
import echo.music.iad1tya.constants.SpotifyAccountAvatarUrlKey
import echo.music.iad1tya.constants.SpotifyAccountNameKey
import echo.music.iad1tya.utils.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Library: local data only. No Spotify or network calls happen when this screen opens. */
@Composable
fun SandiaHomeScreen(navController: NavController) {
  val context = LocalContext.current
  val database = LocalDatabase.current
  val scope = rememberCoroutineScope()

  val playlists by database.playlistsByCreateDateAsc().collectAsState(initial = emptyList())
  val accountName by
    remember { context.dataStore.data.map { it[SpotifyAccountNameKey] } }
      .collectAsState(initial = null)
  val avatarUrl by
    remember { context.dataStore.data.map { it[SpotifyAccountAvatarUrlKey] } }
      .collectAsState(initial = null)

  var menuOpen by remember { mutableStateOf(false) }
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
            else "Update available (${found.tag}). Open the menu and tap Install update."
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

  val sorted = remember(playlists) { playlists.sortedByDescending { it.playlist.id == SANDIA_LIKED_ID } }
  val connected = !accountName.isNullOrBlank()

  SandiaScaffold(navController) {
    LazyColumn(Modifier.fillMaxWidth()) {
      item {
        Row(
          Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            "Sandía",
            color = Sandia.Accent,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = { navController.navigate("sandia/search") }) {
            Icon(Icons.Rounded.Search, null, tint = Sandia.TextPrimary)
          }
          Box {
            IconButton(onClick = { menuOpen = true }) {
              Icon(Icons.Rounded.MoreVert, null, tint = Sandia.TextPrimary)
            }
            DropdownMenu(
              expanded = menuOpen,
              onDismissRequest = { menuOpen = false },
              containerColor = Sandia.SurfaceHigh,
            ) {
              DropdownMenuItem(
                text = { Text("Spotify account") },
                onClick = {
                  menuOpen = false
                  navController.navigate("settings/spotify_import")
                },
              )
              DropdownMenuItem(
                text = { Text("Import from Spotify file") },
                onClick = {
                  menuOpen = false
                  navController.navigate("settings/spotify_file_import")
                },
              )
              DropdownMenuItem(
                text = { Text(if (pendingUpdate != null) "Install update" else "Check for updates") },
                onClick = {
                  menuOpen = false
                  checkOrInstall()
                },
              )
            }
          }
        }
      }

      item {
        Row(
          Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Sandia.Surface)
            .clickable { navController.navigate("settings/spotify_import") }
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (connected) {
            SandiaCover(avatarUrl, Modifier.size(46.dp), 23.dp, sizePx = 160)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
              Text(
                accountName.orEmpty(),
                color = Sandia.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Sandia.Green))
                Spacer(Modifier.size(6.dp))
                Text("Spotify connected", color = Sandia.TextSecondary, fontSize = 12.sp)
              }
            }
            Icon(Icons.Rounded.Sync, null, tint = Sandia.TextSecondary)
          } else {
            Column(Modifier.weight(1f)) {
              Text(
                "Connect Spotify",
                color = Sandia.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                "Bring your Liked Songs into Sandía",
                color = Sandia.TextSecondary,
                fontSize = 12.sp,
              )
            }
          }
        }
      }

      if (updateText.isNotEmpty()) {
        item {
          Text(
            updateText,
            color = Sandia.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
          )
        }
      }

      item {
        Text(
          "Playlists",
          color = Sandia.TextPrimary,
          fontSize = 20.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
        )
      }

      if (sorted.isEmpty()) {
        item {
          Text(
            "Nothing here yet. Connect Spotify or import your file from the menu, or search for any song.",
            color = Sandia.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
          )
        }
      }

      items(sorted, key = { it.playlist.id }) { p ->
        val isLiked = p.playlist.id == SANDIA_LIKED_ID
        Row(
          Modifier.fillMaxWidth()
            .clickable { navController.navigate("sandia/playlist/${Uri.encode(p.playlist.id)}") }
            .padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (isLiked) {
            SandiaLikedCover(Modifier.size(58.dp), 12.dp)
          } else {
            SandiaCover(
              p.playlist.thumbnailUrl ?: p.thumbnails.firstOrNull(),
              Modifier.size(58.dp),
              12.dp,
            )
          }
          Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
              if (isLiked) "Liked Songs" else p.playlist.name,
              color = Sandia.TextPrimary,
              fontSize = 16.sp,
              fontWeight = FontWeight.Medium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text("${p.songCount} songs", color = Sandia.TextSecondary, fontSize = 13.sp)
          }
        }
      }
    }
  }
}
