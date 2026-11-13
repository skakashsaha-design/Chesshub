package com.example.online

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class MatchType {
  QUICK_MATCH,
  RATED,
  CASUAL,
  FRIEND_CHALLENGE,
  PRIVATE_ROOM,
}

data class OnlineAccount(
  val userId: String,
  val handle: String,
  val rating: Int,
  val sessionTokenRef: String,
)

data class OnlineMatchRequest(
  val matchType: MatchType,
  val timeControlLabel: String,
  val rated: Boolean,
  val inviteCode: String? = null,
)

data class OnlineChatMessage(
  val id: String,
  val sender: String,
  val text: String,
  val timestampMillis: Long = System.currentTimeMillis(),
  val isSelf: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class OnlineWireEnvelope(
  val type: String, // "auth", "queue", "create_room", "join_room", "move", "chat", "resign", "draw_offer", "draw_accept", "reconnect", "state", "error"
  val gameId: String? = null,
  val roomCode: String? = null,
  val uciMove: String? = null,
  val fen: String? = null,
  val whiteName: String? = null,
  val blackName: String? = null,
  val whiteTimeMs: Long? = null,
  val blackTimeMs: Long? = null,
  val serverValidated: Boolean? = null,
  val result: String? = null,
  val sender: String? = null,
  val text: String? = null,
  val message: String? = null,
)

sealed interface OnlineConnectionState {
  data object BackendNotConfigured : OnlineConnectionState
  data object Disconnected : OnlineConnectionState
  data object Connecting : OnlineConnectionState
  data class Connected(val serverUrl: String, val account: OnlineAccount?) : OnlineConnectionState
  data class SearchingMatch(val request: OnlineMatchRequest) : OnlineConnectionState
  data class ActiveOnlineGame(
    val gameId: String,
    val opponentName: String,
    val opponentRating: Int?,
    val localColor: String,
    val serverFen: String,
    val whiteTimeMs: Long,
    val blackTimeMs: Long,
    val lastValidatedMove: String?,
  ) : OnlineConnectionState
  data class Error(val message: String) : OnlineConnectionState
}

/**
 * Production online multiplayer transport & protocol architecture over HTTPS/WSS.
 * Supports matchmaking, private rooms, server-validated moves, reconnection, and player chat.
 */
class OnlineMultiplayerClient {

  private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
  private val envelopeAdapter = moshi.adapter(OnlineWireEnvelope::class.java)

  private val okHttp = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.MILLISECONDS)
    .pingInterval(20, TimeUnit.SECONDS)
    .build()

  private var webSocket: WebSocket? = null
  private var configuredUrl: String = ""
  private var currentHandle: String = "Player"

  private val _state = MutableStateFlow<OnlineConnectionState>(OnlineConnectionState.BackendNotConfigured)
  val state: StateFlow<OnlineConnectionState> = _state.asStateFlow()

  private val _chatMessages = MutableStateFlow<List<OnlineChatMessage>>(emptyList())
  val chatMessages: StateFlow<List<OnlineChatMessage>> = _chatMessages.asStateFlow()

  fun updateServerConfiguration(serverUrl: String) {
    val clean = serverUrl.trim()
    configuredUrl = clean
    if (clean.isEmpty()) {
      disconnect()
      _state.value = OnlineConnectionState.BackendNotConfigured
    } else if (!clean.startsWith("https://")) {
      _state.value = OnlineConnectionState.Error("Online server URL must use HTTPS for secure transport.")
    } else if (_state.value is OnlineConnectionState.BackendNotConfigured) {
      _state.value = OnlineConnectionState.Disconnected
    }
  }

  fun isBackendConfigured(): Boolean = configuredUrl.startsWith("https://")

  fun connect(handle: String, sessionToken: String) {
    if (!isBackendConfigured()) {
      _state.value = OnlineConnectionState.BackendNotConfigured
      return
    }
    if (handle.isBlank() || sessionToken.isBlank()) {
      _state.value = OnlineConnectionState.Error("Valid handle and session token are required.")
      return
    }
    currentHandle = handle.trim()
    val wssUrl = configuredUrl.replaceFirst("https://", "wss://").trimEnd('/') + "/ws/chess"
    _state.value = OnlineConnectionState.Connecting

    val req = Request.Builder()
      .url(wssUrl)
      .header("Authorization", "Bearer $sessionToken")
      .build()

    webSocket?.close(1000, "Reconnecting")
    webSocket = okHttp.newWebSocket(
      req,
      object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          _state.value = OnlineConnectionState.Connected(
            serverUrl = configuredUrl,
            account = OnlineAccount(
              userId = currentHandle.lowercase(),
              handle = currentHandle,
              rating = 1200,
              sessionTokenRef = "token_active",
            ),
          )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
          val msg = runCatching { envelopeAdapter.fromJson(text) }.getOrNull() ?: return
          handleServerEnvelope(msg)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
          _state.value = OnlineConnectionState.Error(
            t.localizedMessage ?: "Unable to reach online chess server"
          )
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
          if (isBackendConfigured()) {
            _state.value = OnlineConnectionState.Disconnected
          }
        }
      },
    )
  }

  fun requestMatch(request: OnlineMatchRequest) {
    if (!isBackendConfigured()) {
      _state.value = OnlineConnectionState.BackendNotConfigured
      return
    }
    val ws = webSocket ?: run {
      _state.value = OnlineConnectionState.Error("Connect to the online server first.")
      return
    }
    val wireType = when (request.matchType) {
      MatchType.PRIVATE_ROOM -> if (request.inviteCode.isNullOrBlank()) "create_room" else "join_room"
      else -> "queue"
    }
    val json = envelopeAdapter.toJson(
      OnlineWireEnvelope(
        type = wireType,
        roomCode = request.inviteCode,
        message = "${request.matchType.name}:${request.timeControlLabel}:${request.rated}",
      )
    )
    ws.send(json)
    _state.value = OnlineConnectionState.SearchingMatch(request)
  }

  fun sendChatMessage(gameId: String?, rawText: String): Boolean {
    val clean = rawText.trim()
    if (clean.isEmpty()) return false
    val ws = webSocket ?: return false
    val json = envelopeAdapter.toJson(
      OnlineWireEnvelope(
        type = "chat",
        gameId = gameId,
        sender = currentHandle,
        text = clean,
      )
    )
    ws.send(json)
    _chatMessages.update { cur ->
      (cur + OnlineChatMessage(
        id = "local_${System.nanoTime()}",
        sender = currentHandle,
        text = clean,
        isSelf = true,
      )).takeLast(60)
    }
    return true
  }

  fun submitMoveForServerValidation(gameId: String, uciMove: String) {
    val ws = webSocket ?: return
    ws.send(
      envelopeAdapter.toJson(
        OnlineWireEnvelope(type = "move", gameId = gameId, uciMove = uciMove)
      )
    )
  }

  fun sendResign(gameId: String) {
    webSocket?.send(envelopeAdapter.toJson(OnlineWireEnvelope(type = "resign", gameId = gameId)))
  }

  fun sendDrawOffer(gameId: String) {
    webSocket?.send(envelopeAdapter.toJson(OnlineWireEnvelope(type = "draw_offer", gameId = gameId)))
  }

  fun reconnectToGame(gameId: String) {
    webSocket?.send(envelopeAdapter.toJson(OnlineWireEnvelope(type = "reconnect", gameId = gameId)))
  }

  fun disconnect() {
    webSocket?.close(1000, "User disconnected")
    webSocket = null
    _chatMessages.value = emptyList()
    _state.value = if (isBackendConfigured()) {
      OnlineConnectionState.Disconnected
    } else {
      OnlineConnectionState.BackendNotConfigured
    }
  }

  private fun handleServerEnvelope(msg: OnlineWireEnvelope) {
    when (msg.type) {
      "state" -> {
        val gId = msg.gameId ?: return
        val fen = msg.fen ?: return
        _state.value = OnlineConnectionState.ActiveOnlineGame(
          gameId = gId,
          opponentName = msg.blackName ?: "Opponent",
          opponentRating = null,
          localColor = "WHITE",
          serverFen = fen,
          whiteTimeMs = msg.whiteTimeMs ?: 300_000L,
          blackTimeMs = msg.blackTimeMs ?: 300_000L,
          lastValidatedMove = msg.uciMove,
        )
      }
      "chat" -> {
        val body = msg.text ?: msg.message ?: return
        val sender = msg.sender ?: "Opponent"
        _chatMessages.update { cur ->
          (cur + OnlineChatMessage(
            id = "srv_${System.nanoTime()}",
            sender = sender,
            text = body,
            isSelf = sender.equals(currentHandle, ignoreCase = true),
          )).takeLast(60)
        }
      }
      "error" -> {
        _state.value = OnlineConnectionState.Error(msg.message ?: "Server rejected action")
      }
    }
  }
}
