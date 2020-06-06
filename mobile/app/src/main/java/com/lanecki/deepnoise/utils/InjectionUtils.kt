package com.lanecki.deepnoise.utils

import android.content.Context
import com.lanecki.deepnoise.api.BackendService
import com.lanecki.deepnoise.repositories.UserRepository
import com.lanecki.deepnoise.api.ResponseHandler
import com.lanecki.deepnoise.call.CallLifecycle
import com.lanecki.deepnoise.db.AppDatabase
import com.lanecki.deepnoise.viewmodels.ContactsViewModelFactory
import com.lanecki.deepnoise.viewmodels.SearchResultsViewModelFactory

// TODO: this can be replaced with Dagger
object InjectionUtils {

    fun provideBackendService() = BackendService.getInstance()

    private fun getUserRepository(context: Context): UserRepository {
        return UserRepository.getInstance(
            AppDatabase.getInstance(context.applicationContext).userDao(),
            provideBackendService())
    }

    fun provideContactsViewModelFactory(context: Context): ContactsViewModelFactory {
        val userRepository = getUserRepository(context)
        return ContactsViewModelFactory(userRepository)
    }

    fun provideSearchResultsViewModelFactory() = SearchResultsViewModelFactory()
    fun provideResponseHandler() = ResponseHandler()
    fun provideCallLifecycle() = CallLifecycle()
}