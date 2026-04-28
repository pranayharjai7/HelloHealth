package com.hellohealth.domain.model

data class User(
    val email: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val isGoogleUser: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
