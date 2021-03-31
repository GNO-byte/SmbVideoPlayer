package com.gno.smbvideoplayer.smb.server

import android.util.Log
import jcifs.smb.SmbFile
import java.io.IOException
import java.util.*

class Streamer private constructor(port: Int) : StreamServer(port) {
    private var file: SmbFile? = null
    private var length: Long = 0
    fun setStreamSrc(file: SmbFile?, len: Long) {
        this.file = file
        length = len
    }

    override fun stop() {
        super.stop()
        instance = null
    }

    override fun serve(
        uri: String?,
        method: String?,
        header: Properties?,
        parms: Properties?,
        files: Properties?
    ): Response {
        val res: Response
        var sourceFile: SmbFile? = null
        val name = getNameFromPath(uri)
        if (file != null && file!!.name == name) sourceFile = file
        if (sourceFile == null) res = Response(HTTP_NOTFOUND, MIME_PLAINTEXT, null) else {
            var startFrom: Long = 0
            var endAt: Long = -1
            var range = header?.getProperty("range")
            if (range != null) {
                if (range.startsWith("bytes=")) {
                    range = range.substring("bytes=".length)
                    val minus = range.indexOf('-')
                    try {
                        if (minus > 0) {
                            startFrom = range.substring(0, minus).toLong()
                            endAt = range.substring(minus + 1).toLong()
                        }
                    } catch (nfe: NumberFormatException) {
                    }
                }
            }
            Log.d("Explorer", "Request: $range from: $startFrom, to: $endAt")
            val source = StreamSource(sourceFile, length)
            val fileLen = source.length
            if (range != null && startFrom > 0) {
                if (startFrom >= fileLen) {
                    res = Response(HTTP_RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, null)
                    res.addHeader("Content-Range", "bytes 0-0/$fileLen")
                } else {
                    if (endAt < 0) endAt = fileLen - 1
                    var newLen = fileLen - startFrom
                    if (newLen < 0) newLen = 0
                    Log.d("Explorer", "start=$startFrom, endAt=$endAt, newLen=$newLen")
                    val dataLen = newLen
                    source.moveTo(startFrom)
                    Log.d("Explorer", "Skipped $startFrom bytes")
                    res = Response(HTTP_PARTIALCONTENT, source.mimeType, source)
                    res.addHeader("Content-length", "" + dataLen)
                }
            } else {
                source.reset()
                res = Response(HTTP_OK, source.mimeType, source)
                res.addHeader("Content-Length", "" + fileLen)
            }
        }
        res.addHeader("Accept-Ranges", "bytes") // Announce that the file
        return res
    }

    companion object {
        private const val PORT = 7871
        const val URL = "http://127.0.0.1:$PORT"
        private var instance: Streamer? = null
        fun getInstance(): Streamer? {
            if (instance == null) try {
                instance = Streamer(PORT)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return instance
        }

        private fun getNameFromPath(path: String?): String? {
            if (path == null || path.length < 2) return null
            val slash = path.lastIndexOf('/')
            return if (slash == -1) path else path.substring(slash + 1)
        }
    }
}