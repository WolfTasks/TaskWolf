package com.taskowolf.servicedesk.application

import com.taskowolf.issues.application.IssueService
import com.taskowolf.servicedesk.infrastructure.ServiceDeskRepository
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.mail.dsl.Mail
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("taskowolf.mail.imap.enabled", havingValue = "true")
class EmailIngestionService(
    private val serviceDeskRepo: ServiceDeskRepository,
    private val issueService: IssueService,
    @Value("\${taskowolf.mail.imap.host}") private val host: String,
    @Value("\${taskowolf.mail.imap.port}") private val port: Int,
    @Value("\${taskowolf.mail.imap.user}") private val user: String,
    @Value("\${taskowolf.mail.imap.password}") private val password: String,
    @Value("\${taskowolf.mail.imap.polling-interval}") private val pollingInterval: Long
) {
    private val log = LoggerFactory.getLogger(EmailIngestionService::class.java)

    @Bean
    fun imapFlow() = integrationFlow(
        Mail.imapInboundAdapter("imaps://$user:$password@$host:$port/INBOX")
            .autoCloseFolder(false)
            .shouldDeleteMessages(false),
        { poller { it.fixedDelay(pollingInterval) } }
    ) {
        handle { springMsg: Message<*> ->
            val msg = springMsg.payload as? MimeMessage ?: return@handle
            val subject = msg.subject ?: return@handle
            val body = runCatching { msg.content?.toString() ?: "" }.getOrDefault("")
            val from = msg.from?.firstOrNull()?.toString() ?: "unknown"
            log.info("Email ingestion: processing message from={} subject={}", from, subject)
            serviceDeskRepo.findAll()
                .filter { it.enabled && it.emailAddress != null }
                .forEach { desk ->
                    runCatching {
                        issueService.createTicketFromEmail(desk.projectId, subject, body, from)
                    }.onFailure { ex ->
                        log.error("Failed to create ticket from email for project {}: {}", desk.projectId, ex.message)
                    }
                }
        }
    }
}
