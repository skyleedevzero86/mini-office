package com.sleekydz86.aibackoffice.operation.domain

import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ScheduleRulesTest {

    private val day = LocalDateTime.of(2026, 12, 4, 0, 0)

    private fun at(hour: Int, minute: Int = 0, plusDays: Long = 0): LocalDateTime =
        day.plusDays(plusDays).withHour(hour).withMinute(minute)

    @Test
    @DisplayName("시간이 일부라도 겹치면 겹침으로 본다")
    fun partialOverlapIsOverlap() {
        // when
        val overlapped = ScheduleRules.overlaps(at(20), at(21), at(20, 30), at(21, 30))

        // then
        assertTrue(overlapped)
    }

    @Test
    @DisplayName("끝과 시작이 맞닿기만 하면 겹치지 않는다")
    fun touchingIsNotOverlap() {
        // when
        val before = ScheduleRules.overlaps(at(20), at(21), at(21), at(22))
        val after = ScheduleRules.overlaps(at(20), at(21), at(19), at(20))

        // then
        assertFalse(before)
        assertFalse(after)
    }

    @Test
    @DisplayName("밤 10시부터 새벽 6시 안에 모두 들어오면 심야 시간대로 본다")
    fun insideNightBand() {
        // given
        val cases = listOf(
            at(22) to at(23, 30),
            at(23) to at(1, plusDays = 1),
            at(2) to at(6),
            at(22) to at(6, plusDays = 1),
        )

        // when
        val results = cases.map { (start, end) -> ScheduleRules.insideNightBand(start, end) }

        // then
        assertEquals(listOf(true, true, true, true), results)
    }

    @Test
    @DisplayName("심야 시간대를 조금이라도 벗어나면 심야 시간대로 보지 않는다")
    fun outsideNightBand() {
        // given
        val cases = listOf(
            at(21, 30) to at(22, 30),
            at(5, 30) to at(6, 30),
            at(23) to at(7, plusDays = 1),
            at(12) to at(13),
        )

        // when
        val results = cases.map { (start, end) -> ScheduleRules.insideNightBand(start, end) }

        // then
        assertEquals(listOf(false, false, false, false), results)
    }

    @Test
    @DisplayName("채널 기준 등급 이상만 심야 시간대 제한을 받는다")
    fun onlyRatingsAtOrAboveLimitAreNightOnly() {
        // given
        val limit = ContentRating.AGE_19

        // then
        assertTrue(ScheduleRules.nightOnly(ContentRating.AGE_19, limit))
        assertFalse(ScheduleRules.nightOnly(ContentRating.AGE_15, limit))
        assertFalse(ScheduleRules.nightOnly(null, limit))
        assertFalse(ScheduleRules.nightOnly(ContentRating.AGE_19, null))
    }

    @Test
    @DisplayName("겹침, 숨김, 등급 시간대 충돌을 한 번에 모두 알려 준다")
    fun reportsAllConflictTypes() {
        // given
        val candidate = SlotCandidate(contentId = 7, rating = ContentRating.AGE_19, startAt = at(20), endAt = at(21))
        val others = listOf(SlotWindow(4, at(20, 45), at(22)), SlotWindow(3, at(20, 30), at(21, 30)))

        // when
        val conflicts = ScheduleRules.check(candidate, ContentRating.AGE_19, others, contentHidden = true)

        // then
        assertEquals(
            listOf(ConflictType.TIME_OVERLAP, ConflictType.TIME_OVERLAP, ConflictType.CONTENT_HIDDEN, ConflictType.RATING_TIME_BAND),
            conflicts.map { it.type },
        )
        assertEquals(listOf(3L, 4L, null, null), conflicts.map { it.slotId })
    }

    @Test
    @DisplayName("규칙에 맞는 편성은 충돌이 없다")
    fun validSlotHasNoConflict() {
        // given
        val candidate = SlotCandidate(contentId = 7, rating = ContentRating.AGE_19, startAt = at(22, 30), endAt = at(23, 30))
        val others = listOf(SlotWindow(3, at(21), at(22, 30)))

        // when
        val conflicts = ScheduleRules.check(candidate, ContentRating.AGE_19, others, contentHidden = false)

        // then
        assertTrue(conflicts.isEmpty())
    }

    @Test
    @DisplayName("등급 코드는 앞뒤 공백과 대소문자를 가리지 않고 읽고 모르는 코드는 비워 둔다")
    fun parsesRatingCodes() {
        // when
        val parsed = listOf(" all ", "12", "15", "19", "18", "").map { ContentRating.fromCode(it) }

        // then
        assertEquals(
            listOf(ContentRating.ALL, ContentRating.AGE_12, ContentRating.AGE_15, ContentRating.AGE_19, null, null),
            parsed,
        )
    }
}
