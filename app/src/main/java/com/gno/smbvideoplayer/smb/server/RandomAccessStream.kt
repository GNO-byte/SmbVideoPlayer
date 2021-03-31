package com.gno.smbvideoplayer.smb.server

import java.io.IOException
import java.io.InputStream

abstract class RandomAccessStream(val length: Long) : InputStream() {

    private var markedPosition: Long = 0
    protected abstract val currentPosition: Long

    init {
        mark(-1)
    }

    @Synchronized
    override fun reset() {
        moveTo(markedPosition)
    }

    @Throws(IOException::class)
    abstract override fun read(): Int

    abstract fun moveTo(position: Long)

    @Synchronized
    final override fun mark(readLimit: Int) {
        require(readLimit == -1) { "readLimit argument of RandomAccessStream.mark() is not used, please set to -1!" }
        markedPosition = currentPosition
    }

    override fun markSupported(): Boolean {
        return true
    }

    @Throws(IOException::class)
    override fun available(): Int {
        throw IOException("Use availableExact()!")
    }

}