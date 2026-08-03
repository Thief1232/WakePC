package com.mimic.wakepc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {
    suspend fun sendMagicPacket(
        macAddress: String,
        broadcastIp: String,
        port: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val macBytes = parseMacAddress(macAddress)
            val address = InetAddress.getByName(broadcastIp)
            val packetBytes = ByteArray(6 + 16 * macBytes.size)

            for (index in 0 until 6) {
                packetBytes[index] = 0xFF.toByte()
            }

            for (index in 6 until packetBytes.size) {
                packetBytes[index] = macBytes[(index - 6) % macBytes.size]
            }

            DatagramSocket().use { socket ->
                socket.broadcast = true
                val packet = DatagramPacket(packetBytes, packetBytes.size, address, port)
                socket.send(packet)
            }
        }
    }

    private fun parseMacAddress(macAddress: String): ByteArray {
        val cleanMac = macAddress.trim().replace("-", ":")
        val parts = cleanMac.split(":")

        require(parts.size == 6) {
            "MAC-адрес должен быть в формате AA:BB:CC:DD:EE:FF"
        }

        return parts.map { part ->
            require(part.length == 2) {
                "Каждая часть MAC-адреса должна содержать 2 символа"
            }

            part.toIntOrNull(16)?.toByte()
                ?: throw IllegalArgumentException("MAC-адрес содержит недопустимые символы")
        }.toByteArray()
    }
}
