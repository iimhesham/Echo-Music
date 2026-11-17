package echo.music.iad1tya.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import echo.music.iad1tya.LocalPlayerConnection
import echo.music.iad1tya.db.MusicDatabase
import echo.music.iad1tya.db.entities.LyricsEntity
import echo.music.iad1tya.extensions.metadata
import echo.music.iad1tya.lyrics.LyricsHelper
import echo.music.iad1tya.lyrics.LyricsUtils
import echo.music.iad1tya.lyrics.LyricsUtils.parseLyrics
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.playback.PlayerConnection
import echo.music.iad1tya.ui.utils.resize
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Fetches lyrics for the current song (the original app's providers) and stores them locally. */
@HiltViewModel
class SandiaPlayerViewModel
@Inject
constructor(
  private val lyricsHelper: LyricsHelper,
  private val database: MusicDatabase,
) : ViewModel() {
  private val requested = HashSet<String>()

  fun ensureLyrics(metadata: MediaMetadata) {
    if (!requested.add(metadata.id)) return
    viewModelScope.launch(Dispatchers.IO) {
      if (database.lyrics(metadata.id).first() != null) return@launch
      val result =
        try {
          lyricsHelper.getLyrics(metadata)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          return@launch
        }
      database.query {
        upsert(
          LyricsEntity(
            id = metadata.id,
            lyrics = result.lyrics ?: "",
            provider = result.providerName,
          )
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandiaPlayerScreen(
  navController: NavController,
  viewModel: SandiaPlayerViewModel = hiltViewModel(),
) {
  SandiaTheme {
    val playerConnection = LocalPlayerConnection.current
    val metadata by
      (playerConnection?.mediaMetadata ?: flowOf<MediaMetadata?>(null)).collectAsState(null)
    val isPlaying by
      (playerConnection?.isEffectivelyPlaying ?: flowOf(false)).collectAsState(false)
    val shuffle by (playerConnection?.shuffleModeEnabled ?: flowOf(false)).collectAsState(false)
    val repeat by
      (playerConnection?.repeatMode ?: flowOf(Player.REPEAT_MODE_OFF))
        .collectAsState(Player.REPEAT_MODE_OFF)
    val lyricsEntity by
      (playerConnection?.currentLyrics ?: flowOf<LyricsEntity?>(null)).collectAsState(null)

    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    var position by remember { mutableLongStateOf(0L) }
    var playerDuration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val m = metadata

    LaunchedEffect(m?.id) {
      while (playerConnection != null) {
        if (!dragging) {
          position = playerConnection.player.currentPosition
          playerDuration = playerConnection.player.duration.coerceAtLeast(0L)
        }
        delay(250)
      }
    }
    LaunchedEffect(m?.id, showLyrics) { if (showLyrics && m != null) viewModel.ensureLyrics(m) }

    Column(
      Modifier.fillMaxSize()
        .background(Sandia.Black)
        .systemBarsPadding()
        .padding(horizontal = 24.dp)
    ) {
      Row(
        Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        IconButton(onClick = { navController.navigateUp() }) {
          Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Sandia.TextPrimary, modifier = Modifier.size(32.dp))
        }
        Text("Now playing", color = Sandia.TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.size(48.dp))
      }

      if (m == null || playerConnection == null) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
          Text("Nothing is playing", color = Sandia.TextSecondary)
        }
      } else {
        val total = if (playerDuration > 0) playerDuration else m.duration * 1000L

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
          if (showLyrics) {
            SandiaLyricsView(lyricsEntity, position, Modifier.fillMaxSize())
          } else {
            SandiaCover(
              m.thumbnailUrl,
              Modifier.fillMaxWidth().aspectRatio(1f),
              22.dp,
              sizePx = 544,
            )
          }
        }

        Spacer(Modifier.height(20.dp))
        Text(
          m.title,
          color = Sandia.TextPrimary,
          fontSize = 23.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          m.artists.joinToString { it.name },
          color = Sandia.TextSecondary,
          fontSize = 16.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 2.dp),
        )

        Slider(
          value = if (dragging) dragValue else if (total > 0) (position.toFloat() / total).coerceIn(0f, 1f) else 0f,
          onValueChange = {
            dragging = true
            dragValue = it
          },
          onValueChangeFinished = {
            playerConnection.player.seekTo((dragValue * total).toLong())
            position = (dragValue * total).toLong()
            dragging = false
          },
          colors =
            SliderDefaults.colors(
              thumbColor = Color.White,
              activeTrackColor = Color.White,
              inactiveTrackColor = Sandia.Line,
            ),
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(sandiaFormatTime(if (dragging) (dragValue * total).toLong() else position), color = Sandia.TextSecondary, fontSize = 12.sp)
          Text(sandiaFormatTime(total), color = Sandia.TextSecondary, fontSize = 12.sp)
        }

        Row(
          Modifier.fillMaxWidth().padding(vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          IconButton(onClick = { playerConnection.player.shuffleModeEnabled = !shuffle }) {
            Icon(
              Icons.Rounded.Shuffle,
              null,
              tint = if (shuffle) Sandia.Accent else Sandia.TextSecondary,
            )
          }
          IconButton(onClick = { playerConnection.seekToPrevious() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, null, tint = Sandia.TextPrimary, modifier = Modifier.size(38.dp))
          }
          Box(
            Modifier.size(70.dp).clip(CircleShape).background(Color.White).clickable { playerConnection.togglePlayPause() },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              null,
              tint = Color.Black,
              modifier = Modifier.size(38.dp),
            )
          }
          IconButton(onClick = { playerConnection.seekToNext() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, null, tint = Sandia.TextPrimary, modifier = Modifier.size(38.dp))
          }
          IconButton(
            onClick = {
              playerConnection.player.repeatMode =
                when (repeat) {
                  Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                  Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                  else -> Player.REPEAT_MODE_OFF
                }
            }
          ) {
            Icon(
              if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
              null,
              tint = if (repeat == Player.REPEAT_MODE_OFF) Sandia.TextSecondary else Sandia.Accent,
            )
          }
        }

        Row(
          Modifier.fillMaxWidth().padding(bottom = 12.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          PlayerPill(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", false) { showQueue = true }
          PlayerPill(Icons.Rounded.Lyrics, "Lyrics", showLyrics) { showLyrics = !showLyrics }
          PlayerPill(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add", false) { showMore = true }
        }
      }
    }

    if (showQueue && playerConnection != null) {
      SandiaQueueSheet(playerConnection) { showQueue = false }
    }
    if (showMore && m != null) {
      SandiaSongSheet(m) { showMore = false }
    }
  }
}

@Composable
private fun PlayerPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  active: Boolean,
  onClick: () -> Unit,
) {
  Row(
    Modifier.clip(RoundedCornerShape(24.dp))
      .background(if (active) Sandia.Accent else Sandia.SurfaceHigh)
      .clickable(onClick = onClick)
      .padding(horizontal = 18.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, null, tint = if (active) Sandia.OnAccent else Sandia.TextPrimary, modifier = Modifier.size(20.dp))
    Text(
      label,
      color = if (active) Sandia.OnAccent else Sandia.TextPrimary,
      fontSize = 14.sp,
      modifier = Modifier.padding(start = 8.dp),
    )
  }
}

@Composable
private fun SandiaLyricsView(entity: LyricsEntity?, position: Long, modifier: Modifier) {
  val raw = entity?.lyrics
  Box(modifier, contentAlignment = Alignment.Center) {
    if (entity == null) {
      Text("Looking for lyrics...", color = Sandia.TextSecondary)
    } else if (raw.isNullOrBlank() || raw == LyricsEntity.LYRICS_NOT_FOUND) {
      Text("No lyrics found for this song", color = Sandia.TextSecondary)
    } else {
      val lines = remember(raw) { parseLyrics(raw) }
      val synced = remember(lines) { lines.any { it.time > 0L } }
      val index = if (synced) LyricsUtils.findCurrentLineIndex(lines, position) else -1
      val listState = rememberLazyListState()
      LaunchedEffect(index) {
        if (index >= 0) listState.animateScrollToItem((index - 2).coerceAtLeast(0))
      }
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
      ) {
        itemsIndexed(lines) { i, line ->
          Text(
            line.text,
            fontSize = if (i == index) 24.sp else 20.sp,
            fontWeight = if (i == index) FontWeight.Bold else FontWeight.Medium,
            color = if (!synced || i == index) Color.White else Color(0xFF6B6B72),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SandiaQueueSheet(playerConnection: PlayerConnection, onDismiss: () -> Unit) {
  val windows by playerConnection.queueWindows.collectAsState()
  val current by playerConnection.currentMediaItemIndex.collectAsState()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val listState = rememberLazyListState()
  LaunchedEffect(Unit) { if (current > 0) listState.scrollToItem(current) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Sandia.Surface,
    contentColor = Sandia.TextPrimary,
  ) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
      Text(
        "Up next",
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
      )
      LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        itemsIndexed(windows) { i, window ->
          val md = window.mediaItem.metadata
          SandiaSongRow(
            title = md?.title ?: window.mediaItem.mediaMetadata.title?.toString().orEmpty(),
            artist = md?.artists?.joinToString { it.name }.orEmpty(),
            thumbnailUrl = md?.thumbnailUrl,
            playing = i == current,
            onClick = { playerConnection.player.seekToDefaultPosition(i) },
            onTrailing = if (i == current) null else ({ playerConnection.player.removeMediaItem(i) }),
            trailingIcon = Icons.Rounded.Close,
          )
        }
      }
    }
  }
}
