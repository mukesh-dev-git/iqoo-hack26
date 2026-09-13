package com.limitless.codereview.model

interface UserRepository {
    fun findById(id: String): User?
}

data class User(val id: String, val name: String, val email: String)

class UserProfile(private val repository: UserRepository) {
    var currentUserId: String = ""

    fun displayName(): String {
        val user = repository.findById(currentUserId)
        return user.name
    }

    fun updateEmail(newEmail: String) {
        val user = repository.findById(currentUserId)
        println("Updating email for ${user.name} to $newEmail")
    }
}
