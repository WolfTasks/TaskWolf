package com.taskowolf.comments

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import com.taskowolf.comments.application.ActivityService
import com.taskowolf.comments.domain.ActivityType
import com.taskowolf.comments.infrastructure.CommentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
 * Real-database (Testcontainers Postgres) regression guard for the offset-pagination
 * stability fix: both the comments and activity feeds must break `createdAt` ties by `id`
 * (descending) so that rows sharing an identical timestamp are ordered deterministically
 * instead of risking duplication/omission across page boundaries.
 *
 * Design — built so it FAILS reliably if the `id` tiebreaker is removed:
 *  - Four rows are inserted with an IDENTICAL `createdAt`, using explicit KNOWN UUIDs.
 *  - The ids are chosen so their descending order (0004, 0003, 0002, 0001) DIFFERS from the
 *    insertion order (0001, 0004, 0002, 0003). Without the `id` tiebreaker the query falls back
 *    to scan/insertion order for the tied rows, which is not the expected id-descending order, so
 *    the assertions break. The expected order is written LITERALLY, not derived from a raw
 *    `ORDER BY ... id DESC` query (which would be circular and self-fulfilling).
 *  - Results are fetched across a REAL page boundary (page size 2 < 4 rows) — the only place the
 *    duplicate/skipped-row symptom can manifest. Pages 0 and 1 are concatenated and checked for
 *    exact order, no duplicates, and no missing ids.
 *
 * The UUIDs use only low-order bytes, so Postgres' unsigned-uuid ordering and Java's signed
 * UUID#compareTo agree — the hardcoded expected order is unambiguous under both.
 */
class CommentPaginationTiebreakerIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var commentRepository: CommentRepository
    @Autowired private lateinit var activityService: ActivityService

    private val id1 = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val id2 = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val id3 = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val id4 = UUID.fromString("00000000-0000-0000-0000-000000000004")

    // Insertion order deliberately != id-descending order.
    private val insertionOrder = listOf(id1, id4, id2, id3)
    // Expected result: strictly id-descending, written literally (NOT derived from SQL).
    private val expectedIdDesc = listOf(id4, id3, id2, id1)

    private val fixedInstant = Timestamp.from(Instant.parse("2026-01-01T12:00:00Z"))

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

    private fun assertStableCrossPageOrder(page0: List<UUID>, page1: List<UUID>) {
        // Each page reflects the id-descending order split at the boundary.
        assertEquals(listOf(id4, id3), page0, "page 0 must be the two highest ids, id-descending")
        assertEquals(listOf(id2, id1), page1, "page 1 must be the two lowest ids, id-descending")

        val concatenated = page0 + page1
        // (a) exact order across the page boundary
        assertEquals(expectedIdDesc, concatenated, "rows must be newest-first with id-descending tiebreaker")
        // (b) no duplicate id across page 0 + page 1 (the "repeated row" symptom)
        assertEquals(concatenated.size, concatenated.toSet().size, "duplicate id across page boundary: $concatenated")
        // (c) no expected id missing (the "skipped row" symptom)
        assertTrue(concatenated.toSet().containsAll(expectedIdDesc), "missing id across page boundary: $concatenated")
    }

    @Test
    fun `comments with identical createdAt page deterministically by id descending across boundary`() {
        val token = register("tiebreak-comments@example.com")
        val (issueId, authorId) = createProjectAndIssue(token, "TIEB")

        insertionOrder.forEachIndexed { i, id ->
            jdbcTemplate.update(
                "INSERT INTO comments (id, body, issue_id, author_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, "Comment #$i", issueId, authorId, fixedInstant, fixedInstant
            )
        }

        // Page size 2 < 4 rows → forces a real boundary between page 0 and page 1.
        val page0 = commentRepository.findByIssueIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            issueId, PageRequest.of(0, 2)
        ).content.map { it.id }
        val page1 = commentRepository.findByIssueIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            issueId, PageRequest.of(1, 2)
        ).content.map { it.id }

        assertStableCrossPageOrder(page0, page1)
    }

    @Test
    fun `activity with identical createdAt pages deterministically by id descending across boundary`() {
        val token = register("tiebreak-activity@example.com")
        val (issueId, authorId) = createProjectAndIssue(token, "TIEA")

        insertionOrder.forEach { id ->
            jdbcTemplate.update(
                "INSERT INTO issue_activities (id, issue_id, actor_id, type, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, issueId, authorId, ActivityType.TITLE_CHANGED.name, fixedInstant, fixedInstant
            )
        }

        val page0 = activityService.listActivity(issueId, 0, 2).content.map { it.id }
        val page1 = activityService.listActivity(issueId, 1, 2).content.map { it.id }

        assertStableCrossPageOrder(page0, page1)
    }
}
