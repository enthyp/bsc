package com.lanecki.deepnoise.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.lanecki.deepnoise.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}