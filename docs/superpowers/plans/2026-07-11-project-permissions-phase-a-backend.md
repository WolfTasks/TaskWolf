# #9 Projekt-Rechte-Verwaltung — Phase A (Backend) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Backend für Projekt-Rollen: Member-CRUD-API (Rolle zuweisen/ändern/entfernen), Durchsetzung der Read-only-Rolle auf allen Schreib-Endpoints, `myRole` im ProjectResponse und ein User-Suche-Endpoint für den Add-Dialog.

**Architecture:** Wiederverwendung des vorhandenen `ProjectRole { ADMIN, MEMBER, VIEWER }` (VIEWER = Read-only, MEMBER = Read & Write, ADMIN = Admin) — **keine DB-Migration**. Member-Verwaltung als neue Methoden in `ProjectService` + neuer `ProjectMemberController` (Admin-gegated über das vorhandene `requireAdmin`). Read-only-Durchsetzung deklarativ via `@PreAuthorize("@projectSecurity.canWrite(#key, authentication)")` auf mutierenden Endpoints — analog zum bestehenden `@projectSecurity.isProjectAdmin`-Muster, ohne Eingriff in die Schreib-Services.

**Tech Stack:** Kotlin, Spring Boot 3.5.x, Spring Data JPA, Spring Security (`@EnableMethodSecurity` ist aktiv), JUnit 5 + MockK (Unit), Testcontainers-Postgres via `IntegrationTestBase` (Integration).

**Spec:** `docs/superpowers/specs/2026-07-11-project-permissions-design.md`

## Global Constraints

- **Keine DB-Migration:** `ProjectRole` enthält bereits `ADMIN, MEMBER, VIEWER` (gespeichert als `EnumType.STRING`).
- **Rollen-Semantik:** VIEWER = Read-only, MEMBER = Read & Write, ADMIN = Admin. `project.owner` ist implizit immer ADMIN und **nicht** entfern-/degradierbar.
- **Fehler-Hygiene (H2):** Fehlermeldungen dürfen **keine** internen Namen (Enum-Konstanten, FQCN) leaken. Unbekannte Enum-Werte im Request-Body → sauberer **400**, nicht 500.
- **Autorisierung Member-Verwaltung:** nur Projekt-Admin/Owner/System-ADMIN (`ProjectService.requireAdmin`).
- **Unit-Tests:** MockK (nicht Mockito).
- **Integration-Tests:** erben von `com.taskowolf.IntegrationTestBase` (echtes Postgres via Testcontainers), MockMvc + `/api/v1/auth/register` für Tokens.
- **Test-Kommandos** laufen aus `backend/`: `cd backend && ./gradlew test --tests "<FQN>"`.
- **Commits:** häufig, konventionell, am Ende jeder Task. Commit-Trailer: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

---

## File Structure

**Neu (Backend main):**
- `projects/api/dto/ProjectMemberResponse.kt` — `{ user, role }`
- `projects/api/dto/AddProjectMemberRequest.kt` — `{ userId, role }`
- `projects/api/dto/UpdateProjectMemberRoleRequest.kt` — `{ role }`
- `projects/api/ProjectMemberController.kt` — GET/POST/PATCH/DELETE `…/members`
- `auth/api/dto/UserSearchResponse.kt` — `{ id, email, displayName }`
- `auth/application/UserSearchService.kt` — Suche mit Min-Länge
- `auth/api/UserSearchController.kt` — `GET /api/v1/users/search`

**Modifiziert (Backend main):**
- `projects/application/ProjectService.kt` — `roleOf`, `canWrite`, `addMember`, `changeMemberRole`, `removeMember`, `canManageAnyProjectMembers`; neue Dependency `UserRepository`
- `projects/infrastructure/ProjectSecurity.kt` — `canWrite(key, authentication)`
- `projects/infrastructure/ProjectMemberRepository.kt` — `existsByUserIdAndRole`
- `projects/infrastructure/ProjectRepository.kt` — `existsByOwnerId`
- `projects/api/dto/ProjectResponse.kt` — Feld `myRole: ProjectRole?`
- `projects/api/ProjectController.kt` — `get` setzt `myRole`; `getMembers` entfernt (wandert in `ProjectMemberController`); ungenutzte `ProjectMemberRepository`-Dependency entfernen
- `auth/infrastructure/UserRepository.kt` — `searchActive`
- `core/infrastructure/GlobalExceptionHandler.kt` — Handler für `HttpMessageNotReadableException` → 400
- `issues/api/IssueController.kt`, `comments/api/CommentController.kt`, `labels/api/LabelController.kt`, `sprints/api/SprintController.kt`, `versions/api/VersionController.kt`, `customfields/api/CustomFieldController.kt` — `@PreAuthorize` auf Schreib-Endpoints

**Neu/Modifiziert (Backend test):**
- `projects/ProjectServiceTest.kt` — Konstruktor um `UserRepository`-Mock erweitern; Tests für `roleOf`/`canWrite`, Member-CRUD-Guards, `canManageAnyProjectMembers`
- `projects/ProjectMemberIntegrationTest.kt` (neu) — Member-CRUD end-to-end
- `projects/ProjectWriteEnforcementIntegrationTest.kt` (neu) — VIEWER 403 / MEMBER 201
- `auth/UserSearchIntegrationTest.kt` (neu) — Suche + Autorisierung

---

## Task 1: `roleOf` + `canWrite` in ProjectService

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/projects/ProjectServiceTest.kt`

**Interfaces:**
- Produces:
  - `ProjectService.roleOf(project: Project, userId: UUID): ProjectRole?` — Owner → `ADMIN`; sonst Member-Rolle; Nicht-Mitglied → `null`.
  - `ProjectService.canWrite(projectKey: String, userId: UUID): Boolean` — `true` für Owner/ADMIN/MEMBER, `false` für VIEWER und Nicht-Mitglieder.

- [ ] **Step 1: Write the failing tests**

In `ProjectServiceTest.kt`, add a helper project and tests. Insert these tests inside the class:

```kotlin
    private fun projectOwnedBy(o: User) =
        com.taskowolf.projects.domain.Project(key = "WOLF", name = "TaskWolf", description = null, owner = o)

    @Test
    fun `roleOf returns ADMIN for owner`() {
        val project = projectOwnedBy(owner)
        assertEquals(ProjectRole.ADMIN, service.roleOf(project, owner.id))
    }

    @Test
    fun `roleOf returns member role for a member`() {
        val member = User(email = "m@test.com", displayName = "M")
        val project = projectOwnedBy(owner)
        every { memberRepository.findByProjectIdAndUserId(project.id, member.id) } returns
            ProjectMember(project = project, user = member, role = ProjectRole.VIEWER)
        assertEquals(ProjectRole.VIEWER, service.roleOf(project, member.id))
    }

    @Test
    fun `roleOf returns null for non-member`() {
        val other = User(email = "o@test.com", displayName = "O")
        val project = projectOwnedBy(owner)
        every { memberRepository.findByProjectIdAndUserId(project.id, other.id) } returns null
        assertNull(service.roleOf(project, other.id))
    }

    @Test
    fun `canWrite is true for owner and false for viewer and non-member`() {
        val viewer = User(email = "v@test.com", displayName = "V")
        val other = User(email = "n@test.com", displayName = "N")
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.findByProjectIdAndUserId(project.id, viewer.id) } returns
            ProjectMember(project = project, user = viewer, role = ProjectRole.VIEWER)
        every { memberRepository.findByProjectIdAndUserId(project.id, other.id) } returns null

        assertTrue(service.canWrite("WOLF", owner.id))
        assertFalse(service.canWrite("WOLF", viewer.id))
        assertFalse(service.canWrite("WOLF", other.id))
    }

    @Test
    fun `canWrite is true for MEMBER role`() {
        val member = User(email = "rw@test.com", displayName = "RW")
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.findByProjectIdAndUserId(project.id, member.id) } returns
            ProjectMember(project = project, user = member, role = ProjectRole.MEMBER)
        assertTrue(service.canWrite("WOLF", member.id))
    }
```

Add these imports at the top of `ProjectServiceTest.kt` if missing:

```kotlin
import com.taskowolf.projects.domain.Project
import com.taskowolf.projects.domain.ProjectMember
import com.taskowolf.projects.domain.ProjectRole
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectServiceTest"`
Expected: FAIL — `roleOf`/`canWrite` unresolved reference.

- [ ] **Step 3: Implement `roleOf` and `canWrite`**

In `ProjectService.kt`, add these methods inside the class (e.g. after `isProjectAdmin`):

```kotlin
    @Transactional(readOnly = true)
    fun roleOf(project: Project, userId: UUID): ProjectRole? =
        if (project.owner.id == userId) ProjectRole.ADMIN
        else memberRepository.findByProjectIdAndUserId(project.id, userId)?.role

    @Transactional(readOnly = true)
    fun canWrite(projectKey: String, userId: UUID): Boolean {
        val project = findByKey(projectKey)
        val role = roleOf(project, userId) ?: return false
        return role != ProjectRole.VIEWER
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt backend/src/test/kotlin/com/taskowolf/projects/ProjectServiceTest.kt
git commit -m "feat(projects): add roleOf and canWrite to ProjectService (#9)"
```

---

## Task 2: Member-Management-Methoden in ProjectService

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/projects/ProjectServiceTest.kt`

**Interfaces:**
- Consumes: `ProjectService.requireAdmin(projectKey, userId): Project` (existiert; wirft `ForbiddenException` wenn kein Admin), `ProjectMemberRepository.findByProjectIdAndUserId`, `existsByProjectIdAndUserId`, `UserRepository.findById`.
- Produces:
  - `addMember(projectKey: String, actorId: UUID, targetUserId: UUID, role: ProjectRole): ProjectMember`
  - `changeMemberRole(projectKey: String, actorId: UUID, targetUserId: UUID, role: ProjectRole): ProjectMember`
  - `removeMember(projectKey: String, actorId: UUID, targetUserId: UUID)`
  - Neue Dependency `userRepository: UserRepository` im Konstruktor.

- [ ] **Step 1: Extend the test fixture constructor and write failing tests**

`ProjectService` bekommt eine neue Dependency. Update the fixture in `ProjectServiceTest.kt`:

```kotlin
    private val userRepository = mockk<com.taskowolf.auth.infrastructure.UserRepository>()
    private val service = ProjectService(projectRepository, memberRepository, workflowService, userRepository)
```

Add these tests (actor = `owner`, damit `requireAdmin` über den Owner-Pfad besteht). Common stubs für `requireAdmin(owner)`: `findByKey` liefert das Projekt, `existsByProjectIdAndUserId(project.id, owner.id)` = `false` (Owner-Kurzschluss greift):

```kotlin
    @Test
    fun `addMember saves a new member`() {
        val target = User(email = "t@test.com", displayName = "T")
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false
        every { memberRepository.existsByProjectIdAndUserId(project.id, target.id) } returns false
        every { userRepository.findById(target.id) } returns java.util.Optional.of(target)
        every { memberRepository.save(any()) } returnsArgument 0

        val result = service.addMember("WOLF", owner.id, target.id, ProjectRole.VIEWER)

        assertEquals(ProjectRole.VIEWER, result.role)
        verify(exactly = 1) { memberRepository.save(any()) }
    }

    @Test
    fun `addMember throws Conflict when already a member`() {
        val target = User(email = "t2@test.com", displayName = "T2")
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false
        every { memberRepository.existsByProjectIdAndUserId(project.id, target.id) } returns true

        assertThrows<ConflictException> {
            service.addMember("WOLF", owner.id, target.id, ProjectRole.MEMBER)
        }
    }

    @Test
    fun `addMember throws Conflict when target is the owner`() {
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false

        assertThrows<ConflictException> {
            service.addMember("WOLF", owner.id, owner.id, ProjectRole.ADMIN)
        }
    }

    @Test
    fun `addMember throws NotFound when user unknown`() {
        val unknownId = java.util.UUID.randomUUID()
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false
        every { memberRepository.existsByProjectIdAndUserId(project.id, unknownId) } returns false
        every { userRepository.findById(unknownId) } returns java.util.Optional.empty()

        assertThrows<NotFoundException> {
            service.addMember("WOLF", owner.id, unknownId, ProjectRole.MEMBER)
        }
    }

    @Test
    fun `changeMemberRole throws Forbidden for owner target`() {
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false

        assertThrows<ForbiddenException> {
            service.changeMemberRole("WOLF", owner.id, owner.id, ProjectRole.VIEWER)
        }
    }

    @Test
    fun `changeMemberRole updates an existing member`() {
        val target = User(email = "cr@test.com", displayName = "CR")
        val project = projectOwnedBy(owner)
        val member = ProjectMember(project = project, user = target, role = ProjectRole.VIEWER)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false
        every { memberRepository.findByProjectIdAndUserId(project.id, target.id) } returns member
        every { memberRepository.save(any()) } returnsArgument 0

        val result = service.changeMemberRole("WOLF", owner.id, target.id, ProjectRole.ADMIN)

        assertEquals(ProjectRole.ADMIN, result.role)
    }

    @Test
    fun `changeMemberRole throws NotFound when target not a member`() {
        val target = User(email = "nm@test.com", displayName = "NM")
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false
        every { memberRepository.findByProjectIdAndUserId(project.id, target.id) } returns null

        assertThrows<NotFoundException> {
            service.changeMemberRole("WOLF", owner.id, target.id, ProjectRole.MEMBER)
        }
    }

    @Test
    fun `removeMember throws Forbidden for owner target`() {
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false

        assertThrows<ForbiddenException> {
            service.removeMember("WOLF", owner.id, owner.id)
        }
    }

    @Test
    fun `removeMember deletes an existing member`() {
        val target = User(email = "rm@test.com", displayName = "RM")
        val project = projectOwnedBy(owner)
        val member = ProjectMember(project = project, user = target, role = ProjectRole.MEMBER)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false
        every { memberRepository.findByProjectIdAndUserId(project.id, target.id) } returns member
        every { memberRepository.delete(member) } returns Unit

        service.removeMember("WOLF", owner.id, target.id)

        verify(exactly = 1) { memberRepository.delete(member) }
    }
```

Add imports if missing:
```kotlin
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
```
(`ConflictException` is already imported in this test file.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectServiceTest"`
Expected: FAIL — compile error (constructor arity) / unresolved `addMember` etc.

- [ ] **Step 3: Add the `UserRepository` dependency and member methods**

In `ProjectService.kt`, add the import and constructor param:

```kotlin
import com.taskowolf.auth.infrastructure.UserRepository
```
```kotlin
@Service
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val memberRepository: ProjectMemberRepository,
    private val workflowService: WorkflowService,
    private val userRepository: UserRepository
) {
```

Add the three methods inside the class:

```kotlin
    @Transactional
    fun addMember(projectKey: String, actorId: UUID, targetUserId: UUID, role: ProjectRole): ProjectMember {
        val project = requireAdmin(projectKey, actorId)
        if (project.owner.id == targetUserId || memberRepository.existsByProjectIdAndUserId(project.id, targetUserId)) {
            throw ConflictException("User is already a member of this project")
        }
        val user = userRepository.findById(targetUserId)
            .orElseThrow { NotFoundException("User not found") }
        return memberRepository.save(ProjectMember(project = project, user = user, role = role))
    }

    @Transactional
    fun changeMemberRole(projectKey: String, actorId: UUID, targetUserId: UUID, role: ProjectRole): ProjectMember {
        val project = requireAdmin(projectKey, actorId)
        if (project.owner.id == targetUserId) throw ForbiddenException("Cannot change the project owner's role")
        val member = memberRepository.findByProjectIdAndUserId(project.id, targetUserId)
            ?: throw NotFoundException("Member not found")
        member.role = role
        return memberRepository.save(member)
    }

    @Transactional
    fun removeMember(projectKey: String, actorId: UUID, targetUserId: UUID) {
        val project = requireAdmin(projectKey, actorId)
        if (project.owner.id == targetUserId) throw ForbiddenException("Cannot remove the project owner")
        val member = memberRepository.findByProjectIdAndUserId(project.id, targetUserId)
            ?: throw NotFoundException("Member not found")
        memberRepository.delete(member)
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt backend/src/test/kotlin/com/taskowolf/projects/ProjectServiceTest.kt
git commit -m "feat(projects): member add/change-role/remove in ProjectService (#9)"
```

---

## Task 3: ProjectMemberController (CRUD) + Rollen in der Member-Liste + 400-Handler

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/projects/api/dto/ProjectMemberResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/projects/api/dto/AddProjectMemberRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/projects/api/dto/UpdateProjectMemberRoleRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/projects/api/ProjectMemberController.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/api/ProjectController.kt` (remove `getMembers` + unused repo dependency)
- Modify: `backend/src/main/kotlin/com/taskowolf/core/infrastructure/GlobalExceptionHandler.kt` (400 for malformed body / unknown enum)
- Test: `backend/src/test/kotlin/com/taskowolf/projects/ProjectMemberIntegrationTest.kt`

**Interfaces:**
- Consumes: `ProjectService.addMember/changeMemberRole/removeMember` (Task 2), `ProjectService.requireMember`, `ProjectMemberRepository.findAllByProjectId`.
- Produces: REST-Endpoints unter `/api/v1/projects/{key}/members`:
  - `GET` → `List<ProjectMemberResponse>` (Mitglied)
  - `POST { userId, role }` → `201 ProjectMemberResponse` (Admin)
  - `PATCH /{userId} { role }` → `ProjectMemberResponse` (Admin)
  - `DELETE /{userId}` → `204` (Admin)
  - `ProjectMemberResponse(user: UserResponse, role: ProjectRole)`

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/kotlin/com/taskowolf/projects/ProjectMemberIntegrationTest.kt`:

```kotlin
package com.taskowolf.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ProjectMemberIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun registerAndGetToken(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"Test","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    private fun myId(token: String): String {
        val result = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $token")).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("id").asText()
    }

    private fun createProject(token: String, key: String) {
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key","name":"$key"}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `admin can add, change role, list and remove a member`() {
        val ownerToken = registerAndGetToken("m-owner@test.com")
        val memberToken = registerAndGetToken("m-member@test.com")
        val memberId = myId(memberToken)
        createProject(ownerToken, "MEM")

        // add as VIEWER
        mockMvc.perform(
            post("/api/v1/projects/MEM/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$memberId","role":"VIEWER"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.role").value("VIEWER"))
            .andExpect(jsonPath("$.user.id").value(memberId))

        // list shows owner + member with roles
        mockMvc.perform(get("/api/v1/projects/MEM/members").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.user.id=='$memberId')].role").value("VIEWER"))

        // change role to ADMIN
        mockMvc.perform(
            patch("/api/v1/projects/MEM/members/$memberId").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"ADMIN"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("ADMIN"))

        // remove
        mockMvc.perform(delete("/api/v1/projects/MEM/members/$memberId").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `non-admin member cannot add members`() {
        val ownerToken = registerAndGetToken("m-owner2@test.com")
        val memberToken = registerAndGetToken("m-member2@test.com")
        val thirdToken = registerAndGetToken("m-third@test.com")
        val memberId = myId(memberToken)
        val thirdId = myId(thirdToken)
        createProject(ownerToken, "MEM2")

        // owner adds member as MEMBER (write, not admin)
        mockMvc.perform(
            post("/api/v1/projects/MEM2/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$memberId","role":"MEMBER"}""")
        ).andExpect(status().isCreated)

        // member tries to add third → 403
        mockMvc.perform(
            post("/api/v1/projects/MEM2/members").header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$thirdId","role":"MEMBER"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `adding an existing member returns 409`() {
        val ownerToken = registerAndGetToken("m-owner3@test.com")
        val memberToken = registerAndGetToken("m-member3@test.com")
        val memberId = myId(memberToken)
        createProject(ownerToken, "MEM3")

        val body = """{"userId":"$memberId","role":"MEMBER"}"""
        mockMvc.perform(
            post("/api/v1/projects/MEM3/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isCreated)
        mockMvc.perform(
            post("/api/v1/projects/MEM3/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isConflict)
    }

    @Test
    fun `unknown role in body returns 400 not 500`() {
        val ownerToken = registerAndGetToken("m-owner4@test.com")
        val memberToken = registerAndGetToken("m-member4@test.com")
        val memberId = myId(memberToken)
        createProject(ownerToken, "MEM4")

        mockMvc.perform(
            post("/api/v1/projects/MEM4/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$memberId","role":"SUPERADMIN"}""")
        ).andExpect(status().isBadRequest)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectMemberIntegrationTest"`
Expected: FAIL — endpoints 404 / mapping missing.

- [ ] **Step 3: Create the DTOs**

`projects/api/dto/ProjectMemberResponse.kt`:
```kotlin
package com.taskowolf.projects.api.dto

import com.taskowolf.auth.api.dto.UserResponse
import com.taskowolf.projects.domain.ProjectMember
import com.taskowolf.projects.domain.ProjectRole

data class ProjectMemberResponse(val user: UserResponse, val role: ProjectRole) {
    companion object {
        fun from(m: ProjectMember) = ProjectMemberResponse(UserResponse.from(m.user), m.role)
    }
}
```

`projects/api/dto/AddProjectMemberRequest.kt`:
```kotlin
package com.taskowolf.projects.api.dto

import com.taskowolf.projects.domain.ProjectRole
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class AddProjectMemberRequest(
    @field:NotNull val userId: UUID,
    @field:NotNull val role: ProjectRole
)
```

`projects/api/dto/UpdateProjectMemberRoleRequest.kt`:
```kotlin
package com.taskowolf.projects.api.dto

import com.taskowolf.projects.domain.ProjectRole
import jakarta.validation.constraints.NotNull

data class UpdateProjectMemberRoleRequest(
    @field:NotNull val role: ProjectRole
)
```

- [ ] **Step 4: Create `ProjectMemberController`**

`projects/api/ProjectMemberController.kt`:
```kotlin
package com.taskowolf.projects.api

import com.taskowolf.auth.domain.User
import com.taskowolf.projects.api.dto.AddProjectMemberRequest
import com.taskowolf.projects.api.dto.ProjectMemberResponse
import com.taskowolf.projects.api.dto.UpdateProjectMemberRoleRequest
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/members")
class ProjectMemberController(
    private val projectService: ProjectService,
    private val projectMemberRepository: ProjectMemberRepository
) {
    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User): List<ProjectMemberResponse> {
        val project = projectService.requireMember(key, user.id)
        return projectMemberRepository.findAllByProjectId(project.id).map { ProjectMemberResponse.from(it) }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun add(
        @PathVariable key: String,
        @Valid @RequestBody request: AddProjectMemberRequest,
        @AuthenticationPrincipal user: User
    ) = ProjectMemberResponse.from(projectService.addMember(key, user.id, request.userId, request.role))

    @PatchMapping("/{userId}")
    fun changeRole(
        @PathVariable key: String,
        @PathVariable userId: UUID,
        @Valid @RequestBody request: UpdateProjectMemberRoleRequest,
        @AuthenticationPrincipal user: User
    ) = ProjectMemberResponse.from(projectService.changeMemberRole(key, user.id, userId, request.role))

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(
        @PathVariable key: String,
        @PathVariable userId: UUID,
        @AuthenticationPrincipal user: User
    ) = projectService.removeMember(key, user.id, userId)
}
```

- [ ] **Step 5: Remove the old `getMembers` from `ProjectController`**

In `ProjectController.kt`, delete the `getMembers` method (the `@GetMapping("/{key}/members")` block) and remove the now-unused `projectMemberRepository` constructor parameter and its import (`com.taskowolf.projects.infrastructure.ProjectMemberRepository`) and the unused `UserResponse`/`ResponseEntity` imports if they become unused. The controller then only has `list`, `create`, `get`.

- [ ] **Step 6: Add a 400 handler for malformed/unknown-enum bodies**

In `GlobalExceptionHandler.kt`, add the import and handler (H2: generic message, no internals):

```kotlin
import org.springframework.http.converter.HttpMessageNotReadableException
```
```kotlin
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("BAD_REQUEST", "Malformed or invalid request body"))
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectMemberIntegrationTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/projects backend/src/main/kotlin/com/taskowolf/core/infrastructure/GlobalExceptionHandler.kt backend/src/test/kotlin/com/taskowolf/projects/ProjectMemberIntegrationTest.kt
git commit -m "feat(projects): project member CRUD API with roles (#9)"
```

---

## Task 4: Read-only-Durchsetzung via `@PreAuthorize` canWrite

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/infrastructure/ProjectSecurity.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/IssueController.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/comments/api/CommentController.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/labels/api/LabelController.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/sprints/api/SprintController.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/versions/api/VersionController.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/customfields/api/CustomFieldController.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/projects/ProjectWriteEnforcementIntegrationTest.kt`

**Interfaces:**
- Consumes: `ProjectService.canWrite` (Task 1), `ProjectService.addMember` / member API (Task 3).
- Produces: `ProjectSecurity.canWrite(key: String, authentication: Authentication): Boolean` for SpEL use in `@PreAuthorize`.

- [ ] **Step 1: Write the failing enforcement integration test**

Create `backend/src/test/kotlin/com/taskowolf/projects/ProjectWriteEnforcementIntegrationTest.kt`:

```kotlin
package com.taskowolf.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ProjectWriteEnforcementIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun registerAndGetToken(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"Test","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    private fun myId(token: String): String {
        val result = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $token")).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("id").asText()
    }

    private fun addMember(ownerToken: String, key: String, userId: String, role: String) {
        mockMvc.perform(
            post("/api/v1/projects/$key/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$userId","role":"$role"}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `viewer is blocked from writing but can read; member can write`() {
        val ownerToken = registerAndGetToken("enf-owner@test.com")
        val viewerToken = registerAndGetToken("enf-viewer@test.com")
        val memberToken = registerAndGetToken("enf-member@test.com")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"key":"ENF","name":"Enf"}""")
        ).andExpect(status().isCreated)
        addMember(ownerToken, "ENF", myId(viewerToken), "VIEWER")
        addMember(ownerToken, "ENF", myId(memberToken), "MEMBER")

        // viewer: create issue → 403
        mockMvc.perform(
            post("/api/v1/projects/ENF/issues").header("Authorization", "Bearer $viewerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"title":"nope"}""")
        ).andExpect(status().isForbidden)

        // viewer: read issues → 200
        mockMvc.perform(get("/api/v1/projects/ENF/issues").header("Authorization", "Bearer $viewerToken"))
            .andExpect(status().isOk)

        // member: create issue → 201
        mockMvc.perform(
            post("/api/v1/projects/ENF/issues").header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"title":"yes"}""")
        ).andExpect(status().isCreated)

        // viewer: create label → 403 (covers non-issue write path)
        mockMvc.perform(
            post("/api/v1/projects/ENF/labels").header("Authorization", "Bearer $viewerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"name":"x","color":"#fff"}""")
        ).andExpect(status().isForbidden)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectWriteEnforcementIntegrationTest"`
Expected: FAIL — viewer create issue returns 201 (no enforcement yet), test asserts 403.

- [ ] **Step 3: Add `canWrite` to `ProjectSecurity`**

In `ProjectSecurity.kt`, add:
```kotlin
    fun canWrite(key: String, authentication: Authentication): Boolean {
        val user = authentication.principal as? User ?: return false
        return try { projectService.canWrite(key, user.id) } catch (_: Exception) { false }
    }
```

- [ ] **Step 4: Annotate the write endpoints**

Add `import org.springframework.security.access.prepost.PreAuthorize` to each controller below and put `@PreAuthorize("@projectSecurity.canWrite(#key, authentication)")` on each listed method (all already have `@PathVariable key`):

- `IssueController.kt`: `create`, `update`, `delete`.
- `CommentController.kt`: `addComment`.
- `LabelController.kt`: `create`, `update`, `delete`.
- `SprintController.kt`: `create`, `update`, `start`, `complete`, `assignIssue`, `unassignIssue`.
- `VersionController.kt`: `create`, `update`, `delete`.
- `CustomFieldController.kt`: `create`, `update`, `reorder`, `delete`, `createOption`, `updateOption`, `deleteOption`.

Example (IssueController.create):
```kotlin
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectSecurity.canWrite(#key, authentication)")
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: CreateIssueRequest,
        @AuthenticationPrincipal user: User
    ) = IssueResponse.from(issueService.create(key, request, user))
```

Do **not** annotate read endpoints (`list`, `get`, `listComments`, `listActivity`, etc.).

- [ ] **Step 5: Run the enforcement test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectWriteEnforcementIntegrationTest"`
Expected: PASS.

- [ ] **Step 6: Run the full affected module tests (regression)**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.issues.*" --tests "com.taskowolf.comments.*" --tests "com.taskowolf.labels.*" --tests "com.taskowolf.sprints.*" --tests "com.taskowolf.versions.*" --tests "com.taskowolf.customfields.*"`
Expected: PASS (existing tests act as owner/admin members → still allowed; no stubs changed).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf backend/src/test/kotlin/com/taskowolf/projects/ProjectWriteEnforcementIntegrationTest.kt
git commit -m "feat(projects): enforce read-only role on write endpoints via @PreAuthorize (#9)"
```

---

## Task 5: `myRole` im ProjectResponse

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/api/dto/ProjectResponse.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/api/ProjectController.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/projects/ProjectMyRoleIntegrationTest.kt`

**Interfaces:**
- Consumes: `ProjectService.roleOf` (Task 1), member API (Task 3).
- Produces: `ProjectResponse` mit `myRole: ProjectRole?` (auf `GET /projects/{key}` gesetzt; `null` in der Liste).

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/kotlin/com/taskowolf/projects/ProjectMyRoleIntegrationTest.kt`:

```kotlin
package com.taskowolf.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ProjectMyRoleIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun registerAndGetToken(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"Test","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    private fun myId(token: String): String {
        val result = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $token")).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("id").asText()
    }

    @Test
    fun `myRole reflects the caller's role`() {
        val ownerToken = registerAndGetToken("mr-owner@test.com")
        val viewerToken = registerAndGetToken("mr-viewer@test.com")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"key":"MYR","name":"MyRole"}""")
        ).andExpect(status().isCreated)
        mockMvc.perform(
            post("/api/v1/projects/MYR/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"${myId(viewerToken)}","role":"VIEWER"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/projects/MYR").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk).andExpect(jsonPath("$.myRole").value("ADMIN"))
        mockMvc.perform(get("/api/v1/projects/MYR").header("Authorization", "Bearer $viewerToken"))
            .andExpect(status().isOk).andExpect(jsonPath("$.myRole").value("VIEWER"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectMyRoleIntegrationTest"`
Expected: FAIL — `myRole` is missing/null.

- [ ] **Step 3: Add `myRole` to `ProjectResponse`**

Replace `ProjectResponse.kt` with:
```kotlin
package com.taskowolf.projects.api.dto

import com.taskowolf.projects.domain.Project
import com.taskowolf.projects.domain.ProjectRole
import java.util.UUID

data class ProjectResponse(
    val id: UUID,
    val key: String,
    val name: String,
    val description: String?,
    val ownerId: UUID,
    val archived: Boolean,
    val myRole: ProjectRole? = null
) {
    companion object {
        fun from(p: Project, myRole: ProjectRole? = null) =
            ProjectResponse(p.id, p.key, p.name, p.description, p.owner.id, p.archived, myRole)
    }
}
```

- [ ] **Step 4: Set `myRole` in the single-project GET**

In `ProjectController.kt`, change the `get` handler to resolve the caller's role:
```kotlin
    @GetMapping("/{key}")
    fun get(@PathVariable key: String, @AuthenticationPrincipal user: User): ProjectResponse {
        val project = projectService.requireMember(key, user.id)
        return ProjectResponse.from(project, projectService.roleOf(project, user.id))
    }
```
Leave `list` using `ProjectResponse.from(it)` (myRole stays `null` there).

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectMyRoleIntegrationTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/projects backend/src/test/kotlin/com/taskowolf/projects/ProjectMyRoleIntegrationTest.kt
git commit -m "feat(projects): expose caller myRole on project GET (#9)"
```

---

## Task 6: User-Suche für den Add-Dialog

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/UserRepository.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/infrastructure/ProjectMemberRepository.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/infrastructure/ProjectRepository.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt` (add `canManageAnyProjectMembers`)
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/dto/UserSearchResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/application/UserSearchService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/UserSearchController.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/UserSearchIntegrationTest.kt`

**Interfaces:**
- Produces:
  - `UserRepository.searchActive(q: String, pageable: Pageable): List<User>`
  - `ProjectMemberRepository.existsByUserIdAndRole(userId: UUID, role: ProjectRole): Boolean`
  - `ProjectRepository.existsByOwnerId(ownerId: UUID): Boolean`
  - `ProjectService.canManageAnyProjectMembers(user: User): Boolean`
  - `UserSearchService.search(query: String): List<User>` (Min-Länge 2, sonst leer; max 10)
  - `GET /api/v1/users/search?q=…` → `List<UserSearchResponse>` (nur System-ADMIN oder Admin/Owner ≥1 Projekts; sonst 403)

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/kotlin/com/taskowolf/auth/UserSearchIntegrationTest.kt`:

```kotlin
package com.taskowolf.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class UserSearchIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun registerAndGetToken(email: String, displayName: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"$displayName","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    @Test
    fun `project owner can search users by email fragment`() {
        val ownerToken = registerAndGetToken("search-owner@test.com", "Searcher")
        registerAndGetToken("findme@test.com", "Findme Person")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"key":"SRCH","name":"Search"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/users/search?q=findme").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.email=='findme@test.com')]").exists())
    }

    @Test
    fun `user without any admin project gets 403`() {
        val plainToken = registerAndGetToken("plain@test.com", "Plain")
        registerAndGetToken("target@test.com", "Target")
        mockMvc.perform(get("/api/v1/users/search?q=target").header("Authorization", "Bearer $plainToken"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `query shorter than 2 chars returns empty list`() {
        val ownerToken = registerAndGetToken("short-owner@test.com", "ShortOwner")
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"key":"SHRT","name":"Short"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/users/search?q=a").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.UserSearchIntegrationTest"`
Expected: FAIL — `/api/v1/users/search` 404.

- [ ] **Step 3: Add repository queries**

In `UserRepository.kt`, add the import and method:
```kotlin
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
```
```kotlin
    @Query("""
        SELECT u FROM User u
        WHERE u.active = true AND u.deletedAt IS NULL
          AND (LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY u.displayName ASC
    """)
    fun searchActive(q: String, pageable: Pageable): List<User>
```

In `ProjectMemberRepository.kt`, add:
```kotlin
import com.taskowolf.projects.domain.ProjectRole
```
```kotlin
    fun existsByUserIdAndRole(userId: UUID, role: ProjectRole): Boolean
```

In `ProjectRepository.kt`, add inside the interface:
```kotlin
    fun existsByOwnerId(ownerId: UUID): Boolean
```

- [ ] **Step 4: Add `canManageAnyProjectMembers` to `ProjectService`**

In `ProjectService.kt`, add the import and method:
```kotlin
import com.taskowolf.auth.domain.SystemRole
```
```kotlin
    @Transactional(readOnly = true)
    fun canManageAnyProjectMembers(user: User): Boolean {
        if (user.systemRole == SystemRole.ADMIN) return true
        return memberRepository.existsByUserIdAndRole(user.id, ProjectRole.ADMIN) ||
            projectRepository.existsByOwnerId(user.id)
    }
```

- [ ] **Step 5: Create the search DTO, service, and controller**

`auth/api/dto/UserSearchResponse.kt`:
```kotlin
package com.taskowolf.auth.api.dto

import com.taskowolf.auth.domain.User
import java.util.UUID

data class UserSearchResponse(val id: UUID, val email: String, val displayName: String) {
    companion object {
        fun from(u: User) = UserSearchResponse(u.id, u.email, u.displayName)
    }
}
```

`auth/application/UserSearchService.kt`:
```kotlin
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
```

`auth/api/UserSearchController.kt`:
```kotlin
package com.taskowolf.auth.api

import com.taskowolf.auth.api.dto.UserSearchResponse
import com.taskowolf.auth.application.UserSearchService
import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.projects.application.ProjectService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
class UserSearchController(
    private val userSearchService: UserSearchService,
    private val projectService: ProjectService
) {
    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @AuthenticationPrincipal user: User
    ): List<UserSearchResponse> {
        if (!projectService.canManageAnyProjectMembers(user)) {
            throw ForbiddenException("Not allowed to search users")
        }
        return userSearchService.search(q).map { UserSearchResponse.from(it) }
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.UserSearchIntegrationTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf backend/src/test/kotlin/com/taskowolf/auth/UserSearchIntegrationTest.kt
git commit -m "feat(users): user search endpoint for member add dialog (#9)"
```

---

## Task 7: Full backend regression + backlog note

**Files:**
- Modify: `docs/superpowers/specs/2026-07-07-backlog-overview.md` (Status #9)

- [ ] **Step 1: Run the whole backend test suite**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL. If anything fails, fix before proceeding — do not mark Phase A done with red tests.

- [ ] **Step 2: Update the backlog status line for #9**

In `docs/superpowers/specs/2026-07-07-backlog-overview.md`, change the #9 table row status from `Backlog` to note Phase A backend done, e.g.:
`| 9 | User-Rechte-Verwaltung (Projekt-/Org-Freischaltung + Rollen) | Full-Stack | Phase A (Backend) fertig — B/C (Frontend) offen |`

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-07-07-backlog-overview.md
git commit -m "docs(backlog): #9 Phase A backend complete"
```

---

## Self-Review (Autor)

**Spec coverage:**
- Rollenmodell (VIEWER/MEMBER/ADMIN, keine Migration) → Tasks 1–5 nutzen bestehendes Enum. ✓
- `canWrite`-Enforcement an allen Schreib-Endpoints (Issues/Comments/Labels/Sprints/Versions/CustomFields) → Task 4. ✓ (BoardService ist read-only — bewusst ausgelassen, siehe Spec.)
- Member-CRUD-API (GET mit Rollen, POST/PATCH/DELETE, Owner-Schutz, 409/404) → Tasks 2 + 3. ✓
- `myRole` im ProjectResponse → Task 5. ✓
- User-Suche (E-Mail/DisplayName, Min-Länge, Autorisierung, nur aktive Nutzer, keine sensiblen Felder) → Task 6. ✓
- H2-Konformität (unbekannte Rolle → 400, keine Interna) → Task 3 Step 6. ✓
- Owner-Schutz (kein Remove/Demote) → Task 2 Tests + Impl. ✓

**Offene Spec-Punkte, die bewusst in Phase B/C fallen:** Frontend MembersPage, `canWrite`-Gating im UI. Kein Backend-Gap.

**Placeholder scan:** keine TBD/TODO; alle Code-Schritte enthalten vollständigen Code. ✓

**Type consistency:** `roleOf`/`canWrite`/`addMember`/`changeMemberRole`/`removeMember`/`canManageAnyProjectMembers`/`searchActive`/`existsByUserIdAndRole`/`existsByOwnerId`/`ProjectMemberResponse.from`/`ProjectResponse.from(p, myRole)` durchgängig identisch referenziert. ✓

**Hinweis (Kotlin):** Der `User`-Konstruktor hat viele Default-Parameter; in den Unit-Tests wird `User(email = …, displayName = …)` verwendet (wie im bestehenden `ProjectServiceTest`). `Project(key, name, description, owner)` und `ProjectMember(project, user, role)` entsprechen den vorhandenen Konstruktoren.
