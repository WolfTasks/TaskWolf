package com.taskowolf.servicedesk

import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.integration.endpoint.SourcePollingChannelAdapter
import org.springframework.test.context.TestPropertySource

/**
 * Guard for the Spring Boot 4 module split: without Boot's Spring Integration auto-configuration
 * (@EnableIntegration) the imapFlow bean is registered but never turned into a running poller,
 * so service-desk e-mails are silently not ingested. The IMAP endpoint here is unreachable on
 * purpose; the test only checks that the polling adapter exists and runs.
 */
@TestPropertySource(
    properties = [
        "taskowolf.mail.imap.enabled=true",
        "taskowolf.mail.imap.host=localhost",
        "taskowolf.mail.imap.port=1",
        "taskowolf.mail.imap.user=guard",
        "taskowolf.mail.imap.password=guard",
        "taskowolf.mail.imap.polling-interval=3600000",
    ]
)
class ImapIngestionFlowStartedIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `IMAP ingestion flow runs as a polling adapter`() {
        val adapters = context.getBeansOfType(SourcePollingChannelAdapter::class.java)
        assertTrue(adapters.isNotEmpty(), "no SourcePollingChannelAdapter registered (imapFlow not processed)")
        assertTrue(adapters.values.all { it.isRunning }, "polling adapters not running: ${adapters.keys}")
    }
}
