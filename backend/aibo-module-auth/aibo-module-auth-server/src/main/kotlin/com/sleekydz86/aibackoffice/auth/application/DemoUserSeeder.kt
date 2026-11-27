package com.sleekydz86.aibackoffice.auth.application

import com.sleekydz86.aibackoffice.auth.domain.User
import com.sleekydz86.aibackoffice.auth.infrastructure.config.AuthProperties
import com.sleekydz86.aibackoffice.auth.infrastructure.persistence.RoleRepository
import com.sleekydz86.aibackoffice.auth.infrastructure.persistence.TeamRepository
import com.sleekydz86.aibackoffice.auth.infrastructure.persistence.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DemoUserSeeder(
    private val users: UserRepository,
    private val teams: TeamRepository,
    private val roles: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val properties: AuthProperties,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    data class DemoUser(val email: String, val name: String, val team: String, val roles: List<String>, val channels: List<String>)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (properties.seedPassword.isBlank() || users.count() > 0) return
        val hash = passwordEncoder.encode(properties.seedPassword)
        DEMO_USERS.forEach { demo ->
            val user = User(demo.email, demo.name, hash ?: "", teams.findByName(demo.team))
            user.replaceRoles(roles.findByCodeIn(demo.roles))
            users.save(user)
            user.replaceChannels(demo.channels)
        }
        log.info("데모 사용자 {}명을 만들었습니다.", DEMO_USERS.size)
    }

    companion object {
        private val ALL_CHANNELS = listOf("TVN", "OCN", "JTBC", "MNET")

        val DEMO_USERS = listOf(
            DemoUser("admin@demo.local", "관리자", "서비스운영팀", listOf("ADMIN"), ALL_CHANNELS),
            DemoUser("scheduler@demo.local", "편성 담당", "편성팀", listOf("SCHEDULER"), listOf("TVN", "OCN")),
            DemoUser("scheduler2@demo.local", "편성 담당 둘", "편성팀", listOf("SCHEDULER"), listOf("JTBC")),
            DemoUser("operator@demo.local", "콘텐츠 운영", "콘텐츠운영팀", listOf("CONTENT_OPERATOR"), emptyList()),
            DemoUser("issue@demo.local", "이슈 담당", "서비스운영팀", listOf("ISSUE_MANAGER"), emptyList()),
            DemoUser("approver@demo.local", "승인자", "편성팀", listOf("APPROVER"), listOf("TVN", "OCN")),
            DemoUser("viewer@demo.local", "조회 전용", "서비스운영팀", listOf("VIEWER"), emptyList()),
        )
    }
}
