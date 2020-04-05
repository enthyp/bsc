package com.lanecki.deepnoise.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.lanecki.deepnoise.views.ContactsFragment
import com.lanecki.deepnoise.views.RecentFragment

class MainPagerAdapter(fa : FragmentActivity)
    : FragmentStateAdapter(fa) {

    override fun getItemCount(): Int {
        return 2
    }

    override fun createFragment(position: Int): Fragment =
        when(position) {
            0 -> ContactsFragment.newInstance()
            else -> RecentFragment.newInstance()
    }
}