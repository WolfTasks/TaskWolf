import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.spring") version "2.4.20"
    kotlin("plugin.jpa") version "2.4.20"
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
    // M3 Phase 1: keep Jackson 2 as the active mapper (HTTP + STOMP, see application.yml).
    // Removed in Phase 2 together with all com.fasterxml.jackson.databind usages.
    implementation("org.springframework.boot:spring-boot-jackson2")
    implementation("org.springframework.integration:spring-integration-mail")
    implementation("org.springframework.integration:spring-integration-core")
    implementation("jakarta.mail:jakarta.mail-api")
    implementation("org.eclipse.angus:angus-mail")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    runtimeOnly("com.h2database:h2")
    // Boot 4 moved the H2 web console (dev profile, /h2-console) into its own module.
    runtimeOnly("org.springframework.boot:spring-boot-h2console")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("io.mockk:mockk:1.14.11")
    // okhttp3 is not managed by the Spring Boot BOM; pin explicitly.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // commons-compress 1.24.0 -> 1.26.0 fixes DoS (infinite loop on DUMP file / OOM on Pack200,
    // Dependabot #1/#2). Test-scope only (transitive via Testcontainers).
    constraints {
        testImplementation("org.apache.commons:commons-compress:1.28.0")
    }
}

// Security override of a Spring Boot 4.1.1 BOM-managed transitive version.
// jackson-2-bom 2.21.5 -> 2.21.7: Boot 4.1.1 still pins Jackson 2 at 2.21.5, which re-opens
// CVE-2026-68497 (quadratic BigInteger/BigDecimal parsing in CoreXMLDeserializers), CVE-2026-91776
// and CVE-2026-91777. 2.21.6 is NOT sufficient (verified against a current Trivy DB), 2.21.7 is the
// newest 2.x on Maven Central. Jackson 2 is only needed until M3 Phase 2 (Jackson 3 + jjwt-gson);
// this override goes away with it.
extra["jackson-2-bom.version"] = "2.21.7"

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
