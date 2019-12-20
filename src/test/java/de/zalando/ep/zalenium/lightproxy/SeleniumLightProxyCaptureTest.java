package de.zalando.ep.zalenium.lightproxy;

import de.zalando.ep.zalenium.lightproxy.service.impl.BrowserMobProxy;
import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.internal.verification.VerificationModeFactory;
import org.openqa.grid.web.servlet.handler.SeleniumBasedRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

public class SeleniumLightProxyCaptureTest extends AbstractSeleniumLightProxyTest {

    public static final String ON_INPUT_DATA_HAR_LOG = "onInputData(har log);";
    public static final String HTTP_LOCALHOST_80_PROXY_8001_HAR_PAGE_REF = HTTP_LOCALHOST_80_PROXY_8001 + "/har/pageRef";
    public static final String HTTP_WWW_PAGESJAUNES_FR = "http://www.pagesjaunes.fr";
    public static final String HTTP_WWW_MAPPY_FR = "http://www.mappy.fr";

    @Test
    public void testCaptureTwoPages() {
        SeleniumLightProxy seleniumLightProxy = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);
        when(mockRestTemplate.getForEntity(HTTP_LOCALHOST_80_PROXY_8001_HAR, String.class)).thenReturn(ResponseEntity.of(Optional.empty()));

        SeleniumBasedRequest request = getSeleniumBasedRequestForCommandUrl("{\"url\" : \"" + HTTP_WWW_PAGESJAUNES_FR + "\" }");
        HttpEntity<Object> requestCreateHar1 = getHttpEntityWithCaptureSetting("initialPageRef", HTTP_WWW_PAGESJAUNES_FR);
        doNothing().when(mockRestTemplate).put(HTTP_LOCALHOST_80_PROXY_8001_HAR, requestCreateHar1);
        seleniumLightProxy.addPageRefCaptureForHar(request);

        verify(mockRestTemplate).put(HTTP_LOCALHOST_80_PROXY_8001_HAR, requestCreateHar1);

        when(mockRestTemplate.getForEntity(HTTP_LOCALHOST_80_PROXY_8001_HAR, String.class)).thenReturn(ResponseEntity.ok("har log"));
        Assert.assertThat(seleniumLightProxy.getLightProxy().getHarAsJsonP(), equalTo(ON_INPUT_DATA_HAR_LOG));

        SeleniumBasedRequest request2 = getSeleniumBasedRequestForCommandUrl("{\"url\" : \"" + HTTP_WWW_MAPPY_FR + "\" }");
        HttpEntity<Object> requestCreateHar2 = getHttpEntityWithCaptureSetting("pageRef", HTTP_WWW_MAPPY_FR);
        doNothing().when(mockRestTemplate).put(HTTP_LOCALHOST_80_PROXY_8001_HAR_PAGE_REF, requestCreateHar2);
        seleniumLightProxy.addPageRefCaptureForHar(request2);

        verify(mockRestTemplate).put(HTTP_LOCALHOST_80_PROXY_8001_HAR_PAGE_REF, requestCreateHar2);

        verify(mockRestTemplate, VerificationModeFactory.times(3)).getForEntity(HTTP_LOCALHOST_80_PROXY_8001_HAR, String.class);

    }

    @Test
    public void testCapturePageWithOverriddenSettings() {
        MultiValueMap<String, Object> captureContent = buildDefaultCaptureHarSettingsWithOneOverriddenSetting("captureContent", false);
        capabilitySupportedByDockerSelenium.put(ZaleniumCapabilityType.LIGHT_PROXY_CAPTURE_HAR_SETTINGS, captureContent);
        SeleniumLightProxy seleniumLightProxy = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);
        when(mockRestTemplate.getForEntity(HTTP_LOCALHOST_80_PROXY_8001_HAR, String.class)).thenReturn(ResponseEntity.of(Optional.empty()));

        SeleniumBasedRequest request = getSeleniumBasedRequestForCommandUrl("{\"url\" : \"" + HTTP_WWW_PAGESJAUNES_FR + "\" }");
        captureContent.add("initialPageRef", HTTP_WWW_PAGESJAUNES_FR);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity requestCreateHar = new HttpEntity<>(captureContent, headers);
        doNothing().when(mockRestTemplate).put(HTTP_LOCALHOST_80_PROXY_8001_HAR, requestCreateHar);
        seleniumLightProxy.addPageRefCaptureForHar(request);

        verify(mockRestTemplate).put(HTTP_LOCALHOST_80_PROXY_8001_HAR, requestCreateHar);

    }

    @Test
    public void testCapturePageDisable() {
        capabilitySupportedByDockerSelenium.put(ZaleniumCapabilityType.LIGHT_PROXY_CAPTURE_HAR, "false");
        SeleniumLightProxy seleniumLightProxy = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);
        SeleniumBasedRequest request = getSeleniumBasedRequestForCommandUrl("{\"url\" : \"" + HTTP_WWW_PAGESJAUNES_FR + "\" }");
        seleniumLightProxy.addPageRefCaptureForHar(request);
        verify(mockRestTemplate, times(1)).postForEntity(HTTP_LOCALHOST_80_PROXY, null, BrowserMobProxy.BMProxy.class);
        verifyNoMoreInteractions(mockRestTemplate);
    }

    private SeleniumBasedRequest getSeleniumBasedRequestForCommandUrl(String s) {
        SeleniumBasedRequest request = mock(SeleniumBasedRequest.class);
        when(request.getPathInfo()).thenReturn("/session/62499d645027e6a87dc56ab9f44df68d/url");
        when(request.getBody()).thenReturn(s);
        return request;
    }

    private HttpEntity<Object> getHttpEntityWithCaptureSetting(String pageRefType, String pageRefValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, Object> captureSettings = buildDefaultCaptureHarSettingsWithOneOverriddenSetting(pageRefType, pageRefValue);
        return new HttpEntity<>(captureSettings, headers);
    }

    private MultiValueMap<String, Object> buildDefaultCaptureHarSettingsWithOneOverriddenSetting(String pageRefType, Object pageRefValue) {
        MultiValueMap<String, Object> captureSettings = new LinkedMultiValueMap<>();
        captureSettings.add(pageRefType, pageRefValue);
        DEFAULT_CAPTURE_SETTINGS.forEach(s -> {
            captureSettings.putIfAbsent(s, Collections.singletonList(Boolean.TRUE));
        });
        return captureSettings;
    }

}
