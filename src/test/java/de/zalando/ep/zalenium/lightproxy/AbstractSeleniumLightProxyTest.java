package de.zalando.ep.zalenium.lightproxy;

import de.zalando.ep.zalenium.lightproxy.service.impl.BrowserMobProxy;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static de.zalando.ep.zalenium.lightproxy.service.impl.BrowserMobProxy.BMProxy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.ResponseEntity.ok;

abstract class AbstractSeleniumLightProxyTest {

    protected static final String HAR_LOG = "har log";
    protected static final String HTTP_LOCALHOST_80_PROXY = "http://localhost:80/proxy";
    protected static final String HTTP_LOCALHOST_80_PROXY_8001 = HTTP_LOCALHOST_80_PROXY + "/8001";
    protected static final String HTTP_LOCALHOST_80_PROXY_8001_HAR = HTTP_LOCALHOST_80_PROXY_8001 + "/har";

    protected static final Set<String> DEFAULT_CAPTURE_SETTINGS = SetUtils.hashSet(
            "captureHeaders",
            "captureCookies",
            "captureContent",
            "captureBinaryContent");

    protected Map<String, Object> capabilitySupportedByDockerSelenium;
    protected RestTemplate mockRestTemplate;

    @Before
    public void setup() {
        capabilitySupportedByDockerSelenium = getCapabilitySupportedByDockerSelenium();
        mockRestTemplate = getMockRestTemplateWithSimulateCreateBmp(HTTP_LOCALHOST_80_PROXY);
    }

    protected Map<String, Object> getCapabilitySupportedByDockerSelenium() {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        return requestedCapability;
    }

    protected RestTemplate getMockRestTemplateWithSimulateCreateBmp(String s) {
        // Create mock Rest Template to simulate interaction with bmp server
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        BMProxy bmProxy = new BMProxy();
        bmProxy.setPort(8001);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> requestCreateHar = new HttpEntity<>(StringUtils.EMPTY, headers);
        when(mockRestTemplate.postForEntity(s,
                requestCreateHar, BMProxy.class)).thenReturn(ok().body(bmProxy));
        return mockRestTemplate;
    }

    protected SeleniumLightProxy createSeleniumProxyLightWithMock(RestTemplate mockRestTemplate, Map<String, Object> capabilitySupportedByDockerSelenium) {
        SeleniumLightProxy seleniumLightProxy = new SeleniumLightProxy("localhost", 80, capabilitySupportedByDockerSelenium);
        ((BrowserMobProxy) seleniumLightProxy.getLightProxy()).setRestTemplate(mockRestTemplate);
        seleniumLightProxy.createSubProxy();
        return seleniumLightProxy;
    }
}