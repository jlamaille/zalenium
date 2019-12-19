package de.zalando.ep.zalenium.lightproxy;

import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import static org.mockito.Mockito.*;

public class SeleniumLightProxyAddFilterTest extends AbstractSeleniumLightProxyTest {

    public static final String HTTP_LOCALHOST_80_PROXY_8001_WHITELIST = HTTP_LOCALHOST_80_PROXY_8001 + "/whitelist";
    public static final String HTTP_LOCALHOST_80_PROXY_8001_BLACKLIST = HTTP_LOCALHOST_80_PROXY_8001 + "/blacklist";

    @Test
    public void testFilterBlackList() {
        RestTemplate mockRestTemplate = getMockRestTemplateWithSimulateCreateBmp(HTTP_LOCALHOST_80_PROXY);
        capabilitySupportedByDockerSelenium.put(ZaleniumCapabilityType.LIGHT_PROXY_BLACK_LIST,".*google.*");
        SeleniumLightProxy seleniumLightProxy = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("regex", capabilitySupportedByDockerSelenium.get(ZaleniumCapabilityType.LIGHT_PROXY_BLACK_LIST));
        HttpEntity<MultiValueMap<String, Object>> requestCreateHar1 = new HttpEntity<>(body, headers);
        doNothing().when(mockRestTemplate).put(HTTP_LOCALHOST_80_PROXY_8001_BLACKLIST, requestCreateHar1);
        seleniumLightProxy.addFilterWhiteOrBlackList();

        verify(mockRestTemplate, times(1)).put(HTTP_LOCALHOST_80_PROXY_8001_BLACKLIST, requestCreateHar1);

    }

    @Test
    public void testFilterWhiteList() {
        RestTemplate mockRestTemplate = getMockRestTemplateWithSimulateCreateBmp(HTTP_LOCALHOST_80_PROXY);
        capabilitySupportedByDockerSelenium.put(ZaleniumCapabilityType.LIGHT_PROXY_WHITE_LIST,".*pagesjaunes.*");
        SeleniumLightProxy seleniumLightProxy = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("regex", capabilitySupportedByDockerSelenium.get(ZaleniumCapabilityType.LIGHT_PROXY_WHITE_LIST));
        HttpEntity<MultiValueMap<String, Object>> requestCreateHar1 = new HttpEntity<>(body, headers);
        doNothing().when(mockRestTemplate).put(HTTP_LOCALHOST_80_PROXY_8001_WHITELIST, requestCreateHar1);
        seleniumLightProxy.addFilterWhiteOrBlackList();

        verify(mockRestTemplate, times(1)).put(HTTP_LOCALHOST_80_PROXY_8001_WHITELIST, requestCreateHar1);

    }

}
