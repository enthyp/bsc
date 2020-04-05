package com.lanecki.deepnoise.api

import com.google.gson.annotations.SerializedName

data class Token(
    @SerializedName("id")
    val id: String,
    @SerializedName("token")
    val token: String
)