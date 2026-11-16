/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package echo.music.iad1tya.spotify

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Official Spotify OAuth 2.0 Authorization Code with PKCE support.
 *
 * Sandía is a native Android public client: it must never embed a Spotify client secret.
 * The client ID is supplied through BuildConfig.SPOTIFY_CLIENT_ID.
 */
object SpotifyOAuth {
  const val REDIRECT_URI = "sandia://spotify-callback"
  const val REQUIRED_SCOPE = "user-library-read"

  private const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
  private const val TOKEN_URL = "https://accounts.spotify.com/api/token"

  private val random = SecureRandom()
  private val json = Json { ignoreUnknownKeys = true }

  data class AuthorizationRequest(
    val url: String,
    val state: String,
    val codeVerifier: String,
  )

  @Serializable
  data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresInSeconds: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String? = null,
  )

  fun createAuthorizationRequest(clientId: String): AuthorizationRequest {
    require(clientId.isNotBlank()) { "Spotify Client ID is not configured." }

    val verifier = randomUrlSafeValue(64)
    val state = randomUrlSafeValue(32)
    val challenge = Base64.getUrlEncoder().withoutPadding()
      .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)))

    val url = Uri.parse(AUTHORIZE_URL).buildUpon()
      .appendQueryParameter("client_id", clientId)
      .appendQueryParameter("response_type", "code")
      .appendQueryParameter("redirect_uri", REDIRECT_URI)
      .appendQueryParameter("scope", REQUIRED_SCOPE)
      .appendQueryParameter("state", state)
      .appendQueryParameter("code_challenge_method", "S256")
      .appendQueryParameter("code_challenge", challenge)
      .build()
      .toString()

    return AuthorizationRequest(url = url, state = state, codeVerifier = verifier)
  }

  suspend fun exchangeAuthorizationCode(
    clientId: String,
    code: String,
    codeVerifier: String,
  ): TokenResponse = postToken(
    mapOf(
      "grant_type" to "authorization_code",
      "client_id" to clientId,
      "code" to code,
      "redirect_uri" to REDIRECT_URI,
      "code_verifier" to codeVerifier,
    )
  )

  suspend fun refreshAccessToken(
    clientId: String,
    refreshToken: String,
  ): TokenResponse = postToken(
    mapOf(
      "grant_type" to "refresh_token",
      "client_id" to clientId,
      "refresh_token" to refreshToken,
    )
  )

  private fun randomUrlSafeValue(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    random.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
  }

  private suspend fun postToken(parameters: Map<String, String>): TokenResponse =
    withContext(Dispatchers.IO) {
      val connection = (URL(TOKEN_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15_000
        readTimeout = 15_000
        doOutput = true
        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        setRequestProperty("Accept", "application/json")
      }

      try {
        val body = parameters.entries.joinToString("&") { (key, value) ->
          "${formEncode(key)}=${formEncode(value)}"
        }
        connection.outputStream.use { output ->
          output.write(body.toByteArray(StandardCharsets.UTF_8))
        }

        val status = connection.responseCode
        val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
          ?.bufferedReader(StandardCharsets.UTF_8)
          ?.use { it.readText() }
          .orEmpty()

        if (status !in 200..299) {
          throw IllegalStateException("Spotify token request failed (HTTP $status): $responseBody")
        }

        json.decodeFromString<TokenResponse>(responseBody)
      } finally {
        connection.disconnect()
      }
    }

  private fun formEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
