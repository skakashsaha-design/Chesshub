package com.example.chesscom

import com.example.lichess.ApiFetchOutcome
import com.example.lichess.ErrorKind
import com.example.lichess.PublicRecentGame
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

@JsonClass(generateAdapter = true)
data class ChessComPlayerProfile(
  val username: String = "",
  val title: String? = null,
  val url: String? = null,
  val name: String? = null,
)

@JsonClass(generateAdapter = true)
data class ChessComRatingLast(
  val rating: Int? = null,
)

@JsonClass(generateAdapter = true)
data class ChessComRecord(
  val win: Int? = 0,
  val loss: Int? = 0,
  val draw: Int? = 0,
)

@JsonClass(generateAdapter = true)
data class ChessComModeStats(
  val last: ChessComRatingLast? = null,
  val record: ChessComRecord? = null,
)

@JsonClass(generateAdapter = true)
data class ChessComStatsResponse(
  @Json(name = "chess_rapid") val rapid: ChessComModeStats? = null,
  @Json(name = "chess_blitz") val blitz: ChessComModeStats? = null,
  @Json(name = "chess_bullet") val bullet: ChessComModeStats? = null,
  @Json(name = "chess_daily") val daily: ChessComModeStats? = null,
)

@JsonClass(generateAdapter = true)
data class ChessComArchivesResponse(
  val archives: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ChessComGameSide(
  val username: String = "",
  val rating: Int? = null,
  val result: String? = null,
)

@JsonClass(generateAdapter = true)
data class ChessComGameItem(
  val url: String = "",
  val pgn: String? = null,
  @Json(name = "time_class") val timeClass: String? = null,
  val white: ChessComGameSide? = null,
  val black: ChessComGameSide? = null,
)

@JsonClass(generateAdapter = true)
data class ChessComMonthlyGamesResponse(
  val games: List<ChessComGameItem> = emptyList(),
)

data class ChessComProfileResult(
  val username: String,
  val title: String?,
  val ratingsSummary: String,
  val totalGames: Int,
  val wins: Int,
  val losses: Int,
  val draws: Int,
  val profileUrl: String,
  val recentGames: List<PublicRecentGame>,
)

interface ChessComPubApi {
  @GET("pub/player/{username}")
  suspend fun getPlayer(@Path("username") username: String): ChessComPlayerProfile

  @GET("pub/player/{username}/stats")
  suspend fun getStats(@Path("username") username: String): ChessComStatsResponse

  @GET("pub/player/{username}/games/archives")
  suspend fun getArchives(@Path("username") username: String): ChessComArchivesResponse

  @GET
  suspend fun getMonthlyGames(@Url archiveUrl: String): ChessComMonthlyGamesResponse
}

/**
 * Official read-only Chess.com PubAPI repository.
 * Does not scrape Chess.com and does not attempt to send moves or control live games.
 */
class ChessComRepository {

  private val moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val okHttp = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .addInterceptor { chain ->
      val req = chain.request().newBuilder()
        .header("User-Agent", "ChessHub-Android/1.0 (https://github.com/chesshub)")
        .build()
      chain.proceed(req)
    }
    .build()

  private val api: ChessComPubApi = Retrofit.Builder()
    .baseUrl("https://api.chess.com/")
    .client(okHttp)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create(ChessComPubApi::class.java)

  suspend fun fetchProfileAndGames(rawUsername: String): ApiFetchOutcome<ChessComProfileResult> =
    withContext(Dispatchers.IO) {
      val username = rawUsername.trim().lowercase(Locale.US)
      if (username.isEmpty()) {
        return@withContext ApiFetchOutcome.Failure(ErrorKind.NOT_FOUND, "Username is empty")
      }
      try {
        val player = api.getPlayer(username)
        val stats = runCatching { api.getStats(username) }.getOrNull()

        val ratings = buildList {
          stats?.bullet?.last?.rating?.let { add("Bullet $it") }
          stats?.blitz?.last?.rating?.let { add("Blitz $it") }
          stats?.rapid?.last?.rating?.let { add("Rapid $it") }
          stats?.daily?.last?.rating?.let { add("Daily $it") }
        }.joinToString(" • ").ifEmpty { "Unrated" }

        val modes = listOfNotNull(stats?.bullet, stats?.blitz, stats?.rapid, stats?.daily)
        val wins = modes.sumOf { it.record?.win ?: 0 }
        val losses = modes.sumOf { it.record?.loss ?: 0 }
        val draws = modes.sumOf { it.record?.draw ?: 0 }

        val recentGames = runCatching {
          val archives = api.getArchives(username).archives
          val latestUrl = archives.lastOrNull() ?: return@runCatching emptyList()
          api.getMonthlyGames(latestUrl).games.asReversed().take(8).map { g ->
            val res = when {
              g.white?.result == "win" -> "1-0"
              g.black?.result == "win" -> "0-1"
              else -> "1/2-1/2"
            }
            PublicRecentGame(
              id = g.url.substringAfterLast('/'),
              white = g.white?.username ?: "White",
              black = g.black?.username ?: "Black",
              result = res,
              speed = g.timeClass ?: "standard",
              pgn = g.pgn.orEmpty(),
              opening = null,
              url = g.url,
            )
          }
        }.getOrDefault(emptyList())

        ApiFetchOutcome.Success(
          ChessComProfileResult(
            username = player.username.ifBlank { username },
            title = player.title,
            ratingsSummary = ratings,
            totalGames = wins + losses + draws,
            wins = wins,
            losses = losses,
            draws = draws,
            profileUrl = player.url ?: "https://www.chess.com/member/$username",
            recentGames = recentGames,
          )
        )
      } catch (e: HttpException) {
        when (e.code()) {
          404 -> ApiFetchOutcome.Failure(ErrorKind.NOT_FOUND, "Chess.com user not found")
          429 -> ApiFetchOutcome.Failure(ErrorKind.RATE_LIMITED, "Chess.com rate limit reached")
          else -> ApiFetchOutcome.Failure(ErrorKind.SERVER_ERROR, "Chess.com HTTP ${e.code()}")
        }
      } catch (_: SocketTimeoutException) {
        ApiFetchOutcome.Failure(ErrorKind.TIMEOUT, "Chess.com connection timed out")
      } catch (_: UnknownHostException) {
        ApiFetchOutcome.Failure(ErrorKind.NO_INTERNET, "No internet connection")
      } catch (e: IOException) {
        ApiFetchOutcome.Failure(ErrorKind.NO_INTERNET, e.localizedMessage ?: "Network error")
      } catch (e: Exception) {
        ApiFetchOutcome.Failure(ErrorKind.SERVER_ERROR, e.localizedMessage ?: "Unexpected error")
      }
    }
}
