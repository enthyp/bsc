package com.lanecki.deepnoise.api.model

import com.lanecki.deepnoise.model.User

data class InvitationAnswer(val to: User, val positive: Boolean)