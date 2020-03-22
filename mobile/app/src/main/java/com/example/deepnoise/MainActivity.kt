package com.example.deepnoise

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.deepnoise.databinding.ActivityMainBinding
import com.example.deepnoise.adapters.MainPagerAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fab = binding.fab
        fab.setOnClickListener {
            Snackbar.make(it, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        val viewPager = binding.viewPager
        val sectionsPagerAdapter = MainPagerAdapter(this)
        viewPager.adapter = sectionsPagerAdapter

        viewPager.registerOnPageChangeCallback(ViewFadeCallback(fab))

        val tabs = binding.tabs
        TabLayoutMediator(tabs, viewPager) { tab, position ->
            tab.text = resources.getStringArray(R.array.tab_names)[position]
        }.attach()
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