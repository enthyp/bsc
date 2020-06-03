package com.lanecki.deepnoise.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.lanecki.deepnoise.api.BackendService
import com.lanecki.deepnoise.model.User
import java.util.*

class SearchResultsViewModel : ViewModel() {

    private val backendService: BackendService by lazy { BackendService.getInstance() }

    private val query: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    fun setQuery(originalInput: String) {
        val input = originalInput.trim()
        query.value = input
    }

    val users: LiveData<List<User>> = query.switchMap { query ->
        val response = backendService.getUsers(query)
        response
    }
}