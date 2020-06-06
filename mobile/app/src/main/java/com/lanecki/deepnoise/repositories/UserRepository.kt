package com.lanecki.deepnoise.repositories

import androidx.lifecycle.LiveData
import com.lanecki.deepnoise.api.BackendApi
import com.lanecki.deepnoise.api.BackendService
import com.lanecki.deepnoise.api.Resource
import com.lanecki.deepnoise.db.UserDao
import com.lanecki.deepnoise.model.User

// TODO: FRIENDS NOT USERS!!!
class UserRepository(private val userDao: UserDao, private val backendService: BackendService) {
    val allUsers: LiveData<List<User>> = userDao.getAll()

    suspend fun loadFriendsForSelf(): Resource<List<User>> {
        return backendService.getFriendsForSelf()
    }

    suspend fun insert(user: User) {
        userDao.insert(user)
    }

    suspend fun insertAll(users: List<User>) {
        userDao.insertAll(users)
    }

    suspend fun deleteAll() {
        userDao.deleteAll()
    }

    companion object {
        @Volatile private var instance: UserRepository? = null

        fun getInstance(userDao: UserDao, backendService: BackendService) =
            instance
                ?: synchronized(this) {
                instance
                    ?: UserRepository(userDao, backendService).also { instance = it }
            }
    }
}