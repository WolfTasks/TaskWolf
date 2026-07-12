# Organisationen als Oberkategorie — Phase A (Backend) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rechte werden von Organisationen auf ihre Projekte vererbt (Org-Admins → Projekt-Admins, Org-Member → VIEWER), Projekte lassen sich einer Org zuordnen, und Org-Admins verwalten ihre eigene Org — alles serverseitig durchgesetzt.

**Architecture:** Effektive Projektrolle wird zur Lesezeit in `ProjectService.roleOf` als Maximum aus (Owner, expliziter Projektrolle, Org-Erbe) berechnet. Der Org-Zugriff kommt über einen schmalen Port `OrgMembershipLookup` (im `organizations`-Modul implementiert, ins `projects`-Modul injiziert). Da alle Durchsetzung (`canWrite`, `isProjectAdmin`, `@PreAuthorize`, `requireMember`) über `roleOf` läuft, erbt sie die Vererbung automatisch. Org-Verwaltung bekommt ein `OrgSecurity`-Bean analog zu `ProjectSecurity`.

**Tech Stack:** Kotlin, Spring Boot, Spring Security (method-level `@PreAuthorize`), JPA/Hibernate, Postgres. Unit-Tests mit **MockK**, Integrationstests mit JUnit 5 + MockMvc + Testcontainers (`IntegrationTestBase`).

## Global Constraints

- **Keine DB-Migration.** `Project.orgId: UUID?` existiert bereits (nullable) und bleibt nullable. `orgId == null` ⇒ exakt heutiges Verhalten (keine Vererbung).
- **Rollen-Ordnung:** `VIEWER (0) < MEMBER (1) < ADMIN (2)`.
- **Org→Projekt-Mapping:** `OrgRole.OWNER, OrgRole.ADMIN → ProjectRole.ADMIN`; `OrgRole.MEMBER → ProjectRole.VIEWER`.
- **Effektive Rolle:** `max(ownerAdmin, expliziteProjektrolle, orgErbe)`; alle drei fehlen ⇒ `null` (kein Zugriff).
- **Exceptions** (aus `com.taskowolf.core.infrastructure`): `ForbiddenException` → 403, `NotFoundException` → 404, `ConflictException` → 409. Unbekannter Enum im Body → 400 (bestehender GlobalExceptionHandler).
- **Muster einhalten:** Security-Beans als `@Component("name")`, SpEL `@name.method(#arg, authentication)`; `@Transactional(readOnly = true)` auf Endpoints, die beim Serialisieren lazy `user`-Assoziationen lesen (OSIV ist aus).
- **Vererbung basiert auf `OrganizationMember`-Zeilen**, unabhängig vom JWT-„aktive-Org"-Switch. `OrganizationContextFilter`/`switch-org` werden **nicht** angefasst.
- **Testbefehl:** `cd backend && ./gradlew test --tests "<FQCN>"`. Kein `-i`/interaktiver Modus.

## File Structure

**Neu:**
- `backend/src/main/kotlin/com/taskowolf/organizations/application/OrgMembershipLookup.kt` — Port-Interface + `@Component`-Impl (Org-Rolle/Org-Ids eines Users lesen).
- `backend/src/main/kotlin/com/taskowolf/organizations/application/OrgMemberView.kt` — kleine View `(user, role)` für angereicherte Member-Responses.
- `backend/src/main/kotlin/com/taskowolf/organizations/infrastructure/OrgSecurity.kt` — `@Component("orgSecurity")` mit `isOrgAdmin`.
- `backend/src/main/kotlin/com/taskowolf/organizations/api/dto/UpdateOrgMemberRoleRequest.kt` — Body für Rollen-Änderung.
- `backend/src/main/kotlin/com/taskowolf/projects/api/dto/SetProjectOrganizationRequest.kt` — Body `{ orgId: UUID? }`.
- Tests: `OrgMembershipLookupTest.kt`, `ProjectOrgInheritanceTest.kt` (unit), `ProjectOrgAssignmentIntegrationTest.kt`, `ProjectOrgInheritanceIntegrationTest.kt`, `OrganizationMemberIntegrationTest.kt`.

**Geändert:**
- `projects/application/ProjectService.kt` — Port injizieren; `roleOf`/`requireMember`/`isMember`/`isProjectAdmin` inheritance-aware; `findAllForUser` Union; neue `setOrganization`.
- `projects/infrastructure/ProjectRepository.kt` — `findAllByOrgIdIn`.
- `projects/api/dto/ProjectResponse.kt` — `orgId` ergänzen.
- `projects/api/ProjectController.kt` — `PATCH /{key}/organization`.
- `projects/ProjectServiceTest.kt` — Konstruktor um Port-Mock erweitern.
- `organizations/application/OrganizationService.kt` — `UserRepository` injizieren; `isOrgAdmin`; angereicherte Member-Liste; `addMember` Duplikat-Guard; `changeMemberRole`; actor-aware `removeMember`.
- `organizations/api/OrganizationController.kt` — `@PreAuthorize` lockern; Rollen-Endpoint; actor durchreichen; angereicherte Responses.
- `organizations/api/dto/OrganizationResponse.kt` — `OrganizationMemberResponse` auf `{ user, role }`.
- `organizations/OrganizationServiceTest.kt` — Stubs an neue Signaturen anpassen.

---

### Task 1: `OrgMembershipLookup`-Port + Implementierung

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/organizations/application/OrgMembershipLookup.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/organizations/OrgMembershipLookupTest.kt`

**Interfaces:**
- Consumes: `OrganizationMemberRepository` (`findById(OrganizationMemberId)`, `findByIdUserId(userId)`), `OrganizationMemberId(orgId, userId)`, `OrgRole`.
- Produces: `interface OrgMembershipLookup { fun roleOf(orgId: UUID, userId: UUID): OrgRole?; fun orgIdsForUser(userId: UUID): List<UUID> }` und die Bean `OrgMembershipLookupImpl`. Später von `ProjectService` konsumiert.

- [ ] **Step 1: Failing test schreiben**

`OrgMembershipLookupTest.kt`:
```kotlin
package com.taskowolf.organizations

import com.taskowolf.organizations.application.OrgMembershipLookupImpl
import com.taskowolf.organizations.domain.OrgRole
import com.taskowolf.organizations.domain.OrganizationMember
import com.taskowolf.organizations.domain.OrganizationMemberId
import com.taskowolf.organizations.infrastructure.OrganizationMemberRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class OrgMembershipLookupTest {
    private val memberRepo = mockk<OrganizationMemberRepository>()
    private val lookup = OrgMembershipLookupImpl(memberRepo)

    @Test
    fun `roleOf returns the member role`() {
        val orgId = UUID.randomUUID(); val userId = UUID.randomUUID()
        every { memberRepo.findById(OrganizationMemberId(orgId, userId)) } returns
            Optional.of(OrganizationMember(OrganizationMemberId(orgId, userId), OrgRole.ADMIN))
        assertEquals(OrgRole.ADMIN, lookup.roleOf(orgId, userId))
    }

    @Test
    fun `roleOf returns null when not a member`() {
        val orgId = UUID.randomUUID(); val userId = UUID.randomUUID()
        every { memberRepo.findById(OrganizationMemberId(orgId, userId)) } returns Optional.empty()
        assertNull(lookup.roleOf(orgId, userId))
    }

    @Test
    fun `orgIdsForUser maps memberships to org ids`() {
        val userId = UUID.randomUUID(); val o1 = UUID.randomUUID(); val o2 = UUID.randomUUID()
        every { memberRepo.findByIdUserId(userId) } returns listOf(
            OrganizationMember(OrganizationMemberId(o1, userId), OrgRole.MEMBER),
            OrganizationMember(OrganizationMemberId(o2, userId), OrgRole.OWNER),
        )
        assertEquals(listOf(o1, o2), lookup.orgIdsForUser(userId))
    }
}
```

- [ ] **Step 2: Test laufen lassen → FAIL**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.organizations.OrgMembershipLookupTest"`
Expected: FAIL (Kompilierfehler: `OrgMembershipLookupImpl` existiert nicht).

- [ ] **Step 3: Port + Impl implementieren**

`OrgMembershipLookup.kt`:
```kotlin
package com.taskowolf.organizations.application

import com.taskowolf.organizations.domain.OrgRole
import com.taskowolf.organizations.domain.OrganizationMemberId
import com.taskowolf.organizations.infrastructure.OrganizationMemberRepository
import org.springframework.stereotype.Component
import java.util.UUID

interface OrgMembershipLookup {
    /** Die Rolle des Users in der Org, oder null wenn kein Mitglied. */
    fun roleOf(orgId: UUID, userId: UUID): OrgRole?

    /** Alle Org-Ids, in denen der User Mitglied ist. */
    fun orgIdsForUser(userId: UUID): List<UUID>
}

@Component
class OrgMembershipLookupImpl(
    private val memberRepo: OrganizationMemberRepository
) : OrgMembershipLookup {

    override fun roleOf(orgId: UUID, userId: UUID): OrgRole? =
        memberRepo.findById(OrganizationMemberId(orgId, userId)).map { it.role }.orElse(null)

    override fun orgIdsForUser(userId: UUID): List<UUID> =
        memberRepo.findByIdUserId(userId).map { it.id.orgId }
}
```

- [ ] **Step 4: Test laufen lassen → PASS**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.organizations.OrgMembershipLookupTest"`
Expected: PASS (3 Tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/organizations/application/OrgMembershipLookup.kt \
        backend/src/test/kotlin/com/taskowolf/organizations/OrgMembershipLookupTest.kt
git commit -m "feat(org): add OrgMembershipLookup port for cross-module role reads"
```

---

### Task 2: Effektive Rolle — `roleOf`/`requireMember`/`isProjectAdmin` inheritance-aware

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/projects/ProjectServiceTest.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/projects/ProjectOrgInheritanceTest.kt`

**Interfaces:**
- Consumes: `OrgMembershipLookup.roleOf(orgId, userId)` (Task 1), `Project.orgId`, `ProjectRole`, `OrgRole`.
- Produces: unveränderte öffentliche Signaturen `roleOf(project, userId): ProjectRole?`, `canWrite(key, userId): Boolean`, `isProjectAdmin(key, userId): Boolean`, `requireMember(key, userId): Project` — jetzt inheritance-aware. Diese nutzen später Tasks 4/5 sowie alle bestehenden `@PreAuthorize`.

- [ ] **Step 1: Failing unit test (Maximum-Matrix) schreiben**

`ProjectOrgInheritanceTest.kt`:
```kotlin
package com.taskowolf.projects

import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.organizations.application.OrgMembershipLookup
import com.taskowolf.organizations.domain.OrgRole
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.projects.domain.ProjectMember
import com.taskowolf.projects.domain.ProjectRole
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import com.taskowolf.projects.infrastructure.ProjectRepository
import com.taskowolf.workflows.application.WorkflowService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class ProjectOrgInheritanceTest {
    private val projectRepository = mockk<ProjectRepository>()
    private val memberRepository = mockk<ProjectMemberRepository>()
    private val workflowService = mockk<WorkflowService>()
    private val userRepository = mockk<UserRepository>()
    private val orgLookup = mockk<OrgMembershipLookup>()
    private val service = ProjectService(projectRepository, memberRepository, workflowService, userRepository, orgLookup)

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val orgId = UUID.randomUUID()

    /** Projekt in einer Org, Owner ist ein anderer User (damit Owner-ADMIN nicht die Matrix verdeckt). */
    private fun orgProject() =
        Project(key = "ORG", name = "Org P", description = null, owner = owner).apply { this.orgId = this@ProjectOrgInheritanceTest.orgId }

    @Test
    fun `org MEMBER inherits VIEWER when no explicit project role`() {
        val u = UUID.randomUUID()
        val project = orgProject()
        every { memberRepository.findByProjectIdAndUserId(project.id, u) } returns null
        every { orgLookup.roleOf(orgId, u) } returns OrgRole.MEMBER
        assertEquals(ProjectRole.VIEWER, service.roleOf(project, u))
    }

    @Test
    fun `org ADMIN inherits project ADMIN`() {
        val u = UUID.randomUUID()
        val project = orgProject()
        every { memberRepository.findByProjectIdAndUserId(project.id, u) } returns null
        every { orgLookup.roleOf(orgId, u) } returns OrgRole.ADMIN
        assertEquals(ProjectRole.ADMIN, service.roleOf(project, u))
    }

    @Test
    fun `explicit project role raises inherited VIEWER to the max`() {
        val u = UUID.randomUUID()
        val project = orgProject()
        every { memberRepository.findByProjectIdAndUserId(project.id, u) } returns
            ProjectMember(project = project, user = User(email = "m@test.com", displayName = "M"), role = ProjectRole.MEMBER)
        every { orgLookup.roleOf(orgId, u) } returns OrgRole.MEMBER
        assertEquals(ProjectRole.MEMBER, service.roleOf(project, u))
    }

    @Test
    fun `non-member of org and project has no access`() {
        val u = UUID.randomUUID()
        val project = orgProject()
        every { memberRepository.findByProjectIdAndUserId(project.id, u) } returns null
        every { orgLookup.roleOf(orgId, u) } returns null
        assertNull(service.roleOf(project, u))
    }

    @Test
    fun `org lookup is not consulted when project has no org`() {
        val u = UUID.randomUUID()
        val project = Project(key = "NO", name = "No Org", description = null, owner = owner) // orgId = null
        every { memberRepository.findByProjectIdAndUserId(project.id, u) } returns null
        // orgLookup NICHT gestubbt → würde bei Aufruf werfen; Test beweist Kurzschluss
        assertNull(service.roleOf(project, u))
    }
}
```

- [ ] **Step 2: Test laufen lassen → FAIL**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectOrgInheritanceTest"`
Expected: FAIL (Konstruktor `ProjectService` hat kein 5. Argument).

- [ ] **Step 3: `ProjectService` inheritance-aware machen**

In `ProjectService.kt` den Konstruktor um den Port erweitern und die Imports ergänzen:
```kotlin
import com.taskowolf.organizations.application.OrgMembershipLookup
import com.taskowolf.organizations.domain.OrgRole
```
```kotlin
@Service
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val memberRepository: ProjectMemberRepository,
    private val workflowService: WorkflowService,
    private val userRepository: UserRepository,
    private val orgMembershipLookup: OrgMembershipLookup
) {
```

Private Helfer (am Ende der Klasse) ergänzen:
```kotlin
    private fun rank(role: ProjectRole): Int = when (role) {
        ProjectRole.VIEWER -> 0
        ProjectRole.MEMBER -> 1
        ProjectRole.ADMIN -> 2
    }

    private fun maxRole(a: ProjectRole?, b: ProjectRole?): ProjectRole? = when {
        a == null -> b
        b == null -> a
        rank(a) >= rank(b) -> a
        else -> b
    }

    private fun orgRoleToProjectRole(orgRole: OrgRole): ProjectRole = when (orgRole) {
        OrgRole.OWNER, OrgRole.ADMIN -> ProjectRole.ADMIN
        OrgRole.MEMBER -> ProjectRole.VIEWER
    }

    private fun inheritedRole(project: Project, userId: UUID): ProjectRole? {
        val orgId = project.orgId ?: return null
        val orgRole = orgMembershipLookup.roleOf(orgId, userId) ?: return null
        return orgRoleToProjectRole(orgRole)
    }
```

`roleOf` ersetzen:
```kotlin
    @Transactional(readOnly = true)
    fun roleOf(project: Project, userId: UUID): ProjectRole? {
        val explicit = if (project.owner.id == userId) ProjectRole.ADMIN
            else memberRepository.findByProjectIdAndUserId(project.id, userId)?.role
        return maxRole(explicit, inheritedRole(project, userId))
    }
```

`requireMember` und `isMember` auf `roleOf` umstellen (damit geerbte Org-Member Lesezugriff bekommen):
```kotlin
    @Transactional(readOnly = true)
    fun requireMember(projectKey: String, userId: UUID): Project {
        val project = findByKey(projectKey)
        if (roleOf(project, userId) == null) throw ForbiddenException("Not a member of project $projectKey")
        return project
    }

    @Transactional(readOnly = true)
    fun isMember(project: Project, userId: UUID): Boolean = roleOf(project, userId) != null
```

`isProjectAdmin` aus effektiver Rolle ableiten (Duplikat-Logik entfernen):
```kotlin
    @Transactional(readOnly = true)
    fun isProjectAdmin(projectKey: String, userId: UUID): Boolean {
        val project = findByKey(projectKey)
        return roleOf(project, userId) == ProjectRole.ADMIN
    }
```

`canWrite` bleibt unverändert (nutzt `roleOf`).

- [ ] **Step 4: Bestehenden `ProjectServiceTest`-Konstruktor anpassen**

In `ProjectServiceTest.kt` den Service um den Port-Mock erweitern (Projekte dort haben `orgId=null` ⇒ kein Stub nötig):
```kotlin
    private val orgLookup = mockk<com.taskowolf.organizations.application.OrgMembershipLookup>()
    private val service = ProjectService(projectRepository, memberRepository, workflowService, userRepository, orgLookup)
```

- [ ] **Step 5: Tests laufen lassen → PASS**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectOrgInheritanceTest" --tests "com.taskowolf.projects.ProjectServiceTest"`
Expected: PASS (neue Matrix + bestehende ProjectService-Tests grün).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt \
        backend/src/test/kotlin/com/taskowolf/projects/ProjectServiceTest.kt \
        backend/src/test/kotlin/com/taskowolf/projects/ProjectOrgInheritanceTest.kt
git commit -m "feat(projects): compute effective project role with org inheritance (max of owner/explicit/org)"
```

---

### Task 3: `findAllForUser` liefert auch geerbte Org-Projekte

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/infrastructure/ProjectRepository.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/projects/ProjectOrgInheritanceIntegrationTest.kt` (Datei hier anlegen; Task 5 ergänzt weitere Tests darin)

**Interfaces:**
- Consumes: `OrgMembershipLookup.orgIdsForUser(userId)` (Task 1), neue `ProjectRepository.findAllByOrgIdIn(orgIds)`.
- Produces: `findAllForUser(userId): List<Project>` enthält jetzt Direkt- **und** Org-Projekte (dedupliziert).

- [ ] **Step 1: Failing integration test schreiben**

`ProjectOrgInheritanceIntegrationTest.kt`:
```kotlin
package com.taskowolf.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.infrastructure.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ProjectOrgInheritanceIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository

    private fun register(email: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"U","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("accessToken").asText()
    }

    private fun makeSystemAdmin(email: String) {
        val u = userRepository.findByEmail(email)!!; u.systemRole = SystemRole.ADMIN; userRepository.save(u)
    }

    private fun myId(token: String): String {
        val res = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $token")).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("id").asText()
    }

    private fun createOrg(adminToken: String, slug: String): String {
        val res = mockMvc.perform(
            post("/api/v1/organizations").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"name":"$slug","slug":"$slug"}""")
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("id").asText()
    }

    private fun createProject(token: String, key: String) {
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"key":"$key","name":"$key"}""")
        ).andExpect(status().isCreated)
    }

    private fun assignProjectToOrg(adminToken: String, key: String, orgId: String) {
        mockMvc.perform(
            patch("/api/v1/projects/$key/organization").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"orgId":"$orgId"}""")
        ).andExpect(status().isOk)
    }

    private fun addOrgMember(adminToken: String, orgId: String, userId: String, role: String) {
        mockMvc.perform(
            post("/api/v1/organizations/$orgId/members").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"userId":"$userId","role":"$role"}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `org member sees inherited org projects in their project list`() {
        val adminToken = register("inh-admin@test.com"); makeSystemAdmin("inh-admin@test.com")
        val ownerToken = register("inh-owner@test.com")
        val memberToken = register("inh-member@test.com")
        val memberId = myId(memberToken)

        val orgId = createOrg(adminToken, "inh-org")
        createProject(ownerToken, "INHL")
        assignProjectToOrg(adminToken, "INHL", orgId)
        addOrgMember(adminToken, orgId, memberId, "MEMBER")

        // Der Org-Member war nie explizit im Projekt, sieht es aber via Org-Erbe in der Liste
        // (GET /projects füllt myRole NICHT — nur der Detail-Endpoint tut das)
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer $memberToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.key=='INHL')].key").value("INHL"))
        // Detail-Endpoint zeigt die geerbte effektive Rolle
        mockMvc.perform(get("/api/v1/projects/INHL").header("Authorization", "Bearer $memberToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.myRole").value("VIEWER"))
    }
}
```

- [ ] **Step 2: Test laufen lassen → FAIL**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectOrgInheritanceIntegrationTest"`
Expected: FAIL — der `PATCH /organization`-Endpoint (Task 4) und der Union-Query fehlen noch. (Dieser Test wird in Task 4 grün; hier zunächst nur den **Union-Query** bauen. Der Test bleibt bis Task 4 rot — das ist ok, weil er den Assignment-Endpoint mit-testet. Wer strikt pro Task grün sein will, kann diesen Test-Body erst in Task 5 aktivieren; hier reicht der Repository/Service-Unit-Beleg unten.)

> Hinweis für den Executor: Damit **Task 3 eigenständig grün** ist, zusätzlich den folgenden fokussierten Service-Beleg als Zwischenschritt nutzen (der Integrationstest oben wird mit Task 4 grün):

- [ ] **Step 3: Repository-Union implementieren**

In `ProjectRepository.kt` ergänzen:
```kotlin
    fun findAllByOrgIdIn(orgIds: Collection<UUID>): List<Project>
```

In `ProjectService.kt` `findAllForUser` ersetzen:
```kotlin
    @Transactional(readOnly = true)
    fun findAllForUser(userId: UUID): List<Project> {
        val direct = projectRepository.findAllByMemberOrOwner(userId)
        val orgIds = orgMembershipLookup.orgIdsForUser(userId)
        val viaOrg = if (orgIds.isEmpty()) emptyList() else projectRepository.findAllByOrgIdIn(orgIds)
        return (direct + viaOrg).distinctBy { it.id }
    }
```

- [ ] **Step 4: Fokussierten Unit-Test für die Union ergänzen**

In `ProjectOrgInheritanceTest.kt` (aus Task 2) ergänzen:
```kotlin
    @Test
    fun `findAllForUser unions direct and org projects without duplicates`() {
        val userId = UUID.randomUUID()
        val shared = orgProject() // gehört zu orgId
        val direct = Project(key = "DIR", name = "Direct", description = null, owner = owner)
        every { projectRepository.findAllByMemberOrOwner(userId) } returns listOf(direct, shared)
        every { orgLookup.orgIdsForUser(userId) } returns listOf(orgId)
        every { projectRepository.findAllByOrgIdIn(listOf(orgId)) } returns listOf(shared)
        val result = service.findAllForUser(userId)
        assertEquals(2, result.size) // shared nur einmal
    }
```
(Import `com.taskowolf.projects.domain.Project` ist bereits vorhanden.)

- [ ] **Step 5: Tests laufen lassen → PASS (Unit)**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectOrgInheritanceTest"`
Expected: PASS. Der Integrationstest `ProjectOrgInheritanceIntegrationTest` wird mit Task 4 grün.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/projects/infrastructure/ProjectRepository.kt \
        backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt \
        backend/src/test/kotlin/com/taskowolf/projects/ProjectOrgInheritanceTest.kt \
        backend/src/test/kotlin/com/taskowolf/projects/ProjectOrgInheritanceIntegrationTest.kt
git commit -m "feat(projects): include inherited org projects in findAllForUser"
```

---

### Task 4: Projekt↔Org-Zuordnung (`PATCH /projects/{key}/organization`)

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/projects/api/dto/SetProjectOrganizationRequest.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/api/dto/ProjectResponse.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/api/ProjectController.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/projects/ProjectOrgAssignmentIntegrationTest.kt`

**Interfaces:**
- Consumes: `roleOf`, `findByKey`, `OrgMembershipLookup.roleOf`, `User.systemRole`, `SystemRole.ADMIN`.
- Produces: `ProjectService.setOrganization(projectKey: String, actor: User, orgId: UUID?): Project`; Endpoint `PATCH /api/v1/projects/{key}/organization` mit Body `{ orgId: UUID? }`; `ProjectResponse` hat jetzt Feld `orgId: UUID?`.

- [ ] **Step 1: Failing integration test schreiben**

`ProjectOrgAssignmentIntegrationTest.kt`:
```kotlin
package com.taskowolf.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.infrastructure.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ProjectOrgAssignmentIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository

    private fun register(email: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"U","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("accessToken").asText()
    }
    private fun makeSystemAdmin(email: String) {
        val u = userRepository.findByEmail(email)!!; u.systemRole = SystemRole.ADMIN; userRepository.save(u)
    }
    private fun myId(token: String): String {
        val res = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $token")).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("id").asText()
    }
    private fun createOrg(adminToken: String, slug: String): String {
        val res = mockMvc.perform(
            post("/api/v1/organizations").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"name":"$slug","slug":"$slug"}""")
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("id").asText()
    }
    private fun createProject(token: String, key: String) {
        mockMvc.perform(
            post("/api/v1/projects").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"key":"$key","name":"$key"}""")
        ).andExpect(status().isCreated)
    }
    private fun addOrgMember(adminToken: String, orgId: String, userId: String, role: String) {
        mockMvc.perform(
            post("/api/v1/organizations/$orgId/members").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"userId":"$userId","role":"$role"}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `system admin can assign and unassign a project org`() {
        val adminToken = register("as-admin@test.com"); makeSystemAdmin("as-admin@test.com")
        val ownerToken = register("as-owner@test.com")
        val orgId = createOrg(adminToken, "as-org")
        createProject(ownerToken, "ASG")

        mockMvc.perform(
            patch("/api/v1/projects/ASG/organization").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"orgId":"$orgId"}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.orgId").value(orgId))

        // Jackson serialisiert null-Felder standardmäßig als "orgId":null (kein NON_NULL konfiguriert)
        mockMvc.perform(
            patch("/api/v1/projects/ASG/organization").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"orgId":null}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.orgId").value(org.hamcrest.Matchers.nullValue()))
    }

    @Test
    fun `project admin who is also org admin can assign`() {
        val adminToken = register("pa-admin@test.com"); makeSystemAdmin("pa-admin@test.com")
        val ownerToken = register("pa-owner@test.com")
        val ownerId = myId(ownerToken)
        val orgId = createOrg(adminToken, "pa-org")
        createProject(ownerToken, "PAG")
        addOrgMember(adminToken, orgId, ownerId, "ADMIN") // Projekt-Owner ist auch Org-ADMIN

        mockMvc.perform(
            patch("/api/v1/projects/PAG/organization").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"orgId":"$orgId"}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.orgId").value(orgId))
    }

    @Test
    fun `project admin who is NOT org admin gets 403`() {
        val adminToken = register("na-admin@test.com"); makeSystemAdmin("na-admin@test.com")
        val ownerToken = register("na-owner@test.com")
        val orgId = createOrg(adminToken, "na-org")
        createProject(ownerToken, "NAG") // owner ist NICHT Mitglied der Org

        mockMvc.perform(
            patch("/api/v1/projects/NAG/organization").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"orgId":"$orgId"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `org admin who is NOT project admin gets 403`() {
        val adminToken = register("op-admin@test.com"); makeSystemAdmin("op-admin@test.com")
        val ownerToken = register("op-owner@test.com")
        val orgAdminToken = register("op-orgadmin@test.com")
        val orgAdminId = myId(orgAdminToken)
        val orgId = createOrg(adminToken, "op-org")
        createProject(ownerToken, "OPG")
        addOrgMember(adminToken, orgId, orgAdminId, "ADMIN") // Org-ADMIN, aber kein Projekt-Mitglied

        mockMvc.perform(
            patch("/api/v1/projects/OPG/organization").header("Authorization", "Bearer $orgAdminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"orgId":"$orgId"}""")
        ).andExpect(status().isForbidden)
    }
}
```

- [ ] **Step 2: Test laufen lassen → FAIL**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectOrgAssignmentIntegrationTest"`
Expected: FAIL (Endpoint/DTO fehlen).

- [ ] **Step 3: DTO + `ProjectResponse.orgId` + Service + Controller implementieren**

`SetProjectOrganizationRequest.kt`:
```kotlin
package com.taskowolf.projects.api.dto

import java.util.UUID

data class SetProjectOrganizationRequest(val orgId: UUID?)
```

`ProjectResponse.kt` um `orgId` erweitern (vor `myRole` einfügen, damit bestehende Aufrufer gültig bleiben):
```kotlin
data class ProjectResponse(
    val id: UUID,
    val key: String,
    val name: String,
    val description: String?,
    val ownerId: UUID,
    val archived: Boolean,
    val orgId: UUID?,
    val myRole: ProjectRole? = null
) {
    companion object {
        fun from(p: Project, myRole: ProjectRole? = null) =
            ProjectResponse(p.id, p.key, p.name, p.description, p.owner.id, p.archived, p.orgId, myRole)
    }
}
```

`ProjectService.kt` — `setOrganization` ergänzen (imports `SystemRole`, `User`, `SetProjectOrganizationRequest` sind größtenteils vorhanden; `import com.taskowolf.auth.domain.SystemRole` existiert bereits):
```kotlin
    @Transactional
    fun setOrganization(projectKey: String, actor: User, orgId: UUID?): Project {
        val project = findByKey(projectKey)
        val isSystemAdmin = actor.systemRole == SystemRole.ADMIN
        if (!isSystemAdmin && roleOf(project, actor.id) != ProjectRole.ADMIN)
            throw ForbiddenException("Project admin role required")
        if (orgId != null && !isSystemAdmin) {
            val orgRole = orgMembershipLookup.roleOf(orgId, actor.id)
            if (orgRole != OrgRole.OWNER && orgRole != OrgRole.ADMIN)
                throw ForbiddenException("Must be an admin of the target organization")
        }
        project.orgId = orgId
        return projectRepository.save(project)
    }
```

`ProjectController.kt` — Endpoint + Imports (`SetProjectOrganizationRequest`) ergänzen:
```kotlin
    @PatchMapping("/{key}/organization")
    fun setOrganization(
        @PathVariable key: String,
        @RequestBody request: SetProjectOrganizationRequest,
        @AuthenticationPrincipal user: User
    ): ProjectResponse {
        val project = projectService.setOrganization(key, user, request.orgId)
        return ProjectResponse.from(project, projectService.roleOf(project, user.id))
    }
```

- [ ] **Step 4: Tests laufen lassen → PASS (inkl. Task-3-Integrationstest)**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectOrgAssignmentIntegrationTest" --tests "com.taskowolf.projects.ProjectOrgInheritanceIntegrationTest"`
Expected: PASS (Assignment-Autorisierung + der Task-3-Listen-Test werden grün).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/projects/api/dto/SetProjectOrganizationRequest.kt \
        backend/src/main/kotlin/com/taskowolf/projects/api/dto/ProjectResponse.kt \
        backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt \
        backend/src/main/kotlin/com/taskowolf/projects/api/ProjectController.kt \
        backend/src/test/kotlin/com/taskowolf/projects/ProjectOrgAssignmentIntegrationTest.kt
git commit -m "feat(projects): add PATCH /projects/{key}/organization with dual-role authorization"
```

---

### Task 5: End-to-End-Vererbung — geerbter VIEWER liest, schreibt nicht; geerbter ADMIN verwaltet

**Files:**
- Modify: `backend/src/test/kotlin/com/taskowolf/projects/ProjectOrgInheritanceIntegrationTest.kt`

**Interfaces:**
- Consumes: alle Endpoints aus Tasks 2–4 plus `GET/POST /api/v1/projects/{key}/labels` (canWrite-gegatet) und `POST /api/v1/projects/{key}/members` (admin-gegatet).
- Produces: keine Produktionsartefakte — beweist die komponierte Semantik.

- [ ] **Step 1: Failing E2E-Tests ergänzen**

In `ProjectOrgInheritanceIntegrationTest.kt` zwei Tests ergänzen (Helfer aus Task 3 sind vorhanden):
```kotlin
    @Test
    fun `inherited VIEWER can read but not write`() {
        val adminToken = register("v-admin@test.com"); makeSystemAdmin("v-admin@test.com")
        val ownerToken = register("v-owner@test.com")
        val viewerToken = register("v-viewer@test.com")
        val viewerId = myId(viewerToken)
        val orgId = createOrg(adminToken, "v-org")
        createProject(ownerToken, "VRO")
        assignProjectToOrg(adminToken, "VRO", orgId)
        addOrgMember(adminToken, orgId, viewerId, "MEMBER") // → geerbter VIEWER

        // lesen ok
        mockMvc.perform(get("/api/v1/projects/VRO/labels").header("Authorization", "Bearer $viewerToken"))
            .andExpect(status().isOk)
        // schreiben (Label anlegen) → 403
        mockMvc.perform(
            post("/api/v1/projects/VRO/labels").header("Authorization", "Bearer $viewerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"name":"bug","color":"#ff0000"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `inherited org ADMIN can perform project admin actions`() {
        val adminToken = register("a-admin@test.com"); makeSystemAdmin("a-admin@test.com")
        val ownerToken = register("a-owner@test.com")
        val orgAdminToken = register("a-orgadmin@test.com")
        val orgAdminId = myId(orgAdminToken)
        val thirdToken = register("a-third@test.com")
        val thirdId = myId(thirdToken)
        val orgId = createOrg(adminToken, "a-org")
        createProject(ownerToken, "ARO")
        assignProjectToOrg(adminToken, "ARO", orgId)
        addOrgMember(adminToken, orgId, orgAdminId, "ADMIN") // → geerbter Projekt-ADMIN

        // geerbter ADMIN darf schreiben (Label) …
        mockMvc.perform(
            post("/api/v1/projects/ARO/labels").header("Authorization", "Bearer $orgAdminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"name":"feat","color":"#00ff00"}""")
        ).andExpect(status().isCreated)
        // … und Admin-Aktionen (Projekt-Member hinzufügen)
        mockMvc.perform(
            post("/api/v1/projects/ARO/members").header("Authorization", "Bearer $orgAdminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"userId":"$thirdId","role":"VIEWER"}""")
        ).andExpect(status().isCreated)
    }
```

- [ ] **Step 2: Tests laufen lassen → PASS**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectOrgInheritanceIntegrationTest"`
Expected: PASS (alle 3 Tests der Klasse: Listen-Sichtbarkeit + VIEWER-read-only + ADMIN-Verwaltung).

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/taskowolf/projects/ProjectOrgInheritanceIntegrationTest.kt
git commit -m "test(projects): end-to-end org role inheritance (read-only VIEWER, managing ADMIN)"
```

---

### Task 6: `OrgSecurity` + Org-Verwaltung für Org-Admins öffnen

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/organizations/infrastructure/OrgSecurity.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/organizations/application/OrganizationService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/organizations/api/OrganizationController.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/organizations/OrganizationMemberIntegrationTest.kt`

**Interfaces:**
- Consumes: `OrganizationMemberRepository.findById`, `OrgRole`, `User.systemRole`, `SystemRole.ADMIN`.
- Produces: `OrganizationService.isOrgAdmin(orgId: UUID, user: User): Boolean`; Bean `@Component("orgSecurity")` mit `isOrgAdmin(orgId: UUID, authentication: Authentication): Boolean`; `addMember`/`removeMember`-Endpoints jetzt via `@orgSecurity.isOrgAdmin(#id, authentication)` statt `hasRole('ADMIN')`.

- [ ] **Step 1: Failing integration test schreiben**

`OrganizationMemberIntegrationTest.kt`:
```kotlin
package com.taskowolf.organizations

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.infrastructure.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class OrganizationMemberIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository

    private fun register(email: String): String {
        val res = mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"U","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("accessToken").asText()
    }
    private fun makeSystemAdmin(email: String) {
        val u = userRepository.findByEmail(email)!!; u.systemRole = SystemRole.ADMIN; userRepository.save(u)
    }
    private fun myId(token: String): String {
        val res = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $token")).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("id").asText()
    }
    fun createOrg(adminToken: String, slug: String): String {
        val res = mockMvc.perform(
            post("/api/v1/organizations").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"name":"$slug","slug":"$slug"}""")
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("id").asText()
    }
    fun addMember(actorToken: String, orgId: String, userId: String, role: String) =
        mockMvc.perform(
            post("/api/v1/organizations/$orgId/members").header("Authorization", "Bearer $actorToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"userId":"$userId","role":"$role"}""")
        )

    @Test
    fun `non-system org ADMIN can add members`() {
        val sysToken = register("om-sys@test.com"); makeSystemAdmin("om-sys@test.com")
        val orgAdminToken = register("om-orgadmin@test.com")
        val orgAdminId = myId(orgAdminToken)
        val targetToken = register("om-target@test.com")
        val targetId = myId(targetToken)
        val orgId = createOrg(sysToken, "om-org")
        addMember(sysToken, orgId, orgAdminId, "ADMIN").andExpect(status().isCreated)

        // Org-ADMIN (kein System-Admin) darf jetzt selbst Member hinzufügen
        addMember(orgAdminToken, orgId, targetId, "MEMBER").andExpect(status().isCreated)
    }

    @Test
    fun `plain org MEMBER cannot add members`() {
        val sysToken = register("om-sys2@test.com"); makeSystemAdmin("om-sys2@test.com")
        val memberToken = register("om-plain@test.com")
        val memberId = myId(memberToken)
        val targetToken = register("om-target2@test.com")
        val targetId = myId(targetToken)
        val orgId = createOrg(sysToken, "om-org2")
        addMember(sysToken, orgId, memberId, "MEMBER").andExpect(status().isCreated)

        addMember(memberToken, orgId, targetId, "MEMBER").andExpect(status().isForbidden)
    }
}
```

- [ ] **Step 2: Test laufen lassen → FAIL**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.organizations.OrganizationMemberIntegrationTest"`
Expected: FAIL — `non-system org ADMIN can add members` erhält 403 (Endpoint noch `hasRole('ADMIN')`).

- [ ] **Step 3: `isOrgAdmin` + `OrgSecurity` + gelockerte Gates implementieren**

In `OrganizationService.kt` Imports ergänzen und Methode hinzufügen:
```kotlin
import com.taskowolf.organizations.domain.OrganizationMemberId
```
```kotlin
    @Transactional(readOnly = true)
    fun isOrgAdmin(orgId: UUID, user: User): Boolean {
        if (user.systemRole == SystemRole.ADMIN) return true
        val role = memberRepo.findById(OrganizationMemberId(orgId, user.id)).map { it.role }.orElse(null)
        return role == OrgRole.OWNER || role == OrgRole.ADMIN
    }
```

`OrgSecurity.kt`:
```kotlin
package com.taskowolf.organizations.infrastructure

import com.taskowolf.auth.domain.User
import com.taskowolf.organizations.application.OrganizationService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.util.UUID

@Component("orgSecurity")
class OrgSecurity(private val orgService: OrganizationService) {
    fun isOrgAdmin(orgId: UUID, authentication: Authentication): Boolean {
        val user = authentication.principal as? User ?: return false
        return try { orgService.isOrgAdmin(orgId, user) } catch (_: Exception) { false }
    }
}
```

In `OrganizationController.kt` die `@PreAuthorize` auf `addMember` und `removeMember` ersetzen:
```kotlin
    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@orgSecurity.isOrgAdmin(#id, authentication)")
    fun addMember(
        @PathVariable id: UUID,
        @RequestBody req: AddMemberRequest
    ) = OrganizationMemberResponse.from(orgService.addMember(id, req.userId, req.role))
```
```kotlin
    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@orgSecurity.isOrgAdmin(#id, authentication)")
    fun removeMember(@PathVariable id: UUID, @PathVariable userId: UUID) {
        orgService.removeMember(id, userId)
    }
```
(`create` und `listAll` bleiben `@PreAuthorize("hasRole('ADMIN')")`.)

- [ ] **Step 4: Test laufen lassen → PASS**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.organizations.OrganizationMemberIntegrationTest"`
Expected: PASS (beide Tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/organizations/infrastructure/OrgSecurity.kt \
        backend/src/main/kotlin/com/taskowolf/organizations/application/OrganizationService.kt \
        backend/src/main/kotlin/com/taskowolf/organizations/api/OrganizationController.kt \
        backend/src/test/kotlin/com/taskowolf/organizations/OrganizationMemberIntegrationTest.kt
git commit -m "feat(org): allow org OWNER/ADMIN to manage their org members via OrgSecurity"
```

---

### Task 7: Angereicherte Member-Response `{user, role}` + Add-Duplikat-Guard

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/organizations/application/OrgMemberView.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/organizations/application/OrganizationService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/organizations/api/dto/OrganizationResponse.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/organizations/api/OrganizationController.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/organizations/OrganizationServiceTest.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/organizations/OrganizationMemberIntegrationTest.kt`

**Interfaces:**
- Consumes: `UserRepository.findAllById`, `UserResponse.from(user)`, `ConflictException`.
- Produces: `data class OrgMemberView(val user: User, val role: OrgRole)`; `OrganizationService.listMembersWithUsers(orgId): List<OrgMemberView>`; `addMember(orgId, userId, role): OrgMemberView` (nun Duplikat → 409); `OrganizationMemberResponse(user: UserResponse, role: OrgRole)` mit `from(view: OrgMemberView)`.

- [ ] **Step 1: Failing integration test ergänzen**

In `OrganizationMemberIntegrationTest.kt` ergänzen:
```kotlin
    @Test
    fun `member list returns user displayName and duplicate add is 409`() {
        val sysToken = register("en-sys@test.com"); makeSystemAdmin("en-sys@test.com")
        val targetToken = register("en-target@test.com")
        val targetId = myId(targetToken)
        val orgId = createOrg(sysToken, "en-org")
        addMember(sysToken, orgId, targetId, "MEMBER").andExpect(status().isCreated)

        // Liste enthält den User mit displayName (nicht nur eine rohe UUID)
        mockMvc.perform(get("/api/v1/organizations/$orgId/members").header("Authorization", "Bearer $sysToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.user.id=='$targetId')].role").value("MEMBER"))
            .andExpect(jsonPath("$[?(@.user.id=='$targetId')].user.displayName").value("U"))

        // Doppeltes Hinzufügen → 409
        addMember(sysToken, orgId, targetId, "MEMBER").andExpect(status().isConflict)
    }
```

- [ ] **Step 2: Test laufen lassen → FAIL**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.organizations.OrganizationMemberIntegrationTest"`
Expected: FAIL (Response hat noch `userId` statt `user`; Duplikat gibt aktuell 201/überschreibt).

- [ ] **Step 3: View + Service + DTO + Controller implementieren**

`OrgMemberView.kt`:
```kotlin
package com.taskowolf.organizations.application

import com.taskowolf.auth.domain.User
import com.taskowolf.organizations.domain.OrgRole

data class OrgMemberView(val user: User, val role: OrgRole)
```

`OrganizationService.kt` — `UserRepository` injizieren, Imports ergänzen, Methoden anpassen:
```kotlin
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
```
Konstruktor:
```kotlin
class OrganizationService(
    private val orgRepo: OrganizationRepository,
    private val memberRepo: OrganizationMemberRepository,
    private val userRepository: UserRepository
) {
```
`addMember` ersetzen (Duplikat-Guard + View-Return):
```kotlin
    @Transactional
    fun addMember(orgId: UUID, userId: UUID, role: OrgRole): OrgMemberView {
        if (memberRepo.findById(OrganizationMemberId(orgId, userId)).isPresent)
            throw ConflictException("User is already a member of this organization")
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        memberRepo.save(OrganizationMember(OrganizationMemberId(orgId, userId), role))
        return OrgMemberView(user, role)
    }
```
`listMembers` durch angereicherte Variante ersetzen:
```kotlin
    @Transactional(readOnly = true)
    fun listMembersWithUsers(orgId: UUID): List<OrgMemberView> {
        val members = memberRepo.findByIdOrgId(orgId)
        val users = userRepository.findAllById(members.map { it.id.userId }).associateBy { it.id }
        return members.mapNotNull { m -> users[m.id.userId]?.let { OrgMemberView(it, m.role) } }
    }
```
(Die alte `listMembers(orgId)` kann entfernt werden — einziger Aufrufer ist der Controller, der jetzt `listMembersWithUsers` nutzt.)

`OrganizationResponse.kt` — `OrganizationMemberResponse` ersetzen:
```kotlin
import com.taskowolf.auth.api.dto.UserResponse
import com.taskowolf.organizations.application.OrgMemberView
```
```kotlin
data class OrganizationMemberResponse(
    val user: UserResponse,
    val role: OrgRole
) {
    companion object {
        fun from(view: OrgMemberView) = OrganizationMemberResponse(UserResponse.from(view.user), view.role)
    }
}
```
(Das alte `from(m: OrganizationMember)` entfällt.)

`OrganizationController.kt` — `listMembers` und `addMember` auf View-Basis:
```kotlin
    @GetMapping("/{id}/members")
    @Transactional(readOnly = true)
    fun listMembers(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ): List<OrganizationMemberResponse> {
        orgService.requireMembershipOrAdmin(id, user)
        return orgService.listMembersWithUsers(id).map { OrganizationMemberResponse.from(it) }
    }
```
```kotlin
    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@orgSecurity.isOrgAdmin(#id, authentication)")
    fun addMember(
        @PathVariable id: UUID,
        @RequestBody req: AddMemberRequest
    ) = OrganizationMemberResponse.from(orgService.addMember(id, req.userId, req.role))
```
(Import `org.springframework.transaction.annotation.Transactional` im Controller ergänzen.)

- [ ] **Step 4: Bestehende Unit-Tests anpassen**

In `OrganizationServiceTest.kt`: Der Service-Konstruktor bekommt einen dritten Mock, und die `addMember`/`listOrgsForUser`-Tests brauchen angepasste Stubs.
```kotlin
    private val userRepository = mockk<com.taskowolf.auth.infrastructure.UserRepository>()
    private val service = OrganizationService(orgRepo, memberRepo, userRepository)
```
`addMember saves with correct role` ersetzen:
```kotlin
    @Test
    fun `addMember returns a view with the correct role`() {
        val orgId = UUID.randomUUID()
        val user = com.taskowolf.auth.domain.User(email = "a@test.com", displayName = "A")
        every { memberRepo.findById(OrganizationMemberId(orgId, user.id)) } returns java.util.Optional.empty()
        every { userRepository.findById(user.id) } returns java.util.Optional.of(user)
        every { memberRepo.save(any()) } returnsArgument 0
        val result = service.addMember(orgId, user.id, OrgRole.ADMIN)
        assertEquals(OrgRole.ADMIN, result.role)
        assertEquals(user.id, result.user.id)
    }
```

- [ ] **Step 5: Tests laufen lassen → PASS**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.organizations.OrganizationServiceTest" --tests "com.taskowolf.organizations.OrganizationMemberIntegrationTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/organizations/application/OrgMemberView.kt \
        backend/src/main/kotlin/com/taskowolf/organizations/application/OrganizationService.kt \
        backend/src/main/kotlin/com/taskowolf/organizations/api/dto/OrganizationResponse.kt \
        backend/src/main/kotlin/com/taskowolf/organizations/api/OrganizationController.kt \
        backend/src/test/kotlin/com/taskowolf/organizations/OrganizationServiceTest.kt \
        backend/src/test/kotlin/com/taskowolf/organizations/OrganizationMemberIntegrationTest.kt
git commit -m "feat(org): enrich member responses with user info; reject duplicate adds"
```

---

### Task 8: Org-Rollen-Änderung mit Self-/Owner-/Last-Owner-Schutz

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/organizations/api/dto/UpdateOrgMemberRoleRequest.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/organizations/application/OrganizationService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/organizations/api/OrganizationController.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/organizations/OrganizationServiceTest.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/organizations/OrganizationMemberIntegrationTest.kt`

**Interfaces:**
- Consumes: `ForbiddenException`, `NotFoundException`, `OrgMemberView` (Task 7), `User.systemRole`.
- Produces: `OrganizationService.changeMemberRole(orgId: UUID, actor: User, targetUserId: UUID, newRole: OrgRole): OrgMemberView`; Endpoint `PATCH /api/v1/organizations/{id}/members/{userId}` mit Body `{ role: OrgRole }`; private `isLastOwner(orgId): Boolean`.

- [ ] **Step 1: Failing unit tests schreiben**

In `OrganizationServiceTest.kt` ergänzen (Helper `User` importieren, falls nicht vorhanden):
```kotlin
    @Test
    fun `changeMemberRole forbids changing your own role`() {
        val orgId = UUID.randomUUID()
        val actor = com.taskowolf.auth.domain.User(email = "self@test.com", displayName = "S")
        org.junit.jupiter.api.assertThrows<com.taskowolf.core.infrastructure.ForbiddenException> {
            service.changeMemberRole(orgId, actor, actor.id, OrgRole.ADMIN)
        }
    }

    @Test
    fun `changeMemberRole forbids demoting the last owner`() {
        val orgId = UUID.randomUUID()
        val actor = com.taskowolf.auth.domain.User(email = "sys@test.com", displayName = "Sys")
            .apply { systemRole = com.taskowolf.auth.domain.SystemRole.ADMIN }
        val target = com.taskowolf.auth.domain.User(email = "owner@test.com", displayName = "O")
        every { memberRepo.findById(OrganizationMemberId(orgId, target.id)) } returns
            java.util.Optional.of(OrganizationMember(OrganizationMemberId(orgId, target.id), OrgRole.OWNER))
        every { memberRepo.findByIdOrgId(orgId) } returns
            listOf(OrganizationMember(OrganizationMemberId(orgId, target.id), OrgRole.OWNER))
        org.junit.jupiter.api.assertThrows<com.taskowolf.core.infrastructure.ForbiddenException> {
            service.changeMemberRole(orgId, actor, target.id, OrgRole.MEMBER)
        }
    }

    @Test
    fun `changeMemberRole updates a normal member`() {
        val orgId = UUID.randomUUID()
        val actor = com.taskowolf.auth.domain.User(email = "sys2@test.com", displayName = "Sys")
            .apply { systemRole = com.taskowolf.auth.domain.SystemRole.ADMIN }
        val target = com.taskowolf.auth.domain.User(email = "m@test.com", displayName = "M")
        every { memberRepo.findById(OrganizationMemberId(orgId, target.id)) } returns
            java.util.Optional.of(OrganizationMember(OrganizationMemberId(orgId, target.id), OrgRole.MEMBER))
        every { userRepository.findById(target.id) } returns java.util.Optional.of(target)
        every { memberRepo.save(any()) } returnsArgument 0
        val result = service.changeMemberRole(orgId, actor, target.id, OrgRole.ADMIN)
        assertEquals(OrgRole.ADMIN, result.role)
    }
```

- [ ] **Step 2: Tests laufen lassen → FAIL**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.organizations.OrganizationServiceTest"`
Expected: FAIL (`changeMemberRole` existiert nicht).

- [ ] **Step 3: `changeMemberRole` + Endpoint implementieren**

`UpdateOrgMemberRoleRequest.kt`:
```kotlin
package com.taskowolf.organizations.api.dto

import com.taskowolf.organizations.domain.OrgRole
import jakarta.validation.constraints.NotNull

data class UpdateOrgMemberRoleRequest(@field:NotNull val role: OrgRole)
```

`OrganizationService.kt` ergänzen:
```kotlin
    @Transactional
    fun changeMemberRole(orgId: UUID, actor: User, targetUserId: UUID, newRole: OrgRole): OrgMemberView {
        if (actor.id == targetUserId) throw ForbiddenException("You cannot change your own role")
        val member = memberRepo.findById(OrganizationMemberId(orgId, targetUserId))
            .orElseThrow { NotFoundException("Member not found") }
        val isSystemAdmin = actor.systemRole == SystemRole.ADMIN
        if (member.role == OrgRole.OWNER && !isSystemAdmin)
            throw ForbiddenException("Cannot change an owner's role")
        if (member.role == OrgRole.OWNER && newRole != OrgRole.OWNER && isLastOwner(orgId))
            throw ForbiddenException("Cannot demote the last owner")
        member.role = newRole
        memberRepo.save(member)
        val user = userRepository.findById(targetUserId).orElseThrow { NotFoundException("User not found") }
        return OrgMemberView(user, newRole)
    }

    private fun isLastOwner(orgId: UUID): Boolean =
        memberRepo.findByIdOrgId(orgId).count { it.role == OrgRole.OWNER } <= 1
```
Import ergänzen: `import com.taskowolf.core.infrastructure.ForbiddenException` (falls nicht vorhanden).

`OrganizationController.kt` — Endpoint ergänzen (Imports `UpdateOrgMemberRoleRequest`, `jakarta.validation.Valid`):
```kotlin
    @PatchMapping("/{id}/members/{userId}")
    @PreAuthorize("@orgSecurity.isOrgAdmin(#id, authentication)")
    @Transactional
    fun changeMemberRole(
        @PathVariable id: UUID,
        @PathVariable userId: UUID,
        @Valid @RequestBody req: UpdateOrgMemberRoleRequest,
        @AuthenticationPrincipal actor: User
    ) = OrganizationMemberResponse.from(orgService.changeMemberRole(id, actor, userId, req.role))
```

- [ ] **Step 4: Integration test ergänzen**

In `OrganizationMemberIntegrationTest.kt`:
```kotlin
    @Test
    fun `org admin changes a member role but cannot change own role`() {
        val sysToken = register("cr-sys@test.com"); makeSystemAdmin("cr-sys@test.com")
        val orgAdminToken = register("cr-orgadmin@test.com"); val orgAdminId = myId(orgAdminToken)
        val targetToken = register("cr-target@test.com"); val targetId = myId(targetToken)
        val orgId = createOrg(sysToken, "cr-org")
        addMember(sysToken, orgId, orgAdminId, "ADMIN").andExpect(status().isCreated)
        addMember(sysToken, orgId, targetId, "MEMBER").andExpect(status().isCreated)

        // Org-ADMIN hebt Ziel auf ADMIN → 200
        mockMvc.perform(
            patch("/api/v1/organizations/$orgId/members/$targetId").header("Authorization", "Bearer $orgAdminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"role":"ADMIN"}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.role").value("ADMIN"))

        // eigene Rolle ändern → 403
        mockMvc.perform(
            patch("/api/v1/organizations/$orgId/members/$orgAdminId").header("Authorization", "Bearer $orgAdminToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"role":"MEMBER"}""")
        ).andExpect(status().isForbidden)
    }
```

- [ ] **Step 5: Tests laufen lassen → PASS**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.organizations.OrganizationServiceTest" --tests "com.taskowolf.organizations.OrganizationMemberIntegrationTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/organizations/api/dto/UpdateOrgMemberRoleRequest.kt \
        backend/src/main/kotlin/com/taskowolf/organizations/application/OrganizationService.kt \
        backend/src/main/kotlin/com/taskowolf/organizations/api/OrganizationController.kt \
        backend/src/test/kotlin/com/taskowolf/organizations/OrganizationServiceTest.kt \
        backend/src/test/kotlin/com/taskowolf/organizations/OrganizationMemberIntegrationTest.kt
git commit -m "feat(org): add member role-change endpoint with self/owner/last-owner guards"
```

---

### Task 9: Owner-/Last-Owner-Schutz beim Entfernen von Org-Membern

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/organizations/application/OrganizationService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/organizations/api/OrganizationController.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/organizations/OrganizationServiceTest.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/organizations/OrganizationMemberIntegrationTest.kt`

**Interfaces:**
- Consumes: `isLastOwner` (Task 8), `ForbiddenException`, `NotFoundException`, `User.systemRole`.
- Produces: `OrganizationService.removeMember(orgId: UUID, actor: User, targetUserId: UUID)` (actor-aware, mit Owner-/Last-Owner-Schutz). Controller reicht `@AuthenticationPrincipal` durch.

- [ ] **Step 1: Failing unit test schreiben**

In `OrganizationServiceTest.kt` den alten `removeMember calls deleteById` **ersetzen** durch:
```kotlin
    @Test
    fun `removeMember deletes a normal member`() {
        val orgId = UUID.randomUUID()
        val actor = com.taskowolf.auth.domain.User(email = "act@test.com", displayName = "A")
            .apply { systemRole = com.taskowolf.auth.domain.SystemRole.ADMIN }
        val targetId = UUID.randomUUID()
        every { memberRepo.findById(OrganizationMemberId(orgId, targetId)) } returns
            java.util.Optional.of(OrganizationMember(OrganizationMemberId(orgId, targetId), OrgRole.MEMBER))
        every { memberRepo.deleteById(OrganizationMemberId(orgId, targetId)) } just Runs
        service.removeMember(orgId, actor, targetId)
        verify { memberRepo.deleteById(OrganizationMemberId(orgId, targetId)) }
    }

    @Test
    fun `removeMember forbids removing the last owner`() {
        val orgId = UUID.randomUUID()
        val actor = com.taskowolf.auth.domain.User(email = "act2@test.com", displayName = "A")
            .apply { systemRole = com.taskowolf.auth.domain.SystemRole.ADMIN }
        val targetId = UUID.randomUUID()
        every { memberRepo.findById(OrganizationMemberId(orgId, targetId)) } returns
            java.util.Optional.of(OrganizationMember(OrganizationMemberId(orgId, targetId), OrgRole.OWNER))
        every { memberRepo.findByIdOrgId(orgId) } returns
            listOf(OrganizationMember(OrganizationMemberId(orgId, targetId), OrgRole.OWNER))
        org.junit.jupiter.api.assertThrows<com.taskowolf.core.infrastructure.ForbiddenException> {
            service.removeMember(orgId, actor, targetId)
        }
    }
```

- [ ] **Step 2: Tests laufen lassen → FAIL**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.organizations.OrganizationServiceTest"`
Expected: FAIL (`removeMember` hat noch die alte 2-Argument-Signatur).

- [ ] **Step 3: `removeMember` actor-aware machen**

`OrganizationService.kt` — `removeMember` ersetzen:
```kotlin
    @Transactional
    fun removeMember(orgId: UUID, actor: User, targetUserId: UUID) {
        val member = memberRepo.findById(OrganizationMemberId(orgId, targetUserId))
            .orElseThrow { NotFoundException("Member not found") }
        val isSystemAdmin = actor.systemRole == SystemRole.ADMIN
        if (member.role == OrgRole.OWNER && !isSystemAdmin)
            throw ForbiddenException("Cannot remove an owner")
        if (member.role == OrgRole.OWNER && isLastOwner(orgId))
            throw ForbiddenException("Cannot remove the last owner")
        memberRepo.deleteById(OrganizationMemberId(orgId, targetUserId))
    }
```

`OrganizationController.kt` — `removeMember` actor durchreichen:
```kotlin
    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@orgSecurity.isOrgAdmin(#id, authentication)")
    fun removeMember(
        @PathVariable id: UUID,
        @PathVariable userId: UUID,
        @AuthenticationPrincipal actor: User
    ) {
        orgService.removeMember(id, actor, userId)
    }
```

- [ ] **Step 4: Integration test ergänzen**

In `OrganizationMemberIntegrationTest.kt`:
```kotlin
    @Test
    fun `org admin can remove a normal member`() {
        val sysToken = register("rm-sys@test.com"); makeSystemAdmin("rm-sys@test.com")
        val orgAdminToken = register("rm-admin@test.com"); val orgAdminId = myId(orgAdminToken)
        val targetToken = register("rm-target@test.com"); val targetId = myId(targetToken)
        val orgId = createOrg(sysToken, "rm-org")
        addMember(sysToken, orgId, orgAdminId, "ADMIN").andExpect(status().isCreated)
        addMember(sysToken, orgId, targetId, "MEMBER").andExpect(status().isCreated)

        mockMvc.perform(delete("/api/v1/organizations/$orgId/members/$targetId").header("Authorization", "Bearer $orgAdminToken"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `removing the sole owner is forbidden`() {
        val sysToken = register("so-sys@test.com"); makeSystemAdmin("so-sys@test.com")
        val ownerToken = register("so-owner@test.com"); val ownerId = myId(ownerToken)
        val orgId = createOrg(sysToken, "so-org")
        addMember(sysToken, orgId, ownerId, "OWNER").andExpect(status().isCreated)

        mockMvc.perform(delete("/api/v1/organizations/$orgId/members/$ownerId").header("Authorization", "Bearer $sysToken"))
            .andExpect(status().isForbidden)
    }
```

- [ ] **Step 5: Vollständige Backend-Suite laufen lassen → PASS**

Run: `cd backend && ./gradlew test`
Expected: PASS (gesamte Suite grün, inkl. aller neuen und bestehenden Tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/organizations/application/OrganizationService.kt \
        backend/src/main/kotlin/com/taskowolf/organizations/api/OrganizationController.kt \
        backend/src/test/kotlin/com/taskowolf/organizations/OrganizationServiceTest.kt \
        backend/src/test/kotlin/com/taskowolf/organizations/OrganizationMemberIntegrationTest.kt
git commit -m "feat(org): protect owner and last-owner from removal"
```

---

## Notes für den Executor

- **Reihenfolge einhalten:** Task 1 → 9. Tasks 2–5 (projects) und 6–9 (organizations) hängen jeweils aufeinander auf; der Cross-Cut ist der `OrgMembershipLookup`-Port (Task 1).
- **Whole-branch Final-Review** nach Task 9 (die #9-Lektion: Write-Surfaces per Endpoint, nicht per Service enumerieren — hier relevant, weil `roleOf`/`requireMember`-Änderungen alle lesenden Endpoints betreffen).
- **Wiki-Doc** (ai-guide/Backlog) ist Phase-B-übergreifend; Backlog-Status wird beim Merge/Release aktualisiert (kein Task hier).
- **Nicht** anfassen: `OrganizationContextFilter`, `switch-org`, JWT-`orgId`.
```

