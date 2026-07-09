package com.taskowolf.notifications

import com.taskowolf.notifications.application.NotificationPreferenceService
import com.taskowolf.notifications.domain.NotificationChannel
import com.taskowolf.notifications.domain.NotificationPreference
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.notifications.infrastructure.NotificationPreferenceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class NotificationPreferenceServiceTest {

    private val repository = mockk<NotificationPreferenceRepository>()
    private val service = NotificationPreferenceService(repository)
    private val userId = UUID.randomUUID()

    @Test
    fun `getMatrix returns all four types with defaults when none stored`() {
        every { repository.findByUserId(userId) } returns emptyList()

        val matrix = service.getMatrix(userId)

        assertEquals(NotificationType.entries.size, matrix.size)
        assertTrue(matrix.all { it.inAppEnabled && it.emailEnabled })
        assertEquals(NotificationType.entries.toSet(), matrix.map { it.type }.toSet())
    }

    @Test
    fun `getMatrix reflects a stored row`() {
        every { repository.findByUserId(userId) } returns listOf(
            NotificationPreference(userId, NotificationType.COMMENT_MENTION, inAppEnabled = false, emailEnabled = false)
        )

        val mention = service.getMatrix(userId).first { it.type == NotificationType.COMMENT_MENTION }
        assertFalse(mention.inAppEnabled)
        assertFalse(mention.emailEnabled)
    }

    @Test
    fun `update upserts existing and new rows`() {
        every { repository.findByUserIdAndType(userId, NotificationType.COMMENT_MENTION) } returns
            NotificationPreference(userId, NotificationType.COMMENT_MENTION)
        every { repository.findByUserIdAndType(userId, NotificationType.AUTOMATION) } returns null
        every { repository.save(any()) } returnsArgument 0

        service.update(userId, mapOf(
            NotificationType.COMMENT_MENTION to Pair(false, true),
            NotificationType.AUTOMATION to Pair(true, false),
        ))

        verify(exactly = 2) { repository.save(any()) }
    }

    @Test
    fun `isEnabled defaults to true when no row`() {
        every { repository.findByUserIdAndType(userId, NotificationType.ISSUE_ASSIGNED) } returns null
        assertTrue(service.isEnabled(userId, NotificationType.ISSUE_ASSIGNED, NotificationChannel.IN_APP))
    }

    @Test
    fun `isEnabled respects stored flags per channel`() {
        every { repository.findByUserIdAndType(userId, NotificationType.ISSUE_ASSIGNED) } returns
            NotificationPreference(userId, NotificationType.ISSUE_ASSIGNED, inAppEnabled = false, emailEnabled = true)

        assertFalse(service.isEnabled(userId, NotificationType.ISSUE_ASSIGNED, NotificationChannel.IN_APP))
        assertTrue(service.isEnabled(userId, NotificationType.ISSUE_ASSIGNED, NotificationChannel.EMAIL))
    }
}
