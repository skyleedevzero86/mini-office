package com.sleekydz86.aibackoffice.operation.infrastructure.persistence

import com.sleekydz86.aibackoffice.operation.domain.Issue
import com.sleekydz86.aibackoffice.operation.domain.IssueComment
import com.sleekydz86.aibackoffice.operation.domain.IssueStatus
import com.sleekydz86.aibackoffice.operation.domain.IssueType
import com.sleekydz86.aibackoffice.operation.domain.Severity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface SeverityCount {
    val severity: Severity
    val total: Long
}

interface IssueRepository : JpaRepository<Issue, Long>, JpaSpecificationExecutor<Issue> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Issue i where i.id = :id")
    fun findLockedById(id: Long): Issue?

    @Query("select i.severity as severity, count(i) as total from Issue i where i.status <> :excluded group by i.severity")
    fun countBySeverity(excluded: IssueStatus): List<SeverityCount>
}

interface IssueCommentRepository : JpaRepository<IssueComment, Long> {
    fun findByIssueIdOrderByCreatedAtAscIdAsc(issueId: Long): List<IssueComment>
}

object IssueSpecs {

    fun type(type: IssueType): Specification<Issue> =
        Specification { root, _, builder -> builder.equal(root.get<IssueType>("type"), type) }

    fun severity(severity: Severity): Specification<Issue> =
        Specification { root, _, builder -> builder.equal(root.get<Severity>("severity"), severity) }

    fun status(status: IssueStatus): Specification<Issue> =
        Specification { root, _, builder -> builder.equal(root.get<IssueStatus>("status"), status) }

    fun contentId(contentId: Long): Specification<Issue> =
        Specification { root, _, builder -> builder.equal(root.get<Long>("contentId"), contentId) }

    fun keyword(keyword: String): Specification<Issue> {
        val pattern = "%" + keyword.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return Specification { root, _, builder ->
            builder.or(
                builder.like(builder.lower(root.get("title")), pattern, '\\'),
                builder.like(builder.lower(root.get("description")), pattern, '\\'),
            )
        }
    }
}
