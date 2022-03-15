package com.pcamposu.ps3.hfwserver.config

import com.pcamposu.ps3.hfwserver.dns.Ps3DnsServer
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@CompileStatic
@Configuration
class DnsServerConfig {

    @Bean
    Ps3DnsServer ps3DnsServer(
            @Value('\${ps3.dns.port:53}') int dnsPort,
            @Value('\${ps3.dns.upstream:8.8.8.8}') String upstreamDns,
            @Value('\${ps3.dns.localIp:127.0.0.1}') String localHttpIp
    ) {
        return new Ps3DnsServer(dnsPort, upstreamDns, localHttpIp)
    }
}
