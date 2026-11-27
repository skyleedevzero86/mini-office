package com.sleekydz86.aibackoffice.auth.infrastructure.persistence

import com.sleekydz86.aibackoffice.auth.domain.Permission
import com.sleekydz86.aibackoffice.auth.domain.RefreshToken
import com.sleekydz86.aibackoffice.auth.domain.Role
import com.sleekydz86.aibackoffice.auth.domain.Team
import com.sleekydz86.aibackoffice.auth.domain.User
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
}

interface TeamRepository : JpaRepository<Team, Long> {
    fun findByName(name: String): Team?
}

interface RoleRepository : JpaRepository<Role, Long> {
    fun findByCodeIn(codes: Collection<String>): List<Role>
}

interface PermissionRepository : JpaRepository<Permission, Long>

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByTokenHash(tokenHash: String): RefreshToken?

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    fun revokeAllByUserId(userId: Long, now: LocalDateTime): Int
}
