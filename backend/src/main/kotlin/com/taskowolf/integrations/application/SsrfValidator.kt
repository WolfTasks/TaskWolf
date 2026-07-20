package com.taskowolf.integrations.application

import com.taskowolf.core.infrastructure.BadRequestException
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.URL

@Component
class SsrfValidator {
    fun validate(url: String) {
        val host = try { URL(url).host } catch (e: Exception) {
            throw BadRequestException.keyed("integration.invalidUrl", url)
        }
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) {
            // Cannot resolve — not a private IP issue; the delivery will simply fail
            return
        }
        for (addr in addresses) {
            if (isPrivate(addr)) {
                throw BadRequestException.keyed("integration.blockedAddress")
            }
        }
    }

    private fun isPrivate(addr: InetAddress): Boolean =
        addr.isLoopbackAddress ||
        addr.isSiteLocalAddress ||
        addr.isLinkLocalAddress ||
        addr.isAnyLocalAddress ||
        addr.isMulticastAddress
}
