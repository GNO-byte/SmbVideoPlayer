package com.gno.smbvideoplayer.smb.server

import android.webkit.MimeTypeMap
import java.util.*


object MimeTypes {
    private const val ALL_MIME_TYPES = "*/*"
    private val MIME_TYPES = HashMap<String, String>(1 + (68 / 0.75).toInt())

    init {
        MIME_TYPES["jpgv"] = "video/jpeg"
        MIME_TYPES["jpgm"] = "video/jpm"
        MIME_TYPES["jpm"] = "video/jpm"
        MIME_TYPES["mj2"] = "video/mj2"
        MIME_TYPES["mjp2"] = "video/mj2"
        MIME_TYPES["mpa"] = "video/mpeg"
        MIME_TYPES["ogv"] = "video/ogg"
        MIME_TYPES["flv"] = "video/x-flv"
        MIME_TYPES["mkv"] = "video/x-matroska"
    }

    fun getMimeType(path: String, isDirectory: Boolean): String? {
        if (isDirectory) {
            return null
        }
        var type: String? = ALL_MIME_TYPES
        val extension = getExtension(path)

        if (extension.isNotEmpty()) {
            val extensionLowerCase = extension.toLowerCase(
                Locale
                    .getDefault(),
            )
            val mime = MimeTypeMap.getSingleton()
            type = mime.getMimeTypeFromExtension(extensionLowerCase)
            if (type == null) {
                type = MIME_TYPES[extensionLowerCase]
            }
        }
        if (type == null) type = ALL_MIME_TYPES
        return type
    }

    private fun getExtension(path: String): String {
        return if (path.contains(".")) path.substring(path.lastIndexOf(".") + 1)
            .toLowerCase(Locale.ROOT) else ""
    }

}