package com.miku.ray.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object MikuRayFileCrypto {

    private val MAGIC = byteArrayOf('M'.code.toByte(), 'K'.code.toByte(), 'R'.code.toByte(), 'Y'.code.toByte())
    private const val VERSION: Byte = 1

    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256

    class MikuRayCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun isMikuRayFile(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC.size) return false
        for (i in MAGIC.indices) {
            if (bytes[i] != MAGIC[i]) return false
        }
        return true
    }

    fun encrypt(plainText: String, password: String): ByteArray {
        try {
            val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(password, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val buffer = ByteBuffer.allocate(MAGIC.size + 1 + 1 + salt.size + 1 + iv.size + cipherText.size)
            .order(ByteOrder.BIG_ENDIAN)
            buffer.put(MAGIC)
            buffer.put(VERSION)
            buffer.put(salt.size.toByte())
            buffer.put(salt)
            buffer.put(iv.size.toByte())
            buffer.put(iv)
            buffer.put(cipherText)
            return buffer.array()
        } catch (e: Exception) {
            throw MikuRayCryptoException("Failed to encrypt .mikuray payload", e)
        }
    }

    fun decrypt(fileBytes: ByteArray, password: String): String {
        try {
            if (!isMikuRayFile(fileBytes)) {
                throw MikuRayCryptoException("Not a valid .mikuray file")
            }
            val buffer = ByteBuffer.wrap(fileBytes).order(ByteOrder.BIG_ENDIAN)
            buffer.position(MAGIC.size)

            val version = buffer.get()
            if (version != VERSION) {
                throw MikuRayCryptoException("Unsupported .mikuray file version: $version")
            }

            val saltLength = buffer.get().toInt()
            val salt = ByteArray(saltLength).also { buffer.get(it) }

            val ivLength = buffer.get().toInt()
            val iv = ByteArray(ivLength).also { buffer.get(it) }

            val cipherText = ByteArray(buffer.remaining()).also { buffer.get(it) }

            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plainBytes = cipher.doFinal(cipherText)
            return String(plainBytes, Charsets.UTF_8)
        } catch (e: MikuRayCryptoException) {
            throw e
        } catch (e: Exception) {

            throw MikuRayCryptoException("Failed to decrypt .mikuray file (wrong password or corrupted file)", e)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
