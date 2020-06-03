package com.lanecki.deepnoise.api

import com.lanecki.deepnoise.model.User
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface BackendApi {
    @POST("login")
    suspend fun login(@Body credentials: Credentials)

    @POST("token")
    suspend fun updateToken(@Body token: String)

    @GET("users/search")
    suspend fun getUsers(@Query("query") query: String): List<User>
}