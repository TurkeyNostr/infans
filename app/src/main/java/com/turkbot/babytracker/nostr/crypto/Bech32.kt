package com.turkbot.babytracker.nostr.crypto

/**
 * Bech32 encoding for Nostr keys (npub / nsec).
 * Based on BIP-173 with the Nostr charset.
 */
object Bech32 {

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    private fun bech32Polymod(values: IntArray): Int {
        val generator = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (v in values) {
            val top = chk shr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0..4) {
                if (((top shr i) and 1) == 1) chk = chk xor generator[i]
            }
        }
        return chk
    }

    private fun bech32HrpExpand(hrp: String): IntArray {
        val result = mutableListOf<Int>()
        for (c in hrp) result.add(c.code shr 5)
        result.add(0)
        for (c in hrp) result.add(c.code and 31)
        return result.toIntArray()
    }

    private fun bech32CreateChecksum(hrp: String, data: IntArray): IntArray {
        val values = bech32HrpExpand(hrp) + data.toList()
        val polymod = bech32Polymod(values + intArrayOf(0, 0, 0, 0, 0, 0)) xor 1
        return intArrayOf(
            (polymod shr 25) and 31,
            (polymod shr 20) and 31,
            (polymod shr 15) and 31,
            (polymod shr 10) and 31,
            (polymod shr 5) and 31,
            polymod and 31
        )
    }

    private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): IntArray {
        var acc = 0
        var bits = 0
        val result = mutableListOf<Int>()
        val maxv = (1 shl toBits) - 1
        for (b in data) {
            val value = b.toInt() and 0xff
            if (value ushr fromBits != 0) throw IllegalArgumentException("Invalid data")
            acc = (acc shl fromBits) or value
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add((acc shr bits) and maxv)
            }
        }
        if (pad) {
            if (bits > 0) result.add((acc shl (toBits - bits)) and maxv)
        } else if (bits >= fromBits || (acc shl (toBits - bits)) and maxv != 0) {
            throw IllegalArgumentException("Invalid padding")
        }
        return result.toIntArray()
    }

    fun encode(hrp: String, data: ByteArray): String {
        val converted = convertBits(data, 8, 5, true)
        val checksum = bech32CreateChecksum(hrp, converted)
        val combined = converted + checksum
        return hrp + "1" + combined.joinToString("") { CHARSET[it].toString() }
    }

    fun decode(s: String): Pair<String, ByteArray> {
        val str = s.lowercase()
        if (str.any { it.code < 33 || it.code > 126 }) throw IllegalArgumentException("Invalid char")
        val pos = str.lastIndexOf('1')
        if (pos < 1 || pos + 7 > str.length) throw IllegalArgumentException("Invalid format")
        val hrp = str.substring(0, pos)
        val dataPart = str.substring(pos + 1)
        val data = dataPart.map { c ->
            val idx = CHARSET.indexOf(c)
            if (idx < 0) throw IllegalArgumentException("Invalid char $c")
            idx
        }
        // Verify checksum
        val values = bech32HrpExpand(hrp) + data
        if (bech32Polymod(values.toIntArray()) != 1) throw IllegalArgumentException("Invalid checksum")
        val dataWithoutChecksum = data.dropLast(6)
        // Convert 5-bit values to 8-bit bytes
        val converted = convertBitsFromInts(dataWithoutChecksum.toIntArray(), 5, 8, false)
        return hrp to converted
    }

    /**
     * Convert from list of 5-bit integers to ByteArray of 8-bit values.
     */
    private fun convertBitsFromInts(data: IntArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
        var acc = 0
        var bits = 0
        val result = mutableListOf<Byte>()
        val maxv = (1 shl toBits) - 1
        for (v in data) {
            if (v ushr fromBits != 0) throw IllegalArgumentException("Invalid data")
            acc = (acc shl fromBits) or v
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add(((acc shr bits) and maxv).toByte())
            }
        }
        if (pad) {
            if (bits > 0) result.add(((acc shl (toBits - bits)) and maxv).toByte())
        } else if (bits >= fromBits || ((acc shl (toBits - bits)) and maxv) != 0) {
            throw IllegalArgumentException("Invalid padding")
        }
        return result.toByteArray()
    }
}
