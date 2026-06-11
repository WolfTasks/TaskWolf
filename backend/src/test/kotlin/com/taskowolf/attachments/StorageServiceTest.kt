package com.taskowolf.attachments

import com.taskowolf.attachments.application.StorageService
import com.taskowolf.core.infrastructure.NotFoundException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.nio.file.Path

class StorageServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private fun service() = StorageService(tempDir.toString()).also {
        it.init()
    }

    @Test
    fun `store saves file and returns stored name`() {
        val svc = service()
        val file = MockMultipartFile("file", "test.pdf", "application/pdf", "hello".toByteArray())

        val storedName = svc.store(file)

        assertTrue(storedName.endsWith(".pdf"))
        assertTrue(tempDir.resolve(storedName).toFile().exists())
    }

    @Test
    fun `load returns resource for existing file`() {
        val svc = service()
        val file = MockMultipartFile("file", "doc.txt", "text/plain", "content".toByteArray())
        val storedName = svc.store(file)

        val resource = svc.load(storedName)

        assertTrue(resource.exists())
        assertEquals("content", resource.inputStream.use { it.bufferedReader().readText() })
    }

    @Test
    fun `load throws NotFoundException for missing file`() {
        val svc = service()
        assertThrows<NotFoundException> { svc.load("nonexistent.pdf") }
    }

    @Test
    fun `delete removes the stored file`() {
        val svc = service()
        val file = MockMultipartFile("file", "remove.txt", "text/plain", "bye".toByteArray())
        val storedName = svc.store(file)

        svc.delete(storedName)

        assertFalse(tempDir.resolve(storedName).toFile().exists())
    }

    @Test
    fun `load rejects path traversal attempt`() {
        val svc = service()
        assertThrows<NotFoundException> { svc.load("../etc/passwd") }
    }
}
