package com.pcamposu.ps3.hfwserver.model

import groovy.transform.CompileStatic

@CompileStatic
enum RegionInfo {
    US("us", "dus01", "0100"),
    EU("eu", "deu01", "0200"),
    JP("jp", "djp01", "0101"),
    UK("uk", "duk01", "0300"),
    KR("kr", "dkr01", "0200"),
    AU("au", "dau01", "0400"),
    MX("mx", "dmx01", "0500"),
    BR("br", "dbr01", "0600"),
    HK("hk", "dhk01", "0200"),
    TW("tw", "dtw01", "0200"),
    SG("sg", "dsg01", "0200"),
    MY("my", "dmy01", "0200"),
    ID("id", "did01", "0200"),
    TH("th", "dth01", "0200"),
    PH("ph", "dph01", "0200"),
    NZ("nz", "dnz01", "0700"),
    RU("ru", "dru01", "0800"),
    ZA("za", "dza01", "0900"),
    IN("in", "din01", "0200"),
    CL("cl", "dcl01", "1000"),
    CO("co", "dco01", "1100"),
    PE("pe", "dpe01", "1200"),
    AR("ar", "dar01", "1300"),
    CA("ca", "dca01", "0500"),
    CN("cn", "dcn01", "0200")

    final String code
    final String dnsPrefix
    final String targetId

    RegionInfo(String code, String dnsPrefix, String targetId) {
        this.code = code
        this.dnsPrefix = dnsPrefix
        this.targetId = targetId
    }

    static RegionInfo fromCode(String code) {
        values().find { it.code == code.toLowerCase() } ?: US
    }

    static RegionInfo fromDnsPrefix(String dnsPrefix) {
        values().find { it.dnsPrefix == dnsPrefix.toLowerCase() }
    }

    static String extractRegionFromUrl(String urlPath) {
        def pattern = ~/\/update\/ps3\/list\/([a-z]{2})\//
        def matcher = (urlPath =~ pattern)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return "us"
    }
}
