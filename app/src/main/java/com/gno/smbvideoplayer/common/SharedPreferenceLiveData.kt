package com.gno.smbvideoplayer.common

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener

import androidx.lifecycle.LiveData


abstract class SharedPreferenceLiveData<T>(
    var sharedPrefs: SharedPreferences,
    private var key: String,
    private var defValue: T
) :
    LiveData<T>() {
    private val preferenceChangeListener =
        OnSharedPreferenceChangeListener { _, key ->
            if (this@SharedPreferenceLiveData.key == key) {
                value = getValueFromPreferences(key, defValue)
            }
        }

    abstract fun getValueFromPreferences(key: String, defValue: T): T
    override fun onActive() {
        super.onActive()
        value = getValueFromPreferences(key, defValue)
        sharedPrefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onInactive() {
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        super.onInactive()
    }

    fun getStringLiveData(
        key: String,
        defaultValue: String
    ): SharedPreferenceLiveData<String> {
        return SharedPreferenceStringLiveData(sharedPrefs, key, defaultValue)
    }
}