package com.taskowolf.attachments.application

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import com.taskowolf.core.infrastructure.NotFoundException
import jakarta.annotation.PostConstruct

@Service
class StorageService(
    @Value("\${taskowolf.attachment.path:./data/attachments}") private val storagePath: String
) {
    private lateinit var rootPath: Path

    @PostConstruct
    fun init() {
        rootPath = Paths.get(storagePath).toAbsolutePath().normalize()
        Files.createDirectories(rootPath)
    }

    fun store(file: MultipartFile): String {
        val extension = file.originalFilename
            ?.substringAfterLast('.', "")
            ?.let { if (it.isNotBlank()) ".$it" else "" }
            ?: ""
        val storedName = "${UUID.randomUUID()}$extension"
        val target = rootPath.resolve(storedName)
        file.transferTo(target)
        return storedName
    }

    fun load(storedName: String): Resource {
        val file = rootPath.resolve(storedName).normalize()
        if (!file.startsWith(rootPath)) {
            throw NotFoundException("File not found: $storedName")
        }
        val resource = UrlResource(file.toUri())
        if (!resource.exists()) throw NotFoundException("File not found: $storedName")
        return resource
    }

    fun delete(storedName: String) {
        val file = rootPath.resolve(storedName).normalize()
        if (file.startsWith(rootPath)) {
            Files.deleteIfExists(file)
        }
    }
}
