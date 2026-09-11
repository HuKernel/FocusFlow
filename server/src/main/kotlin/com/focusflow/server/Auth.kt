package com.focusflow.server

import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import java.util.Base64

object Auth {
    private const val ITERATIONS = 120_000
    private const val TOKEN_TTL_MILLIS = 30L * 24 * 3600 * 1000

    fun salt(): String = Base64.getEncoder().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })

    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return Base64.getEncoder().encodeToString(factory.generateSecret(spec).encoded)
    }

    fun issueToken(userId: String, secret: String, now: Long): String {
        val expiry = now + TOKEN_TTL_MILLIS
        val body = "$userId.$expiry"
        return body + "." + hmac(body, secret)
    }

    fun verifyToken(token: String, secret: String, now: Long): String? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        val (userId, expiry) = parts
        val body = "$userId.$expiry"
        if (expiry.toLongOrNull() ?: 0 < now) return null
        return if (hmac(body, secret) == parts[2]) userId else null
    }

    private fun hmac(body: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.toByteArray()))
    }
}
