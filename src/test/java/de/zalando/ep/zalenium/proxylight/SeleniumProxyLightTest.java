package de.zalando.ep.zalenium.proxylight;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;
import org.openqa.selenium.Platform;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import de.zalando.ep.zalenium.proxylight.service.impl.BrowserMobProxy;

public class SeleniumProxyLightTest {

    @Test
    public void testCreateSeleniumProxyLightWithCorporateProxy() {

        // Create mock Rest Template to simulate interraction with bmp server
        RestTemplate mockRestTemplate = Mockito.mock(RestTemplate.class);
//        Mockito.when(mockRestTemplate.postForEntity("http://localhost:80/proxy?httpProxy=proxytest:3128",
//                null, BrowserMobProxy.BMProxy.class)).thenReturn(ResponseEntity.ok().body(new BrowserMobProxy.BMProxy()));
        Mockito.doNothing().when(mockRestTemplate);

        Map<String, Object> capabilitySupportedByDockerSelenium = getCapabilitySupportedByDockerSelenium();
//        Proxy proxy = new Proxy();
//        proxy.setHttpProxy("proxytest:3128");
//        proxy.setProxyType(Proxy.ProxyType.MANUAL);
        TreeMap tp = new TreeMap();
        tp.put("httpProxy", "proxytest:3128");
        tp.put("proxyType", Proxy.ProxyType.MANUAL.name());
        capabilitySupportedByDockerSelenium.put(CapabilityType.PROXY, tp);
        SeleniumProxyLight seleniumProxyLight = new SeleniumProxyLight("localhost", 80, capabilitySupportedByDockerSelenium);
        ((BrowserMobProxy) seleniumProxyLight.getProxyLight()).setRestTemplate(mockRestTemplate);
        seleniumProxyLight.createSubProxy();

        Mockito.verify(mockRestTemplate).postForEntity("http://localhosts:80/proxy?httpProxy=proxytest:3128",
                null, BrowserMobProxy.BMProxy.class);

//        Mockito.verify(mockRestTemplate);

        Assert.assertThat(seleniumProxyLight.getProxyLight(), Matchers.notNullValue());

    }

    private Map<String, Object> getCapabilitySupportedByDockerSelenium() {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        return requestedCapability;
    }
}
