package com.lanecki.deepnoise.api

import retrofit2.http.Body
import retrofit2.http.POST

interface BackendApi {
    @POST("login")
    suspend fun login(@Body credentials: Credentials)

    @POST("token")
    suspend fun updateToken(@Body token: String)
}