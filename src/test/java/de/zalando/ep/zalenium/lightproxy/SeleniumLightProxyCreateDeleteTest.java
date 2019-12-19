package de.zalando.ep.zalenium.lightproxy;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.remote.CapabilityType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import static de.zalando.ep.zalenium.lightproxy.service.impl.BrowserMobProxy.BMProxy;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.ResponseEntity.badRequest;

public class SeleniumLightProxyCreateDeleteTest extends AbstractSeleniumLightProxyTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testCreateWithCorporateProxy() {
        RestTemplate mockRestTemplate = getMockRestTemplateWithSimulateCreateBmp("http://localhost:80/proxy?httpProxy=proxytest:3128");

        TreeMap tp = new TreeMap();
        tp.put("httpProxy", "proxytest:3128");
        tp.put("proxyType", Proxy.ProxyType.MANUAL.name());
        capabilitySupportedByDockerSelenium.put(CapabilityType.PROXY, tp);
        callCreateBmpAndCheckResult(mockRestTemplate, capabilitySupportedByDockerSelenium, "http://localhost:80/proxy?httpProxy=proxytest:3128");

    }

    @Test
    public void testCreateWithoutCorporateProxy() {
        RestTemplate mockRestTemplate = getMockRestTemplateWithSimulateCreateBmp(HTTP_LOCALHOST_80_PROXY);

        callCreateBmpAndCheckResult(mockRestTemplate, capabilitySupportedByDockerSelenium, HTTP_LOCALHOST_80_PROXY);
    }

    @Test
    public void testDeleteSeleniumProxyLight() {
        RestTemplate mockRestTemplate = getMockRestTemplateWithSimulateCreateBmp(HTTP_LOCALHOST_80_PROXY);
        SeleniumLightProxy seleniumLightProxy = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);
        doNothing().when(mockRestTemplate).delete(HTTP_LOCALHOST_80_PROXY_8001);
        seleniumLightProxy.getLightProxy().delete();
        verify(mockRestTemplate).delete(HTTP_LOCALHOST_80_PROXY_8001);
    }

    @Test
    public void testDeleteSeleniumProxyLightServerRestError() {
        RestTemplate mockRestTemplate = getMockRestTemplateWithSimulateCreateBmp(HTTP_LOCALHOST_80_PROXY);
        SeleniumLightProxy seleniumLightProxy = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);
        doThrow(new RestClientException("Error Rest Mock")).when(mockRestTemplate).delete(HTTP_LOCALHOST_80_PROXY_8001);
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Error when deleting proxy [port : 8001] in browsermob proxy.");
        seleniumLightProxy.getLightProxy().delete();
    }

    @Test
    public void testCreateWithBadTypeInCorporateProxy() {
        RestTemplate mockRestTemplate = getMockRestTemplateWithSimulateCreateBmp(HTTP_LOCALHOST_80_PROXY);

        TreeMap tp = new TreeMap();
        Integer one = new Integer(1);
        tp.put("httpProxy", one);
        tp.put("proxyType", one);
        capabilitySupportedByDockerSelenium.put(CapabilityType.PROXY, tp);
        thrown.expect(RuntimeException.class);
        thrown.expectMessage(is("Error when getting proxyType or httpProxy in proxy : {httpProxy=1, proxyType=1}. Type required 'String'."));
        SeleniumLightProxy seleniumLightProxy = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);
    }

    @Test
    public void testCreateWithServerErrorBadRequest() {
        // Create mock Rest Template to simulate interaction with bmp server
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        when(mockRestTemplate.postForEntity(HTTP_LOCALHOST_80_PROXY,
                null, BMProxy.class)).thenReturn(badRequest().build());

        thrown.expect(RuntimeException.class);
        thrown.expectMessage(Matchers.startsWith("Error when creating proxy in browsermob proxy."));
        SeleniumLightProxy seleniumLightProxy = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);
    }

    @Test
    public void testCreateWithServerRestError() {
        // Create mock Rest Template to simulate interaction with bmp server
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        RestClientException restError = new RestClientException("Rest Error");
        when(mockRestTemplate.postForEntity(HTTP_LOCALHOST_80_PROXY,
                null, BMProxy.class)).thenThrow(restError);

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Error when creating proxy in browsermob proxy.");
        thrown.expectCause(is(restError));
        SeleniumLightProxy seleniumLightProxy = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);
    }

    private HttpEntity<Object> getHttpEntityWithCaptureSetting(String pageRefType, String pageRefValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, Object> captureSettings = new LinkedMultiValueMap<>();
        captureSettings.add(pageRefType, pageRefValue);
        DEFAULT_CAPTURE_SETTINGS.stream().forEach(s -> {
            captureSettings.putIfAbsent(s, Collections.singletonList(Boolean.TRUE));
        });
        return new HttpEntity<>(captureSettings, headers);
    }

    private void callCreateBmpAndCheckResult(RestTemplate mockRestTemplate, Map<String, Object> capabilitySupportedByDockerSelenium, String s) {
        SeleniumLightProxy seleniumLightProxy = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);

        verify(mockRestTemplate).postForEntity(s,
                null, BMProxy.class);

        Assert.assertThat(seleniumLightProxy.getLightProxy(), notNullValue());
        Assert.assertThat(seleniumLightProxy.getLightProxy().getProxyUrl(), equalTo("http://127.0.0.1:8001"));
    }

}
