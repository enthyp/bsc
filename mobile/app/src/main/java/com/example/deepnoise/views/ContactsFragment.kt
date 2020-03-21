package com.example.deepnoise.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.deepnoise.adapters.ContactsAdapter
import com.example.deepnoise.databinding.FragmentMainBinding
import com.example.deepnoise.utils.InjectionUtils
import com.example.deepnoise.viewmodels.ContactsViewModel

/**
 * A placeholder fragment containing a simple view.
 */
class ContactsFragment : Fragment() {

    private val contactsViewModel: ContactsViewModel by viewModels {
        InjectionUtils.providePageViewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactsViewModel.setIndex(arguments?.getInt(ARG_SECTION_NUMBER) ?: 1)
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentMainBinding.inflate(inflater)
        val textView = binding.sectionLabel

        val viewManager = LinearLayoutManager(this.context)
        val viewAdapter = ContactsAdapter(contactsViewModel.getContacts())

        binding.stuffList.apply {
            layoutManager = viewManager
            adapter = viewAdapter
        }

        contactsViewModel.text.observe(viewLifecycleOwner, Observer<String> {
            textView.text = it
        })
        return binding.root
    }

    companion object {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private const val ARG_SECTION_NUMBER = "section_number"

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        @JvmStatic
        fun newInstance(sectionNumber: Int): ContactsFragment {
            return ContactsFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }
}