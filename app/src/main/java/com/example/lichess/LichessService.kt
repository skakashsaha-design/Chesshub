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
import org.json.JSONArray
import org.json.JSONObject
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

data class TvChannelGame(
  val channelKey: String,
  val channelLabel: String,
  val gameId: String,
  val featuredPlayer: String,
  val featuredTitle: String?,
  val featuredRating: Int?,
  val color: String,
)

data class LiveTournamentItem(
  val id: String,
  val fullName: String,
  val speedLabel: String,
  val timeControlLabel: String,
  val nbPlayers: Int,
  val statusLabel: String,
  val minutes: Int,
  val url: String,
)

data class ChessNewsItem(
  val id: String,
  val title: String,
  val description: String,
  val roundName: String?,
  val url: String,
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

  suspend fun fetchTvChannels(): ApiFetchOutcome<List<TvChannelGame>> =
    withContext(Dispatchers.IO) {
      try {
        val req = Request.Builder()
          .url("https://lichess.org/api/tv/channels")
          .header("Accept", "application/json")
          .build()
        okHttp.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) {
            return@withContext ApiFetchOutcome.Failure(ErrorKind.SERVER_ERROR, "TV HTTP ${resp.code}")
          }
          val body = resp.body?.string().orEmpty()
          val json = JSONObject(body)
          val ordered = listOf(
            "topRated" to "Top Rated",
            "bullet" to "Bullet",
            "blitz" to "Blitz",
            "rapid" to "Rapid",
            "classical" to "Classical",
            "chess960" to "Chess960",
            "ultraBullet" to "UltraBullet",
          )
          val out = ArrayList<TvChannelGame>(ordered.size)
          for ((key, label) in ordered) {
            val ch = json.optJSONObject(key) ?: continue
            val gameId = ch.optString("gameId")
            val userObj = ch.optJSONObject("user") ?: continue
            val name = userObj.optString("name", "Player")
            val title = userObj.optString("title").takeIf { it.isNotBlank() }
            val rating = if (ch.has("rating")) ch.optInt("rating") else null
            val color = ch.optString("color", "white")
            if (gameId.isNotBlank()) {
              out.add(TvChannelGame(key, label, gameId, name, title, rating, color))
            }
          }
          ApiFetchOutcome.Success(out)
        }
      } catch (_: SocketTimeoutException) {
        ApiFetchOutcome.Failure(ErrorKind.TIMEOUT, "Lichess TV timed out")
      } catch (_: UnknownHostException) {
        ApiFetchOutcome.Failure(ErrorKind.NO_INTERNET, "No internet connection")
      } catch (e: Exception) {
        ApiFetchOutcome.Failure(ErrorKind.SERVER_ERROR, e.localizedMessage ?: "Unable to fetch live games")
      }
    }

  suspend fun fetchPublicGamePgn(gameId: String): ApiFetchOutcome<String> =
    withContext(Dispatchers.IO) {
      try {
        val req = Request.Builder()
          .url("https://lichess.org/game/export/$gameId?clocks=false&evals=false&literate=false")
          .header("Accept", "application/x-chess-pgn")
          .build()
        okHttp.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) {
            return@withContext ApiFetchOutcome.Failure(ErrorKind.SERVER_ERROR, "Game export HTTP ${resp.code}")
          }
          val pgn = resp.body?.string().orEmpty()
          if (pgn.isBlank()) {
            ApiFetchOutcome.Failure(ErrorKind.NOT_FOUND, "Game PGN unavailable")
          } else {
            ApiFetchOutcome.Success(pgn)
          }
        }
      } catch (_: UnknownHostException) {
        ApiFetchOutcome.Failure(ErrorKind.NO_INTERNET, "No internet connection")
      } catch (e: Exception) {
        ApiFetchOutcome.Failure(ErrorKind.SERVER_ERROR, e.localizedMessage ?: "Failed to load game")
      }
    }

  suspend fun fetchLiveTournaments(): ApiFetchOutcome<List<LiveTournamentItem>> =
    withContext(Dispatchers.IO) {
      try {
        val req = Request.Builder()
          .url("https://lichess.org/api/tournament")
          .header("Accept", "application/json")
          .build()
        okHttp.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) {
            return@withContext ApiFetchOutcome.Failure(ErrorKind.SERVER_ERROR, "Tournament HTTP ${resp.code}")
          }
          val json = JSONObject(resp.body?.string().orEmpty())
          val list = ArrayList<LiveTournamentItem>()
          fun addArray(arr: JSONArray?, statusLabel: String) {
            if (arr == null) return
            for (i in 0 until minOf(arr.length(), 10)) {
              val o = arr.optJSONObject(i) ?: continue
              val id = o.optString("id")
              val name = o.optString("fullName", "Lichess Arena")
              val perfName = o.optJSONObject("perf")?.optString("name") ?: "Standard"
              val clock = o.optJSONObject("clock")
              val limitSec = clock?.optInt("limit", 180) ?: 180
              val incSec = clock?.optInt("increment", 0) ?: 0
              val tcLabel = if (limitSec >= 60) "${limitSec / 60}+$incSec" else "${limitSec}s+$incSec"
              val players = o.optInt("nbPlayers", 0)
              val minutes = o.optInt("minutes", 60)
              if (id.isNotBlank()) {
                list.add(
                  LiveTournamentItem(
                    id = id,
                    fullName = name,
                    speedLabel = perfName,
                    timeControlLabel = tcLabel,
                    nbPlayers = players,
                    statusLabel = statusLabel,
                    minutes = minutes,
                    url = "https://lichess.org/tournament/$id",
                  )
                )
              }
            }
          }
          addArray(json.optJSONArray("started"), "Live Now")
          addArray(json.optJSONArray("created"), "Upcoming")
          ApiFetchOutcome.Success(list)
        }
      } catch (_: UnknownHostException) {
        ApiFetchOutcome.Failure(ErrorKind.NO_INTERNET, "No internet connection")
      } catch (e: Exception) {
        ApiFetchOutcome.Failure(ErrorKind.SERVER_ERROR, e.localizedMessage ?: "Failed to load tournaments")
      }
    }

  suspend fun fetchChessNewsBroadcasts(): ApiFetchOutcome<List<ChessNewsItem>> =
    withContext(Dispatchers.IO) {
      try {
        val req = Request.Builder()
          .url("https://lichess.org/api/broadcast?nb=10")
          .header("Accept", "application/x-ndjson")
          .build()
        okHttp.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) {
            return@withContext ApiFetchOutcome.Failure(ErrorKind.SERVER_ERROR, "News HTTP ${resp.code}")
          }
          val body = resp.body ?: return@withContext ApiFetchOutcome.Success(emptyList())
          val items = ArrayList<ChessNewsItem>(10)
          body.charStream().buffered().useLines { lines ->
            for (line in lines) {
              if (line.isBlank()) continue
              val o = runCatching { JSONObject(line) }.getOrNull() ?: continue
              val tour = o.optJSONObject("tour") ?: continue
              val id = tour.optString("id")
              val name = tour.optString("name", "Chess Broadcast")
              val desc = tour.optString("description", "").ifBlank { "Live tournament coverage & annotated rounds" }
              val url = tour.optString("url", "https://lichess.org/broadcast")
              val rounds = o.optJSONArray("rounds")
              val lastRound = rounds?.optJSONObject(rounds.length() - 1)?.optString("name")
              if (id.isNotBlank()) {
                items.add(
                  ChessNewsItem(
                    id = id,
                    title = name,
                    description = desc,
                    roundName = lastRound?.takeIf { it.isNotBlank() },
                    url = url,
                  )
                )
              }
              if (items.size >= 10) break
            }
          }
          ApiFetchOutcome.Success(items)
        }
      } catch (_: UnknownHostException) {
        ApiFetchOutcome.Failure(ErrorKind.NO_INTERNET, "No internet connection")
      } catch (e: Exception) {
        ApiFetchOutcome.Failure(ErrorKind.SERVER_ERROR, e.localizedMessage ?: "Failed to load chess news")
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
    }
    return result
  }

  fun buildOfficialOAuthAuthorizeUrl(clientId: String, redirectUri: String, codeChallenge: String): String {
    return "https://lichess.org/oauth?response_type=code&client_id=$clientId" +
      "&redirect_uri=$redirectUri&scope=preference:read&code_challenge_method=S256&code_challenge=$codeChallenge"
  }
}
