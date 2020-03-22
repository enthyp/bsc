package com.example.deepnoise.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.deepnoise.adapters.ContactsAdapter
import com.example.deepnoise.databinding.FragmentContactsBinding
import com.example.deepnoise.utils.InjectionUtils
import com.example.deepnoise.viewmodels.ContactsViewModel

class ContactsFragment : Fragment() {

    private val contactsViewModel: ContactsViewModel by viewModels {
        InjectionUtils.provideContactsViewModelFactory()
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentContactsBinding.inflate(inflater)

        val viewManager = LinearLayoutManager(this.context)
        val viewAdapter = ContactsAdapter(contactsViewModel.getContacts())

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