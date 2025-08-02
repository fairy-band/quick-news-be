package com.nexters.external.repository

import com.nexters.external.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, Long> {
    @Modifying
    @Query("INSERT INTO users(device_token) values(:deviceToken) ON CONFLICT (device_token) DO NOTHING", nativeQuery = true)
    fun registerUser(deviceToken: String): User

    fun findByDeviceToken(deviceToken: String): User?
}
