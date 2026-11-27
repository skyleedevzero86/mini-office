package com.sleekydz86.aibackoffice.auth.infrastructure.security

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.sleekydz86.aibackoffice.auth.infrastructure.config.AuthProperties
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RsaKeyHolder(properties: AuthProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    val keyId: String = properties.keyId

    private val pair: Pair<RSAPublicKey, RSAPrivateKey> = load(properties)

    val publicKey: RSAPublicKey get() = pair.first

    val privateKey: RSAPrivateKey get() = pair.second

    private fun load(properties: AuthProperties): Pair<RSAPublicKey, RSAPrivateKey> {
        if (properties.privateKeyPem.isNotBlank() && properties.publicKeyPem.isNotBlank()) {
            val factory = KeyFactory.getInstance("RSA")
            val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(decode(properties.privateKeyPem))) as RSAPrivateKey
            val publicKey = factory.generatePublic(X509EncodedKeySpec(decode(properties.publicKeyPem))) as RSAPublicKey
            return publicKey to privateKey
        }
        if (properties.allowEphemeralKey) {
            log.warn("서명 키가 없어 임시 키를 만들었습니다. 서버를 다시 띄우면 기존 토큰은 모두 무효가 됩니다. 운영에서는 JWT_PRIVATE_KEY 와 JWT_PUBLIC_KEY 를 넣으세요.")
            val generated = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            return (generated.public as RSAPublicKey) to (generated.private as RSAPrivateKey)
        }
        throw BusinessException(ErrorCode.SERVICE_NOT_CONFIGURED, "서명 키 환경변수 JWT_PRIVATE_KEY 와 JWT_PUBLIC_KEY 가 비어 있습니다.")
    }

    fun publicJwkSet(): Map<String, Any> =
        JWKSet(RSAKey.Builder(publicKey).keyID(keyId).keyUse(KeyUse.SIGNATURE).build()).toJSONObject()

    private fun decode(pem: String): ByteArray =
        Base64.getDecoder().decode(
            pem.lines().filterNot { it.startsWith("-----") }.joinToString("").replace("\\s".toRegex(), ""),
        )
}
