package com.gno.smbvideoplayer.smb.server

import android.net.Uri
import android.util.Log
import java.io.*
import java.net.BindException
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

abstract class StreamServer(myTcpPort: Int) {
    abstract fun serve(
        uri: String?,
        method: String?,
        header: Properties?,
        parms: Properties?,
        files: Properties?
    ): Response?

    inner class Response(var status: String, var mimeType: String, var data: StreamSource?) {
        fun addHeader(name: String, value: String) {
            header[name] = value
        }

        var header = Properties()
    }

    open fun stop() {
        try {
            myServerSocket.close()
            myThread.join()
        } catch (e: IOException) {
        } catch (e: InterruptedException) {
        }
    }

    @Throws(IOException::class)
    private fun tryBind(port: Int): ServerSocket {
        return try {
            ServerSocket(port)
        } catch (ifPortIsOccupiedByCloudStreamer: BindException) {
            ServerSocket(port)
        }
    }

    private inner class HTTPSession(private val socket: Socket) : Runnable {
        private var `is`: InputStream? = null
        override fun run() {
            try {
                handleResponse(socket)
            } finally {
                if (`is` != null) {
                    try {
                        `is`!!.close()
                        socket.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }

        private fun handleResponse(socket: Socket) {
            try {
                `is` = socket.getInputStream()
                if (`is` == null) return

                val bufsize = 8192
                var buf = ByteArray(bufsize)
                var rlen = `is`!!.read(buf, 0, bufsize)
                if (rlen <= 0) return

                val hbis = ByteArrayInputStream(buf, 0, rlen)
                val hin = BufferedReader(InputStreamReader(hbis, "utf-8"))
                val pre = Properties()
                val parms = Properties()
                val header = Properties()
                val files = Properties()

                decodeHeader(hin, pre, parms, header)
                Log.d("Explorer", pre.toString())
                Log.d("Explorer", "Params: $parms")
                Log.d("Explorer", "Header: $header")
                val method = pre.getProperty("method")
                val uri = pre.getProperty("uri")
                var size = 0x7FFFFFFFFFFFFFFFL
                val contentLength = header.getProperty("content-length")
                if (contentLength != null) {
                    try {
                        size = contentLength.toInt().toLong()
                    } catch (ex: NumberFormatException) {
                    }
                }

                var splitbyte = 0
                var sbfound = false
                while (splitbyte < rlen) {
                    if (buf[splitbyte] == '\r'.toByte() && buf[++splitbyte] == '\n'.toByte() && buf[++splitbyte] == '\r'.toByte() && buf[++splitbyte] == '\n'.toByte()) {
                        sbfound = true
                        break
                    }
                    splitbyte++
                }
                splitbyte++

                val f = ByteArrayOutputStream()
                if (splitbyte < rlen) f.write(buf, splitbyte, rlen - splitbyte)

                if (splitbyte < rlen) size -= (rlen - splitbyte + 1).toLong() else if (!sbfound || size == 0x7FFFFFFFFFFFFFFFL) size =
                    0

                buf = ByteArray(512)
                while (rlen >= 0 && size > 0) {
                    rlen = `is`!!.read(buf, 0, 512)
                    size -= rlen.toLong()
                    if (rlen > 0) f.write(buf, 0, rlen)
                }

                val fbuf = f.toByteArray()

                val bin = ByteArrayInputStream(fbuf)
                val `in` = BufferedReader(InputStreamReader(bin))

                if (method.equals("POST", ignoreCase = true)) {
                    var contentType = ""
                    val contentTypeHeader = header.getProperty("content-type")
                    var st = StringTokenizer(contentTypeHeader, "; ")
                    if (st.hasMoreTokens()) {
                        contentType = st.nextToken()
                    }
                    if (contentType.equals("multipart/form-data", ignoreCase = true)) {
                        if (!st.hasMoreTokens()) sendError(
                            socket,
                            HTTP_BADREQUEST,
                            "BAD REQUEST: Content type is multipart/form-data but boundary missing. Usage: GET /example/file.html"
                        )
                        val boundaryExp = st.nextToken()
                        st = StringTokenizer(boundaryExp, "=")
                        if (st.countTokens() != 2) sendError(
                            socket,
                            HTTP_BADREQUEST,
                            "BAD REQUEST: Content type is multipart/form-data but boundary syntax error. Usage: GET /example/file.html"
                        )
                        st.nextToken()
                        val boundary = st.nextToken()
                        decodeMultipartData(boundary, fbuf, `in`, parms, files)
                    } else {
                        var postLine = ""
                        val pbuf = CharArray(512)
                        var read = `in`.read(pbuf)
                        while (read >= 0 && !postLine.endsWith("\r\n")) {
                            postLine += String(pbuf, 0, read)
                            read = `in`.read(pbuf)
                            if (Thread.interrupted()) {
                                throw InterruptedException()
                            }
                        }
                        postLine = postLine.trim { it <= ' ' }
                        decodeParms(postLine, parms)
                    }
                }

                val r = serve(uri, method, header, parms, files)
                if (r == null) sendError(
                    socket,
                    HTTP_INTERNALERROR,
                    "SERVER INTERNAL ERROR: Serve() returned a null response."
                ) else sendResponse(socket, r.status, r.mimeType, r.header, r.data)
                `in`.close()
            } catch (ioe: IOException) {
                try {
                    sendError(
                        socket,
                        HTTP_INTERNALERROR,
                        "SERVER INTERNAL ERROR: IOException: " + ioe.message
                    )
                } catch (t: Throwable) {
                }
            } catch (ie: InterruptedException) {
            }
        }

        @Throws(InterruptedException::class)
        private fun decodeHeader(
            `in`: BufferedReader,
            pre: Properties,
            parms: Properties,
            header: Properties
        ) {
            try {
                val inLine = `in`.readLine() ?: return
                val st = StringTokenizer(inLine)
                if (!st.hasMoreTokens()) sendError(
                    socket,
                    HTTP_BADREQUEST,
                    "BAD REQUEST: Syntax error. Usage: GET /example/file.html"
                )
                val method = st.nextToken()
                pre["method"] = method
                if (!st.hasMoreTokens()) sendError(
                    socket,
                    HTTP_BADREQUEST,
                    "BAD REQUEST: Missing URI. Usage: GET /example/file.html"
                )
                var uri = st.nextToken()

                val qmi = uri!!.indexOf('?')
                uri = if (qmi >= 0) {
                    decodeParms(uri.substring(qmi + 1), parms)
                    decodePercent(uri.substring(0, qmi))
                } else Uri.decode(uri)

                if (st.hasMoreTokens()) {
                    var line = `in`.readLine()
                    while (line != null && line.trim { it <= ' ' }.isNotEmpty()) {
                        val p = line.indexOf(':')
                        if (p >= 0) header[line.substring(0, p).trim { it <= ' ' }.toLowerCase(
                            Locale.ROOT
                        )] =
                            line.substring(p + 1).trim { it <= ' ' }
                        line = `in`.readLine()
                    }
                }
                pre["uri"] = uri
            } catch (ioe: IOException) {
                sendError(
                    socket,
                    HTTP_INTERNALERROR,
                    "SERVER INTERNAL ERROR: IOException: " + ioe.message
                )
            }
        }

        @Throws(InterruptedException::class)
        private fun decodeMultipartData(
            boundary: String,
            fbuf: ByteArray,
            `in`: BufferedReader,
            parms: Properties,
            files: Properties
        ) {
            try {
                val bpositions = getBoundaryPositions(fbuf, boundary.toByteArray())
                var boundarycount = 1
                var mpline = `in`.readLine()
                while (mpline != null) {
                    if (mpline.indexOf(boundary) == -1) sendError(
                        socket,
                        HTTP_BADREQUEST,
                        "BAD REQUEST: Content type is multipart/form-data but next chunk does not start with boundary. Usage: GET /example/file.html"
                    )
                    boundarycount++
                    val item = Properties()
                    mpline = `in`.readLine()
                    while (mpline != null && mpline.trim { it <= ' ' }.isNotEmpty()) {
                        val p = mpline.indexOf(':')
                        if (p != -1) item[mpline.substring(0, p).trim { it <= ' ' }.toLowerCase(
                            Locale.ROOT
                        )] =
                            mpline.substring(p + 1).trim { it <= ' ' }
                        mpline = `in`.readLine()
                    }
                    if (mpline != null) {
                        val contentDisposition = item.getProperty("content-disposition")
                        if (contentDisposition == null) {
                            sendError(
                                socket,
                                HTTP_BADREQUEST,
                                "BAD REQUEST: Content type is multipart/form-data but no content-disposition info found. Usage: GET /example/file.html"
                            )
                        }
                        val st = StringTokenizer(contentDisposition, "; ")
                        val disposition = Properties()
                        while (st.hasMoreTokens()) {
                            val token = st.nextToken()
                            val p = token.indexOf('=')
                            if (p != -1) disposition[token.substring(0, p).trim { it <= ' ' }
                                .toLowerCase(Locale.ROOT)] =
                                token.substring(p + 1).trim { it <= ' ' }
                        }
                        var pname = disposition.getProperty("name")
                        pname = pname.substring(1, pname.length - 1)
                        var value = ""
                        if (item.getProperty("content-type") == null) {
                            while (mpline != null && mpline.indexOf(boundary) == -1) {
                                mpline = `in`.readLine()
                                if (mpline != null) {
                                    val d = mpline.indexOf(boundary)
                                    value += if (d == -1) mpline else mpline.substring(0, d - 2)
                                }
                            }
                        } else {
                            if (boundarycount > bpositions.size) sendError(
                                socket,
                                HTTP_INTERNALERROR,
                                "Error processing request"
                            )
                            val offset = stripMultipartHeaders(fbuf, bpositions[boundarycount - 2])
                            val path = saveTmpFile(
                                fbuf,
                                offset,
                                bpositions[boundarycount - 1] - offset - 4
                            )
                            files[pname] = path
                            value = disposition.getProperty("filename")
                            value = value.substring(1, value.length - 1)
                            do {
                                mpline = `in`.readLine()
                            } while (mpline != null && mpline.indexOf(boundary) == -1)
                        }
                        parms[pname] = value
                    }
                }
            } catch (ioe: IOException) {
                sendError(
                    socket,
                    HTTP_INTERNALERROR,
                    "SERVER INTERNAL ERROR: IOException: " + ioe.message
                )
            }
        }

        fun getBoundaryPositions(b: ByteArray, boundary: ByteArray): IntArray {
            var matchcount = 0
            var matchbyte = -1
            val matchbytes: Vector<*> = Vector<Any?>()
            run {
                var i = 0
                while (i < b.size) {
                    if (b[i] == boundary[matchcount]) {
                        if (matchcount == 0) matchbyte = i
                        matchcount++
                        if (matchcount == boundary.size) {
                            matchbytes.addElement(matchbyte as Nothing?)
                            matchcount = 0
                            matchbyte = -1
                        }
                    } else {
                        i -= matchcount
                        matchcount = 0
                        matchbyte = -1
                    }
                    i++
                }
            }
            val ret = IntArray(matchbytes.size)
            for (i in ret.indices) {
                ret[i] = matchbytes.elementAt(i) as Int
            }
            return ret
        }

        private fun saveTmpFile(b: ByteArray, offset: Int, len: Int): String {
            var path = ""
            if (len > 0) {
                val tmpdir = System.getProperty("java.io.tmpdir")
                try {
                    val temp = File.createTempFile("NanoHTTPD", "", File(tmpdir))
                    val fstream: OutputStream = FileOutputStream(temp)
                    fstream.write(b, offset, len)
                    fstream.close()
                    path = temp.absolutePath
                } catch (e: Exception) { // Catch exception if any
                    System.err.println("Error: " + e.message)
                }
            }
            return path
        }

        private fun stripMultipartHeaders(b: ByteArray, offset: Int): Int {
            var i = 0
            i = offset
            while (i < b.size) {
                if (b[i] == '\r'.toByte() && b[++i] == '\n'.toByte() && b[++i] == '\r'.toByte() && b[++i] == '\n'.toByte()) break
                i++
            }
            return i + 1
        }

        @Throws(InterruptedException::class)
        private fun decodePercent(str: String): String? {
            return try {
                val sb = StringBuffer()
                var i = 0
                while (i < str.length) {
                    val c = str[i]
                    when (c) {
                        '+' -> sb.append(' ')
                        '%' -> {
                            sb.append(str.substring(i + 1, i + 3).toInt(16).toChar())
                            i += 2
                        }
                        else -> sb.append(c)
                    }
                    i++
                }
                sb.toString()
            } catch (e: Exception) {
                sendError(socket, HTTP_BADREQUEST, "BAD REQUEST: Bad percent-encoding.")
                null
            }
        }

        @Throws(InterruptedException::class)
        private fun decodeParms(parms: String?, p: Properties) {
            if (parms == null) return
            val st = StringTokenizer(parms, "&")
            while (st.hasMoreTokens()) {
                val e = st.nextToken()
                val sep = e.indexOf('=')
                if (sep >= 0) p[decodePercent(e.substring(0, sep))!!.trim { it <= ' ' }] =
                    decodePercent(e.substring(sep + 1))
            }
        }

        @Throws(InterruptedException::class)
        private fun sendError(socket: Socket, status: String, msg: String) {
            sendResponse(socket, status, MIME_PLAINTEXT, null, null)
            throw InterruptedException()
        }

        private fun sendResponse(
            socket: Socket,
            status: String?,
            mime: String?,
            header: Properties?,
            data: StreamSource?
        ) {
            try {
                if (status == null) throw Error("sendResponse(): Status can't be null.")
                val out = socket.getOutputStream()
                val pw = PrintWriter(out)
                pw.print("HTTP/1.0 $status \r\n")
                if (mime != null) pw.print("Content-Type: $mime\r\n")
                if (header?.getProperty("Date") == null) pw.print(
                    """
    Date: ${gmtFrmt!!.format(Date())}
    
    """.trimIndent()
                )
                if (header != null) {
                    val e: Enumeration<*> = header.keys()
                    while (e.hasMoreElements()) {
                        val key = e.nextElement() as String
                        val value = header.getProperty(key)
                        pw.print("$key: $value\r\n")
                    }
                }
                pw.print("\r\n")
                pw.flush()
                if (data != null) {
                    data.open()
                    val buff = ByteArray(8192)
                    var read = 0
                    while (data.read(buff).also { read = it } > 0) {
                        out.write(buff, 0, read)
                    }
                }
                out.flush()
                out.close()
                data?.close()
            } catch (ioe: IOException) {
                try {
                    socket.close()
                } catch (t: Throwable) {
                }
            }
        }

        init {
            val t = Thread(this)
            t.isDaemon = true
            t.start()
        }
    }

    private val myServerSocket = tryBind(myTcpPort)
    private val myThread: Thread

    companion object {
        const val HTTP_OK = "200 OK"
        const val HTTP_PARTIALCONTENT = "206 Partial Content"
        const val HTTP_RANGE_NOT_SATISFIABLE = "416 Requested Range Not Satisfiable"
        const val HTTP_NOTFOUND = "404 Not Found"
        const val HTTP_BADREQUEST = "400 Bad Request"
        const val HTTP_INTERNALERROR = "500 Internal Server Error"
        const val MIME_PLAINTEXT = "text/plain"
        private var gmtFrmt: SimpleDateFormat? = null

        init {
            gmtFrmt = SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            gmtFrmt!!.timeZone = TimeZone.getTimeZone("GMT")
        }
    }

    init {
        myThread = Thread {
            try {
                while (true) {
                    val accept = myServerSocket.accept()
                    HTTPSession(accept)
                }
            } catch (ioe: IOException) {
            }
        }
        myThread.isDaemon = true
        myThread.start()
    }
}