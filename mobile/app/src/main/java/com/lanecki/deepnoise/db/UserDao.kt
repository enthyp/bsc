package com.lanecki.deepnoise.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lanecki.deepnoise.model.User

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(user: User)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(users: List<User>)

    @Query("SELECT * FROM users")
    fun getAll(): LiveData<List<User>>

    @Query("DELETE FROM users")
    suspend fun deleteAll()
}