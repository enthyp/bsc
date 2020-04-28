package com.lanecki.deepnoise.settings

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.lanecki.deepnoise.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings);

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, SettingsFragment())
            .commit()
    }

    companion object {
        const val NICK_KEY = "NICK"
        const val SERVER_ADDRESS_KEY = "SERVER_ADDRESS"
    }
}
