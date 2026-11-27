package com.sleekydz86.aibackoffice.auth.presentation

import com.sleekydz86.aibackoffice.auth.infrastructure.security.RsaKeyHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class JwksController(private val keys: RsaKeyHolder) {

    @GetMapping("/oauth2/jwks")
    fun jwks(): Map<String, Any> = keys.publicJwkSet()
}
