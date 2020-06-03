package com.lanecki.deepnoise.utils

import com.lanecki.deepnoise.api.ResponseHandler
import com.lanecki.deepnoise.call.CallLifecycle
import com.lanecki.deepnoise.viewmodels.ContactsViewModelFactory
import com.lanecki.deepnoise.viewmodels.SearchResultsViewModelFactory

// TODO: this can be replaced with Dagger
object InjectionUtils {
    fun provideContactsViewModelFactory() = ContactsViewModelFactory()
    fun provideSearchResultsViewModelFactory() = SearchResultsViewModelFactory()
    fun provideResponseHandler() = ResponseHandler()
    fun provideCallLifecycle() = CallLifecycle()
}