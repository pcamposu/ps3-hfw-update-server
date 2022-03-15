package com.pcamposu.ps3.hfwserver.http.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils

import static org.junit.jupiter.api.Assertions.*

class UpdateListServiceTest {

    UpdateListService service

    @BeforeEach
    void setup() {
        service = new UpdateListService()
    }

    @Test
    void "generateUpdateList should not include port in URL"() {
        ReflectionTestUtils.setField(service, "hfwVersion", "9.00")
        ReflectionTestUtils.setField(service, "localIp", "10.0.0.243")

        def result = service.generateUpdateList("us")

        assert result.contains("Dest=0100;")
        assert result.contains("SystemSoftwareVersion=9.00;")
        assert result.contains("CDN=http://10.0.0.243/PS3UPDAT.PUP;")
        assert result.contains("CDN_Timeout=30;")
    }

    @Test
    void "generateUpdateList for different regions"() {
        ReflectionTestUtils.setField(service, "hfwVersion", "9.00")
        ReflectionTestUtils.setField(service, "localIp", "127.0.0.1")

        def usResult = service.generateUpdateList("us")
        assert usResult.contains("Dest=0100;")

        def euResult = service.generateUpdateList("eu")
        assert euResult.contains("Dest=0200;")

        def jpResult = service.generateUpdateList("jp")
        assert jpResult.contains("Dest=0101;")
    }

    @Test
    void "CDN URL should be absolute HTTP URL without port"() {
        ReflectionTestUtils.setField(service, "localIp", "10.0.0.50")

        def result = service.generateUpdateList("us")

        assert result.contains("CDN=http://10.0.0.50/PS3UPDAT.PUP;")
    }
}
