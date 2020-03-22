package com.example.deepnoise.utils

import com.example.deepnoise.viewmodels.ContactsViewModelFactory

// TODO: this is to be replaced with Dagger
object InjectionUtils {
    fun provideContactsViewModelFactory() = ContactsViewModelFactory()
}