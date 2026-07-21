package com.taskowolf.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Static guard: no user-facing free-text throw may reach production code — every such
 * throw must go through the `.keyed(...)` factory so it localizes. Catches BARE and
 * FULLY-QUALIFIED throws alike (`throw NotFoundException("…")` AND
 * `throw com.taskowolf...NotFoundException("…")`) — the FQ form is the blind spot that
 * let an un-keyed AttachmentController throw ship through the sweep. `.keyed(` never
 * matches (the exception name is followed by `.keyed(`, not `("`).
 *
 * Allowlisted: the two intentional internal invariants (Decision 2), which are NOT
 * user-facing and stay as `?: error(...)`.
 */
class NoUnkeyedUserFacingThrowTest {

    private fun sourceRoot(): File {
        val direct = File("src/main/kotlin")
        return if (direct.isDirectory) direct else File("backend/src/main/kotlin")
    }

    // (fileName, substring of the offending line) — Decision-2 internal invariants.
    private val allowlist = listOf(
        "SsoController.kt" to "clientSecret required",
        "OidcUserProvisioningService.kt" to "OIDC user has no email",
    )

    private val patterns = listOf(
        // free-text throw of a keyed domain exception (bare or fully-qualified)
        Regex("""throw\s+(?:[\w.]+\.)?(?:NotFoundException|ForbiddenException|ConflictException|BadRequestException)\s*\(\s*""""),
        // free-text orElseThrow lambda (bare or fully-qualified)
        Regex("""orElseThrow\s*\{\s*(?:[\w.]+\.)?(?:NotFoundException|ForbiddenException|ConflictException|BadRequestException)\s*\(\s*""""),
        // nullable-fallback error() → user-facing IllegalStateException
        Regex("""\?:\s*error\s*\("""),
        // web-layer / security constructs that should be keyed instead
        Regex("""throw\s+ResponseStatusException\s*\("""),
        Regex("""throw\s+AccessDeniedException\s*\("""),
    )

    private fun allowlisted(fileName: String, line: String) =
        allowlist.any { (f, s) -> fileName == f && line.contains(s) }

    @Test
    fun `no un-keyed user-facing throw in production code`() {
        val root = sourceRoot()
        assertTrue(root.isDirectory) { "sourceRoot() did not resolve to a directory: ${root.absolutePath}" }

        val violations = mutableListOf<String>()
        var filesScanned = 0
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                filesScanned++
                file.readLines().forEachIndexed { i, line ->
                    if (patterns.any { it.containsMatchIn(line) } && !allowlisted(file.name, line)) {
                        violations.add("${file.name}:${i + 1}  ${line.trim()}")
                    }
                }
            }

        assertTrue(filesScanned > 0) { "guard scanned no files under ${root.absolutePath}" }
        assertTrue(violations.isEmpty()) {
            "Un-keyed user-facing throw(s) found — use `.keyed(\"key\", …)` " +
                "(or allowlist a genuine internal invariant):\n" + violations.joinToString("\n")
        }
    }
}
