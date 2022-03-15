package com.pcamposu.ps3.hfwserver.util

import groovy.transform.CompileStatic

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Enumeration

@CompileStatic
class NetworkUtils {

    static List<String> getLocalIpAddresses() {
        List<String> ips = []
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces()

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement()

                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses()

                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement()

                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        ips << addr.getHostAddress()
                    }
                }
            }
        } catch (Exception e) {
        }

        return ips.unique()
    }

    static String getBestLocalIp() {
        List<String> ips = getLocalIpAddresses()

        if (ips.isEmpty()) {
            return "127.0.0.1"
        }

        for (String ip : ips) {
            if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                return ip
            }
        }

        return ips[0]
    }

    static String displayAvailableIps() {
        List<String> ips = getLocalIpAddresses()

        if (ips.isEmpty()) {
            return "  - 127.0.0.1 (loopback)"
        }

        StringBuilder sb = new StringBuilder()
        sb.append("Available network addresses:\n")

        for (String ip : ips) {
            sb.append("  - ${ip}\n")
        }

        return sb.toString()
    }
}
