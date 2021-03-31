package com.gno.smbvideoplayer.main

import android.app.Activity
import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import com.gno.smbvideoplayer.common.BaseViewModel
import com.gno.smbvideoplayer.common.SharedPreferenceStringLiveData
import com.gno.smbvideoplayer.smb.SmbUtils
import com.gno.smbvideoplayer.smb.SmbUtils.launchSMB
import jcifs.smb.SmbFile
import kotlinx.coroutines.launch

class MainViewModel : BaseViewModel() {

    //LiveData
    val popularMoviesLiveData = MutableLiveData<PopularMoviesLiveDataAnswer>()
    lateinit var sharedPreferenceStringLiveDataUrl: SharedPreferenceStringLiveData
    lateinit var sharedPreferenceStringLiveDataUserName: SharedPreferenceStringLiveData
    lateinit var sharedPreferenceStringLiveDataPassword: SharedPreferenceStringLiveData

    var url = ""
    var username = ""
    var password = ""

    fun initSharedPreferenceStringLiveDataUrl(sharedPreferences: SharedPreferences) {
        sharedPreferenceStringLiveDataUrl =
            SharedPreferenceStringLiveData(sharedPreferences, "setting_url", "")
        sharedPreferenceStringLiveDataUserName =
            SharedPreferenceStringLiveData(sharedPreferences, "setting_username", "")
        sharedPreferenceStringLiveDataPassword =
            SharedPreferenceStringLiveData(sharedPreferences, "setting_password", "")
    }


    fun getListFiles() {
        if (url.isEmpty() || username.isEmpty() || password.isEmpty()) {
            return
        }
        scope.launch {
            popularMoviesLiveData.postValue(
                PopularMoviesLiveDataAnswer(
                    SmbUtils.getSMBListFiles(
                        url,
                        username,
                        password
                    ), null
                )
            )
        }
    }

    fun processFileSelection(sfile: SmbFile, thisActivity: Activity) {
        scope.launch {

            if (sfile.isDirectory) {

                val pathSMBFile: String = sfile.canonicalPath

                var parentSmbFile: SmbFile? = null
                if (url != pathSMBFile) {
                    parentSmbFile = SmbUtils.getSMBFile(
                        sfile.parent,
                        username,
                        password
                    )
                }

                popularMoviesLiveData.postValue(
                    PopularMoviesLiveDataAnswer(
                        SmbUtils.getSMBListFiles(
                            pathSMBFile,
                            username,
                            password
                        ), parentSmbFile
                    )
                )

            } else {
                launchSMB(sfile, thisActivity)
            }

        }
    }

}