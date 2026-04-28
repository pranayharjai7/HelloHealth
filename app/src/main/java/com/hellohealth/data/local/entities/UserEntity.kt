package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val email: String,
    val password: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val isGoogleUser: Boolean = false,
    val isLoggedIn: Boolean = false
)
