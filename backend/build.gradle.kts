import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    kotlin("plugin.jpa") version "2.4.10"
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
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.integration:spring-integration-mail")
    implementation("org.springframework.integration:spring-integration-core")
    implementation("jakarta.mail:jakarta.mail-api")
    implementation("org.eclipse.angus:angus-mail")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("io.mockk:mockk:1.14.11")
    // okhttp3 is no longer managed by the Spring Boot 3.5 BOM (was 4.12.0 in the 3.3 BOM); pin explicitly.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // commons-compress 1.24.0 -> 1.26.0 fixes DoS (infinite loop on DUMP file / OOM on Pack200,
    // Dependabot #1/#2). Test-scope only (transitive via Testcontainers).
    constraints {
        testImplementation("org.apache.commons:commons-compress:1.28.0")
    }
}

// Security override of a Spring Boot 3.5.16 BOM-managed transitive version.
// commons-lang3 3.17.0 -> 3.18.0 fixes CVE-2025-48924 (BOM pins 3.17.0 even though swagger-core wants 3.20.0).
extra["commons-lang3.version"] = "3.18.0"
// jackson-bom 2.21.4 -> 2.21.5 fixes @JsonView bypass CVEs on jackson-databind
// (GHSA-mhm7-754m-9p8w and GHSA-5gvw-p9qm-jgwh / CVE-2026-59889, both medium; Dependabot #80/#81).
// 2.21.5 shipped to Maven Central; Spring Boot 3.5.16 BOM still pins 2.21.4. Override the shared
// jackson-bom property so the whole Jackson family (databind, core, module-kotlin, ...) moves together.
// jackson-databind is transitive-only here, so Dependabot's direct-only security update could not patch
// it (security_update_dependency_not_found) — this BOM override is the fix.
extra["jackson-bom.version"] = "2.21.5"
// logback-core 1.5.34 -> 1.5.35 fixes CVE (object injection via HardenedObjectInputStream, Dependabot #79).
// Spring Boot 3.5.16 BOM pins 1.5.34; override the shared property so logback-core AND logback-classic move together.
extra["logback.version"] = "1.5.35"
// postgresql 42.7.11 -> 42.7.12 fixes CVE-2026-54291 (HIGH). Spring Boot 3.5.16 BOM pins 42.7.11;
// override the managed property so the runtime JDBC driver picks up the patched release.
extra["postgresql.version"] = "42.7.12"

// Override Spring Boot BOM version for Testcontainers to support Docker Desktop 4.x on Windows
dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.21.4")
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    if (System.getProperty("os.name", "").lowercase().contains("win")) {
        // Docker Desktop 4.x enforces minimum API version 1.44.
        // docker-java (used by Testcontainers) negotiates starting at 1.32, which Docker Desktop rejects.
        // Setting DOCKER_HOST to TCP (exposed by Docker Desktop) and api.version=1.44 bypasses this issue.
        environment("DOCKER_HOST", "tcp://localhost:2375")
        systemProperty("api.version", "1.44")
    }
}
