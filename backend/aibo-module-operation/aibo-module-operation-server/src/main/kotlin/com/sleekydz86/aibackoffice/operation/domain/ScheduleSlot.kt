package com.sleekydz86.aibackoffice.operation.domain

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDateTime

enum class SlotStatus { DRAFT, PUBLISHED, CANCELED }

@Entity
@Table(name = "schedule_slots")
class ScheduleSlot(
    @Column(name = "channel_id", nullable = false)
    var channelId: Long,
    @Column(name = "content_id", nullable = false)
    var contentId: Long,
    @Column(name = "episode_id")
    var episodeId: Long?,
    @Column(name = "content_rating", length = 10)
    var contentRating: String?,
    @Column(name = "start_at", nullable = false)
    var startAt: LocalDateTime,
    @Column(name = "end_at", nullable = false)
    var endAt: LocalDateTime,
    @Column(name = "created_by", nullable = false, length = 100)
    var createdBy: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var status: SlotStatus = SlotStatus.DRAFT

    @Column(name = "approval_id")
    var approvalId: Long? = null

    @Version
    @Column(nullable = false)
    var version: Int = 0

    fun requireId(): Long = id ?: throw IllegalStateException("저장되지 않은 편성입니다.")

    fun window(): SlotWindow = SlotWindow(id, startAt, endAt)

    fun alreadyDone(target: SlotStatus, approvalId: Long): Boolean = status == target && this.approvalId == approvalId

    fun publish(approvalId: Long) {
        if (status != SlotStatus.DRAFT) {
            throw BusinessException(ErrorCode.INVALID_STATE, "초안인 편성만 게시할 수 있습니다. 지금 상태는 ${status.name} 입니다.")
        }
        status = SlotStatus.PUBLISHED
        this.approvalId = approvalId
    }

    fun cancel(approvalId: Long) {
        if (status == SlotStatus.CANCELED) {
            throw BusinessException(ErrorCode.INVALID_STATE, "이미 취소된 편성입니다.")
        }
        status = SlotStatus.CANCELED
        this.approvalId = approvalId
    }
}
