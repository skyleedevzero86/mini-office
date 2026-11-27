package com.sleekydz86.aibackoffice.operation.domain

import java.time.LocalDateTime
import java.time.LocalTime

enum class ContentRating(val code: String, val level: Int) {
    ALL("ALL", 0),
    AGE_12("12", 12),
    AGE_15("15", 15),
    AGE_19("19", 19);

    companion object {
        fun fromCode(code: String?): ContentRating? {
            val normalized = code?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.code == normalized }
        }

        fun codes(): String = entries.joinToString(", ") { it.code }
    }
}

enum class ConflictType { TIME_OVERLAP, CONTENT_HIDDEN, RATING_TIME_BAND }

data class ScheduleConflict(val type: ConflictType, val message: String, val slotId: Long?)

data class SlotWindow(val slotId: Long?, val startAt: LocalDateTime, val endAt: LocalDateTime)

data class SlotCandidate(
    val contentId: Long,
    val rating: ContentRating?,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
)

object ScheduleRules {

    private val NIGHT_START: LocalTime = LocalTime.of(22, 0)
    private val NIGHT_END: LocalTime = LocalTime.of(6, 0)
    private const val NIGHT_HOURS = 8L

    fun overlaps(aStart: LocalDateTime, aEnd: LocalDateTime, bStart: LocalDateTime, bEnd: LocalDateTime): Boolean =
        aStart.isBefore(bEnd) && aEnd.isAfter(bStart)

    fun insideNightBand(startAt: LocalDateTime, endAt: LocalDateTime): Boolean {
        val startTime = startAt.toLocalTime()
        val bandStart = when {
            !startTime.isBefore(NIGHT_START) -> startAt.toLocalDate().atTime(NIGHT_START)
            startTime.isBefore(NIGHT_END) -> startAt.toLocalDate().minusDays(1).atTime(NIGHT_START)
            else -> return false
        }
        return !endAt.isAfter(bandStart.plusHours(NIGHT_HOURS))
    }

    fun nightOnly(rating: ContentRating?, channelLimit: ContentRating?): Boolean =
        rating != null && channelLimit != null && rating.level >= channelLimit.level

    fun check(
        candidate: SlotCandidate,
        channelLimit: ContentRating?,
        others: List<SlotWindow>,
        contentHidden: Boolean,
    ): List<ScheduleConflict> {
        val conflicts = mutableListOf<ScheduleConflict>()
        others.filter { overlaps(candidate.startAt, candidate.endAt, it.startAt, it.endAt) }
            .sortedBy { it.startAt }
            .forEach {
                conflicts += ScheduleConflict(
                    ConflictType.TIME_OVERLAP,
                    "같은 채널의 다른 편성과 시간이 겹칩니다. 겹치는 편성 시간은 ${it.startAt} 부터 ${it.endAt} 까지입니다.",
                    it.slotId,
                )
            }
        if (contentHidden) {
            conflicts += ScheduleConflict(
                ConflictType.CONTENT_HIDDEN,
                "숨김 처리된 작품이라 편성할 수 없습니다. 작품 번호는 ${candidate.contentId} 입니다.",
                null,
            )
        }
        if (nightOnly(candidate.rating, channelLimit) && !insideNightBand(candidate.startAt, candidate.endAt)) {
            conflicts += ScheduleConflict(
                ConflictType.RATING_TIME_BAND,
                "이 채널에서 ${channelLimit?.code} 등급 이상은 밤 10시부터 새벽 6시 사이에만 편성할 수 있습니다.",
                null,
            )
        }
        return conflicts
    }
}
