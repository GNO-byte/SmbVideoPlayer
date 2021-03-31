package com.gno.smbvideoplayer.setting

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragment
import com.gno.smbvideoplayer.R

class SettingsFragment : PreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val editTextPreference: EditTextPreference? = findPreference("setting_password")
        editTextPreference?.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        editTextPreference?.onPreferenceChangeListener =
            object : Preference.OnPreferenceChangeListener {
                override fun onPreferenceChange(preference: Preference, value: Any): Boolean {
                    var stringValue = value.toString()
                    if (preference is EditTextPreference) {
                        stringValue = toStars(stringValue)
                        preference.setSummary(stringValue)
                    }
                    return true
                }

                fun toStars(text: String): String {

                    val sb = StringBuilder()
                    for (i in text.indices) {
                        sb.append('*')
                    }
                    return sb.toString()

                }
            }
    }
}

