package com.gno.smbvideoplayer.smb

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri.fromFile
import android.net.Uri.parse
import android.widget.Toast
import com.gno.smbvideoplayer.smb.server.MimeTypes
import com.gno.smbvideoplayer.smb.server.Streamer
import jcifs.smb.NtlmPasswordAuthentication
import jcifs.smb.SmbFile
import java.io.File

object SmbUtils {

    fun launchSMB(sfile: SmbFile, ThisActivity: Activity) {
        val s: Streamer? = Streamer.getInstance()
        object : Thread() {
            override fun run() {
                try {
                    s?.setStreamSrc(sfile, sfile.length())
                    ThisActivity.runOnUiThread {
                        try {
                            val uri = parse(
                                Streamer.URL + fromFile(
                                    File(
                                        parse(sfile.path).path ?: ""
                                    )
                                ).encodedPath
                            )
                            val i = Intent(Intent.ACTION_VIEW)
                            i.setDataAndType(uri, MimeTypes.getMimeType(sfile.path, false))
                            val packageManager = ThisActivity.packageManager
                            val resInfos = packageManager.queryIntentActivities(i, 0)
                            if (resInfos.size > 0) ThisActivity.startActivity(i) else {
                                val toast = Toast.makeText(
                                    ThisActivity.applicationContext,
                                    "Необходимо скопировать этот файл в хранилище для того, чтобы открыть",
                                    Toast.LENGTH_SHORT
                                )
                                toast.show()
                            }
                        } catch (e: ActivityNotFoundException) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    fun getSMBListFiles(url: String, username: String, password: String): List<SmbFile> {
        return getSMBFile(url, username, password).listFiles().toList()
    }

    fun getSMBFile(url: String, username: String, password: String): SmbFile {
        val auth = NtlmPasswordAuthentication(null, username, password)

        return SmbFile(url, auth)
    }

}