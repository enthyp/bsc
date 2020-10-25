package com.lanecki.deepnoise

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import com.lanecki.deepnoise.databinding.ActivityMainBinding
import com.lanecki.deepnoise.adapters.MainPagerAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.lanecki.deepnoise.settings.SettingsActivity
import com.lanecki.deepnoise.workers.UpdateFCMTokenWorker
import com.lanecki.deepnoise.workers.GetFCMTokenWorker
import com.lanecki.deepnoise.workers.LoginWorker
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val viewPager = binding.viewPager
        val sectionsPagerAdapter = MainPagerAdapter(this)
        viewPager.adapter = sectionsPagerAdapter

        val tabs = binding.tabs
        TabLayoutMediator(tabs, viewPager) { tab, position ->
            tab.text = resources.getStringArray(R.array.tab_names)[position]
        }.attach()

        setSupportActionBar(binding.mainToolbar)

        connect()
    }

    private fun connect() {
        // Login and update FCM token
        val loginRequest = OneTimeWorkRequestBuilder<LoginWorker>()
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .build()

        val getTokenRequest = OneTimeWorkRequestBuilder<GetFCMTokenWorker>().build()

        val updateTokenRequest = OneTimeWorkRequestBuilder<UpdateFCMTokenWorker>()
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this)
            .beginWith(loginRequest)
            .then(getTokenRequest)
            .then(updateTokenRequest)
            .enqueue()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.options_menu, menu)

        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        (menu?.findItem(R.id.search)?.actionView as SearchView).apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                settingsActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun settingsActivity() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    companion object {
        private const val TAG: String = "MainActivity"
    }
}