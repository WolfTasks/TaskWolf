import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Spring Boot 4.1.1 Gradle plugin brings Jackson 3.1.5 onto the *build* classpath
// (GHSA-p6pp-m3f8-5c89, GHSA-7hhh-6rmp-j9qf). It never ships in the app, but GitHub's dependency
// submission reports the plugin classpath and dependency-review fails on it. Same minimal fix as
// the runtime jackson-bom override below.
buildscript {
    dependencies {
        constraints {
            classpath("tools.jackson.core:jackson-core:3.1.7")
            classpath("tools.jackson.core:jackson-databind:3.1.7")
        }
    }
}

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
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    // Jackson 2 Kotlin module for springdoc/swagger-core only (they still run on Jackson 2): without it
    // the OpenAPI schemas lose `required` derived from Kotlin nullability. Our code uses Jackson 3.
    // Guard: OpenApiKotlinSchemaIntegrationTest.
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    // jjwt-gson instead of jjwt-jackson (M3 Phase 2): jjwt-jackson only exists for Jackson 2, and
    // untrusted JWT headers/claims should not go through Jackson 2. Token format is unchanged
    // (guard: JwtServiceTest fixture token issued with jjwt-jackson).
    runtimeOnly("io.jsonwebtoken:jjwt-gson:0.13.0")
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
}

// Security override of a Spring Boot 4.1.1 BOM-managed transitive version.
// jackson-2-bom 2.21.5 -> 2.21.7: Boot 4.1.1 still pins Jackson 2 at 2.21.5, which re-opens
// CVE-2026-68497 (quadratic BigInteger/BigDecimal parsing in CoreXMLDeserializers), CVE-2026-91776
// and CVE-2026-91777. 2.21.6 is NOT sufficient (verified against a current Trivy DB), 2.21.7 is the
// newest 2.x on Maven Central.
// Jackson 2 is no longer used by our code or by Spring, but springdoc 3.1.1 still pulls it in
// transitively via swagger-core-jakarta 2.2.55 (no Jackson 3 swagger-core exists yet). Keep this
// override until swagger-core moves to Jackson 3.
extra["jackson-2-bom.version"] = "2.21.7"
// jackson-bom (Jackson 3, tools.jackson) 3.1.5 -> 3.1.7: the same advisories exist on the 3.x line.
// CVE-2026-68497 (fixed 3.1.6), CVE-2026-91776/91777 (databind) and CVE-2026-89407/89425 (core) are
// fixed in 3.1.7, the minimal release clearing all five. Boot 4.1.1 pins 3.1.5.
// Jackson 3 is the active mapper (HTTP, STOMP, application code).
extra["jackson-bom.version"] = "3.1.7"
// tomcat-embed 11.0.24 -> 11.0.25 fixes the three CRITICAL auth-bypass CVEs on the 11.0 line:
// CVE-2026-68525 (FORM authentication), CVE-2026-65905 (DIGEST replay), CVE-2026-65182 (security
// constraint bypass). Boot 4.1.1 pins 11.0.24; 11.0.25 is the minimal fixed release. Override the
// shared property so core, el and websocket move together.
extra["tomcat.version"] = "11.0.25"

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
