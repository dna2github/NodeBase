package net.seven.nodebase

import android.content.Context
import android.net.wifi.WifiManager

import java.net.NetworkInterface
import java.net.SocketException
import java.util.Collections
import java.util.HashMap

object Network {
    val nicIps: HashMap<String, Array<String>>
        get() {
            val name_ip = HashMap<String, Array<String>>()
            try {
                for (nic in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    val nic_addr = nic.interfaceAddresses
                    if (nic_addr.size == 0) continue
                    val ips = Array(nic_addr.size, { _ -> "" });
                    val name = nic.name
                    var index = 0
                    for (ia in nic_addr) {
                        var addr = ia.address.hostAddress
                        if (addr.indexOf('%') >= 0) {
                            addr = addr.split("%".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()[0]
                        }
                        ips[index++] = addr.orEmpty()
                    }
                    name_ip[name] = ips
                }
            } catch (e: SocketException) {
            }

            return name_ip
        }

    fun getWifiIpv4(context: Context): String {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ip = wifiInfo.ipAddress
        return String.format("%d.%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
    }
}
