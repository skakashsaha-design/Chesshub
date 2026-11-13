package com.example.lichess

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class LichessPerf(
  val games: Int? = 0,
  val rating: Int? = null,
  val rd: Int? = null,
)

@JsonClass(generateAdapter = true)
data class LichessPerfs(
  val bullet: LichessPerf? = null,
  val blitz: LichessPerf? = null,
  val rapid: LichessPerf? = null,
  val classical: LichessPerf? = null,
  val puzzle: LichessPerf? = null,
)

@JsonClass(generateAdapter = true)
data class LichessCount(
  val all: Int? = 0,
  val win: Int? = 0,
  val loss: Int? = 0,
  val draw: Int? = 0,
)

@JsonClass(generateAdapter = true)
data class LichessUserResponse(
  val id: String,
  val username: String,
  val title: String? = null,
  val perfs: LichessPerfs? = null,
  val count: LichessCount? = null,
  val url: String? = null,
)

@JsonClass(generateAdapter = true)
data class LichessGamePlayerUser(
  val name: String? = null,
)

@JsonClass(generateAdapter = true)
data class LichessGamePlayer(
  val user: LichessGamePlayerUser? = null,
  val rating: Int? = null,
  val aiLevel: Int? = null,
)

@JsonClass(generateAdapter = true)
data class LichessGamePlayers(
  val white: LichessGamePlayer? = null,
  val black: LichessGamePlayer? = null,
)

@JsonClass(generateAdapter = true)
data class LichessGameNdjson(
  val id: String = "",
  val speed: String? = null,
  val status: String? = null,
  val winner: String? = null,
  val createdAt: Long? = null,
  val players: LichessGamePlayers? = null,
  val moves: String? = null,
  val pgn: String? = null,
  @Json(name = "opening") val opening: LichessOpening? = null,
)

@JsonClass(generateAdapter = true)
data class LichessOpening(
  val eco: String? = null,
  val name: String? = null,
)

data class PublicRecentGame(
  val id: String,
  val white: String,
  val black: String,
  val result: String,
  val speed: String,
  val pgn: String,
  val opening: String?,
  val url: String,
)

data class LichessProfileResult(
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

interface LichessRetrofitApi {
  @GET("api/user/{username}")
  suspend fun getUser(@Path("username") username: String): LichessUserResponse
}

sealed interface ApiFetchOutcome<out T> {
  data class Success<T>(val data: T) : ApiFetchOutcome<T>
  data class Failure(val kind: ErrorKind, val message: String) : ApiFetchOutcome<Nothing>
}

enum class ErrorKind {
  NO_INTERNET,
  TIMEOUT,
  NOT_FOUND,
  RATE_LIMITED,
  SERVER_ERROR,
}

class LichessRepository {

  private val moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val okHttp = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .addInterceptor { chain ->
      val req = chain.request().newBuilder()
        .header("User-Agent", "ChessHub-Android/1.0")
        .build()
      chain.proceed(req)
    }
    .build()

  private val api: LichessRetrofitApi = Retrofit.Builder()
    .baseUrl("https://lichess.org/")
    .client(okHttp)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create(LichessRetrofitApi::class.java)

  private val gameAdapter = moshi.adapter(LichessGameNdjson::class.java)

  suspend fun fetchProfileAndGames(rawUsername: String): ApiFetchOutcome<LichessProfileResult> =
    withContext(Dispatchers.IO) {
      val username = rawUsername.trim()
      if (username.isEmpty()) {
        return@withContext ApiFetchOutcome.Failure(ErrorKind.NOT_FOUND, "Username is empty")
      }
      try {
        val user = api.getUser(username)
        val ratings = buildList {
          user.perfs?.bullet?.rating?.let { add("Bullet $it") }
          user.perfs?.blitz?.rating?.let { add("Blitz $it") }
          user.perfs?.rapid?.rating?.let { add("Rapid $it") }
          user.perfs?.classical?.rating?.let { add("Classical $it") }
        }.joinToString(" • ").ifEmpty { "Unrated" }

        val games = fetchRecentPublicGames(user.username)
        val count = user.count
        ApiFetchOutcome.Success(
          LichessProfileResult(
            username = user.username,
            title = user.title,
            ratingsSummary = ratings,
            totalGames = count?.all ?: 0,
            wins = count?.win ?: 0,
            losses = count?.loss ?: 0,
            draws = count?.draw ?: 0,
            profileUrl = user.url ?: "https://lichess.org/@/${user.username}",
            recentGames = games,
          )
        )
      } catch (e: HttpException) {
        when (e.code()) {
          404 -> ApiFetchOutcome.Failure(ErrorKind.NOT_FOUND, "Lichess user not found")
          429 -> ApiFetchOutcome.Failure(ErrorKind.RATE_LIMITED, "Lichess rate limit reached")
          else -> ApiFetchOutcome.Failure(ErrorKind.SERVER_ERROR, "Lichess HTTP ${e.code()}")
        }
      } catch (_: SocketTimeoutException) {
        ApiFetchOutcome.Failure(ErrorKind.TIMEOUT, "Lichess connection timed out")
      } catch (_: UnknownHostException) {
        ApiFetchOutcome.Failure(ErrorKind.NO_INTERNET, "No internet connection")
      } catch (e: IOException) {
        ApiFetchOutcome.Failure(ErrorKind.NO_INTERNET, e.localizedMessage ?: "Network error")
      } catch (e: Exception) {
        ApiFetchOutcome.Failure(ErrorKind.SERVER_ERROR, e.localizedMessage ?: "Unexpected error")
      }
    }

  private fun fetchRecentPublicGames(username: String): List<PublicRecentGame> {
    val req = Request.Builder()
      .url("https://lichess.org/api/games/user/$username?max=8&pgnInJson=true&opening=true")
      .header("Accept", "application/x-ndjson")
      .build()

    val result = ArrayList<PublicRecentGame>(8)
    try {
      okHttp.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return emptyList()
        val body = resp.body ?: return emptyList()
        body.charStream().buffered().useLines { lines ->
          for (line in lines) {
            if (line.isBlank()) continue
            val g = runCatching { gameAdapter.fromJson(line) }.getOrNull() ?: continue
            val w = g.players?.white?.user?.name
              ?: g.players?.white?.aiLevel?.let { "Stockfish $it" }
              ?: "White"
            val b = g.players?.black?.user?.name
              ?: g.players?.black?.aiLevel?.let { "Stockfish $it" }
              ?: "Black"
            val res = when (g.winner) {
              "white" -> "1-0"
              "black" -> "0-1"
              else -> if (g.status == "draw" || g.status == "stalemate") "1/2-1/2" else "*"
            }
            val pgnText = g.pgn?.takeIf { it.isNotBlank() } ?: g.moves.orEmpty()
            result.add(
              PublicRecentGame(
                id = g.id,
                white = w,
                black = b,
                result = res,
                speed = g.speed ?: "standard",
                pgn = pgnText,
                opening = g.opening?.name,
                url = "https://lichess.org/${g.id}",
              )
            )
            if (result.size >= 8) break
          }
        }
      }
    } catch (_: Exception) {
      // Games list is optional enrichment; never fail whole profile if games stream fails
    }
    return result
  }

  /**
   * Official Lichess OAuth2 PKCE authorization URI builder (no client secret stored in APK).
   */
  fun buildOfficialOAuthAuthorizeUrl(clientId: String, redirectUri: String, codeChallenge: String): String {
    return "https://lichess.org/oauth?response_type=code&client_id=$clientId" +
      "&redirect_uri=$redirectUri&scope=preference:read&code_challenge_method=S256&code_challenge=$codeChallenge"
  }
}
