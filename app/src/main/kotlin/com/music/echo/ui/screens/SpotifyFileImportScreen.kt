@file:OptIn(ExperimentalMaterial3Api::class)

package echo.music.iad1tya.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import echo.music.iad1tya.spotifyimport.SpotifyFileImportViewModel

@Composable
fun SpotifyFileImportScreen(
  navController: NavController,
  viewModel: SpotifyFileImportViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) viewModel.import(uri)
    }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Import Spotify Liked Songs") },
        navigationIcon = {
          IconButton(onClick = { navController.navigateUp() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        },
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        "1) On spotify.com: Account > Privacy settings > Download your data (Account data).\n" +
          "2) When Spotify emails you the ZIP, pick it here (or YourLibrary.json).\n" +
          "Keep this screen open until it finishes.",
        style = MaterialTheme.typography.bodyMedium,
      )

      Button(
        onClick = { picker.launch(arrayOf("*/*")) },
        enabled = !state.running,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Choose Spotify ZIP / YourLibrary.json")
      }

      if (state.running) {
        val progress = if (state.total > 0) state.done / state.total.toFloat() else 0f
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text("${state.done} / ${state.total}")
      }

      state.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }

      state.result?.let { r ->
        Text(
          "Done: ${r.imported} of ${r.total} songs added to Liked Songs.",
          style = MaterialTheme.typography.titleMedium,
        )
        if (r.failed.isNotEmpty()) {
          Text("Not found (${r.failed.size}):", style = MaterialTheme.typography.titleSmall)
          LazyColumn(modifier = Modifier.weight(1f)) {
            items(r.failed) { t -> Text("${t.artist} - ${t.track}") }
          }
        }
      }
    }
  }
}
