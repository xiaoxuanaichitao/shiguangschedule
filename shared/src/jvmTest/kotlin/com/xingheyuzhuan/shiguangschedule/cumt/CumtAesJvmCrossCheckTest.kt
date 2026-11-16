package com.xingheyuzhuan.shiguangschedule.cumt

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals

class CumtAesJvmCrossCheckTest {

    @Test
    fun matchesJavaxCryptoAesCbcPkcs5PaddingExactly() {
        val salt = "AbCdEfGh12345678"
        val prefix64 = "A".repeat(64)
        val iv16 = "1234567890abcdef"
        val passwords = listOf("", "1", "Cumt@2026!", "PassWith16Bytes!", "VeryLongPasswordExceedingOneAesBlockSize_2026")

        for (pwd in passwords) {
            val actualBase64 = CumtAesCrypto.encryptPasswordWithRandom(pwd, salt, prefix64, iv16)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "AES")
            val ivSpec = IvParameterSpec(iv16.toByteArray(Charsets.UTF_8))
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val expectedBase64 = Base64.getEncoder().encodeToString(
                cipher.doFinal((prefix64 + pwd).toByteArray(Charsets.UTF_8))
            )

            assertEquals(expectedBase64, actualBase64, "Mismatch for password='$pwd'")
        }
    }
}
