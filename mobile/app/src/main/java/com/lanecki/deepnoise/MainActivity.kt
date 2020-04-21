package com.lanecki.deepnoise

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lanecki.deepnoise.databinding.ActivityMainBinding
import com.lanecki.deepnoise.adapters.MainPagerAdapter
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.iid.FirebaseInstanceId
import com.lanecki.deepnoise.settings.SettingsActivity
import com.lanecki.deepnoise.workers.FMSTokenUpdateWorker

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fab = binding.fab
        fab.setOnClickListener {
            FirebaseInstanceId.getInstance().instanceId
                .addOnCompleteListener(OnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w(TAG, "getInstanceId failed", task.exception)
                        return@OnCompleteListener
                    }

                    // Get new Instance ID token
                    val token = task.result?.token

                    // Send to server.
                    // TODO: method in FMService?
                    // TODO: handle failure?
                    val inputData = workDataOf("identity" to "client", "token" to token)
                    val updateTokenRequest = OneTimeWorkRequestBuilder<FMSTokenUpdateWorker>()
                        .setInputData(inputData)
                        .build()
                    WorkManager.getInstance(this).enqueue(updateTokenRequest)

                    // Log and toast
                    val msg = "Token sending scheduled."
                    Log.d(TAG, msg)
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                })
        }

        val viewPager = binding.viewPager
        val sectionsPagerAdapter = MainPagerAdapter(this)
        viewPager.adapter = sectionsPagerAdapter

        viewPager.registerOnPageChangeCallback(ViewFadeCallback(fab))

        val tabs = binding.tabs
        TabLayoutMediator(tabs, viewPager) { tab, position ->
            tab.text = resources.getStringArray(R.array.tab_names)[position]
        }.attach()

        setSupportActionBar(binding.mainToolbar)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)
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

    class ViewFadeCallback(private val view: View) : ViewPager2.OnPageChangeCallback() {

        override fun onPageScrollStateChanged(state: Int) {
            super.onPageScrollStateChanged(state)
            when(state) {
                ViewPager2.SCROLL_STATE_SETTLING ->
                    when(view.visibility) {
                        View.VISIBLE -> view.visibility = View.GONE
                        View.GONE -> view.visibility = View.VISIBLE
                    }
                else -> {}
            }
        }
    }

    companion object {
        private const val TAG: String = "MainActivity"
    }
}