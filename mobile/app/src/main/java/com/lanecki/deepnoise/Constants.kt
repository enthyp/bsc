package com.lanecki.deepnoise

object Constants {
    const val CALLEE_KEY = "CALLEE"
    const val INITIAL_STATE_KEY = "INITIAL_STATE"
    const val CALL_ID_KEY = "CALL_ID"
    const val CHANNEL_ID_KEY = "CHANNEL_ID"

    const val ACTION_ACCEPT_INVITATION = "com.lanecki.deepnoise.invitation.accept"
    const val ACTION_REFUSE_INVITATION = "com.lanecki.deepnoise.invitation.refuse"
    const val EXTRA_WHO_INVITES = "com.lanecki.deepnoise.invitation.from"
    const val EXTRA_NOTIFICATION_ID = "com.lanecki.deepnoise.notification.id"
    const val NOTIFICATION_TAG_INVITATION = "FRIENDS_INVITATION"

    const val DATABASE_NAME = "deep-noise-db"
}