package com.pcamposu.ps3.hfwserver.dns

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Ps3DnsServerTest {

    static Ps3DnsServer dnsServer
    static int testPort
    static String testIp = "127.0.0.1"
    static String upstreamDns = "8.8.8.8"

    @BeforeAll
    static void setupSpec() {
        testPort = findAvailablePort()
        dnsServer = new Ps3DnsServer(testPort, upstreamDns, testIp)
        dnsServer.start()
        waitForServerStartup()
    }

    @AfterAll
    static void cleanupSpec() {
        if (dnsServer != null && dnsServer.isRunning()) {
            dnsServer.stop()
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void "PS3 US domain should redirect to local IP"() {
        def resolver = new SimpleResolver(testIp)
        resolver.setPort(testPort)
        def result = new Lookup("dus01.ps3.update.playstation.net", Type.A)
        result.setResolver(resolver)
        result.run()

        assert result.result == Lookup.SUCCESSFUL
        assert result.answers.length > 0
        assert result.answers[0] instanceof org.xbill.DNS.ARecord
        def addr = ((org.xbill.DNS.ARecord) result.answers[0]).getAddress()
        assert addr.getHostAddress() == testIp
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void "PS3 EU domain should redirect to local IP"() {
        def resolver = new SimpleResolver(testIp)
        resolver.setPort(testPort)
        def result = new Lookup("deu01.ps3.update.playstation.net", Type.A)
        result.setResolver(resolver)
        result.run()

        assert result.result == Lookup.SUCCESSFUL
        assert result.answers.length > 0
        assert result.answers[0] instanceof org.xbill.DNS.ARecord
        def addr = ((org.xbill.DNS.ARecord) result.answers[0]).getAddress()
        assert addr.getHostAddress() == testIp
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void "PS3 JP domain should redirect to local IP"() {
        def resolver = new SimpleResolver(testIp)
        resolver.setPort(testPort)
        def result = new Lookup("djp01.ps3.update.playstation.net", Type.A)
        result.setResolver(resolver)
        result.run()

        assert result.result == Lookup.SUCCESSFUL
        assert result.answers.length > 0
        assert result.answers[0] instanceof org.xbill.DNS.ARecord
        def addr = ((org.xbill.DNS.ARecord) result.answers[0]).getAddress()
        assert addr.getHostAddress() == testIp
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void "PS3 KR domain should redirect to local IP"() {
        def resolver = new SimpleResolver(testIp)
        resolver.setPort(testPort)
        def result = new Lookup("dkr01.ps3.update.playstation.net", Type.A)
        result.setResolver(resolver)
        result.run()

        assert result.result == Lookup.SUCCESSFUL
        assert result.answers.length > 0
        assert result.answers[0] instanceof org.xbill.DNS.ARecord
        def addr = ((org.xbill.DNS.ARecord) result.answers[0]).getAddress()
        assert addr.getHostAddress() == testIp
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void "PS3 UK domain should redirect to local IP"() {
        def resolver = new SimpleResolver(testIp)
        resolver.setPort(testPort)
        def result = new Lookup("duk01.ps3.update.playstation.net", Type.A)
        result.setResolver(resolver)
        result.run()

        assert result.result == Lookup.SUCCESSFUL
        assert result.answers.length > 0
        assert result.answers[0] instanceof org.xbill.DNS.ARecord
        def addr = ((org.xbill.DNS.ARecord) result.answers[0]).getAddress()
        assert addr.getHostAddress() == testIp
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void "Non-PS3 domain should resolve via upstream DNS"() {
        def resolver = new SimpleResolver(testIp)
        resolver.setPort(testPort)
        def result = new Lookup("google.com", Type.A)
        result.setResolver(resolver)
        result.run()

        assert result.result == Lookup.SUCCESSFUL
        assert result.answers.length > 0
        assert result.answers[0].rdataToWireCanonical().length > 0
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void "Non-PS3 domain with different TLD should resolve via upstream DNS"() {
        def resolver = new SimpleResolver(testIp)
        resolver.setPort(testPort)
        def result = new Lookup("example.org", Type.A)
        result.setResolver(resolver)
        result.run()

        assert result.result == Lookup.SUCCESSFUL
        assert result.answers.length > 0
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void "Invalid PS3 domain should not redirect"() {
        def resolver = new SimpleResolver(testIp)
        resolver.setPort(testPort)
        def result = new Lookup("invalid.ps3.update.playstation.net", Type.A)
        result.setResolver(resolver)
        result.run()

        assert result.result != Lookup.SUCCESSFUL
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void "Non-matching PS3 update domain should not redirect"() {
        def resolver = new SimpleResolver(testIp)
        resolver.setPort(testPort)
        def result = new Lookup("test123.ps3.update.playstation.net", Type.A)
        result.setResolver(resolver)
        result.run()

        assert result.result != Lookup.SUCCESSFUL
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void "Multiple queries should be handled"() {
        def results = []
        10.times {
            def resolver = new SimpleResolver(testIp)
            resolver.setPort(testPort)
            def result = new Lookup("dus01.ps3.update.playstation.net", Type.A)
            result.setResolver(resolver)
            result.run()
            results << result.result
        }

        assert results.every { it == Lookup.SUCCESSFUL }
    }

    private static int findAvailablePort() {
        def socket = new java.net.ServerSocket(0)
        def port = socket.getLocalPort()
        socket.close()
        return port
    }

    private static void waitForServerStartup() {
        def latch = new CountDownLatch(1)
        def maxWait = 5

        def executor = Executors.newSingleThreadExecutor()
        def future = executor.submit({
            def resolver = new SimpleResolver(testIp)
            resolver.setPort(testPort)

            for (int i = 0; i < maxWait * 10; i++) {
                try {
                    def result = new Lookup("dus01.ps3.update.playstation.net", Type.A)
                    result.setResolver(resolver)
                    result.run()
                    if (result.result == Lookup.SUCCESSFUL) {
                        latch.countDown()
                        return
                    }
                } catch (Exception e) {
                }
                Thread.sleep(100)
            }
        } as Callable<Void>)

        try {
            latch.await(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }
}
