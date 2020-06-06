package com.lanecki.deepnoise.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.lanecki.deepnoise.adapters.ContactsAdapter
import com.lanecki.deepnoise.databinding.FragmentContactsBinding
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

        val viewManager = LinearLayoutManager(this.context)
        val viewAdapter = ContactsAdapter()

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