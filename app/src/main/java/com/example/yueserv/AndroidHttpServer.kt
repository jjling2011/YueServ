package com.example.yueserv

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

private const val TAG = "AndroidHttpServer"

class AndroidHttpServer(
    port: Int, private val context: android.content.Context
) : NanoHTTPD(port) {


    override fun serve(session: IHTTPSession): Response {
        var path = session.uri
        if (path == "" || path == "/") {
            path = "/index.html"
        }

        // Log.i(TAG, "accept path: $path")
        // 特殊处理 serv.php
        if (path == "/serv.php") {
            return responseWithMusicData()
        }

        // 检查 assets 中是否存在文件
        try {
            val stream = tryFile(path)
            val mimeType = Utils.getMimeType(path)
            if (mimeType.startsWith("audio/")) {
                val headers = session.headers
                var rangeHeader: String? = null
                for (key in headers.keys) {
                    if ("range" == key) {
                        rangeHeader = headers[key]
                        break
                    }
                }
                if (!rangeHeader.isNullOrEmpty()) {
                    return getPartialResponse(stream, mimeType, rangeHeader)
                }
            }
            return newFixedLengthResponse(
                Response.Status.OK, mimeType, stream, stream.available().toLong()
            )
        } catch (e: Exception) {
            // 文件不存在，返回 404 错误
            Log.e(TAG, "${e.message}")
        }
        val http404 = newFixedLengthResponse(
            Response.Status.NOT_FOUND, "text/plain", "404 Not Found"
        )
        return http404
    }

    private fun getPartialResponse(
        stream: InputStream, mimeType: String, rangeHeader: String
    ): Response {
        val rangeValue = rangeHeader.trim { it <= ' ' }.substring("bytes=".length)
        val streamLength = stream.available().toLong()
        val start: Long
        var end: Long
        if (rangeValue.startsWith("-")) {
            end = streamLength - 1
            start = (streamLength - 1 - rangeValue.substring("-".length).toLong())
        } else {
            val range =
                rangeValue.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            start = range[0].toLong()
            end = if (range.size > 1) range[1].toLong() else streamLength - 1
        }
        if (end > streamLength - 1) {
            end = streamLength - 1
        }
        if (start <= end) {
            val contentLength = end - start + 1
            stream.skip(start)
            val response = newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT, mimeType, stream, contentLength
            )
            response.addHeader("Content-Length", contentLength.toString() + "")
            response.addHeader("Content-Range", "bytes $start-$end/$streamLength")
            response.addHeader("Content-Type", mimeType)
            return response
        } else {
            return newChunkedResponse(Response.Status.INTERNAL_ERROR, "text/html", null)
        }
    }

    private fun responseWithMusicData(): Response {
        val musicDb = MusicDb(context.applicationContext)
        val data = musicDb.getData()
        return newFixedLengthResponse(
            Response.Status.OK, "application/json", data
        )
    }

    private fun tryFile(path: String): InputStream {
        // try music first
        if (path.startsWith(MUSIC_ROOT)) {
            val absPath = File(path).absolutePath
            // double check
            if (absPath.startsWith(MUSIC_ROOT)) {
                return FileInputStream(absPath)
            }
        }

        // try web dir
        val fileName = path.substring(1)
        return context.assets.open(fileName)
    }

}
