package com.gno.smbvideoplayer.smb.server

import android.webkit.MimeTypeMap
import jcifs.smb.SmbFile
import java.io.IOException
import java.io.InputStream

class StreamSource(private var file: SmbFile, l: Long) : RandomAccessStream(l) {

    val mimeType: String = MimeTypeMap.getFileExtensionFromUrl(file.name)
    override var currentPosition: Long = 0
    var name: String = file.name
    private lateinit var input: InputStream

    @Throws(IOException::class)
    fun open() {
        try {
            input = file.inputStream
            if (currentPosition > 0) input.skip(currentPosition)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun read(): Int {
        val read = input.read()
        if (read != -1) currentPosition++
        return read
    }

    @Throws(IOException::class)
    override fun read(bytes: ByteArray, start: Int, offs: Int): Int {
        val read = input.read(bytes, start, offs)
        currentPosition += read.toLong()
        return read
    }

    @Throws(IllegalArgumentException::class)
    override fun moveTo(position: Long) {
        require(!(position < 0 || length < position)) { "Position out of the bounds of the file!" }
        currentPosition = position
    }

    override fun close() {
        try {
            input.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}