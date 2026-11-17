package echo.music.iad1tya.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import echo.music.iad1tya.LocalPlayerConnection
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.ui.utils.resize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

const val SANDIA_LIKED_ID = "SPOTIFY_LIKED_SONGS"

/** Sandía's own palette: true AMOLED black, charcoal surfaces, soft coral accent. */
object Sandia {
  val Black = Color(0xFF000000)
  val Surface = Color(0xFF0D0D0E)
  val SurfaceHigh = Color(0xFF171719)
  val Line = Color(0xFF242427)
  val Accent = Color(0xFFFF8F7F)
  val OnAccent = Color(0xFF1B0B08)
  val TextPrimary = Color(0xFFF4F4F5)
  val TextSecondary = Color(0xFF9A9AA2)
  val Green = Color(0xFF1ED760)
}

@Composable
fun SandiaTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme =
      darkColorScheme(
        primary = Sandia.Accent,
        onPrimary = Sandia.OnAccent,
        background = Sandia.Black,
        onBackground = Sandia.TextPrimary,
        surface = Sandia.Black,
        onSurface = Sandia.TextPrimary,
        surfaceVariant = Sandia.SurfaceHigh,
        onSurfaceVariant = Sandia.TextSecondary,
        outline = Sandia.Line,
        outlineVariant = Sandia.Line,
        surfaceContainer = Sandia.Surface,
        surfaceContainerHigh = Sandia.SurfaceHigh,
        surfaceContainerHighest = Sandia.SurfaceHigh,
        secondaryContainer = Sandia.SurfaceHigh,
        onSecondaryContainer = Sandia.TextPrimary,
      ),
    content = content,
  )
}

fun sandiaFormatTime(ms: Long): String {
  val total = (ms / 1000).coerceAtLeast(0)
  return "%d:%02d".format(total / 60, total % 60)
}

/** One square cover with a neutral placeholder; no collages. */
@Composable
fun SandiaCover(
  url: String?,
  modifier: Modifier = Modifier,
  corner: Dp = 10.dp,
  sizePx: Int = 226,
) {
  Box(
    modifier.clip(RoundedCornerShape(corner)).background(Sandia.SurfaceHigh),
    contentAlignment = Alignment.Center,
  ) {
    if (!url.isNullOrBlank()) {
      AsyncImage(
        model = url.resize(sizePx, sizePx),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Icon(Icons.Rounded.MusicNote, null, tint = Sandia.TextSecondary, modifier = Modifier.size(22.dp))
    }
  }
}

@Composable
fun SandiaLikedCover(modifier: Modifier = Modifier, corner: Dp = 10.dp) {
  Box(
    modifier
      .clip(RoundedCornerShape(corner))
      .background(Brush.linearGradient(listOf(Color(0xFFFF8F7F), Color(0xFF7B3FE4)))),
    contentAlignment = Alignment.Center,
  ) {
    Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.48f))
  }
}

@Composable
fun SandiaSongRow(
  title: String,
  artist: String,
  thumbnailUrl: String?,
  playing: Boolean,
  onClick: () -> Unit,
  onTrailing: (() -> Unit)? = null,
  trailingIcon: ImageVector = Icons.Rounded.MoreVert,
) {
  Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SandiaCover(thumbnailUrl, Modifier.size(48.dp), 8.dp)
    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
      Text(
        title,
        color = if (playing) Sandia.Accent else Sandia.TextPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        artist,
        color = Sandia.TextSecondary,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (onTrailing != null) {
      IconButton(onClick = onTrailing) {
        Icon(trailingIcon, null, tint = Sandia.TextSecondary)
      }
    }
  }
}

@Composable
fun SandiaMiniPlayer(navController: NavController) {
  val playerConnection = LocalPlayerConnection.current ?: return
  val metadata by playerConnection.mediaMetadata.collectAsState(null)
  val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState(false)
  val m = metadata ?: return

  var progress by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(m.id, isPlaying) {
    while (true) {
      val duration = playerConnection.player.duration
      val position = playerConnection.player.currentPosition
      progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
      delay(if (isPlaying) 500 else 2000)
    }
  }

  Column(Modifier.fillMaxWidth().background(Sandia.Surface)) {
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier.fillMaxWidth().height(2.dp),
      color = Sandia.Accent,
      trackColor = Sandia.Line,
    )
    Row(
      Modifier.fillMaxWidth()
        .clickable { navController.navigate("sandia/player") }
        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SandiaCover(m.thumbnailUrl, Modifier.size(44.dp), 8.dp)
      Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text(
          m.title,
          color = Sandia.TextPrimary,
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          m.artists.joinToString { it.name },
          color = Sandia.TextSecondary,
          fontSize = 12.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(onClick = { playerConnection.seekToPrevious() }) {
        Icon(Icons.Rounded.SkipPrevious, null, tint = Sandia.TextPrimary)
      }
      IconButton(onClick = { playerConnection.togglePlayPause() }) {
        Icon(
          if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
          null,
          tint = Sandia.TextPrimary,
          modifier = Modifier.size(30.dp),
        )
      }
      IconButton(onClick = { playerConnection.seekToNext() }) {
        Icon(Icons.Rounded.SkipNext, null, tint = Sandia.TextPrimary)
      }
    }
  }
}

/** Common frame: AMOLED black, system bars respected, mini player pinned at the bottom. */
@Composable
fun SandiaScaffold(
  navController: NavController,
  showMiniPlayer: Boolean = true,
  content: @Composable () -> Unit,
) {
  SandiaTheme {
    Column(Modifier.fillMaxSize().background(Sandia.Black).systemBarsPadding()) {
      Box(Modifier.weight(1f).fillMaxWidth()) { content() }
      if (showMiniPlayer) SandiaMiniPlayer(navController)
    }
  }
}
