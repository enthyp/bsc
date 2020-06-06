package com.lanecki.deepnoise.repositories

import androidx.lifecycle.LiveData
import com.lanecki.deepnoise.db.UserDao
import com.lanecki.deepnoise.model.User

class UserRepository(private val userDao: UserDao) {
    val allUsers: LiveData<List<User>> = userDao.getAll()

    suspend fun insert(user: User) {
        userDao.insert(user)
    }

    companion object {
        @Volatile private var instance: UserRepository? = null

        fun getInstance(userDao: UserDao) =
            instance
                ?: synchronized(this) {
                instance
                    ?: UserRepository(userDao).also { instance = it }
            }
    }
}