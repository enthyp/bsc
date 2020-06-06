package com.lanecki.deepnoise.viewmodels

import androidx.lifecycle.*
import com.lanecki.deepnoise.api.BackendService
import com.lanecki.deepnoise.api.Resource
import com.lanecki.deepnoise.model.User
import com.lanecki.deepnoise.utils.InjectionUtils

class SearchResultsViewModel : ViewModel() {

    // TODO: use InjectionUtils
    private val backendService: BackendService by lazy { InjectionUtils.provideBackendService() }

    private val query: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    fun setQuery(originalInput: String) {
        val input = originalInput.trim()
        query.value = input
    }

    val users: LiveData<List<User>> = query.switchMap { query ->
        val response = backendService.findUsers(query)
        response
    }

    fun sendInvitation(user: User): LiveData<Resource<Unit>> {
        return backendService.invite(user)
    }
}