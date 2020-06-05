package com.lanecki.deepnoise

import android.app.SearchManager
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.lanecki.deepnoise.adapters.SearchResultsAdapter
import com.lanecki.deepnoise.api.BackendService
import com.lanecki.deepnoise.api.Status
import com.lanecki.deepnoise.databinding.ActivitySearchBinding
import com.lanecki.deepnoise.utils.InjectionUtils
import com.lanecki.deepnoise.viewmodels.SearchResultsViewModel

class SearchActivity : AppCompatActivity() {

    private val viewModel: SearchResultsViewModel by viewModels {
        InjectionUtils.provideSearchResultsViewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val viewManager = LinearLayoutManager(this)
        val viewAdapter = SearchResultsAdapter { user ->
            val response = viewModel.sendInvitation(user)
            response.observe(this, Observer {
                when (it.status) {
                    Status.SUCCESS -> Toast.makeText(this, "Invited!", Toast.LENGTH_LONG).show()

                    // TODO: why?
                    else -> Toast.makeText(this, "Failed!", Toast.LENGTH_LONG).show()
                }
            })
        }

        binding.resultsList.apply {
            layoutManager = viewManager
            adapter = viewAdapter
        }

        viewModel.users.observe(this, Observer {
            viewAdapter.updateResults(it)
        })

        if (Intent.ACTION_SEARCH == intent.action) {
            intent.getStringExtra(SearchManager.QUERY)?.also { query ->
                searchUsers(query)
            }
        }
    }

    private fun searchUsers(query: String) {
        viewModel.setQuery(query)
        // TODO:
        //  - progress dialog? (https://developer.android.com/guide/topics/ui/dialogs#ProgressDialog)
    }
}
