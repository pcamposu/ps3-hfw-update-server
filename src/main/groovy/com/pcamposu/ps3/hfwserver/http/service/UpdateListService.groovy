package com.pcamposu.ps3.hfwserver.http.service

import com.pcamposu.ps3.hfwserver.model.RegionInfo
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Slf4j
@CompileStatic
@Service
class UpdateListService {

    @Value('${ps3.hfw.version:9.00}')
    private String hfwVersion

    @Value('${local.ip:127.0.0.1}')
    private String localIp

    private static final List<String> ALL_REGION_TARGETS = [
        "83", "84", "85", "86", "87", "88", "89", "8A", "8B", "8C", "8D", "8F"
    ]

    String generateUpdateList(String regionCode) {
        RegionInfo regionInfo = RegionInfo.fromCode(regionCode)

        log.debug("Generating updatelist for region: ${regionInfo.code} (target: ${regionInfo.targetId})")

        String cdnUrl = buildCdnUrl()

        return "Dest=${regionInfo.targetId};" +
                "ImageVersion=ffffffff;" +
                "SystemSoftwareVersion=${hfwVersion};" +
                "CDN=${cdnUrl};" +
                "CDN_Timeout=30;\r\n"
    }

    String generateUniversalUpdateList() {
        log.debug("Generating universal updatelist for all regions")

        String cdnUrl = buildCdnUrl()
        StringBuilder sb = new StringBuilder()

        for (String targetId : ALL_REGION_TARGETS) {
            sb.append("Dest=${targetId};")
            sb.append("ImageVersion=ffffffff;")
            sb.append("SystemSoftwareVersion=${hfwVersion};")
            sb.append("CDN=${cdnUrl};")
            sb.append("CDN_Timeout=30;\r\n")
            sb.append("\r\n")
        }

        return sb.toString()
    }

    String generateUpdateList(RegionInfo regionInfo) {
        return generateUpdateList(regionInfo.code)
    }

    String extractRegionFromPath(String path) {
        return RegionInfo.extractRegionFromUrl(path)
    }

    private String buildCdnUrl() {
        return "http://${localIp}/PS3UPDAT.PUP"
    }
}
