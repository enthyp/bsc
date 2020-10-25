package com.lanecki.deepnoise.views

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.lanecki.deepnoise.ChannelActivity
import com.lanecki.deepnoise.Constants
import com.lanecki.deepnoise.adapters.ContactsAdapter
import com.lanecki.deepnoise.databinding.FragmentContactsBinding
import com.lanecki.deepnoise.model.User
import com.lanecki.deepnoise.utils.InjectionUtils
import com.lanecki.deepnoise.viewmodels.ContactsViewModel

class ContactsFragment : Fragment() {

    private val contactsViewModel: ContactsViewModel by viewModels {
        InjectionUtils.provideContactsViewModelFactory(this.requireContext())
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentContactsBinding.inflate(inflater)

        binding.fab.setOnClickListener {
            val intent = Intent(activity, ChannelActivity::class.java).apply {
                putExtra(Constants.CHANNEL_ID_KEY, "1")
            }
            startActivity(intent)
        }

        val viewManager = LinearLayoutManager(this.context)
        val viewAdapter = ContactsAdapter()

        binding.swipeRefresh.setOnRefreshListener {
            contactsViewModel.refreshContacts()
            contactsViewModel.isFresh.observe(viewLifecycleOwner, Observer { fresh ->
                binding.swipeRefresh.isRefreshing = !fresh
            })
        }

        contactsViewModel.getContacts().observe(viewLifecycleOwner, Observer { contacts ->
            viewAdapter.updateContacts(contacts)
        })

        binding.stuffList.apply {
            layoutManager = viewManager
            adapter = viewAdapter
        }

        return binding.root
    }

    companion object {
        @JvmStatic
        fun newInstance(): ContactsFragment = ContactsFragment()
    }
}