package com.sleekydz86.aibackoffice.operation.infrastructure.persistence

import com.sleekydz86.aibackoffice.operation.domain.Channel
import com.sleekydz86.aibackoffice.operation.domain.ScheduleSlot
import com.sleekydz86.aibackoffice.operation.domain.SlotStatus
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface ChannelRepository : JpaRepository<Channel, Long> {

    fun findByCode(code: String): Channel?

    fun findByCodeIn(codes: Collection<String>): List<Channel>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Channel c where c.id = :id")
    fun findLockedById(id: Long): Channel?
}

interface ScheduleSlotRepository : JpaRepository<ScheduleSlot, Long>, JpaSpecificationExecutor<ScheduleSlot> {

    @Query(
        "select s from ScheduleSlot s where s.channelId = :channelId and s.status <> :excluded " +
            "and s.startAt < :endAt and s.endAt > :startAt",
    )
    fun findOverlapping(
        channelId: Long,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        excluded: SlotStatus,
    ): List<ScheduleSlot>

    @Query(
        "select count(s) from ScheduleSlot s where s.channelId in :channelIds and s.status <> :excluded " +
            "and s.startAt >= :from and s.startAt < :to",
    )
    fun countStarting(channelIds: Collection<Long>, from: LocalDateTime, to: LocalDateTime, excluded: SlotStatus): Long

    fun countByChannelIdInAndStatus(channelIds: Collection<Long>, status: SlotStatus): Long
}

object ScheduleSlotSpecs {

    fun channelIn(channelIds: Collection<Long>): Specification<ScheduleSlot> =
        Specification { root, _, _ -> root.get<Long>("channelId").`in`(channelIds) }

    fun status(status: SlotStatus): Specification<ScheduleSlot> =
        Specification { root, _, builder -> builder.equal(root.get<SlotStatus>("status"), status) }

    fun endsAfter(from: LocalDateTime): Specification<ScheduleSlot> =
        Specification { root, _, builder -> builder.greaterThan(root.get("endAt"), from) }

    fun startsBefore(to: LocalDateTime): Specification<ScheduleSlot> =
        Specification { root, _, builder -> builder.lessThan(root.get("startAt"), to) }
}
