package com.xingheyuzhuan.shiguangschedule.cumt

import kotlin.random.Random

/**
 * 中国矿业大学统一身份认证 CAS 密码加密工具及轻量 Base64 编解码器。
 * 协议与原 FlyingKxz `aes_parser.dart` 完全一致：
 * - 明文 = 64 位随机字符 + 用户密码
 * - 密钥 = 服务端返回的 `#pwdEncryptSalt` (trim 后 UTF-8 字节)
 * - IV = 16 位随机字符
 * - 算法 = AES/CBC/PKCS7Padding，输出标准 Base64
 */
object CumtAesCrypto {
    private const val RANDOM_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
    private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private val SBOX = intArrayOf(
        0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
        0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
        0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
        0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
        0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
        0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
        0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
        0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
        0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
        0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
        0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
        0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
        0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
        0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
        0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
        0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16
    )

    private val RCON = intArrayOf(
        0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36
    )

    fun randomString(length: Int, random: Random = Random.Default): String {
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(RANDOM_CHARS[random.nextInt(RANDOM_CHARS.length)])
        }
        return sb.toString()
    }

    fun encryptPassword(password: String, salt: String): String {
        val trimmedSalt = salt.trim()
        if (trimmedSalt.isEmpty()) return password
        val prefix = randomString(64)
        val iv = randomString(16)
        return encryptPasswordWithRandom(password, trimmedSalt, prefix, iv)
    }

    fun encryptPasswordWithRandom(
        password: String,
        salt: String,
        prefix64: String,
        iv16: String
    ): String {
        val trimmedSalt = salt.trim()
        val plaintext = (prefix64 + password).encodeToByteArray()
        val keyBytes = trimmedSalt.encodeToByteArray()
        val ivBytes = iv16.encodeToByteArray()
        require(ivBytes.size == 16) { "IV 必须为 16 字节，当前为 ${ivBytes.size}" }
        val cipherBytes = encryptAesCbcPkcs7(plaintext, keyBytes, ivBytes)
        return base64Encode(cipherBytes)
    }

    fun encryptAesCbcPkcs7(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(key.size == 16 || key.size == 24 || key.size == 32) {
            "AES 密钥长度必须为 16/24/32 字节，当前为 ${key.size}"
        }
        require(iv.size == 16) { "AES-CBC IV 必须为 16 字节，当前为 ${iv.size}" }

        val nr = when (key.size) {
            16 -> 10
            24 -> 12
            else -> 14
        }
        val roundKeys = keyExpansion(key, nr)

        val padLen = 16 - (plaintext.size % 16)
        val padded = ByteArray(plaintext.size + padLen)
        plaintext.copyInto(padded)
        for (i in plaintext.size until padded.size) {
            padded[i] = padLen.toByte()
        }

        val output = ByteArray(padded.size)
        var prevBlock = iv.copyOf()
        val block = ByteArray(16)

        for (offset in padded.indices step 16) {
            for (i in 0 until 16) {
                block[i] = ((padded[offset + i].toInt() and 0xFF) xor (prevBlock[i].toInt() and 0xFF)).toByte()
            }
            val encrypted = encryptBlock(block, roundKeys, nr)
            encrypted.copyInto(output, destinationOffset = offset)
            prevBlock = encrypted
        }
        return output
    }

    private fun keyExpansion(key: ByteArray, nr: Int): IntArray {
        val nk = key.size / 4
        val totalWords = 4 * (nr + 1)
        val w = IntArray(totalWords)

        for (i in 0 until nk) {
            w[i] = ((key[4 * i].toInt() and 0xFF) shl 24) or
                ((key[4 * i + 1].toInt() and 0xFF) shl 16) or
                ((key[4 * i + 2].toInt() and 0xFF) shl 8) or
                (key[4 * i + 3].toInt() and 0xFF)
        }

        for (i in nk until totalWords) {
            var temp = w[i - 1]
            if (i % nk == 0) {
                temp = subWord(rotWord(temp)) xor (RCON[i / nk] shl 24)
            } else if (nk > 6 && i % nk == 4) {
                temp = subWord(temp)
            }
            w[i] = w[i - nk] xor temp
        }
        return w
    }

    private fun rotWord(word: Int): Int = (word shl 8) or (word ushr 24)

    private fun subWord(word: Int): Int {
        return (SBOX[(word ushr 24) and 0xFF] shl 24) or
            (SBOX[(word ushr 16) and 0xFF] shl 16) or
            (SBOX[(word ushr 8) and 0xFF] shl 8) or
            SBOX[word and 0xFF]
    }

    private fun encryptBlock(input: ByteArray, roundKeys: IntArray, nr: Int): ByteArray {
        val state = IntArray(16)
        for (i in 0 until 16) {
            state[i] = input[i].toInt() and 0xFF
        }

        addRoundKey(state, roundKeys, 0)

        for (round in 1 until nr) {
            subBytes(state)
            shiftRows(state)
            mixColumns(state)
            addRoundKey(state, roundKeys, round)
        }

        subBytes(state)
        shiftRows(state)
        addRoundKey(state, roundKeys, nr)

        return ByteArray(16) { state[it].toByte() }
    }

    private fun addRoundKey(state: IntArray, roundKeys: IntArray, round: Int) {
        for (c in 0 until 4) {
            val w = roundKeys[round * 4 + c]
            state[c * 4] = state[c * 4] xor ((w ushr 24) and 0xFF)
            state[c * 4 + 1] = state[c * 4 + 1] xor ((w ushr 16) and 0xFF)
            state[c * 4 + 2] = state[c * 4 + 2] xor ((w ushr 8) and 0xFF)
            state[c * 4 + 3] = state[c * 4 + 3] xor (w and 0xFF)
        }
    }

    private fun subBytes(state: IntArray) {
        for (i in 0 until 16) {
            state[i] = SBOX[state[i]]
        }
    }

    private fun shiftRows(state: IntArray) {
        // Row 1: left 1
        val t1 = state[1]
        state[1] = state[5]
        state[5] = state[9]
        state[9] = state[13]
        state[13] = t1

        // Row 2: left 2
        val t2a = state[2]
        val t2b = state[6]
        state[2] = state[10]
        state[6] = state[14]
        state[10] = t2a
        state[14] = t2b

        // Row 3: left 3 (= right 1)
        val t3 = state[15]
        state[15] = state[11]
        state[11] = state[7]
        state[7] = state[3]
        state[3] = t3
    }

    private fun xtime(b: Int): Int {
        val shifted = (b shl 1) and 0xFF
        return if ((b and 0x80) != 0) shifted xor 0x1b else shifted
    }

    private fun mixColumns(state: IntArray) {
        for (c in 0 until 4) {
            val i = c * 4
            val s0 = state[i]
            val s1 = state[i + 1]
            val s2 = state[i + 2]
            val s3 = state[i + 3]
            val t = s0 xor s1 xor s2 xor s3
            state[i] = s0 xor t xor xtime(s0 xor s1)
            state[i + 1] = s1 xor t xor xtime(s1 xor s2)
            state[i + 2] = s2 xor t xor xtime(s2 xor s3)
            state[i + 3] = s3 xor t xor xtime(s3 xor s0)
        }
    }

    fun base64Encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder(((data.size + 2) / 3) * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val hasB1 = i + 1 < data.size
            val b1 = if (hasB1) data[i + 1].toInt() and 0xFF else 0
            val hasB2 = i + 2 < data.size
            val b2 = if (hasB2) data[i + 2].toInt() and 0xFF else 0

            val triple = (b0 shl 16) or (b1 shl 8) or b2
            sb.append(BASE64_ALPHABET[(triple ushr 18) and 0x3F])
            sb.append(BASE64_ALPHABET[(triple ushr 12) and 0x3F])
            sb.append(if (hasB1) BASE64_ALPHABET[(triple ushr 6) and 0x3F] else '=')
            sb.append(if (hasB2) BASE64_ALPHABET[triple and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }

    fun base64Decode(input: String): ByteArray {
        val cleaned = input.filter { !it.isWhitespace() }
        if (cleaned.isEmpty()) return ByteArray(0)
        val padding = when {
            cleaned.endsWith("==") -> 2
            cleaned.endsWith("=") -> 1
            else -> 0
        }
        val out = ByteArray((cleaned.length / 4) * 3 - padding)
        var outIndex = 0
        for (i in cleaned.indices step 4) {
            val c0 = decodeChar(cleaned[i])
            val c1 = decodeChar(cleaned[i + 1])
            val c2 = if (cleaned[i + 2] == '=') 0 else decodeChar(cleaned[i + 2])
            val c3 = if (cleaned[i + 3] == '=') 0 else decodeChar(cleaned[i + 3])
            val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
            if (outIndex < out.size) out[outIndex++] = ((triple ushr 16) and 0xFF).toByte()
            if (outIndex < out.size) out[outIndex++] = ((triple ushr 8) and 0xFF).toByte()
            if (outIndex < out.size) out[outIndex++] = (triple and 0xFF).toByte()
        }
        return out
    }

    private fun decodeChar(ch: Char): Int {
        return when (ch) {
            in 'A'..'Z' -> ch - 'A'
            in 'a'..'z' -> ch - 'a' + 26
            in '0'..'9' -> ch - '0' + 52
            '+' -> 62
            '/' -> 63
            else -> throw IllegalArgumentException("非法 Base64 字符: $ch")
        }
    }
}
