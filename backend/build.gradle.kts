import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.owasp.dependencycheck") version "11.1.1"
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.spring") version "2.4.0"
    kotlin("plugin.jpa") version "2.4.0"
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
}

// Override Spring Boot BOM version for Testcontainers to support Docker Desktop 4.x on Windows
dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.21.4")
    }
}

dependencyCheck {
    failBuildOnCVSS = 7.0f   // High/Critical blockieren
    suppressionFile = "dependency-check-suppressions.xml"
    nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""
    formats = listOf("HTML", "SARIF")
    analyzers.assemblyEnabled = false
    analyzers.nodeEnabled = false
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "21"
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
