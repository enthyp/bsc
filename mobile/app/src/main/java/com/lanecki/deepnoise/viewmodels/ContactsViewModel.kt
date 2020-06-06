package com.lanecki.deepnoise.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.lanecki.deepnoise.repositories.UserRepository
import com.lanecki.deepnoise.model.User

class ContactsViewModel(userRepository: UserRepository) : ViewModel() {

    private val contacts: LiveData<List<User>> = userRepository.allUsers

    fun getContacts() = contacts
}