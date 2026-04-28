package com.hellohealth.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hellohealth.data.local.dao.UserDao
import com.hellohealth.data.local.entities.UserEntity

@Database(entities = [UserEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
