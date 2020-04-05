package com.lanecki.deepnoise.api

import retrofit2.http.Body
import retrofit2.http.POST

interface BackendApi {
    @POST("token")
    suspend fun updateToken(@Body token: Token)
}