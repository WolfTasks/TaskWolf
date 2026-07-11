package com.taskowolf.auth.application

import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserSearchService(private val userRepository: UserRepository) {
    @Transactional(readOnly = true)
    fun search(query: String): List<User> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return userRepository.searchActive(trimmed, PageRequest.of(0, 10))
    }
}
