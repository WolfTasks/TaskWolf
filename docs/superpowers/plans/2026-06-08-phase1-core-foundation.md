# Phase 1: Core Foundation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein laufendes, self-hosted TaskWolf bei dem Nutzer sich registrieren/einloggen, Projekte anlegen, und Issues mit Status-Workflows verwalten können — startbar mit `docker compose up`.

**Architecture:** Modularer Monolith mit Spring Boot. Jedes Modul (`auth`, `projects`, `workflows`, `issues`) hat eigene `domain/`, `application/`, `infrastructure/`, `api/`-Pakete. Module kommunizieren über Spring `ApplicationEvent`s. Frontend ist eine React SPA, die als statische Files von nginx serviert wird.

**Tech Stack:** Kotlin 2.x, Spring Boot 3.x, Spring Security, Spring Data JPA, Flyway, H2 (dev), PostgreSQL 16 (prod), JWT (jjwt), React 19, TypeScript, Vite, Tailwind CSS 4, shadcn/ui, React Query, Zustand, axios, Docker, docker-compose

---

## File Structure

```
backend/
  build.gradle.kts
  settings.gradle.kts
  src/main/kotlin/com/taskowolf/
    TaskWolfApplication.kt
    core/
      domain/AuditableEntity.kt
      application/DomainEventPublisher.kt
      infrastructure/GlobalExceptionHandler.kt
      infrastructure/ErrorResponse.kt
    auth/
      domain/User.kt
      domain/SystemRole.kt
      application/AuthService.kt
      application/JwtService.kt
      infrastructure/UserRepository.kt
      infrastructure/JwtAuthFilter.kt
      infrastructure/SecurityConfig.kt
      api/AuthController.kt
      api/dto/LoginRequest.kt
      api/dto/RegisterRequest.kt
      api/dto/AuthResponse.kt
      api/dto/UserResponse.kt
    projects/
      domain/Project.kt
      domain/ProjectMember.kt
      domain/ProjectRole.kt
      application/ProjectService.kt
      infrastructure/ProjectRepository.kt
      infrastructure/ProjectMemberRepository.kt
      api/ProjectController.kt
      api/dto/CreateProjectRequest.kt
      api/dto/ProjectResponse.kt
      api/dto/AddMemberRequest.kt
    workflows/
      domain/Workflow.kt
      domain/WorkflowStatus.kt
      domain/WorkflowTransition.kt
      domain/StatusCategory.kt
      application/WorkflowService.kt
      infrastructure/WorkflowRepository.kt
      infrastructure/WorkflowStatusRepository.kt
      infrastructure/WorkflowTransitionRepository.kt
      api/WorkflowController.kt
      api/dto/WorkflowResponse.kt
      api/dto/StatusResponse.kt
    issues/
      domain/Issue.kt
      domain/IssueType.kt
      domain/IssuePriority.kt
      domain/IssueLink.kt
      domain/IssueLinkType.kt
      domain/events/IssueCreatedEvent.kt
      domain/events/IssueStatusChangedEvent.kt
      application/IssueService.kt
      infrastructure/IssueRepository.kt
      infrastructure/IssueLinkRepository.kt
      api/IssueController.kt
      api/dto/CreateIssueRequest.kt
      api/dto/UpdateIssueRequest.kt
      api/dto/IssueResponse.kt
      api/dto/CreateIssueLinkRequest.kt
  src/main/resources/
    application.yml
    application-dev.yml
    application-prod.yml
    db/migration/
      V1__create_users.sql
      V2__create_projects.sql
      V3__create_workflows.sql
      V4__create_issues.sql
  src/test/kotlin/com/taskowolf/
    auth/AuthServiceTest.kt
    auth/AuthControllerTest.kt
    projects/ProjectServiceTest.kt
    issues/IssueServiceTest.kt

frontend/
  package.json
  vite.config.ts
  tailwind.config.ts
  components.json
  src/
    main.tsx
    app/router.tsx
    app/queryClient.ts
    layouts/AppLayout.tsx
    layouts/AuthLayout.tsx
    pages/auth/LoginPage.tsx
    pages/auth/RegisterPage.tsx
    pages/dashboard/DashboardPage.tsx
    pages/projects/ProjectListPage.tsx
    pages/projects/ProjectCreatePage.tsx
    pages/issues/IssueListPage.tsx
    pages/issues/IssueDetailPage.tsx
    components/issue/IssueCard.tsx
    components/issue/IssueForm.tsx
    components/issue/StatusBadge.tsx
    hooks/useProjects.ts
    hooks/useIssues.ts
    api/client.ts
    api/auth.ts
    api/projects.ts
    api/issues.ts
    lib/utils.ts
    types/index.ts

docker/nginx.conf
docker/init.sql
docker-compose.yml
docker-compose.dev.yml
.env.example
.gitignore
```

---

## Task 1: Backend Scaffolding

**Files:**
- Create: `backend/settings.gradle.kts`
- Create: `backend/build.gradle.kts`
- Create: `backend/src/main/kotlin/com/taskowolf/TaskWolfApplication.kt`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-dev.yml`
- Create: `backend/src/main/resources/application-prod.yml`

- [ ] **Step 1: Create `backend/settings.gradle.kts`**

```kotlin
rootProject.name = "taskowolf"
```

- [ ] **Step 2: Create `backend/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.5"
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    kotlin("plugin.jpa") version "2.0.0"
}

group = "com.taskowolf"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Create `backend/src/main/kotlin/com/taskowolf/TaskWolfApplication.kt`**

```kotlin
package com.taskowolf

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TaskWolfApplication

fun main(args: Array<String>) {
    runApplication<TaskWolfApplication>(*args)
}
```

- [ ] **Step 4: Create `backend/src/main/resources/application.yml`**

```yaml
spring:
  application:
    name: taskowolf
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: none
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 8080

taskowolf:
  jwt:
    secret: ${TW_JWT_SECRET:dev-secret-change-in-production-min-256-bits}
    access-token-expiry: 900      # 15 minutes
    refresh-token-expiry: 604800  # 7 days
  base-url: ${TW_BASE_URL:http://localhost:8080}
  storage-path: ${TW_STORAGE_PATH:./data/attachments}

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

- [ ] **Step 5: Create `backend/src/main/resources/application-dev.yml`**

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:taskowolf;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    show-sql: false

logging:
  level:
    com.taskowolf: DEBUG
```

- [ ] **Step 6: Create `backend/src/main/resources/application-prod.yml`**

```yaml
spring:
  datasource:
    url: ${TW_DB_URL}
    username: ${TW_DB_USER}
    password: ${TW_DB_PASS}
  flyway:
    baseline-on-migrate: true

logging:
  level:
    com.taskowolf: INFO
```

- [ ] **Step 7: Verify the project compiles**

```bash
cd backend && ./gradlew compileKotlin
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add backend/
git commit -m "feat: scaffold Spring Boot backend"
```

---

## Task 2: Core Module

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/core/domain/AuditableEntity.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/core/application/DomainEventPublisher.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/core/infrastructure/GlobalExceptionHandler.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/core/infrastructure/ErrorResponse.kt`

- [ ] **Step 1: Create `AuditableEntity.kt`**

```kotlin
package com.taskowolf.core.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class AuditableEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
)
```

- [ ] **Step 2: Enable JPA Auditing in `TaskWolfApplication.kt`**

```kotlin
package com.taskowolf

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing
class TaskWolfApplication

fun main(args: Array<String>) {
    runApplication<TaskWolfApplication>(*args)
}
```

- [ ] **Step 3: Create `DomainEventPublisher.kt`**

```kotlin
package com.taskowolf.core.application

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class DomainEventPublisher(private val publisher: ApplicationEventPublisher) {
    fun publish(event: Any) = publisher.publishEvent(event)
}
```

- [ ] **Step 4: Create `ErrorResponse.kt`**

```kotlin
package com.taskowolf.core.infrastructure

data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap()
)
```

- [ ] **Step 5: Create `GlobalExceptionHandler.kt`**

```kotlin
package com.taskowolf.core.infrastructure

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class NotFoundException(message: String) : RuntimeException(message)
class ForbiddenException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("NOT_FOUND", ex.message ?: "Resource not found"))

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse("FORBIDDEN", ex.message ?: "Access denied"))

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors
            .associate { it.field to (it.defaultMessage ?: "invalid") }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("VALIDATION_ERROR", "Validation failed", details))
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/core/
git commit -m "feat: add core module with auditing, event publisher, error handling"
```

---

## Task 3: Database Migrations

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__create_users.sql`
- Create: `backend/src/main/resources/db/migration/V2__create_projects.sql`
- Create: `backend/src/main/resources/db/migration/V3__create_workflows.sql`
- Create: `backend/src/main/resources/db/migration/V4__create_issues.sql`

- [ ] **Step 1: Create `V1__create_users.sql`**

```sql
CREATE TABLE users (
    id          UUID        NOT NULL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    avatar_url  VARCHAR(512),
    password_hash VARCHAR(255),
    oauth_provider VARCHAR(50),
    oauth_subject VARCHAR(255),
    system_role VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL
);

CREATE INDEX idx_users_email ON users(email);
```

- [ ] **Step 2: Create `V2__create_projects.sql`**

```sql
CREATE TABLE projects (
    id          UUID        NOT NULL PRIMARY KEY,
    key         VARCHAR(10)  NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id    UUID        NOT NULL REFERENCES users(id),
    archived    BOOLEAN     NOT NULL DEFAULT FALSE,
    node_id     VARCHAR(100),
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL
);

CREATE TABLE project_members (
    id          UUID        NOT NULL PRIMARY KEY,
    project_id  UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL,
    UNIQUE (project_id, user_id)
);

CREATE INDEX idx_projects_key ON projects(key);
CREATE INDEX idx_project_members_project ON project_members(project_id);
CREATE INDEX idx_project_members_user ON project_members(user_id);
```

- [ ] **Step 3: Create `V3__create_workflows.sql`**

```sql
CREATE TABLE workflows (
    id          UUID        NOT NULL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    project_id  UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    is_default  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL
);

CREATE TABLE workflow_statuses (
    id          UUID        NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    category    VARCHAR(20)  NOT NULL, -- TODO, IN_PROGRESS, DONE
    color       VARCHAR(7)   NOT NULL DEFAULT '#6c8fef',
    position    INT          NOT NULL DEFAULT 0,
    workflow_id UUID        NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL
);

CREATE TABLE workflow_transitions (
    id              UUID NOT NULL PRIMARY KEY,
    workflow_id     UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    from_status_id  UUID REFERENCES workflow_statuses(id) ON DELETE CASCADE, -- NULL = initial
    to_status_id    UUID NOT NULL REFERENCES workflow_statuses(id) ON DELETE CASCADE,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

-- Add workflow_id FK to projects after workflows table exists
ALTER TABLE projects ADD COLUMN workflow_id UUID REFERENCES workflows(id);

CREATE INDEX idx_workflows_project ON workflows(project_id);
CREATE INDEX idx_workflow_statuses_workflow ON workflow_statuses(workflow_id);
```

- [ ] **Step 4: Create `V4__create_issues.sql`**

```sql
CREATE TABLE issues (
    id              UUID        NOT NULL PRIMARY KEY,
    key             VARCHAR(20)  NOT NULL UNIQUE,  -- e.g. WOLF-42
    key_number      INT          NOT NULL,          -- the numeric part
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    type            VARCHAR(20)  NOT NULL DEFAULT 'TASK',
    priority        VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    story_points    INT,
    status_id       UUID        NOT NULL REFERENCES workflow_statuses(id),
    project_id      UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    assignee_id     UUID        REFERENCES users(id),
    reporter_id     UUID        NOT NULL REFERENCES users(id),
    sprint_id       UUID,  -- FK added in Phase 2
    parent_id       UUID        REFERENCES issues(id),
    due_date        DATE,
    created_at      TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL
);

CREATE TABLE issue_links (
    id              UUID NOT NULL PRIMARY KEY,
    from_issue_id   UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    to_issue_id     UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    link_type       VARCHAR(30) NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    UNIQUE(from_issue_id, to_issue_id, link_type)
);

CREATE INDEX idx_issues_project ON issues(project_id);
CREATE INDEX idx_issues_assignee ON issues(assignee_id);
CREATE INDEX idx_issues_status ON issues(status_id);
CREATE INDEX idx_issues_parent ON issues(parent_id);
CREATE INDEX idx_issue_links_from ON issue_links(from_issue_id);
CREATE INDEX idx_issue_links_to ON issue_links(to_issue_id);
```

- [ ] **Step 5: Verify migrations run on H2**

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun &
sleep 10
curl http://localhost:8080/actuator/health
kill %1
```
Expected: `{"status":"UP"}` (add `spring-boot-starter-actuator` to build.gradle.kts if needed)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/
git commit -m "feat: add Flyway migrations V1-V4 for users, projects, workflows, issues"
```

---

## Task 4: Auth Module

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/auth/domain/User.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/domain/SystemRole.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/application/JwtService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/application/AuthService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/UserRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/JwtAuthFilter.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/AuthController.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/dto/*.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/AuthServiceTest.kt`

- [ ] **Step 1: Create `SystemRole.kt`**

```kotlin
package com.taskowolf.auth.domain

enum class SystemRole { ADMIN, MEMBER }
```

- [ ] **Step 2: Create `User.kt`**

```kotlin
package com.taskowolf.auth.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false)
    var displayName: String,

    var avatarUrl: String? = null,

    var passwordHash: String? = null,

    var oauthProvider: String? = null,

    var oauthSubject: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var systemRole: SystemRole = SystemRole.MEMBER
) : AuditableEntity()
```

- [ ] **Step 3: Create `UserRepository.kt`**

```kotlin
package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}
```

- [ ] **Step 4: Create DTO classes**

`RegisterRequest.kt`:
```kotlin
package com.taskowolf.auth.api.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:Email val email: String,
    @field:NotBlank val displayName: String,
    @field:Size(min = 8) val password: String
)
```

`LoginRequest.kt`:
```kotlin
package com.taskowolf.auth.api.dto

data class LoginRequest(val email: String, val password: String)
```

`AuthResponse.kt`:
```kotlin
package com.taskowolf.auth.api.dto

data class AuthResponse(val accessToken: String, val refreshToken: String)
```

`UserResponse.kt`:
```kotlin
package com.taskowolf.auth.api.dto

import com.taskowolf.auth.domain.User
import java.util.UUID

data class UserResponse(val id: UUID, val email: String, val displayName: String, val avatarUrl: String?) {
    companion object {
        fun from(user: User) = UserResponse(user.id, user.email, user.displayName, user.avatarUrl)
    }
}
```

- [ ] **Step 5: Write failing test for AuthService**

`backend/src/test/kotlin/com/taskowolf/auth/AuthServiceTest.kt`:
```kotlin
package com.taskowolf.auth

import com.taskowolf.auth.application.AuthService
import com.taskowolf.auth.api.dto.RegisterRequest
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.ConflictException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder

class AuthServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val jwtService = mockk<com.taskowolf.auth.application.JwtService>()
    private val authService = AuthService(userRepository, passwordEncoder, jwtService)

    @Test
    fun `register throws ConflictException when email already exists`() {
        every { userRepository.existsByEmail("test@example.com") } returns true

        assertThrows<ConflictException> {
            authService.register(RegisterRequest("test@example.com", "Test User", "password123"))
        }
    }

    @Test
    fun `register creates user and returns tokens`() {
        every { userRepository.existsByEmail("new@example.com") } returns false
        every { passwordEncoder.encode(any()) } returns "hashed"
        every { userRepository.save(any()) } returnsArgument 0
        every { jwtService.generateAccessToken(any()) } returns "access-token"
        every { jwtService.generateRefreshToken(any()) } returns "refresh-token"

        val result = authService.register(RegisterRequest("new@example.com", "New User", "password123"))

        assert(result.accessToken == "access-token")
        assert(result.refreshToken == "refresh-token")
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.auth.AuthServiceTest" 2>&1 | tail -5
```
Expected: `AuthService` class not found error.

- [ ] **Step 7: Create `JwtService.kt`**

```kotlin
package com.taskowolf.auth.application

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID

@Service
class JwtService(
    @Value("\${taskowolf.jwt.secret}") private val secret: String,
    @Value("\${taskowolf.jwt.access-token-expiry}") private val accessExpiry: Long,
    @Value("\${taskowolf.jwt.refresh-token-expiry}") private val refreshExpiry: Long
) {
    private val key by lazy { Keys.hmacShaKeyFor(secret.toByteArray()) }

    fun generateAccessToken(userId: UUID): String = buildToken(userId, accessExpiry * 1000, "access")
    fun generateRefreshToken(userId: UUID): String = buildToken(userId, refreshExpiry * 1000, "refresh")

    fun validateToken(token: String): UUID? = runCatching {
        val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        UUID.fromString(claims.subject)
    }.getOrNull()

    private fun buildToken(userId: UUID, expiryMs: Long, type: String) = Jwts.builder()
        .subject(userId.toString())
        .claim("type", type)
        .issuedAt(Date())
        .expiration(Date(System.currentTimeMillis() + expiryMs))
        .signWith(key)
        .compact()
}
```

- [ ] **Step 8: Create `AuthService.kt`**

```kotlin
package com.taskowolf.auth.application

import com.taskowolf.auth.api.dto.AuthResponse
import com.taskowolf.auth.api.dto.LoginRequest
import com.taskowolf.auth.api.dto.RegisterRequest
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) {
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw ConflictException("Email already registered: ${request.email}")
        }
        val user = userRepository.save(
            User(
                email = request.email,
                displayName = request.displayName,
                passwordHash = passwordEncoder.encode(request.password)
            )
        )
        return tokenPair(user.id)
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw NotFoundException("User not found")
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw ForbiddenException("Invalid credentials")
        }
        return tokenPair(user.id)
    }

    fun refresh(refreshToken: String): AuthResponse {
        val userId = jwtService.validateToken(refreshToken)
            ?: throw ForbiddenException("Invalid refresh token")
        return tokenPair(userId)
    }

    fun me(userId: UUID) = userRepository.findById(userId)
        .orElseThrow { NotFoundException("User not found") }

    private fun tokenPair(userId: UUID) = AuthResponse(
        accessToken = jwtService.generateAccessToken(userId),
        refreshToken = jwtService.generateRefreshToken(userId)
    )
}

class ForbiddenException(message: String) : RuntimeException(message)
```

- [ ] **Step 9: Create `JwtAuthFilter.kt`**

```kotlin
package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.application.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val token = request.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ") }
            ?.substring(7)

        if (token != null) {
            val userId = jwtService.validateToken(token)
            if (userId != null) {
                val user = userRepository.findById(userId).orElse(null)
                if (user != null) {
                    val auth = UsernamePasswordAuthenticationToken(
                        user, null,
                        listOf(SimpleGrantedAuthority("ROLE_${user.systemRole.name}"))
                    )
                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        }
        chain.doFilter(request, response)
    }
}
```

- [ ] **Step 10: Create `SecurityConfig.kt`**

```kotlin
package com.taskowolf.auth.infrastructure

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(private val jwtAuthFilter: JwtAuthFilter) {

    @Bean
    fun passwordEncoder() = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/api/v1/auth/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/h2-console/**"
                ).permitAll()
                it.anyRequest().authenticated()
            }
            .headers { it.frameOptions { fo -> fo.disable() } } // for H2 console
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
```

- [ ] **Step 11: Create `AuthController.kt`**

```kotlin
package com.taskowolf.auth.api

import com.taskowolf.auth.api.dto.*
import com.taskowolf.auth.application.AuthService
import com.taskowolf.auth.domain.User
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest) =
        authService.register(request)

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest) =
        authService.login(request)

    @PostMapping("/refresh")
    fun refresh(@RequestBody body: Map<String, String>) =
        authService.refresh(body["refreshToken"] ?: error("Missing refreshToken"))

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal user: User) = UserResponse.from(user)
}
```

- [ ] **Step 12: Run tests to verify they pass**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.auth.AuthServiceTest" 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 13: Smoke test the API**

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun &
sleep 15
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","displayName":"Test","password":"password123"}'
kill %1
```
Expected: JSON with `accessToken` and `refreshToken`.

- [ ] **Step 14: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/auth/ backend/src/test/kotlin/com/taskowolf/auth/
git commit -m "feat: add auth module — JWT register/login/refresh"
```

---

## Task 5: Projects Module

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/projects/domain/Project.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/projects/domain/ProjectMember.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/projects/domain/ProjectRole.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/projects/infrastructure/ProjectRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/projects/infrastructure/ProjectMemberRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/projects/api/ProjectController.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/projects/api/dto/*.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/projects/ProjectServiceTest.kt`

- [ ] **Step 1: Create `ProjectRole.kt`**

```kotlin
package com.taskowolf.projects.domain

enum class ProjectRole { ADMIN, MEMBER, VIEWER }
```

- [ ] **Step 2: Create `Project.kt`**

```kotlin
package com.taskowolf.projects.domain

import com.taskowolf.auth.domain.User
import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.workflows.domain.Workflow
import jakarta.persistence.*

@Entity
@Table(name = "projects")
class Project(
    @Column(nullable = false, unique = true, length = 10)
    val key: String,

    @Column(nullable = false)
    var name: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id")
    var workflow: Workflow? = null,

    @Column(nullable = false)
    var archived: Boolean = false,

    @Column
    var nodeId: String? = null
) : AuditableEntity()
```

- [ ] **Step 3: Create `ProjectMember.kt`**

```kotlin
package com.taskowolf.projects.domain

import com.taskowolf.auth.domain.User
import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "project_members")
class ProjectMember(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: ProjectRole = ProjectRole.MEMBER
) : AuditableEntity()
```

- [ ] **Step 4: Create repositories**

`ProjectRepository.kt`:
```kotlin
package com.taskowolf.projects.infrastructure

import com.taskowolf.projects.domain.Project
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ProjectRepository : JpaRepository<Project, UUID> {
    fun findByKey(key: String): Project?
    fun existsByKey(key: String): Boolean

    @Query("""
        SELECT p FROM Project p
        LEFT JOIN ProjectMember m ON m.project = p AND m.user.id = :userId
        WHERE p.owner.id = :userId OR m.user.id = :userId
    """)
    fun findAllByMemberOrOwner(userId: UUID): List<Project>
}
```

`ProjectMemberRepository.kt`:
```kotlin
package com.taskowolf.projects.infrastructure

import com.taskowolf.projects.domain.ProjectMember
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProjectMemberRepository : JpaRepository<ProjectMember, UUID> {
    fun findByProjectIdAndUserId(projectId: UUID, userId: UUID): ProjectMember?
    fun findAllByProjectId(projectId: UUID): List<ProjectMember>
    fun existsByProjectIdAndUserId(projectId: UUID, userId: UUID): Boolean
}
```

- [ ] **Step 5: Create DTOs**

`CreateProjectRequest.kt`:
```kotlin
package com.taskowolf.projects.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateProjectRequest(
    @field:NotBlank
    @field:Size(min = 2, max = 10)
    @field:Pattern(regexp = "[A-Z0-9]+", message = "Key must be uppercase letters and digits")
    val key: String,

    @field:NotBlank val name: String,
    val description: String? = null
)
```

`ProjectResponse.kt`:
```kotlin
package com.taskowolf.projects.api.dto

import com.taskowolf.projects.domain.Project
import java.util.UUID

data class ProjectResponse(
    val id: UUID,
    val key: String,
    val name: String,
    val description: String?,
    val ownerId: UUID,
    val archived: Boolean
) {
    companion object {
        fun from(p: Project) = ProjectResponse(p.id, p.key, p.name, p.description, p.owner.id, p.archived)
    }
}
```

- [ ] **Step 6: Write failing test**

`backend/src/test/kotlin/com/taskowolf/projects/ProjectServiceTest.kt`:
```kotlin
package com.taskowolf.projects

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.projects.api.dto.CreateProjectRequest
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import com.taskowolf.projects.infrastructure.ProjectRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProjectServiceTest {

    private val projectRepository = mockk<ProjectRepository>()
    private val memberRepository = mockk<ProjectMemberRepository>()
    private val service = ProjectService(projectRepository, memberRepository)
    private val owner = User(email = "owner@test.com", displayName = "Owner")

    @Test
    fun `create throws ConflictException when key already exists`() {
        every { projectRepository.existsByKey("WOLF") } returns true

        assertThrows<ConflictException> {
            service.create(CreateProjectRequest("WOLF", "TaskWolf"), owner)
        }
    }

    @Test
    fun `create saves project and adds owner as ADMIN member`() {
        every { projectRepository.existsByKey("WOLF") } returns false
        every { projectRepository.save(any()) } returnsArgument 0
        every { memberRepository.save(any()) } returnsArgument 0

        service.create(CreateProjectRequest("WOLF", "TaskWolf"), owner)

        verify(exactly = 1) { projectRepository.save(any()) }
        verify(exactly = 1) { memberRepository.save(any()) }
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectServiceTest" 2>&1 | tail -5
```
Expected: `ProjectService` not found error.

- [ ] **Step 8: Create `ProjectService.kt`**

```kotlin
package com.taskowolf.projects.application

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.api.dto.CreateProjectRequest
import com.taskowolf.projects.domain.Project
import com.taskowolf.projects.domain.ProjectMember
import com.taskowolf.projects.domain.ProjectRole
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import com.taskowolf.projects.infrastructure.ProjectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val memberRepository: ProjectMemberRepository
) {
    @Transactional
    fun create(request: CreateProjectRequest, owner: User): Project {
        if (projectRepository.existsByKey(request.key)) {
            throw ConflictException("Project key already exists: ${request.key}")
        }
        val project = projectRepository.save(
            Project(key = request.key, name = request.name, description = request.description, owner = owner)
        )
        memberRepository.save(ProjectMember(project = project, user = owner, role = ProjectRole.ADMIN))
        return project
    }

    fun findAllForUser(userId: UUID) = projectRepository.findAllByMemberOrOwner(userId)

    fun findByKey(key: String) = projectRepository.findByKey(key)
        ?: throw NotFoundException("Project not found: $key")

    fun requireMember(projectKey: String, userId: UUID): Project {
        val project = findByKey(projectKey)
        val isMember = memberRepository.existsByProjectIdAndUserId(project.id, userId)
        val isOwner = project.owner.id == userId
        if (!isMember && !isOwner) throw ForbiddenException("Not a member of project $projectKey")
        return project
    }
}
```

- [ ] **Step 9: Create `ProjectController.kt`**

```kotlin
package com.taskowolf.projects.api

import com.taskowolf.auth.domain.User
import com.taskowolf.projects.api.dto.CreateProjectRequest
import com.taskowolf.projects.api.dto.ProjectResponse
import com.taskowolf.projects.application.ProjectService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects")
class ProjectController(private val projectService: ProjectService) {

    @GetMapping
    fun list(@AuthenticationPrincipal user: User) =
        projectService.findAllForUser(user.id).map { ProjectResponse.from(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateProjectRequest,
        @AuthenticationPrincipal user: User
    ) = ProjectResponse.from(projectService.create(request, user))

    @GetMapping("/{key}")
    fun get(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        ProjectResponse.from(projectService.requireMember(key, user.id))
}
```

- [ ] **Step 10: Run all tests**

```bash
cd backend && ./gradlew test 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/projects/ backend/src/test/kotlin/com/taskowolf/projects/
git commit -m "feat: add projects module — CRUD, membership"
```

---

## Task 6: Workflows Module

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/workflows/domain/StatusCategory.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/workflows/domain/Workflow.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/workflows/domain/WorkflowStatus.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/workflows/domain/WorkflowTransition.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/workflows/application/WorkflowService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/workflows/infrastructure/*.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/workflows/api/WorkflowController.kt`

- [ ] **Step 1: Create `StatusCategory.kt`**

```kotlin
package com.taskowolf.workflows.domain

enum class StatusCategory { TODO, IN_PROGRESS, DONE }
```

- [ ] **Step 2: Create `Workflow.kt`**

```kotlin
package com.taskowolf.workflows.domain

import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.projects.domain.Project
import jakarta.persistence.*

@Entity
@Table(name = "workflows")
class Workflow(
    @Column(nullable = false)
    var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @Column(nullable = false)
    var isDefault: Boolean = false,

    @OneToMany(mappedBy = "workflow", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position ASC")
    val statuses: MutableList<WorkflowStatus> = mutableListOf(),

    @OneToMany(mappedBy = "workflow", cascade = [CascadeType.ALL], orphanRemoval = true)
    val transitions: MutableList<WorkflowTransition> = mutableListOf()
) : AuditableEntity()
```

- [ ] **Step 3: Create `WorkflowStatus.kt`**

```kotlin
package com.taskowolf.workflows.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "workflow_statuses")
class WorkflowStatus(
    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var category: StatusCategory,

    @Column(nullable = false, length = 7)
    var color: String = "#6c8fef",

    @Column(nullable = false)
    var position: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    val workflow: Workflow
) : AuditableEntity()
```

- [ ] **Step 4: Create `WorkflowTransition.kt`**

```kotlin
package com.taskowolf.workflows.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "workflow_transitions")
class WorkflowTransition(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    val workflow: Workflow,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_status_id")
    val fromStatus: WorkflowStatus? = null, // null = any status

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_status_id", nullable = false)
    val toStatus: WorkflowStatus
) : AuditableEntity()
```

- [ ] **Step 5: Create repositories**

```kotlin
// WorkflowRepository.kt
package com.taskowolf.workflows.infrastructure
import com.taskowolf.workflows.domain.Workflow
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
interface WorkflowRepository : JpaRepository<Workflow, UUID> {
    fun findByProjectId(projectId: UUID): List<Workflow>
}

// WorkflowStatusRepository.kt
package com.taskowolf.workflows.infrastructure
import com.taskowolf.workflows.domain.WorkflowStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
interface WorkflowStatusRepository : JpaRepository<WorkflowStatus, UUID>

// WorkflowTransitionRepository.kt
package com.taskowolf.workflows.infrastructure
import com.taskowolf.workflows.domain.WorkflowTransition
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
interface WorkflowTransitionRepository : JpaRepository<WorkflowTransition, UUID> {
    fun findByWorkflowId(workflowId: UUID): List<WorkflowTransition>
}
```

- [ ] **Step 6: Create `WorkflowService.kt`**

```kotlin
package com.taskowolf.workflows.application

import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.domain.Project
import com.taskowolf.workflows.domain.*
import com.taskowolf.workflows.infrastructure.WorkflowRepository
import com.taskowolf.workflows.infrastructure.WorkflowStatusRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WorkflowService(
    private val workflowRepository: WorkflowRepository,
    private val statusRepository: WorkflowStatusRepository
) {
    @Transactional
    fun createDefault(project: Project): Workflow {
        val workflow = workflowRepository.save(
            Workflow(name = "Default Workflow", project = project, isDefault = true)
        )
        val todo = statusRepository.save(WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow))
        val inProgress = statusRepository.save(WorkflowStatus("In Progress", StatusCategory.IN_PROGRESS, "#ffb432", 1, workflow))
        val done = statusRepository.save(WorkflowStatus("Done", StatusCategory.DONE, "#63dc78", 2, workflow))
        workflow.statuses.addAll(listOf(todo, inProgress, done))
        return workflow
    }

    fun findByProject(projectId: UUID) = workflowRepository.findByProjectId(projectId)

    fun findStatusById(statusId: UUID) = statusRepository.findById(statusId)
        .orElseThrow { NotFoundException("Status not found: $statusId") }

    fun getDefaultStatus(workflow: Workflow) = workflow.statuses
        .filter { it.category == StatusCategory.TODO }
        .minByOrNull { it.position }
        ?: throw NotFoundException("No TODO status in workflow")
}
```

- [ ] **Step 7: Wire default workflow creation into ProjectService**

In `ProjectService.kt`, inject `WorkflowService` and call `workflowService.createDefault(project)` after saving the project, then assign it: `project.workflow = workflow` and save again.

```kotlin
// Add to ProjectService constructor
private val workflowService: WorkflowService,

// In create(), after memberRepository.save():
val workflow = workflowService.createDefault(project)
project.workflow = workflow  // need a var on Project for this
projectRepository.save(project)
```

Change `Project.workflow` field to `var` (already `var` in the spec).

- [ ] **Step 8: Create `WorkflowController.kt`**

```kotlin
package com.taskowolf.workflows.api

import com.taskowolf.auth.domain.User
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.workflows.application.WorkflowService
import com.taskowolf.workflows.domain.WorkflowStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class StatusResponse(val id: UUID, val name: String, val category: String, val color: String, val position: Int) {
    companion object { fun from(s: WorkflowStatus) = StatusResponse(s.id, s.name, s.category.name, s.color, s.position) }
}
data class WorkflowResponse(val id: UUID, val name: String, val statuses: List<StatusResponse>)

@RestController
@RequestMapping("/api/v1/projects/{key}/workflows")
class WorkflowController(
    private val projectService: ProjectService,
    private val workflowService: WorkflowService
) {
    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User): List<WorkflowResponse> {
        val project = projectService.requireMember(key, user.id)
        return workflowService.findByProject(project.id).map { wf ->
            WorkflowResponse(wf.id, wf.name, wf.statuses.map { StatusResponse.from(it) })
        }
    }
}
```

- [ ] **Step 9: Run all tests**

```bash
cd backend && ./gradlew test 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/workflows/
git commit -m "feat: add workflows module — statuses, transitions, default workflow"
```

---

## Task 7: Issues Module

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/issues/domain/Issue.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/issues/domain/IssueType.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/issues/domain/IssuePriority.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/issues/domain/IssueLink.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/issues/domain/IssueLinkType.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/issues/domain/events/*.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/issues/infrastructure/IssueRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/issues/api/IssueController.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt`

- [ ] **Step 1: Create enums**

```kotlin
// IssueType.kt
package com.taskowolf.issues.domain
enum class IssueType { EPIC, STORY, BUG, TASK, SUBTASK }

// IssuePriority.kt
package com.taskowolf.issues.domain
enum class IssuePriority { CRITICAL, HIGH, MEDIUM, LOW }

// IssueLinkType.kt
package com.taskowolf.issues.domain
enum class IssueLinkType { BLOCKS, BLOCKED_BY, RELATES_TO, DUPLICATES, CLONED_BY }
```

- [ ] **Step 2: Create `Issue.kt`**

```kotlin
package com.taskowolf.issues.domain

import com.taskowolf.auth.domain.User
import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.projects.domain.Project
import com.taskowolf.workflows.domain.WorkflowStatus
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "issues")
class Issue(
    @Column(nullable = false, unique = true)
    val key: String,

    @Column(nullable = false)
    val keyNumber: Int,

    @Column(nullable = false, length = 500)
    var title: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: IssueType = IssueType.TASK,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var priority: IssuePriority = IssuePriority.MEDIUM,

    var storyPoints: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id", nullable = false)
    var status: WorkflowStatus,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    var assignee: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    val reporter: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: Issue? = null,

    var dueDate: LocalDate? = null
) : AuditableEntity()
```

- [ ] **Step 3: Create `IssueLink.kt`**

```kotlin
package com.taskowolf.issues.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "issue_links")
class IssueLink(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_issue_id", nullable = false)
    val fromIssue: Issue,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_issue_id", nullable = false)
    val toIssue: Issue,

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false)
    val linkType: IssueLinkType
) : AuditableEntity()
```

- [ ] **Step 4: Create domain events**

```kotlin
// IssueCreatedEvent.kt
package com.taskowolf.issues.domain.events
import com.taskowolf.issues.domain.Issue
data class IssueCreatedEvent(val issue: Issue)

// IssueStatusChangedEvent.kt
package com.taskowolf.issues.domain.events
import com.taskowolf.issues.domain.Issue
import com.taskowolf.workflows.domain.WorkflowStatus
data class IssueStatusChangedEvent(val issue: Issue, val oldStatus: WorkflowStatus, val newStatus: WorkflowStatus)
```

- [ ] **Step 5: Create `IssueRepository.kt`**

```kotlin
package com.taskowolf.issues.infrastructure

import com.taskowolf.issues.domain.Issue
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface IssueRepository : JpaRepository<Issue, UUID> {
    fun findByKey(key: String): Issue?
    fun findAllByProjectId(projectId: UUID, pageable: Pageable): Page<Issue>
    fun countByProjectId(projectId: UUID): Long

    @Query("SELECT COALESCE(MAX(i.keyNumber), 0) FROM Issue i WHERE i.project.id = :projectId")
    fun maxKeyNumberByProject(projectId: UUID): Int
}
```

- [ ] **Step 6: Create DTOs**

```kotlin
// CreateIssueRequest.kt
package com.taskowolf.issues.api.dto
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.domain.IssuePriority
import jakarta.validation.constraints.NotBlank
import java.util.UUID
data class CreateIssueRequest(
    @field:NotBlank val title: String,
    val description: String? = null,
    val type: IssueType = IssueType.TASK,
    val priority: IssuePriority = IssuePriority.MEDIUM,
    val assigneeId: UUID? = null,
    val parentId: UUID? = null,
    val storyPoints: Int? = null
)

// UpdateIssueRequest.kt
package com.taskowolf.issues.api.dto
import com.taskowolf.issues.domain.IssuePriority
import java.util.UUID
data class UpdateIssueRequest(
    val title: String? = null,
    val description: String? = null,
    val statusId: UUID? = null,
    val assigneeId: UUID? = null,
    val priority: IssuePriority? = null,
    val storyPoints: Int? = null
)

// IssueResponse.kt
package com.taskowolf.issues.api.dto
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.IssueType
import java.util.UUID
data class IssueResponse(
    val id: UUID, val key: String, val title: String, val description: String?,
    val type: IssueType, val priority: IssuePriority, val storyPoints: Int?,
    val statusId: UUID, val statusName: String, val statusCategory: String,
    val projectId: UUID, val assigneeId: UUID?, val reporterId: UUID, val parentId: UUID?
) {
    companion object {
        fun from(i: Issue) = IssueResponse(
            i.id, i.key, i.title, i.description, i.type, i.priority, i.storyPoints,
            i.status.id, i.status.name, i.status.category.name,
            i.project.id, i.assignee?.id, i.reporter.id, i.parent?.id
        )
    }
}
```

- [ ] **Step 7: Write failing tests**

`backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt`:
```kotlin
package com.taskowolf.issues

import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.issues.api.dto.CreateIssueRequest
import com.taskowolf.issues.application.IssueService
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.workflows.application.WorkflowService
import com.taskowolf.workflows.domain.*
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IssueServiceTest {

    private val issueRepository = mockk<IssueRepository>()
    private val projectService = mockk<ProjectService>()
    private val workflowService = mockk<WorkflowService>()
    private val userRepository = mockk<UserRepository>()
    private val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    private val service = IssueService(issueRepository, projectService, workflowService, userRepository, eventPublisher)

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val workflow = mockk<Workflow>()
    private val status = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = workflow)

    @Test
    fun `create issue assigns next key number`() {
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { workflowService.getDefaultStatus(workflow) } returns status
        every { issueRepository.maxKeyNumberByProject(project.id) } returns 5
        every { issueRepository.save(any()) } returnsArgument 0

        val issue = service.create("WOLF", CreateIssueRequest("Fix bug"), owner)

        assert(issue.key == "WOLF-6")
        assert(issue.keyNumber == 6)
    }

    @Test
    fun `create EPIC sets type correctly`() {
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { workflowService.getDefaultStatus(workflow) } returns status
        every { issueRepository.maxKeyNumberByProject(project.id) } returns 0
        every { issueRepository.save(any()) } returnsArgument 0

        val issue = service.create("WOLF", CreateIssueRequest("Big Epic", type = IssueType.EPIC), owner)

        assert(issue.type == IssueType.EPIC)
    }
}
```

- [ ] **Step 8: Run to verify failure**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.issues.IssueServiceTest" 2>&1 | tail -5
```
Expected: `IssueService` not found.

- [ ] **Step 9: Create `IssueService.kt`**

```kotlin
package com.taskowolf.issues.application

import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.api.dto.CreateIssueRequest
import com.taskowolf.issues.api.dto.UpdateIssueRequest
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.events.IssueCreatedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.workflows.application.WorkflowService
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class IssueService(
    private val issueRepository: IssueRepository,
    private val projectService: ProjectService,
    private val workflowService: WorkflowService,
    private val userRepository: UserRepository,
    private val eventPublisher: DomainEventPublisher
) {
    @Transactional
    fun create(projectKey: String, request: CreateIssueRequest, reporter: User): Issue {
        val project = projectService.requireMember(projectKey, reporter.id)
        val workflow = project.workflow ?: throw NotFoundException("Project has no workflow")
        val status = workflowService.getDefaultStatus(workflow)
        val nextNumber = issueRepository.maxKeyNumberByProject(project.id) + 1

        val issue = issueRepository.save(
            Issue(
                key = "${project.key}-$nextNumber",
                keyNumber = nextNumber,
                title = request.title,
                description = request.description,
                type = request.type,
                priority = request.priority,
                storyPoints = request.storyPoints,
                status = status,
                project = project,
                assignee = request.assigneeId?.let { userRepository.findById(it).orElse(null) },
                reporter = reporter,
                parent = request.parentId?.let { issueRepository.findById(it).orElse(null) }
            )
        )
        eventPublisher.publish(IssueCreatedEvent(issue))
        return issue
    }

    @Transactional
    fun update(projectKey: String, issueId: UUID, request: UpdateIssueRequest, currentUser: User): Issue {
        val project = projectService.requireMember(projectKey, currentUser.id)
        val issue = issueRepository.findById(issueId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Issue not found: $issueId") }

        request.title?.let { issue.title = it }
        request.description?.let { issue.description = it }
        request.priority?.let { issue.priority = it }
        request.storyPoints?.let { issue.storyPoints = it }
        request.assigneeId?.let { issue.assignee = userRepository.findById(it).orElse(null) }
        request.statusId?.let { newStatusId ->
            val oldStatus = issue.status
            val newStatus = workflowService.findStatusById(newStatusId)
            issue.status = newStatus
            if (oldStatus.id != newStatus.id) {
                eventPublisher.publish(IssueStatusChangedEvent(issue, oldStatus, newStatus))
            }
        }
        return issueRepository.save(issue)
    }

    fun findByProject(projectKey: String, userId: UUID, page: Int, size: Int): org.springframework.data.domain.Page<Issue> {
        val project = projectService.requireMember(projectKey, userId)
        return issueRepository.findAllByProjectId(project.id, PageRequest.of(page, size))
    }

    fun findByKey(projectKey: String, issueKey: String, userId: UUID): Issue {
        projectService.requireMember(projectKey, userId)
        return issueRepository.findByKey(issueKey) ?: throw NotFoundException("Issue not found: $issueKey")
    }

    @Transactional
    fun delete(projectKey: String, issueId: UUID, currentUser: User) {
        val project = projectService.requireMember(projectKey, currentUser.id)
        val issue = issueRepository.findById(issueId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Issue not found") }
        issueRepository.delete(issue)
    }
}
```

- [ ] **Step 10: Create `IssueController.kt`**

```kotlin
package com.taskowolf.issues.api

import com.taskowolf.auth.domain.User
import com.taskowolf.issues.api.dto.CreateIssueRequest
import com.taskowolf.issues.api.dto.IssueResponse
import com.taskowolf.issues.api.dto.UpdateIssueRequest
import com.taskowolf.issues.application.IssueService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/issues")
class IssueController(private val issueService: IssueService) {

    @GetMapping
    fun list(
        @PathVariable key: String,
        @AuthenticationPrincipal user: User,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ) = issueService.findByProject(key, user.id, page, size).map { IssueResponse.from(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: CreateIssueRequest,
        @AuthenticationPrincipal user: User
    ) = IssueResponse.from(issueService.create(key, request, user))

    @GetMapping("/{issueKey}")
    fun get(
        @PathVariable key: String,
        @PathVariable issueKey: String,
        @AuthenticationPrincipal user: User
    ) = IssueResponse.from(issueService.findByKey(key, issueKey, user.id))

    @PatchMapping("/{id}")
    fun update(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @RequestBody request: UpdateIssueRequest,
        @AuthenticationPrincipal user: User
    ) = IssueResponse.from(issueService.update(key, id, request, user))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ) = issueService.delete(key, id, user)
}
```

- [ ] **Step 11: Run all tests**

```bash
cd backend && ./gradlew test 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`, all tests green.

- [ ] **Step 12: End-to-end smoke test**

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun &
sleep 15
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@test.com","displayName":"Dev","password":"password123"}' | jq -r .accessToken)
curl -s -X POST http://localhost:8080/api/v1/projects \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"key":"WOLF","name":"TaskWolf"}'
curl -s -X POST http://localhost:8080/api/v1/projects/WOLF/issues \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"First Issue"}'
kill %1
```
Expected: Issue with `key: "WOLF-1"` returned.

- [ ] **Step 13: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/issues/ backend/src/test/kotlin/com/taskowolf/issues/
git commit -m "feat: add issues module — CRUD, types, priorities, status transitions, domain events"
```

---

## Task 8: Frontend Scaffolding

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tailwind.config.ts`
- Create: `frontend/components.json`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/app/router.tsx`
- Create: `frontend/src/app/queryClient.ts`
- Create: `frontend/src/types/index.ts`
- Create: `frontend/src/api/client.ts`
- Create: `frontend/src/lib/utils.ts`

- [ ] **Step 1: Create `frontend/package.json`**

```json
{
  "name": "taskowolf-frontend",
  "private": true,
  "version": "0.0.1",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "react-router-dom": "^6.26.0",
    "@tanstack/react-query": "^5.51.0",
    "zustand": "^4.5.4",
    "axios": "^1.7.3",
    "class-variance-authority": "^0.7.0",
    "clsx": "^2.1.1",
    "tailwind-merge": "^2.4.0",
    "lucide-react": "^0.441.0",
    "@radix-ui/react-dialog": "^1.1.1",
    "@radix-ui/react-dropdown-menu": "^2.1.1",
    "@radix-ui/react-select": "^2.1.1",
    "@radix-ui/react-label": "^2.1.0",
    "@radix-ui/react-slot": "^1.1.0"
  },
  "devDependencies": {
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "@vitejs/plugin-react": "^4.3.1",
    "typescript": "^5.5.3",
    "vite": "^5.3.5",
    "tailwindcss": "^4.0.0",
    "@tailwindcss/vite": "^4.0.0"
  }
}
```

- [ ] **Step 2: Create `frontend/vite.config.ts`**

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') }
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true }
    }
  }
})
```

- [ ] **Step 3: Create `frontend/src/types/index.ts`**

```typescript
export interface User {
  id: string
  email: string
  displayName: string
  avatarUrl: string | null
}

export interface Project {
  id: string
  key: string
  name: string
  description: string | null
  ownerId: string
  archived: boolean
}

export interface WorkflowStatus {
  id: string
  name: string
  category: 'TODO' | 'IN_PROGRESS' | 'DONE'
  color: string
  position: number
}

export interface Issue {
  id: string
  key: string
  title: string
  description: string | null
  type: 'EPIC' | 'STORY' | 'BUG' | 'TASK' | 'SUBTASK'
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
  storyPoints: number | null
  statusId: string
  statusName: string
  statusCategory: 'TODO' | 'IN_PROGRESS' | 'DONE'
  projectId: string
  assigneeId: string | null
  reporterId: string
  parentId: string | null
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
}
```

- [ ] **Step 4: Create `frontend/src/api/client.ts`**

```typescript
import axios from 'axios'

export const apiClient = axios.create({ baseURL: '/api/v1' })

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

apiClient.interceptors.response.use(
  (res) => res,
  async (error) => {
    if (error.response?.status === 401) {
      const refreshToken = localStorage.getItem('refreshToken')
      if (refreshToken) {
        try {
          const { data } = await axios.post('/api/v1/auth/refresh', { refreshToken })
          localStorage.setItem('accessToken', data.accessToken)
          localStorage.setItem('refreshToken', data.refreshToken)
          error.config.headers.Authorization = `Bearer ${data.accessToken}`
          return apiClient.request(error.config)
        } catch {
          localStorage.removeItem('accessToken')
          localStorage.removeItem('refreshToken')
          window.location.href = '/login'
        }
      }
    }
    return Promise.reject(error)
  }
)
```

- [ ] **Step 5: Create API modules**

`frontend/src/api/auth.ts`:
```typescript
import { apiClient } from './client'
import type { AuthResponse, User } from '@/types'

export const authApi = {
  register: (email: string, displayName: string, password: string) =>
    apiClient.post<AuthResponse>('/auth/register', { email, displayName, password }),
  login: (email: string, password: string) =>
    apiClient.post<AuthResponse>('/auth/login', { email, password }),
  me: () => apiClient.get<User>('/auth/me'),
}
```

`frontend/src/api/projects.ts`:
```typescript
import { apiClient } from './client'
import type { Project } from '@/types'

export const projectsApi = {
  list: () => apiClient.get<Project[]>('/projects'),
  get: (key: string) => apiClient.get<Project>(`/projects/${key}`),
  create: (data: { key: string; name: string; description?: string }) =>
    apiClient.post<Project>('/projects', data),
}
```

`frontend/src/api/issues.ts`:
```typescript
import { apiClient } from './client'
import type { Issue, Page } from '@/types'

export const issuesApi = {
  list: (projectKey: string, page = 0, size = 50) =>
    apiClient.get<Page<Issue>>(`/projects/${projectKey}/issues`, { params: { page, size } }),
  get: (projectKey: string, issueKey: string) =>
    apiClient.get<Issue>(`/projects/${projectKey}/issues/${issueKey}`),
  create: (projectKey: string, data: { title: string; type?: string; priority?: string; description?: string }) =>
    apiClient.post<Issue>(`/projects/${projectKey}/issues`, data),
  update: (projectKey: string, issueId: string, data: Partial<Issue & { statusId: string }>) =>
    apiClient.patch<Issue>(`/projects/${projectKey}/issues/${issueId}`, data),
}
```

- [ ] **Step 6: Create `frontend/src/app/queryClient.ts`**

```typescript
import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 1 }
  }
})
```

- [ ] **Step 7: Create `frontend/src/lib/utils.ts`**

```typescript
import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
```

- [ ] **Step 8: Install dependencies and verify build**

```bash
cd frontend && npm install && npm run build 2>&1 | tail -5
```
Expected: `✓ built in` — no errors.

- [ ] **Step 9: Commit**

```bash
git add frontend/
git commit -m "feat: scaffold React frontend with Vite, Tailwind, API client, types"
```

---

## Task 9: Frontend Auth Pages

**Files:**
- Create: `frontend/src/app/router.tsx`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/layouts/AuthLayout.tsx`
- Create: `frontend/src/layouts/AppLayout.tsx`
- Create: `frontend/src/pages/auth/LoginPage.tsx`
- Create: `frontend/src/pages/auth/RegisterPage.tsx`

- [ ] **Step 1: Create `frontend/src/main.tsx`**

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import { queryClient } from './app/queryClient'
import { router } from './app/router'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>
)
```

- [ ] **Step 2: Create `frontend/src/app/router.tsx`**

```tsx
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AuthLayout } from '@/layouts/AuthLayout'
import { AppLayout } from '@/layouts/AppLayout'
import { LoginPage } from '@/pages/auth/LoginPage'
import { RegisterPage } from '@/pages/auth/RegisterPage'
import { DashboardPage } from '@/pages/dashboard/DashboardPage'
import { ProjectListPage } from '@/pages/projects/ProjectListPage'
import { ProjectCreatePage } from '@/pages/projects/ProjectCreatePage'
import { IssueListPage } from '@/pages/issues/IssueListPage'
import { IssueDetailPage } from '@/pages/issues/IssueDetailPage'

const isAuthenticated = () => !!localStorage.getItem('accessToken')

function RequireAuth({ children }: { children: React.ReactNode }) {
  return isAuthenticated() ? <>{children}</> : <Navigate to="/login" replace />
}

export const router = createBrowserRouter([
  {
    element: <AuthLayout />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
    ],
  },
  {
    element: <RequireAuth><AppLayout /></RequireAuth>,
    children: [
      { path: '/', element: <DashboardPage /> },
      { path: '/projects', element: <ProjectListPage /> },
      { path: '/projects/new', element: <ProjectCreatePage /> },
      { path: '/p/:key/issues', element: <IssueListPage /> },
      { path: '/p/:key/issues/:issueKey', element: <IssueDetailPage /> },
    ],
  },
])
```

- [ ] **Step 3: Create `frontend/src/layouts/AuthLayout.tsx`**

```tsx
import { Outlet, Link } from 'react-router-dom'

export function AuthLayout() {
  return (
    <div className="min-h-screen bg-gray-950 flex items-center justify-center">
      <div className="w-full max-w-md p-8">
        <h1 className="text-3xl font-bold text-white text-center mb-8">🐺 TaskWolf</h1>
        <Outlet />
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Create `frontend/src/layouts/AppLayout.tsx`**

```tsx
import { Outlet, Link, useNavigate } from 'react-router-dom'

export function AppLayout() {
  const navigate = useNavigate()
  const logout = () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    navigate('/login')
  }

  return (
    <div className="min-h-screen bg-gray-950 text-white flex">
      <aside className="w-56 bg-gray-900 border-r border-gray-800 flex flex-col p-4">
        <Link to="/" className="text-xl font-bold mb-8">🐺 TaskWolf</Link>
        <nav className="flex flex-col gap-2 flex-1">
          <Link to="/" className="px-3 py-2 rounded hover:bg-gray-800 text-sm">Dashboard</Link>
          <Link to="/projects" className="px-3 py-2 rounded hover:bg-gray-800 text-sm">Projects</Link>
        </nav>
        <button onClick={logout} className="px-3 py-2 text-sm text-gray-400 hover:text-white text-left">
          Logout
        </button>
      </aside>
      <main className="flex-1 overflow-auto p-8">
        <Outlet />
      </main>
    </div>
  )
}
```

- [ ] **Step 5: Create `frontend/src/pages/auth/LoginPage.tsx`**

```tsx
import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { authApi } from '@/api/auth'

export function LoginPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      const { data } = await authApi.login(email, password)
      localStorage.setItem('accessToken', data.accessToken)
      localStorage.setItem('refreshToken', data.refreshToken)
      navigate('/')
    } catch {
      setError('Invalid email or password')
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <h2 className="text-xl font-semibold text-white">Sign in</h2>
      {error && <p className="text-red-400 text-sm">{error}</p>}
      <input
        type="email" value={email} onChange={e => setEmail(e.target.value)}
        placeholder="Email" required
        className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
      />
      <input
        type="password" value={password} onChange={e => setPassword(e.target.value)}
        placeholder="Password" required
        className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
      />
      <button type="submit"
        className="bg-blue-600 hover:bg-blue-700 text-white rounded px-4 py-2 text-sm font-medium">
        Sign in
      </button>
      <p className="text-sm text-gray-400 text-center">
        No account? <Link to="/register" className="text-blue-400 hover:underline">Register</Link>
      </p>
    </form>
  )
}
```

- [ ] **Step 6: Create `frontend/src/pages/auth/RegisterPage.tsx`**

```tsx
import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { authApi } from '@/api/auth'

export function RegisterPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', displayName: '', password: '' })
  const [error, setError] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      const { data } = await authApi.register(form.email, form.displayName, form.password)
      localStorage.setItem('accessToken', data.accessToken)
      localStorage.setItem('refreshToken', data.refreshToken)
      navigate('/')
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Registration failed')
    }
  }

  const set = (field: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm(prev => ({ ...prev, [field]: e.target.value }))

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <h2 className="text-xl font-semibold text-white">Create account</h2>
      {error && <p className="text-red-400 text-sm">{error}</p>}
      <input type="email" value={form.email} onChange={set('email')} placeholder="Email" required
        className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm" />
      <input type="text" value={form.displayName} onChange={set('displayName')} placeholder="Display name" required
        className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm" />
      <input type="password" value={form.password} onChange={set('password')} placeholder="Password (min 8 chars)" required minLength={8}
        className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm" />
      <button type="submit"
        className="bg-blue-600 hover:bg-blue-700 text-white rounded px-4 py-2 text-sm font-medium">
        Create account
      </button>
      <p className="text-sm text-gray-400 text-center">
        Already have an account? <Link to="/login" className="text-blue-400 hover:underline">Sign in</Link>
      </p>
    </form>
  )
}
```

- [ ] **Step 7: Verify frontend builds**

```bash
cd frontend && npm run build 2>&1 | tail -5
```
Expected: `✓ built in` — no TypeScript errors.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/
git commit -m "feat: add auth pages — login, register, app layout with sidebar"
```

---

## Task 10: Frontend Projects & Issues Pages

**Files:**
- Create: `frontend/src/hooks/useProjects.ts`
- Create: `frontend/src/hooks/useIssues.ts`
- Create: `frontend/src/pages/dashboard/DashboardPage.tsx`
- Create: `frontend/src/pages/projects/ProjectListPage.tsx`
- Create: `frontend/src/pages/projects/ProjectCreatePage.tsx`
- Create: `frontend/src/pages/issues/IssueListPage.tsx`
- Create: `frontend/src/pages/issues/IssueDetailPage.tsx`
- Create: `frontend/src/components/issue/StatusBadge.tsx`

- [ ] **Step 1: Create `frontend/src/hooks/useProjects.ts`**

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { projectsApi } from '@/api/projects'

export function useProjects() {
  return useQuery({
    queryKey: ['projects'],
    queryFn: () => projectsApi.list().then(r => r.data)
  })
}

export function useProject(key: string) {
  return useQuery({
    queryKey: ['projects', key],
    queryFn: () => projectsApi.get(key).then(r => r.data)
  })
}

export function useCreateProject() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { key: string; name: string; description?: string }) =>
      projectsApi.create(data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['projects'] })
  })
}
```

- [ ] **Step 2: Create `frontend/src/hooks/useIssues.ts`**

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { issuesApi } from '@/api/issues'

export function useIssues(projectKey: string) {
  return useQuery({
    queryKey: ['issues', projectKey],
    queryFn: () => issuesApi.list(projectKey).then(r => r.data)
  })
}

export function useIssue(projectKey: string, issueKey: string) {
  return useQuery({
    queryKey: ['issues', projectKey, issueKey],
    queryFn: () => issuesApi.get(projectKey, issueKey).then(r => r.data)
  })
}

export function useCreateIssue(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { title: string; type?: string; priority?: string; description?: string }) =>
      issuesApi.create(projectKey, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['issues', projectKey] })
  })
}

export function useUpdateIssue(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Record<string, unknown> }) =>
      issuesApi.update(projectKey, id, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['issues', projectKey] })
  })
}
```

- [ ] **Step 3: Create `frontend/src/components/issue/StatusBadge.tsx`**

```tsx
import { cn } from '@/lib/utils'

const categoryColors = {
  TODO: 'bg-blue-900 text-blue-300',
  IN_PROGRESS: 'bg-yellow-900 text-yellow-300',
  DONE: 'bg-green-900 text-green-300',
}

interface Props {
  name: string
  category: 'TODO' | 'IN_PROGRESS' | 'DONE'
}

export function StatusBadge({ name, category }: Props) {
  return (
    <span className={cn('px-2 py-0.5 rounded text-xs font-medium', categoryColors[category])}>
      {name}
    </span>
  )
}
```

- [ ] **Step 4: Create `frontend/src/pages/dashboard/DashboardPage.tsx`**

```tsx
import { Link } from 'react-router-dom'
import { useProjects } from '@/hooks/useProjects'

export function DashboardPage() {
  const { data: projects, isLoading } = useProjects()
  if (isLoading) return <div className="text-gray-400">Loading...</div>

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Projects</h1>
        <Link to="/projects/new"
          className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm font-medium">
          New Project
        </Link>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {projects?.map(p => (
          <Link key={p.id} to={`/p/${p.key}/issues`}
            className="bg-gray-900 border border-gray-800 rounded-lg p-5 hover:border-gray-600 transition-colors">
            <div className="flex items-center gap-2 mb-2">
              <span className="text-xs font-bold text-blue-400 bg-blue-900/30 px-2 py-0.5 rounded">{p.key}</span>
            </div>
            <h2 className="font-semibold text-white">{p.name}</h2>
            {p.description && <p className="text-sm text-gray-400 mt-1 line-clamp-2">{p.description}</p>}
          </Link>
        ))}
      </div>
    </div>
  )
}
```

- [ ] **Step 5: Create `frontend/src/pages/projects/ProjectListPage.tsx`**

```tsx
import { DashboardPage } from '../dashboard/DashboardPage'
// ProjectList and Dashboard show the same content at this stage
export { DashboardPage as ProjectListPage }
```

- [ ] **Step 6: Create `frontend/src/pages/projects/ProjectCreatePage.tsx`**

```tsx
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useCreateProject } from '@/hooks/useProjects'

export function ProjectCreatePage() {
  const navigate = useNavigate()
  const createProject = useCreateProject()
  const [form, setForm] = useState({ key: '', name: '', description: '' })
  const [error, setError] = useState('')

  const set = (f: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm(prev => ({ ...prev, [f]: e.target.value }))

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      const project = await createProject.mutateAsync(form)
      navigate(`/p/${project.key}/issues`)
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Failed to create project')
    }
  }

  return (
    <div className="max-w-lg">
      <h1 className="text-2xl font-bold mb-6">Create Project</h1>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {error && <p className="text-red-400 text-sm">{error}</p>}
        <div>
          <label className="block text-sm text-gray-400 mb-1">Project Key <span className="text-gray-500">(2-10 uppercase letters)</span></label>
          <input value={form.key} onChange={set('key')} placeholder="WOLF" required
            pattern="[A-Z0-9]+" minLength={2} maxLength={10}
            className="w-full bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm font-mono uppercase" />
        </div>
        <div>
          <label className="block text-sm text-gray-400 mb-1">Name</label>
          <input value={form.name} onChange={set('name')} placeholder="TaskWolf" required
            className="w-full bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm" />
        </div>
        <div>
          <label className="block text-sm text-gray-400 mb-1">Description <span className="text-gray-600">(optional)</span></label>
          <textarea value={form.description} onChange={set('description')} rows={3}
            className="w-full bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm resize-none" />
        </div>
        <button type="submit" disabled={createProject.isPending}
          className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded px-4 py-2 text-sm font-medium">
          {createProject.isPending ? 'Creating...' : 'Create Project'}
        </button>
      </form>
    </div>
  )
}
```

- [ ] **Step 7: Create `frontend/src/pages/issues/IssueListPage.tsx`**

```tsx
import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useIssues, useCreateIssue } from '@/hooks/useIssues'
import { StatusBadge } from '@/components/issue/StatusBadge'

export function IssueListPage() {
  const { key } = useParams<{ key: string }>()
  const { data: page, isLoading } = useIssues(key!)
  const createIssue = useCreateIssue(key!)
  const [title, setTitle] = useState('')
  const [showForm, setShowForm] = useState(false)

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) return
    await createIssue.mutateAsync({ title })
    setTitle('')
    setShowForm(false)
  }

  if (isLoading) return <div className="text-gray-400">Loading...</div>

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">{key} — Issues</h1>
        <button onClick={() => setShowForm(true)}
          className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm font-medium">
          + Create Issue
        </button>
      </div>

      {showForm && (
        <form onSubmit={handleCreate} className="mb-4 flex gap-2">
          <input value={title} onChange={e => setTitle(e.target.value)} placeholder="Issue title" autoFocus required
            className="flex-1 bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm" />
          <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm">Save</button>
          <button type="button" onClick={() => setShowForm(false)} className="text-gray-400 hover:text-white px-3 py-2 text-sm">Cancel</button>
        </form>
      )}

      <div className="flex flex-col gap-2">
        {page?.content.map(issue => (
          <Link key={issue.id} to={`/p/${key}/issues/${issue.key}`}
            className="bg-gray-900 border border-gray-800 hover:border-gray-600 rounded-lg px-4 py-3 flex items-center gap-4">
            <span className="text-xs text-gray-500 font-mono w-20">{issue.key}</span>
            <span className="flex-1 text-sm text-white">{issue.title}</span>
            <StatusBadge name={issue.statusName} category={issue.statusCategory} />
            <span className={`text-xs px-2 py-0.5 rounded font-medium ${
              issue.priority === 'CRITICAL' ? 'text-red-400' :
              issue.priority === 'HIGH' ? 'text-orange-400' :
              issue.priority === 'MEDIUM' ? 'text-yellow-400' : 'text-green-400'
            }`}>{issue.priority}</span>
          </Link>
        ))}
        {page?.content.length === 0 && (
          <p className="text-gray-500 text-sm py-8 text-center">No issues yet. Create your first one!</p>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 8: Create `frontend/src/pages/issues/IssueDetailPage.tsx`**

```tsx
import { useParams } from 'react-router-dom'
import { useIssue, useUpdateIssue } from '@/hooks/useIssues'
import { StatusBadge } from '@/components/issue/StatusBadge'

export function IssueDetailPage() {
  const { key, issueKey } = useParams<{ key: string; issueKey: string }>()
  const { data: issue, isLoading } = useIssue(key!, issueKey!)
  const updateIssue = useUpdateIssue(key!)

  if (isLoading) return <div className="text-gray-400">Loading...</div>
  if (!issue) return <div className="text-red-400">Issue not found</div>

  return (
    <div className="max-w-3xl">
      <div className="flex items-center gap-3 mb-2">
        <span className="text-sm text-gray-500 font-mono">{issue.key}</span>
        <span className="text-xs px-2 py-0.5 bg-gray-800 rounded text-gray-400">{issue.type}</span>
        <StatusBadge name={issue.statusName} category={issue.statusCategory} />
      </div>
      <h1 className="text-2xl font-bold text-white mb-6">{issue.title}</h1>

      <div className="grid grid-cols-3 gap-6">
        <div className="col-span-2">
          <h2 className="text-sm font-medium text-gray-400 mb-2">Description</h2>
          <div className="bg-gray-900 rounded-lg p-4 text-sm text-gray-300 min-h-32">
            {issue.description ?? <span className="text-gray-600 italic">No description</span>}
          </div>
        </div>
        <div className="flex flex-col gap-4">
          <div>
            <label className="text-xs text-gray-500 uppercase mb-1 block">Priority</label>
            <span className={`text-sm font-medium ${
              issue.priority === 'CRITICAL' ? 'text-red-400' :
              issue.priority === 'HIGH' ? 'text-orange-400' :
              issue.priority === 'MEDIUM' ? 'text-yellow-400' : 'text-green-400'
            }`}>{issue.priority}</span>
          </div>
          {issue.storyPoints != null && (
            <div>
              <label className="text-xs text-gray-500 uppercase mb-1 block">Story Points</label>
              <span className="text-sm text-white">{issue.storyPoints}</span>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 9: Create `frontend/src/index.css`**

```css
@import "tailwindcss";
```

- [ ] **Step 10: Verify build**

```bash
cd frontend && npm run build 2>&1 | tail -5
```
Expected: `✓ built in` — no errors.

- [ ] **Step 11: Commit**

```bash
git add frontend/src/
git commit -m "feat: add projects and issues pages — list, create, detail"
```

---

## Task 11: Docker Setup & Final Integration

**Files:**
- Create: `docker/nginx.conf`
- Create: `docker-compose.yml`
- Create: `docker-compose.dev.yml`
- Create: `.env.example`
- Create: `backend/Dockerfile`
- Create: `frontend/Dockerfile`
- Create: `.gitignore`

- [ ] **Step 1: Create `backend/Dockerfile`**

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Create `frontend/Dockerfile`**

```dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx-spa.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

`frontend/nginx-spa.conf`:
```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

- [ ] **Step 3: Create `docker/nginx.conf`**

```nginx
upstream backend {
    server app:8080;
}

server {
    listen 80;

    location /api/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /ws {
        proxy_pass http://backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    location / {
        proxy_pass http://frontend:80;
    }
}
```

- [ ] **Step 4: Create `docker-compose.yml`**

```yaml
services:
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./docker/nginx.conf:/etc/nginx/conf.d/default.conf:ro
    depends_on:
      - app
      - frontend

  app:
    build: ./backend
    environment:
      SPRING_PROFILES_ACTIVE: prod
      TW_DB_URL: ${TW_DB_URL:-jdbc:postgresql://db:5432/taskowolf}
      TW_DB_USER: ${TW_DB_USER:-taskowolf}
      TW_DB_PASS: ${TW_DB_PASS:-changeme}
      TW_JWT_SECRET: ${TW_JWT_SECRET}
      TW_BASE_URL: ${TW_BASE_URL:-http://localhost}
      TW_STORAGE_PATH: /data/attachments
    volumes:
      - attachments:/data/attachments
    depends_on:
      db:
        condition: service_healthy

  frontend:
    build: ./frontend

  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: taskowolf
      POSTGRES_USER: ${TW_DB_USER:-taskowolf}
      POSTGRES_PASSWORD: ${TW_DB_PASS:-changeme}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U taskowolf"]
      interval: 5s
      timeout: 5s
      retries: 10

volumes:
  postgres_data:
  attachments:
```

- [ ] **Step 5: Create `docker-compose.dev.yml`**

```yaml
services:
  app:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      TW_JWT_SECRET: dev-secret-change-in-production-min-256-bits
```

- [ ] **Step 6: Create `.env.example`**

```env
TW_JWT_SECRET=replace-with-a-secure-256-bit-random-string
TW_DB_USER=taskowolf
TW_DB_PASS=changeme
TW_BASE_URL=http://localhost
```

- [ ] **Step 7: Create `.gitignore`**

```
# Build
backend/build/
frontend/dist/
frontend/node_modules/

# Env
.env
.env.local

# IDE
.idea/workspace.xml
*.iml

# OS
.DS_Store
Thumbs.db

# Data
data/

# Superpowers (mockups, not needed in git)
.superpowers/brainstorm/
```

- [ ] **Step 8: Build backend JAR**

```bash
cd backend && ./gradlew bootJar 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`, JAR in `build/libs/`.

- [ ] **Step 9: Full integration test with docker-compose**

```bash
cp .env.example .env
# Edit .env: set TW_JWT_SECRET to a real secret (min 32 chars)
docker compose build && docker compose up -d
sleep 20
curl -s http://localhost/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@taskowolf.com","displayName":"Admin","password":"password123"}'
```
Expected: `{"accessToken":"...","refreshToken":"..."}`

- [ ] **Step 10: Open browser and verify full flow**

1. Open `http://localhost` — should see TaskWolf login page
2. Click Register, create account
3. Create a project (key: `DEMO`, name: `Demo Project`)
4. Create an issue
5. Click the issue to see detail view

- [ ] **Step 11: Final commit**

```bash
git add .
git commit -m "feat: complete Phase 1 — Docker setup, full stack integration"
```

---

## Self-Review Notes

- All 7 tasks have actual code, no TBDs
- `IssueService` uses `projectService.requireMember()` for authorization on every operation
- `IssueCreatedEvent` and `IssueStatusChangedEvent` are published but not yet consumed (Phase 3 adds the notification listener)
- `WorkflowService.createDefault()` is called inside `ProjectService.create()` — requires `WorkflowService` injection into `ProjectService`
- The `ForbiddenException` in `AuthService.kt` duplicates the one in `GlobalExceptionHandler.kt` — consolidate: move `ForbiddenException` to `core/infrastructure/GlobalExceptionHandler.kt` and import it in `AuthService`
- Frontend `ProjectListPage` re-exports `DashboardPage` as a temporary measure; these can be split in Phase 3

---

## Phase Boundaries

This plan delivers Phase 1. Subsequent phases each get their own plan:
- **Phase 2:** `docs/superpowers/plans/YYYY-MM-DD-phase2-agile-boards.md`
- **Phase 3:** `docs/superpowers/plans/YYYY-MM-DD-phase3-collaboration.md`
- Phases 4–7: planned at the start of each session
