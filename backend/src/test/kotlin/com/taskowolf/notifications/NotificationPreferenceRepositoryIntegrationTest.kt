package com.taskowolf.notifications

import com.taskowolf.IntegrationTestBase
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.notifications.domain.NotificationPreference
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.notifications.infrastructure.NotificationPreferenceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class NotificationPreferenceRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var repo: NotificationPreferenceRepository
    @Autowired private lateinit var userRepository: UserRepository

    @Test
    fun `persists and finds by user and type`() {
        // notification_preferences.user_id has an FK to users(id), so a real user row is required.
        val userId = userRepository.save(User(email = "notif-pref-${System.nanoTime()}@test.com", displayName = "Notif Pref User")).id
        repo.save(NotificationPreference(userId, NotificationType.COMMENT_MENTION, inAppEnabled = false, emailEnabled = true))

        val found = repo.findByUserIdAndType(userId, NotificationType.COMMENT_MENTION)
        assertEquals(false, found?.inAppEnabled)
        assertEquals(true, found?.emailEnabled)
        assertEquals(1, repo.findByUserId(userId).size)
        assertNull(repo.findByUserIdAndType(userId, NotificationType.ISSUE_ASSIGNED))
        assertFalse(found!!.inAppEnabled)
    }
}
