package com.sleekydz86.aibackoffice.framework.web.core.list

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

class OffsetPageRequest(private val offsetValue: Int, size: Int, sort: Sort) :
    PageRequest(offsetValue / size, size, sort) {

    override fun getOffset(): Long = offsetValue.toLong()
}

fun ListQuery.toPageable(allowedSorts: Set<String>, defaultSort: Sort): Pageable {
    val direction = if (ascending) Sort.Direction.ASC else Sort.Direction.DESC
    val resolved = sort?.takeIf { it in allowedSorts }?.let { Sort.by(direction, it) } ?: defaultSort
    return OffsetPageRequest(offset, limit, resolved)
}
