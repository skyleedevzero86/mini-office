package com.sleekydz86.aibackoffice.framework.security.core

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Headers
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

object SecurityFrameworkUtils {

    fun loginUserOrNull(): LoginUser? {
        val user = (SecurityContextHolder.getContext().authentication as? LoginUserAuthentication)?.loginUser ?: return null
        val actor = currentActorHeader()
        return if (actor == ActorType.AGENT && !user.service) user.copy(actorType = ActorType.AGENT) else user
    }

    fun loginUser(): LoginUser = loginUserOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

    fun requireChannel(user: LoginUser, channelCode: String?) {
        if (!user.inChannel(channelCode)) {
            throw BusinessException(ErrorCode.SCOPE_DENIED, "담당하는 채널 밖의 대상입니다: ${channelCode ?: "채널 없음"}")
        }
    }

    fun requirePermission(user: LoginUser, permission: String) {
        if (!user.has(permission)) {
            throw BusinessException(ErrorCode.PERMISSION_DENIED, "이 작업에는 $permission 권한이 필요합니다.")
        }
    }

    private fun currentActorHeader(): ActorType? {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request ?: return null
        return request.getHeader(Headers.ACTOR_TYPE)?.let { value -> ActorType.entries.firstOrNull { it.name == value } }
    }
}
