package com.lanecki.deepnoise.viewmodels

import androidx.lifecycle.ViewModel

class ContactsViewModel : ViewModel() {

    private val contacts: List<String> = listOf("XD", "LOL")

    fun getContacts() = contacts
}