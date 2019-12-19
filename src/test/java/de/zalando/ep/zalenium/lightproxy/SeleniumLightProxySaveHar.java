package de.zalando.ep.zalenium.lightproxy;

import com.google.common.collect.ImmutableMap;
import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.mockito.Mockito.*;

public class SeleniumLightProxySaveHar extends AbstractSeleniumLightProxyTest {

    @Test
    public void testFilterAddHeaders() {
        RestTemplate mockRestTemplate = getMockRestTemplateWithSimulateCreateBmp(HTTP_LOCALHOST_80_PROXY);
        ImmutableMap<String, Object> overriddenHeaders = ImmutableMap.of("User-Agent", "BrowserMob-Agent");
        capabilitySupportedByDockerSelenium.put(ZaleniumCapabilityType.LIGHT_PROXY_HEADERS, overriddenHeaders);
        SeleniumLightProxy seleniumLightProxy = createSeleniumProxyLightWithMock(mockRestTemplate, capabilitySupportedByDockerSelenium);

        HttpEntity<Object> httpEntityWithOverriddenHeaders = getHttpEntityWithOverriddenHeaders(overriddenHeaders);
        doReturn(StringUtils.EMPTY).when(mockRestTemplate).postForObject(HTTP_LOCALHOST_80_PROXY_8001 + "/headers",
                httpEntityWithOverriddenHeaders,
                String.class);
        seleniumLightProxy.addHeaders();
        verify(mockRestTemplate, times(1)).postForObject(HTTP_LOCALHOST_80_PROXY_8001 + "/headers",
                httpEntityWithOverriddenHeaders,
                String.class);

    }
    private HttpEntity<Object> getHttpEntityWithOverriddenHeaders(final Map<String, Object> overriddenHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(overriddenHeaders, headers);
    }

}
