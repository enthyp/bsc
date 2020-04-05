package com.lanecki.deepnoise.utils

import com.lanecki.deepnoise.api.ResponseHandler
import com.lanecki.deepnoise.viewmodels.ContactsViewModelFactory

// TODO: this can be replaced with Dagger
object InjectionUtils {
    fun provideContactsViewModelFactory() = ContactsViewModelFactory()
    fun provideResponseHandler() = ResponseHandler()
}