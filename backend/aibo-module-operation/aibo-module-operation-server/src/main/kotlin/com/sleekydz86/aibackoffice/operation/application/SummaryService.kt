package com.sleekydz86.aibackoffice.operation.application

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.operation.domain.IssueStatus
import com.sleekydz86.aibackoffice.operation.domain.Severity
import com.sleekydz86.aibackoffice.operation.domain.SlotStatus
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.ChannelRepository
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.ContentExposureStore
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IssueRepository
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.ScheduleSlotRepository
import java.time.Clock
import java.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SummaryService(
    private val channels: ChannelRepository,
    private val slots: ScheduleSlotRepository,
    private val issues: IssueRepository,
    private val exposures: ContentExposureStore,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun summary(user: LoginUser): SummaryView {
        val canSchedule = user.has(Permissions.SCHEDULE_READ)
        val canIssue = user.has(Permissions.ISSUE_READ)
        if (!canSchedule && !canIssue) {
            throw BusinessException(ErrorCode.PERMISSION_DENIED, "요약을 보려면 schedule:read 나 issue:read 권한이 필요합니다.")
        }
        val channelIds = if (canSchedule && user.scopes.isNotEmpty()) {
            channels.findByCodeIn(user.scopes).map { it.requireId() }
        } else {
            emptyList()
        }
        val today = LocalDate.now(clock)
        val todaySlots = if (channelIds.isEmpty()) 0 else
            slots.countStarting(channelIds, today.atStartOfDay(), today.plusDays(1).atStartOfDay(), SlotStatus.CANCELED)
        val draftSlots = if (channelIds.isEmpty()) 0 else slots.countByChannelIdInAndStatus(channelIds, SlotStatus.DRAFT)
        val bySeverity = Severity.entries.associateWith { 0L }.toMutableMap()
        if (canIssue) {
            issues.countBySeverity(IssueStatus.RESOLVED).forEach { bySeverity[it.severity] = it.total }
        }
        val hidden = if (canIssue) exposures.count(false) else 0
        return SummaryView(todaySlots, draftSlots, bySeverity, hidden)
    }
}
