package de.zalando.ep.zalenium.browsermobproxy;

import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.remote.CapabilityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class BrowserMobProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserMobProxy.class.getName());

    public static final String WHITELIST = "whitelist";

    public static final String BLACKLIST = "blacklist";

    // Object to manipulate Rest Service
    private RestTemplate restTemplate = new RestTemplate();

    /**
     * Browser mob proxy url
     */
    private String url;

    /**
     * Port for sub proxys.
     */
    private Set<Integer> testSubProxyPorts;

    public BrowserMobProxy(String host, final Map<String, Object> requestedCapability) {
        this.url = String.format("http://%s:%s", host, "8080"); // TODO Port à param ?;
        testSubProxyPorts = new HashSet<>();
        createSubProxy(requestedCapability);

        if (!SetUtils.emptyIfNull(testSubProxyPorts).isEmpty()) {
            requestedCapability.entrySet().stream().filter(r -> r.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_WHITE_LIST) ||
                    r.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_BLACK_LIST)).forEach(r -> {
                        addFilterWhiteOrBlackListInSubProxy(r);
                    });
            requestedCapability.entrySet().stream().filter(r -> r.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_HEADERS)).findFirst().ifPresent(r -> {
                addHeadersInSubProxy(r);
            });
        }
    }

    private void createSubProxy(final Map<String, Object> requestedCapability) {

        // Create proxy in browsermob proxy service. One proxy for one session.
        LOGGER.debug("Creating proxy on browsermob proxy...");
        String urlCreateProxy = String.format("%s/proxy", this.url);

        if (requestedCapability != null
                && requestedCapability.containsKey(CapabilityType.PROXY)
                && requestedCapability.get(CapabilityType.PROXY) instanceof TreeMap) {
            TreeMap proxy = (TreeMap) requestedCapability.get(CapabilityType.PROXY);
            if (proxy != null) {
                String proxyType = (String) proxy.getOrDefault("proxyType", StringUtils.EMPTY);
                String httpProxy = (String) proxy.getOrDefault("httpProxy", StringUtils.EMPTY);
                if (proxyType.equalsIgnoreCase(String.valueOf(Proxy.ProxyType.MANUAL)) && StringUtils.isNotEmpty(httpProxy)) {
                    LOGGER.debug("Request capability contains proxy {}, add this proxy in browser mob proxy.", proxy.toString());
                    urlCreateProxy += String.format("?httpProxy=%s", httpProxy);
                }
            }
        }

        try {
            ResponseEntity<BMProxy> responseCreatedProxy = restTemplate.postForEntity(urlCreateProxy, // TODO JLA
                    null, BMProxy.class);
            if (responseCreatedProxy.getBody() != null
                    && responseCreatedProxy.getStatusCode().equals(HttpStatus.OK)) {
                int testProxyPort = responseCreatedProxy.getBody().getPort();
                LOGGER.debug("Browsermob proxy created on port {}", testProxyPort);

                // Set proxy on browser
                Proxy seleniumProxy = new Proxy();
                seleniumProxy.setHttpProxy(String.format("127.0.0.1:%s", testProxyPort));
                seleniumProxy.setSslProxy(seleniumProxy.getHttpProxy());
                seleniumProxy.setProxyType(Proxy.ProxyType.MANUAL);
                requestedCapability.put(CapabilityType.PROXY, seleniumProxy);
                testSubProxyPorts.add(testProxyPort);

            } else {
                LOGGER.error("Error when creating proxy in browsermob proxy. {}.",
                        responseCreatedProxy.getBody() != null
                                ? responseCreatedProxy.getBody().toString()
                                : StringUtils.EMPTY);
            }
        } catch (RestClientException e) {
            e.printStackTrace();
            LOGGER.error("Error when creating proxy in browsermob proxy. {}.", e.getLocalizedMessage());
        }
    }

    public void addPageRefCaptureForHarInSubProxy(final String urlPageRef) {
        if (!SetUtils.emptyIfNull(testSubProxyPorts).isEmpty()) {
            Integer testProxyPort = testSubProxyPorts.iterator().next();
            LOGGER.debug("Adding pageRef {} in browsermob proxy on port {}", url, testProxyPort);
            // If HAR not created
            ResponseEntity<String> har = getHarForCurrentSubProxy();
            if (har == null || StringUtils.isEmpty(har.getBody())) {
                // Create HAR with pageRef
                LOGGER.debug("Adding HAR with initial pageRef {} in browsermob proxy on port {}", url, testProxyPort);
                HttpHeaders headers = new HttpHeaders(); // TODO à mutaliser
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
                map.add("initialPageRef", urlPageRef);
                map.add("captureHeaders", true);
                map.add("captureCookies", true);
                map.add("captureContent", true);
                map.add("captureBinaryContent", true);
                HttpEntity<MultiValueMap<String, Object>> requestCreateHar = new HttpEntity<>(map, headers);
                try {
                    restTemplate.put(String.format("%s/proxy/%s/har", url,
                            testProxyPort), requestCreateHar);
                } catch (RestClientException e) {
                    e.printStackTrace();
                    LOGGER.error("Error when creating HAR in browsermob proxy. {}.", e.getLocalizedMessage());
                }
            } else {
                HttpHeaders headers = new HttpHeaders(); // TODO à mutaliser
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
                map.add("pageRef", urlPageRef);
                HttpEntity<MultiValueMap<String, Object>> requestPageRef = new HttpEntity<>(map, headers);
                try {
                    restTemplate.put(String.format("%s/proxy/%s/har/pageRef", url,
                            testProxyPort), requestPageRef);
                } catch (RestClientException e) {
                    e.printStackTrace();
                    LOGGER.error("Error when creating pageRef in browsermob proxy. {}.", e.getLocalizedMessage());
                }
            }
        }

    }

    /**
     * Add filters in sub proxy for each browser's request.
     * @param capability
     */
    private void addFilterWhiteOrBlackListInSubProxy(final Map.Entry<String, Object> capability) {
        String regexType = capability.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_WHITE_LIST) ? WHITELIST : BLACKLIST;
        String regex = String.valueOf(capability.getValue());
        LOGGER.debug("Adding {} '{}' on browsermob proxy", regexType, regex);
        HttpHeaders headers = new HttpHeaders(); // TODO à mutaliser
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.add("regex", regex);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(map, headers);
        try {
            restTemplate.put(String.format("%s/proxy/%s/%s", url, testSubProxyPorts.iterator().next(), regexType), request);
        } catch (RestClientException e) {
            e.printStackTrace();
            LOGGER.error("Error when adding black/white list in browsermob proxy. {}.", e.getLocalizedMessage());
        }
    }

    /**
     * Add headers in sub proxy for each browser's request.
     * @param capability
     */
    private void addHeadersInSubProxy(final Map.Entry<String, Object> capability) {
        Map<String, Object> overridedHeaders = (HashMap<String, Object>) capability.getValue();
        LOGGER.debug("Adding headers '{}' on browsermob proxy", overridedHeaders);
        try {
            HttpHeaders headers = new HttpHeaders(); // TODO à mutaliser
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> requestAddHeaders = new HttpEntity<>(overridedHeaders, headers);
            restTemplate.postForObject(String.format("%s/proxy/%s/headers", url, testSubProxyPorts.iterator().next()), requestAddHeaders, String.class);
        } catch (RestClientException e) {
            e.printStackTrace();
            LOGGER.error("Error when adding headers in browsermob proxy. {}.", e.getLocalizedMessage());
        }
    }

    /**
     * Delete proxy for current test in after session.
     */
    public void deleteCurrentSubProxy() {
        if (!SetUtils.emptyIfNull(testSubProxyPorts).isEmpty()) {
            try {
                Integer subProxyPort = testSubProxyPorts.iterator().next();
                LOGGER.debug("Deleting Browsermob proxy on port {}.", subProxyPort);
                restTemplate.delete(this.url + "/proxy/" + subProxyPort.toString());
                testSubProxyPorts.remove(subProxyPort);
            } catch (RestClientException e) {
                e.printStackTrace();
                LOGGER.error("Error when deleting proxy in browsermob proxy. {}.", e.getLocalizedMessage());
            }
        }
    }

    public Set<Integer> getTestSubProxyPorts() {
        return testSubProxyPorts;
    }

    public ResponseEntity<String> getHarForCurrentSubProxy() {
        return restTemplate.getForEntity(String.format("%s/proxy/%s/har", this.url,
                testSubProxyPorts.iterator().next()), String.class);
    }

    public static class BMProxy {

        private int port;

        public int getPort() {
            return port;
        }
    }
}
