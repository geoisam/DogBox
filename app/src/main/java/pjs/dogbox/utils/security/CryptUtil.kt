package pjs.dogbox.utils.security

import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptUtil {
    private const val AES_CBC_PKCS5 = "AES/CBC/PKCS5Padding"
    private const val AES_CBC_NOPADDING = "AES/CBC/NoPadding"
    private const val AES_CFB_NOPADDING = "AES/CFB/NoPadding"

    fun cbcEncrypt(data: String, key: String, iv: String, noPadding: Boolean = false): String {
        val cipher = Cipher.getInstance(if (noPadding) AES_CBC_NOPADDING else AES_CBC_PKCS5)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec(key), ivSpec(iv))
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    fun cbcDecrypt(data: String?, key: String, iv: String, noPadding: Boolean = false): String {
        val cipher = Cipher.getInstance(if (noPadding) AES_CBC_NOPADDING else AES_CBC_PKCS5)
        cipher.init(Cipher.DECRYPT_MODE, keySpec(key), ivSpec(iv))
        val decrypted = cipher.doFinal(Base64.getMimeDecoder().decode(data))
        return String(decrypted, Charsets.UTF_8)
    }

    fun cfbEncrypt(data: String, keyHex: String, ivHex: String): String {
        val cipher = Cipher.getInstance(AES_CFB_NOPADDING)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(hexToBytes(keyHex), "AES"),
            IvParameterSpec(hexToBytes(ivHex))
        )
        return bytesToHex(cipher.doFinal(data.toByteArray(Charsets.UTF_8)))
    }

    fun cfbDecrypt(dataHex: String, keyHex: String, ivHex: String): String {
        val cipher = Cipher.getInstance(AES_CFB_NOPADDING)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(hexToBytes(keyHex), "AES"),
            IvParameterSpec(hexToBytes(ivHex))
        )
        return String(cipher.doFinal(hexToBytes(dataHex)), Charsets.UTF_8)
    }

    fun sha256(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8))
        return bytesToHex(digest)
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun keySpec(key: String): SecretKeySpec {
        return SecretKeySpec(key.toByteArray(Charsets.UTF_8).copyOf(16), "AES")
    }

    private fun ivSpec(iv: String): IvParameterSpec {
        return IvParameterSpec(iv.toByteArray(Charsets.UTF_8).copyOf(16))
    }

    fun md5(data: String, bitLength: Int = 32, lowercase: Boolean = true): String {
        val digest = MessageDigest.getInstance("MD5").digest(data.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return when (bitLength) {
            32 -> if (lowercase) hex else hex.uppercase()
            16 -> {
                val middle = hex.substring(8, 24)
                if (lowercase) middle else middle.uppercase()
            }

            else -> throw IllegalArgumentException("bitLength must be 16 or 32")
        }
    }

    fun base64Encode(data: String, charset: Charset = Charsets.UTF_8): String {
        return Base64.getEncoder().encodeToString(data.toByteArray(charset))
    }

    fun base64Decode(
        data: String,
        charset: Charset = Charsets.UTF_8,
        lenient: Boolean = true
    ): String {
        val decoder = if (lenient) Base64.getMimeDecoder() else Base64.getDecoder()
        return String(decoder.decode(data), charset)
    }
}