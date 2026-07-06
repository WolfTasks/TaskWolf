package com.taskowolf.comments

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import com.taskowolf.comments.application.ActivityService
import com.taskowolf.comments.domain.ActivityType
import com.taskowolf.comments.infrastructure.CommentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Real-database (Testcontainers Postgres) regression coverage for the offset-pagination
 * stability fix: both the comments and activity feeds must break createdAt ties by id
 * (descending) so that rows sharing an identical timestamp are ordered deterministically
 * instead of risking duplication/omission across page boundaries.
 */
class CommentPaginationTiebreakerIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var commentRepository: CommentRepository
    @Autowired private lateinit var activityService: ActivityService

    private fun register(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"Tiebreaker User","password":"password123"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    private fun createProjectAndIssue(token: String, projectKey: String): Pair<UUID, UUID> {
        mockMvc.perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $token")
                .content("""{"key":"$projectKey","name":"Tiebreak Test"}""")
        ).andExpect(status().isCreated)

        val issueResult = mockMvc.perform(
            post("/api/v1/projects/$projectKey/issues")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $token")
                .content("""{"title":"Tiebreaker Issue"}""")
        ).andExpect(status().isCreated).andReturn()
        val issueJson = objectMapper.readTree(issueResult.response.contentAsString)
        val issueId = UUID.fromString(issueJson.get("id").asText())
        val authorId = UUID.fromString(issueJson.get("reporterId").asText())
        return issueId to authorId
    }

    @Test
    fun `comments with identical createdAt are ordered deterministically by id descending`() {
        val token = register("tiebreak-comments@example.com")
        val (issueId, authorId) = createProjectAndIssue(token, "TIEB")

        val fixedInstant = Timestamp.from(Instant.parse("2026-01-01T12:00:00Z"))
        val commentIdA = UUID.randomUUID()
        val commentIdB = UUID.randomUUID()
        jdbcTemplate.update(
            "INSERT INTO comments (id, body, issue_id, author_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            commentIdA, "Comment A", issueId, authorId, fixedInstant, fixedInstant
        )
        jdbcTemplate.update(
            "INSERT INTO comments (id, body, issue_id, author_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            commentIdB, "Comment B", issueId, authorId, fixedInstant, fixedInstant
        )

        // Ground truth: what Postgres itself returns for "ORDER BY created_at DESC, id DESC".
        // We don't assert this equals a Java-side UUID.sortedDescending() computation, since
        // Postgres orders uuid bytes unsigned while java.util.UUID#compareTo compares signed
        // longs — the two can legitimately disagree. The repository result must match the DB's
        // own ordering, which is what actually determines pagination stability.
        val expectedOrder = jdbcTemplate.queryForList(
            "SELECT id FROM comments WHERE issue_id = ? ORDER BY created_at DESC, id DESC",
            issueId
        ).map { it["id"] as UUID }
        assertEquals(setOf(commentIdA, commentIdB), expectedOrder.toSet())

        val firstCall = commentRepository.findByIssueIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            issueId, PageRequest.of(0, 10)
        ).content.map { it.id }
        val secondCall = commentRepository.findByIssueIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            issueId, PageRequest.of(0, 10)
        ).content.map { it.id }

        assertEquals(expectedOrder, firstCall)
        assertEquals(firstCall, secondCall) // stable across repeated calls, no reshuffling of tied rows
    }

    @Test
    fun `activity entries with identical createdAt are ordered deterministically by id descending`() {
        val token = register("tiebreak-activity@example.com")
        val (issueId, authorId) = createProjectAndIssue(token, "TIEA")

        val fixedInstant = Timestamp.from(Instant.parse("2026-01-01T12:00:00Z"))
        val activityIdA = UUID.randomUUID()
        val activityIdB = UUID.randomUUID()
        jdbcTemplate.update(
            "INSERT INTO issue_activities (id, issue_id, actor_id, type, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            activityIdA, issueId, authorId, ActivityType.TITLE_CHANGED.name, fixedInstant, fixedInstant
        )
        jdbcTemplate.update(
            "INSERT INTO issue_activities (id, issue_id, actor_id, type, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            activityIdB, issueId, authorId, ActivityType.TITLE_CHANGED.name, fixedInstant, fixedInstant
        )

        val expectedOrder = jdbcTemplate.queryForList(
            "SELECT id FROM issue_activities WHERE issue_id = ? ORDER BY created_at DESC, id DESC",
            issueId
        ).map { it["id"] as UUID }
        assertEquals(setOf(activityIdA, activityIdB), expectedOrder.toSet())

        val page = activityService.listActivity(issueId, 0, 10)
        assertEquals(expectedOrder, page.content.map { it.id })
    }
}
