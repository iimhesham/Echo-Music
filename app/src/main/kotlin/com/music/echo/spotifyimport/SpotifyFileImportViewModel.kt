package echo.music.iad1tya.spotifyimport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FileImportUiState(
  val running: Boolean = false,
  val done: Int = 0,
  val total: Int = 0,
  val result: ImportResult? = null,
  val error: String? = null,
)

@HiltViewModel
class SpotifyFileImportViewModel
@Inject
constructor(private val importer: SpotifyExportImporter) : ViewModel() {
  private val _state = MutableStateFlow(FileImportUiState())
  val state: StateFlow<FileImportUiState> = _state.asStateFlow()

  fun import(uri: Uri) {
    if (_state.value.running) return
    _state.value = FileImportUiState(running = true)
    viewModelScope.launch {
      try {
        val result = importer.import(uri) { done, total ->
          _state.update { it.copy(done = done, total = total) }
        }
        _state.value = FileImportUiState(result = result)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        _state.value = FileImportUiState(error = e.message ?: e.toString())
      }
    }
  }
}
