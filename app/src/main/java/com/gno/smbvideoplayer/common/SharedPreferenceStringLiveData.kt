package com.gno.smbvideoplayer.common

import android.content.SharedPreferences

class SharedPreferenceStringLiveData(prefs: SharedPreferences?, key: String?, defValue: String?) :
    SharedPreferenceLiveData<String?>(prefs!!, key!!, defValue) {

    override fun getValueFromPreferences(key: String?, defValue: String?): String? {
        return sharedPrefs.getString(key, defValue ?: "")
    }
}