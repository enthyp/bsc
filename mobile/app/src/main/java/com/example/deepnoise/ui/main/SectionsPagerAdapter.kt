package com.example.deepnoise.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.deepnoise.R

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
class SectionsPagerAdapter(fa : FragmentActivity)
    : FragmentStateAdapter(fa) {

    override fun getItemCount(): Int {
        // Show 2 total pages.
        return 2
    }

    override fun createFragment(position: Int): Fragment {
        return PlaceholderFragment.newInstance(position + 1)
    }
}