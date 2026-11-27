package com.sleekydz86.aibackoffice.operation.application

import com.sleekydz86.aibackoffice.common.Zones
import com.sleekydz86.aibackoffice.operation.domain.Channel
import com.sleekydz86.aibackoffice.operation.domain.ConflictType
import com.sleekydz86.aibackoffice.operation.domain.ScheduleConflict
import com.sleekydz86.aibackoffice.operation.domain.ScheduleSlot
import com.sleekydz86.aibackoffice.operation.domain.SlotStatus
import java.time.LocalDateTime
import java.time.OffsetDateTime

data class ChannelView(val id: Long, val code: String, val name: String, val minRatingAfter22: String?) {
    companion object {
        fun from(channel: Channel): ChannelView =
            ChannelView(channel.requireId(), channel.code, channel.name, channel.minRatingAfter22)
    }
}

data class ScheduleSlotView(
    val id: Long,
    val channelCode: String,
    val contentId: Long,
    val episodeId: Long?,
    val contentRating: String?,
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime,
    val status: SlotStatus,
    val approvalId: Long?,
    val createdBy: String,
    val version: Int,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(slot: ScheduleSlot, channelCode: String): ScheduleSlotView = ScheduleSlotView(
            id = slot.requireId(),
            channelCode = channelCode,
            contentId = slot.contentId,
            episodeId = slot.episodeId,
            contentRating = slot.contentRating,
            startAt = SeoulTime.toOffset(slot.startAt),
            endAt = SeoulTime.toOffset(slot.endAt),
            status = slot.status,
            approvalId = slot.approvalId,
            createdBy = slot.createdBy,
            version = slot.version,
            createdAt = SeoulTime.toOffset(slot.createdAt),
        )
    }
}

data class ConflictView(val type: ConflictType, val message: String, val slotId: Long?) {
    companion object {
        fun from(conflict: ScheduleConflict): ConflictView = ConflictView(conflict.type, conflict.message, conflict.slotId)
    }
}

data class SlotDraftCommand(
    val channelCode: String? = null,
    val contentId: Long? = null,
    val episodeId: Long? = null,
    val contentRating: String? = null,
    val startAt: OffsetDateTime? = null,
    val endAt: OffsetDateTime? = null,
)

data class SlotFilter(
    val channelCode: String? = null,
    val from: OffsetDateTime? = null,
    val to: OffsetDateTime? = null,
    val status: SlotStatus? = null,
)

data class ConflictQuery(
    val channelCode: String,
    val contentId: Long,
    val contentRating: String? = null,
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime,
    val excludeSlotId: Long? = null,
)

data class SlotPage(val items: List<ScheduleSlotView>, val total: Long)

object SeoulTime {
    fun toLocal(value: OffsetDateTime): LocalDateTime = value.atZoneSameInstant(Zones.SEOUL).toLocalDateTime()

    fun toOffset(value: LocalDateTime): OffsetDateTime = value.atZone(Zones.SEOUL).toOffsetDateTime()
}
