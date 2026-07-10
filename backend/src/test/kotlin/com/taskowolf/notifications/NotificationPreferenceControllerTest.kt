package com.taskowolf.notifications

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.BadRequestException
import com.taskowolf.notifications.api.NotificationPreferenceController
import com.taskowolf.notifications.api.dto.NotificationPreferenceItem
import com.taskowolf.notifications.api.dto.NotificationPreferencesRequest
import com.taskowolf.notifications.application.NotificationPreferenceService
import com.taskowolf.notifications.domain.NotificationPreference
import com.taskowolf.notifications.domain.NotificationType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NotificationPreferenceControllerTest {

    private val service = mockk<NotificationPreferenceService>(relaxed = true)
    private val controller = NotificationPreferenceController(service)
    private val user = User(email = "u@x.com", displayName = "U")

    @Test
    fun `get maps matrix to dto`() {
        every { service.getMatrix(user.id) } returns listOf(
            NotificationPreference(user.id, NotificationType.COMMENT_MENTION, inAppEnabled = false, emailEnabled = true)
        )

        val body = controller.get(user)

        assertEquals(1, body.preferences.size)
        assertEquals("COMMENT_MENTION", body.preferences[0].type)
        assertEquals(false, body.preferences[0].inApp)
        assertEquals(true, body.preferences[0].email)
    }

    @Test
    fun `put converts items to typed map and returns refreshed matrix`() {
        every { service.getMatrix(user.id) } returns emptyList()
        val request = NotificationPreferencesRequest(listOf(
            NotificationPreferenceItem("ISSUE_ASSIGNED", inApp = false, email = false)
        ))

        controller.update(request, user)

        val captured = slot<Map<NotificationType, Pair<Boolean, Boolean>>>()
        verify { service.update(user.id, capture(captured)) }
        assertEquals(Pair(false, false), captured.captured[NotificationType.ISSUE_ASSIGNED])
    }

    @Test
    fun `put with unknown type throws BadRequest without leaking enum FQCN`() {
        val request = NotificationPreferencesRequest(listOf(
            NotificationPreferenceItem("NOT_A_REAL_TYPE", inApp = true, email = true)
        ))

        val ex = assertThrows<BadRequestException> {
            controller.update(request, user)
        }

        // Message must not leak the fully qualified enum name
        assertFalse(ex.message!!.contains("com.taskowolf"))
        assertFalse(ex.message!!.contains("No enum constant"))
        // Message names the reported unknown type
        assertTrue(ex.message!!.contains("NOT_A_REAL_TYPE"))

        verify(exactly = 0) { service.update(any(), any()) }
    }

    @Test
    fun `put with only valid types still updates and returns matrix`() {
        every { service.getMatrix(user.id) } returns emptyList()
        val request = NotificationPreferencesRequest(listOf(
            NotificationPreferenceItem("ISSUE_ASSIGNED", inApp = true, email = false)
        ))

        controller.update(request, user)

        val captured = slot<Map<NotificationType, Pair<Boolean, Boolean>>>()
        verify { service.update(user.id, capture(captured)) }
        assertEquals(Pair(true, false), captured.captured[NotificationType.ISSUE_ASSIGNED])
    }
}
