package com.mimic.wakepc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException

object NetworkScanner {
    suspend fun scan(): Result<List<DiscoveredDevice>> = withContext(Dispatchers.IO) {
        runCatching {
            val localAddress = findLocalAddress()
                ?: error("Телефон не подключен к локальной IPv4-сети")
            val prefix = localAddress.hostAddress.orEmpty().substringBeforeLast('.')
            val semaphore = Semaphore(40)

            val reachableAddresses = coroutineScope {
                (1..254).map { lastOctet ->
                    async {
                        semaphore.withPermit {
                            val ipAddress = "$prefix.$lastOctet"
                            if (isDeviceReachable(ipAddress)) ipAddress else null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            val arpEntries = readArpTable()
            reachableAddresses.map { ipAddress ->
                DiscoveredDevice(
                    name = resolveDeviceName(ipAddress, localAddress.hostAddress),
                    ipAddress = ipAddress,
                    macAddress = arpEntries[ipAddress].orEmpty()
                )
            }.sortedBy { InetAddress.getByName(it.ipAddress).address.last().toInt() and 0xFF }
        }
    }

    private fun findLocalAddress(): Inet4Address? = NetworkInterface.getNetworkInterfaces()
        ?.toList()
        ?.asSequence()
        ?.filter { it.isUp && !it.isLoopback }
        ?.flatMap { it.inetAddresses.toList().asSequence() }
        ?.filterIsInstance<Inet4Address>()
        ?.firstOrNull { it.isSiteLocalAddress }

    private fun isDeviceReachable(ipAddress: String): Boolean {
        val address = InetAddress.getByName(ipAddress)
        if (address.isReachable(250)) return true

        for (port in PROBE_PORTS) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), 180)
                    return true
                }
            } catch (_: SocketTimeoutException) {
                // Try another common port.
            } catch (_: java.net.ConnectException) {
                // A refused connection still proves that the host answered.
                return true
            } catch (_: Exception) {
                // Try another common port.
            }
        }
        return false
    }

    private fun resolveDeviceName(ipAddress: String, localIpAddress: String?): String {
        if (ipAddress == localIpAddress) return "Это устройство"

        val hostName = runCatching {
            InetAddress.getByName(ipAddress).canonicalHostName
        }.getOrNull()

        return if (!hostName.isNullOrBlank() && hostName != ipAddress) {
            hostName.removeSuffix(".local")
        } else {
            "Устройство $ipAddress"
        }
    }

    private fun readArpTable(): Map<String, String> = runCatching {
        java.io.File("/proc/net/arp").useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val columns = line.trim().split(Regex("\\s+"))
                if (columns.size >= 4 && columns[3] != "00:00:00:00:00:00") {
                    columns[0] to columns[3].uppercase()
                } else {
                    null
                }
            }.toMap()
        }
    }.getOrDefault(emptyMap())

    private val PROBE_PORTS = intArrayOf(80, 443, 22, 445, 9)
}
