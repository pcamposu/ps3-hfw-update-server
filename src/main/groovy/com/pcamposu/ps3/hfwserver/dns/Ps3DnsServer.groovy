package com.pcamposu.ps3.hfwserver.dns

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Header
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

@Slf4j
@CompileStatic
class Ps3DnsServer {

    private final int port
    private final String upstreamDns
    private final String localHttpIp
    private final ExecutorService executorService
    private volatile boolean running = false
    private DatagramSocket udpSocket
    private ServerSocket tcpServerSocket

    private static final String PS3_UPDATE_DOMAIN = "ps3.update.playstation.net"

    Ps3DnsServer(int port, String upstreamDns, String localHttpIp) {
        this.port = port
        this.upstreamDns = upstreamDns
        this.localHttpIp = localHttpIp
        this.executorService = Executors.newCachedThreadPool({ r ->
            def thread = new Thread(r)
            thread.name = "DNS-Worker-${System.currentTimeMillis()}"
            thread.daemon = true
            return thread
        })
    }

    synchronized void start() {
        if (running) {
            log.warn("DNS server is already running")
            return
        }

        try {
            udpSocket = new DatagramSocket(port)
            tcpServerSocket = new ServerSocket(port)
        } catch (IOException e) {
            log.error("[DNS] Failed to bind to port $port: ${e.message}")
            log.error("[DNS] Port 53 requires administrator/root privileges")
            throw new RuntimeException("Cannot bind to port $port", e)
        }

        running = true
        log.info("[DNS] Server started on port $port (UDP+TCP, upstream: $upstreamDns, local HTTP IP: $localHttpIp)")

        executorService.submit({ runUdpServer() } as Runnable)
        executorService.submit({ runTcpServer() } as Runnable)
    }

    private void runUdpServer() {
        byte[] buffer = new byte[512]

        while (running && udpSocket != null && !udpSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length)
                udpSocket.receive(packet)

                InetAddress clientAddress = packet.address
                int clientPort = packet.port
                log.debug("[DNS UDP] Query from ${clientAddress.hostAddress}:${clientPort}")

                executorService.submit({
                    try {
                        byte[] queryData = new byte[packet.length]
                        System.arraycopy(packet.data, 0, queryData, 0, packet.length)
                        Message query = new Message(queryData)
                        Message response = handleQuery(query, clientAddress)

                        byte[] responseBytes = response.toWire()
                        DatagramPacket responsePacket = new DatagramPacket(
                            responseBytes,
                            responseBytes.length,
                            packet.address,
                            packet.port
                        )
                        udpSocket.send(responsePacket)
                    } catch (Exception e) {
                        log.debug("[DNS] UDP query error: ${e.message}")
                    }
                } as Runnable)
            } catch (SocketException e) {
                if (running) {
                    log.warn("[DNS] UDP socket error: ${e.message}")
                }
            } catch (IOException e) {
                if (running) {
                    log.error("[DNS] UDP IO error: ${e.message}")
                }
            }
        }

        log.info("[DNS] UDP server stopped")
    }

    private void runTcpServer() {
        while (running && tcpServerSocket != null && !tcpServerSocket.isClosed()) {
            try {
                Socket clientSocket = tcpServerSocket.accept()

                executorService.submit({
                    handleTcpClient(clientSocket)
                } as Runnable)
            } catch (SocketException e) {
                if (running) {
                    log.warn("[DNS] TCP socket error: ${e.message}")
                }
            } catch (IOException e) {
                if (running) {
                    log.error("[DNS] TCP IO error: ${e.message}")
                }
            }
        }

        log.info("[DNS] TCP server stopped")
    }

    private void handleTcpClient(Socket clientSocket) {
        InetAddress clientAddress = clientSocket.inetAddress
        int clientPort = clientSocket.port
        log.debug("[DNS TCP] Connection from ${clientAddress.hostAddress}:${clientPort}")

        try {
            DataInputStream inputStream = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()))
            int length = inputStream.readUnsignedShort()
            byte[] queryData = new byte[length]
            inputStream.readFully(queryData)

            Message query = new Message(queryData)
            Message response = handleQuery(query, clientAddress)

            byte[] responseBytes = response.toWire()
            DataOutputStream outputStream = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()))
            outputStream.writeShort(responseBytes.length)
            outputStream.write(responseBytes)
            outputStream.flush()
        } catch (Exception e) {
            log.debug("[DNS] TCP client error: ${e.message}")
        } finally {
            clientSocket.close()
        }
    }

    synchronized void stop() {
        if (!running) {
            return
        }
        running = false

        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close()
        }
        if (tcpServerSocket != null && !tcpServerSocket.isClosed()) {
            tcpServerSocket.close()
        }

        executorService.shutdownNow()
        try {
            if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("[DNS] Executor did not terminate in time")
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
        }

        log.info("[DNS] Server stopped")
    }

    private Message handleQuery(Message query, InetAddress clientAddress) {
        Header header = query.getHeader()
        Message response = new Message(header.getID())
        response.getHeader().setFlag(Flags.QR)
        response.getHeader().setFlag(Flags.RA)
        response.getHeader().setFlag(Flags.RD)

        if (header.getFlag(Flags.QR)) {
            return response
        }

        Record[] questions = query.getSectionArray(Section.QUESTION)
        for (Record question : questions) {
            response.addRecord(question, Section.QUESTION)
            String name = question.name.toString()
            String typeStr = Type.string(question.type)
            String clientIp = clientAddress?.hostAddress ?: "unknown"

            log.debug("[DNS] Query: ${name} (${typeStr}) from ${clientIp}")

            if ((question.type == Type.A || question.type == Type.AAAA) && isPs3UpdateDomain(name)) {
                log.info("[DNS] ${name} -> ${localHttpIp} (PS3 UPDATE) [client: ${clientIp}]")
                response.addRecord(createARecord(question.name, localHttpIp), Section.ANSWER)
            } else {
                log.debug("[DNS] ${name} (${typeStr}) -> forwarding to ${upstreamDns} [client: ${clientIp}]")
                forwardQuery(query, response)
            }
        }

        return response
    }

    private boolean isPs3UpdateDomain(String domain) {
        String lowerDomain = domain.toLowerCase()

        if (lowerDomain.endsWith(PS3_UPDATE_DOMAIN)) {
            String subdomain = lowerDomain.substring(0, lowerDomain.length() - PS3_UPDATE_DOMAIN.length() - 1)
            return subdomain.matches("^[a-z]{3}\\d{2}\$")
        }

        if (lowerDomain.endsWith(PS3_UPDATE_DOMAIN + ".")) {
            String subdomain = lowerDomain.substring(0, lowerDomain.length() - PS3_UPDATE_DOMAIN.length() - 2)
            return subdomain.matches("^[a-z]{3}\\d{2}\$")
        }

        return false
    }

    private void forwardQuery(Message query, Message response) {
        try {
            SimpleResolver resolver = new SimpleResolver(upstreamDns)
            Message upstreamResponse = resolver.send(query)

            for (int section : [Section.ANSWER, Section.AUTHORITY, Section.ADDITIONAL]) {
                Record[] records = upstreamResponse.getSectionArray(section)
                if (records != null) {
                    for (Record record : records) {
                        response.addRecord(record, section)
                    }
                }
            }

            response.getHeader().setRcode(upstreamResponse.getHeader().getRcode())
        } catch (Exception e) {
            log.debug("[DNS] Forward query failed: ${e.message}")
            response.getHeader().setRcode(Rcode.SERVFAIL)
        }
    }

    private Record createARecord(Name name, String ip) {
        try {
            return new ARecord(
                name,
                DClass.IN,
                3600L,
                InetAddress.getByName(ip)
            )
        } catch (Exception e) {
            log.error("[DNS] Failed to create A record: ${e.message}")
            return null
        }
    }

    boolean isRunning() {
        return running
    }
}
