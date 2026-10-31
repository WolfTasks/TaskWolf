import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Spring Boot 4.1.1 Gradle plugin brings Jackson 3.1.5 onto the *build* classpath
// (GHSA-p6pp-m3f8-5c89, GHSA-7hhh-6rmp-j9qf). It never ships in the app, but GitHub's dependency
// submission reports the plugin classpath and dependency-review fails on it. Same minimal fix as
// the runtime jackson-bom override below.
buildscript {
    dependencies {
        constraints {
            classpath("tools.jackson.core:jackson-core:3.2.4")
            classpath("tools.jackson.core:jackson-databind:3.2.4")
        }
    }
}

plugins {
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    kotlin("plugin.jpa") version "2.4.21"
}

group = "com.taskowolf"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Boot 4 split auto-configuration into modules: flyway-core alone no longer runs migrations.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    // Boot 4 split auto-configuration into modules: spring-integration-core alone no longer gets
    // @EnableIntegration, so the IMAP ingestion flow would never start (guard:
    // ImapIngestionFlowStartedIntegrationTest).
    implementation("org.springframework.boot:spring-boot-starter-integration")
    implementation("org.springframework.integration:spring-integration-mail")
    implementation("jakarta.mail:jakarta.mail-api")
    implementation("org.eclipse.angus:angus-mail")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")
    // Jackson 2 Kotlin module for springdoc/swagger-core only (they still run on Jackson 2): without it
    // the OpenAPI schemas lose `required` derived from Kotlin nullability. Our code uses Jackson 3.
    // Guard: OpenApiKotlinSchemaIntegrationTest.
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    // jjwt-gson instead of jjwt-jackson (M3 Phase 2): jjwt-jackson only exists for Jackson 2, and
    // untrusted JWT headers/claims should not go through Jackson 2. Token format is unchanged
    // (guard: JwtServiceTest fixture token issued with jjwt-jackson).
    runtimeOnly("io.jsonwebtoken:jjwt-gson:0.13.0")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.security:spring-security-test")
    // H2 only backs the @DataJpaTest slices (Flyway off); the app itself runs on PostgreSQL, the dev profile too (B7).
    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("io.mockk:mockk:1.14.11")
    // okhttp3 is not managed by the Spring Boot BOM; pin explicitly.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.1")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// `./gradlew bootRun` starts with the dev profile (local PostgreSQL from docker-compose.dev.yml) unless
// SPRING_PROFILES_ACTIVE says otherwise.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    if (System.getenv("SPRING_PROFILES_ACTIVE") == null) systemProperty("spring.profiles.active", "dev")
}
