package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.application.JwtService
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component

@Component
class JwtStompInterceptor(
    private val jwtService: JwtService,
    private val userRepository: UserRepository
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
        if (accessor?.command == StompCommand.CONNECT) {
            val token = accessor.getNativeHeader("Authorization")
                ?.firstOrNull()
                ?.takeIf { it.startsWith("Bearer ") }
                ?.substring(7)

            if (token != null) {
                val userId = jwtService.validateToken(token)
                if (userId != null) {
                    val user = userRepository.findById(userId).orElse(null)
                    if (user != null) {
                        accessor.user = UsernamePasswordAuthenticationToken(
                            user, null,
                            listOf(SimpleGrantedAuthority("ROLE_${user.systemRole.name}"))
                        )
                    }
                }
            }
        }
        return message
    }
}
