package com.example.deepnoise.utils

import com.example.deepnoise.viewmodels.PageViewModelFactory

// TODO: this is to be replaced with Dagger
object InjectionUtils {
    fun providePageViewModelFactory() = PageViewModelFactory()
}