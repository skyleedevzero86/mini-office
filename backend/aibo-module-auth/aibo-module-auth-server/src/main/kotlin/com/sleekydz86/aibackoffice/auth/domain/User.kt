package com.sleekydz86.aibackoffice.auth.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "teams")
class Team(
    @Column(nullable = false, length = 100)
    var name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

@Entity
@Table(name = "permissions")
class Permission(
    @Column(nullable = false, length = 50)
    var code: String,
    @Column(nullable = false, length = 200)
    var description: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

@Entity
@Table(name = "roles")
class Role(
    @Column(nullable = false, length = 30)
    var code: String,
    @Column(nullable = false, length = 100)
    var name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = [JoinColumn(name = "role_id")],
        inverseJoinColumns = [JoinColumn(name = "permission_id")],
    )
    var permissions: MutableSet<Permission> = mutableSetOf()
}

enum class UserStatus { ACTIVE, LOCKED }

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, length = 200)
    var email: String,
    @Column(nullable = false, length = 100)
    var name: String,
    @Column(name = "password_hash", nullable = false, length = 100)
    var passwordHash: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    var team: Team?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var status: UserStatus = UserStatus.ACTIVE

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")],
    )
    var roles: MutableSet<Role> = mutableSetOf()

    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    var scopes: MutableSet<UserScope> = mutableSetOf()

    val active: Boolean get() = status == UserStatus.ACTIVE

    fun roleCodes(): Set<String> = roles.map { it.code }.toSortedSet()

    fun permissionCodes(): Set<String> = roles.flatMap { role -> role.permissions.map { it.code } }.toSortedSet()

    fun channelCodes(): Set<String> =
        scopes.filter { it.scopeType == UserScope.CHANNEL }.map { it.scopeValue }.toSortedSet()

    fun replaceRoles(newRoles: Collection<Role>) {
        roles.clear()
        roles.addAll(newRoles)
    }

    fun replaceChannels(channels: Collection<String>) {
        scopes.removeIf { it.scopeType == UserScope.CHANNEL }
        channels.distinct().forEach { scopes.add(UserScope(this, UserScope.CHANNEL, it)) }
    }

    fun requireId(): Long = id ?: throw IllegalStateException("저장되지 않은 사용자입니다.")
}

@Entity
@Table(name = "user_scopes")
class UserScope(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    @Column(name = "scope_type", nullable = false, length = 20)
    var scopeType: String,
    @Column(name = "scope_value", nullable = false, length = 50)
    var scopeValue: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    override fun equals(other: Any?): Boolean =
        other is UserScope && other.scopeType == scopeType && other.scopeValue == scopeValue

    override fun hashCode(): Int = 31 * scopeType.hashCode() + scopeValue.hashCode()

    companion object {
        const val CHANNEL = "CHANNEL"
    }
}

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    var tokenHash: String,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null

    fun usable(now: LocalDateTime): Boolean = revokedAt == null && expiresAt.isAfter(now)

    fun revoke(now: LocalDateTime) {
        if (revokedAt == null) revokedAt = now
    }
}
