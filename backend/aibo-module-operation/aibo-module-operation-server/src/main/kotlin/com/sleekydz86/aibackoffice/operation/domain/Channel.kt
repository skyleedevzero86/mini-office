package com.sleekydz86.aibackoffice.operation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "channels")
class Channel(
    @Column(nullable = false, length = 20)
    var code: String,
    @Column(nullable = false, length = 100)
    var name: String,
    @Column(name = "min_rating_after_22", length = 10)
    var minRatingAfter22: String?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    fun requireId(): Long = id ?: throw IllegalStateException("저장되지 않은 채널입니다.")

    fun nightOnlyFrom(): ContentRating? = ContentRating.fromCode(minRatingAfter22)
}
