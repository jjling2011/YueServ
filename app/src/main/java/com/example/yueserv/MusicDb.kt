package com.example.yueserv

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MusicData(
    val ok: Boolean, val data: String
)

const val MUSIC_ROOT = "/storage/"
private const val TAG = "MusicDB"

class MusicDb(private val context: Context) {

    fun getData(): String {
        val data = loadData()
        val musicData = MusicData(ok = true, data = data)
        return Json.encodeToString(musicData)
    }

    private fun loadData(): String {
        val musicList = mutableListOf<String>()
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media.DATA
        )
        val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)

        cursor?.use {
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (it.moveToNext()) {
                val data = it.getString(dataColumn)
                if (!data.startsWith(MUSIC_ROOT)) {
                    continue
                }
                // Log.d(TAG, "find file: $data")
                val fix = ".$data" // 为了配合 web 那边
                musicList.add(fix)
            }
        }

        return Json.encodeToString(musicList)
    }
}
