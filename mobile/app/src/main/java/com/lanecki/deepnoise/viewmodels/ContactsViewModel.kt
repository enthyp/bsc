package com.lanecki.deepnoise.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanecki.deepnoise.api.Status
import com.lanecki.deepnoise.repositories.UserRepository
import com.lanecki.deepnoise.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val contacts: LiveData<List<User>> = userRepository.allUsers

    private val _isFresh = MutableLiveData<Boolean>()

    val isFresh: LiveData<Boolean> = _isFresh

    fun refreshContacts() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val usersResource = userRepository.loadFriendsForSelf()

            when (usersResource.status) {
                Status.SUCCESS -> {
                    usersResource.data?.let { users ->
                        // NOTE: could use a diff
                        userRepository.deleteAll()
                        userRepository.insertAll(users)
                    }
                }
                else -> {
                    // TODO: isFresh should be LiveData<Resource>!
                }
            }
        }

        _isFresh.value = true
    }

    fun getContacts() = contacts
}