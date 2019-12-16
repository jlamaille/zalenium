package de.zalando.ep.zalenium.proxylight;

import de.zalando.ep.zalenium.proxylight.service.impl.BrowserMobProxy;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openqa.selenium.Platform;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static de.zalando.ep.zalenium.proxylight.service.impl.BrowserMobProxy.BMProxy;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.ResponseEntity.badRequest;
import static org.springframework.http.ResponseEntity.ok;

public class SeleniumProxyLightTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private Map<String, Object> capabilitySupportedByDockerSelenium;

    @Before
    public void setup() {
        capabilitySupportedByDockerSelenium = getCapabilitySupportedByDockerSelenium();
    }

    @Test
    public void testCreateSeleniumProxyLightWithCorporateProxy() {
        RestTemplate mockRestTemplate = getMockRestTemplateWithSimulateCreateBmp("http://localhost:80/proxy?httpProxy=proxytest:3128");

        TreeMap tp = new TreeMap();
        tp.put("httpProxy", "proxytest:3128");
        tp.put("proxyType", Proxy.ProxyType.MANUAL.name());
        capabilitySupportedByDockerSelenium.put(CapabilityType.PROXY, tp);
        callCreateBmpAndCheckResult(mockRestTemplate, capabilitySupportedByDockerSelenium, "http://localhost:80/proxy?httpProxy=proxytest:3128");

    }

    @Test
    public void testCreateSeleniumProxyLightWithoutCorporateProxy() {
        RestTemplate mockRestTemplate = getMockRestTemplateWithSimulateCreateBmp("http://localhost:80/proxy");

        callCreateBmpAndCheckResult(mockRestTemplate, capabilitySupportedByDockerSelenium, "http://localhost:80/proxy");
    }

    @Test
    public void testDeleteSeleniumProxyLight() {
        RestTemplate mockRestTemplate = getMockRestTemplateWithSimulateCreateBmp("http://localhost:80/proxy");
        SeleniumProxyLight seleniumProxyLight = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);
        doNothing().when(mockRestTemplate).delete("http://localhost:80/proxy/8001");
        seleniumProxyLight.getProxyLight().delete();
        verify(mockRestTemplate).delete("http://localhost:80/proxy/8001");
    }

    @Test
    public void testCreateSeleniumProxyLightWithBadTypeInCorporateProxy() {
        RestTemplate mockRestTemplate = getMockRestTemplateWithSimulateCreateBmp("http://localhost:80/proxy");

        TreeMap tp = new TreeMap();
        Integer one = new Integer(1);
        tp.put("httpProxy", one);
        tp.put("proxyType", one);
        capabilitySupportedByDockerSelenium.put(CapabilityType.PROXY, tp);
        thrown.expect(RuntimeException.class);
        thrown.expectMessage(is("Error when getting proxyType or httpProxy in proxy : {httpProxy=1, proxyType=1}. Type required 'String'."));
        SeleniumProxyLight seleniumProxyLight = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);
    }

    @Test
    public void testCreateSeleniumProxyLightWithServerErrorBadRequest() {
        // Create mock Rest Template to simulate interaction with bmp server
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        when(mockRestTemplate.postForEntity("http://localhost:80/proxy",
                null, BMProxy.class)).thenReturn(badRequest().build());

        thrown.expect(RuntimeException.class);
        thrown.expectMessage(Matchers.startsWith("Error when creating proxy in browsermob proxy."));
        SeleniumProxyLight seleniumProxyLight = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);
    }

    @Test
    public void testCreateSeleniumProxyLightWithServerRestError() {
        // Create mock Rest Template to simulate interaction with bmp server
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        RestClientException restError = new RestClientException("Rest Error");
        when(mockRestTemplate.postForEntity("http://localhost:80/proxy",
                null, BMProxy.class)).thenThrow(restError);

        thrown.expect(RuntimeException.class);
        thrown.expectMessage(is("Error when creating proxy in browsermob proxy."));
        thrown.expectCause(is(restError));
        SeleniumProxyLight seleniumProxyLight = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);
    }

    private void callCreateBmpAndCheckResult(RestTemplate mockRestTemplate, Map<String, Object> capabilitySupportedByDockerSelenium, String s) {
        SeleniumProxyLight seleniumProxyLight = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);

        verify(mockRestTemplate).postForEntity(s,
                null, BMProxy.class);

        Assert.assertThat(seleniumProxyLight.getProxyLight(), notNullValue());
        Assert.assertThat(seleniumProxyLight.getProxyLight().getProxyUrl(), equalTo("http://127.0.0.1:8001"));
    }

    private SeleniumProxyLight createSeleniumProxyLightWithMock(RestTemplate mockRestTemplate, Map<String, Object> capabilitySupportedByDockerSelenium) {
        SeleniumProxyLight seleniumProxyLight = new SeleniumProxyLight("localhost", 80, capabilitySupportedByDockerSelenium);
        ((BrowserMobProxy) seleniumProxyLight.getProxyLight()).setRestTemplate(mockRestTemplate);
        seleniumProxyLight.createSubProxy();
        return seleniumProxyLight;
    }

    private RestTemplate getMockRestTemplateWithSimulateCreateBmp(String s) {
        // Create mock Rest Template to simulate interaction with bmp server
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        BMProxy bmProxy = new BMProxy();
        bmProxy.setPort(8001);
        when(mockRestTemplate.postForEntity(s,
                null, BMProxy.class)).thenReturn(ok().body(bmProxy));
        return mockRestTemplate;
    }

    private Map<String, Object> getCapabilitySupportedByDockerSelenium() {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        return requestedCapability;
    }
}
