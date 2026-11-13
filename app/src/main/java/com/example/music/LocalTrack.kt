package com.example.music

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class RepeatMode {
  OFF,
  ALL,
  ONE,
}

data class LocalTrack(
  val id: String,
  val title: String,
  val artist: String,
  val uriString: String,
  val durationMs: Long,
) {
  val uri: Uri get() = Uri.parse(uriString)
}

object LocalAudioScanner {

  suspend fun scanDeviceMediaStore(context: Context, maxItems: Int = 200): List<LocalTrack> =
    withContext(Dispatchers.IO) {
      val tracks = ArrayList<LocalTrack>()
      val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.IS_MUSIC,
      )
      val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
      val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

      try {
        context.contentResolver.query(
          MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
          projection,
          selection,
          null,
          sortOrder,
        )?.use { cursor ->
          val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
          val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
          val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
          val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

          while (cursor.moveToNext() && tracks.size < maxItems) {
            val id = cursor.getLong(idCol)
            val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Track $id"
            val artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }
              ?: "Unknown Artist"
            val dur = cursor.getLong(durCol).coerceAtLeast(0L)
            if (dur < 3_000L) continue // skip tiny notification clips
            val contentUri = ContentUris.withAppendedId(
              MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
              id,
            )
            tracks.add(
              LocalTrack(
                id = "ms_$id",
                title = title,
                artist = artist,
                uriString = contentUri.toString(),
                durationMs = dur,
              )
            )
          }
        }
      } catch (_: SecurityException) {
        return@withContext emptyList()
      } catch (_: Throwable) {
        return@withContext emptyList()
      }
      tracks
    }

  suspend fun resolvePickedUris(context: Context, uris: List<Uri>): List<LocalTrack> =
    withContext(Dispatchers.IO) {
      uris.mapIndexed { idx, uri ->
        var title = uri.lastPathSegment?.substringAfterLast('/') ?: "Audio ${idx + 1}"
        runCatching {
          context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
              if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) {
                  c.getString(nameIdx)?.let { title = it.substringBeforeLast('.') }
                }
              }
            }
        }
        LocalTrack(
          id = "doc_${uri.hashCode()}",
          title = title,
          artist = "Local File",
          uriString = uri.toString(),
          durationMs = 0L,
        )
      }
    }
}
