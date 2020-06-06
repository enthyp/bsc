package com.lanecki.deepnoise.api

import com.lanecki.deepnoise.api.model.Credentials
import com.lanecki.deepnoise.api.model.InvitationAnswer
import com.lanecki.deepnoise.api.model.Token
import com.lanecki.deepnoise.model.User
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface BackendApi {
    @POST("login")
    suspend fun login(@Body credentials: Credentials)

    @POST("token")
    suspend fun updateToken(@Body token: Token)

    @GET("users/friends")
    suspend fun getFriendsForSelf(): List<User>

    @GET("users/search")
    suspend fun getUsers(@Query("query") query: String): List<User>

    @POST("users/invite")
    suspend fun inviteUser(@Body user: User)

    @POST("invitations/answer")
    suspend fun answerInvitation(@Body answer: InvitationAnswer)
}