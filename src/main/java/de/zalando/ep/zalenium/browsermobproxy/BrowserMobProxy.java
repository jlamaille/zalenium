package de.zalando.ep.zalenium.browsermobproxy;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.util.resource.URLResource;
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

import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;

public class BrowserMobProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserMobProxy.class.getName());

    private static final String WHITELIST = "whitelist";

    private static final String BLACKLIST = "blacklist";

    // Object to manipulate Rest Service
    private RestTemplate restTemplate = new RestTemplate();

    /**
     * Browser mob proxy url
     */
    private String url;

    /**
     * Set of ports for sub proxys.
     */
    private Set<Integer> testSubProxyPorts; // TODO clé/valeur avec test name obligatoire ?

    public BrowserMobProxy(String host, final Map<String, Object> requestedCapabilities) { // TODO Service technique en dessous
        this.url = String.format("http://%s:%s", host, "8080"); // TODO Port à param ?; var static HTTP
        testSubProxyPorts = new HashSet<>();
        createSubProxy(requestedCapabilities);

        if (!SetUtils.emptyIfNull(testSubProxyPorts).isEmpty()) {
            requestedCapabilities.entrySet().stream().filter(requestedCapability -> requestedCapability.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_WHITE_LIST) ||
                    requestedCapability.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_BLACK_LIST)).forEach(this::addFilterWhiteOrBlackListInSubProxy);
            requestedCapabilities.entrySet().stream().filter(r -> r.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_HEADERS)).findFirst().ifPresent(this::addHeadersInSubProxy);
        }
    }

    /**
     * Allows to create sub proxy for current test execution.
     * @param requestedCapabilities : requested capability
     */
    private void createSubProxy(final Map<String, Object> requestedCapabilities) {

        // Create proxy in browsermob proxy service. One proxy for one session.
        LOGGER.debug("Creating proxy on browsermob proxy...");

        if (requestedCapabilities != null
                && requestedCapabilities.containsKey(CapabilityType.PROXY)
                && requestedCapabilities.get(CapabilityType.PROXY) instanceof TreeMap) {
            String urlCreateProxy = String.format("%s/proxy", this.url); // TODO Var static URL Resource
            TreeMap proxy = (TreeMap) requestedCapabilities.get(CapabilityType.PROXY);
            if (proxy != null) {
                String proxyType;
                String httpProxy;
                try {
                    proxyType = (String) proxy.getOrDefault("proxyType", StringUtils.EMPTY);
                    httpProxy = (String) proxy.getOrDefault("httpProxy", StringUtils.EMPTY);
                } catch (ClassCastException e) {
                    throw new RuntimeException("Error when getting proxyType or httpProxy in proxy : " + proxy + ". Type required 'String'.", e);
                }
                if (proxyType.equalsIgnoreCase(String.valueOf(Proxy.ProxyType.MANUAL)) && StringUtils.isNotEmpty(httpProxy)) {
                    LOGGER.debug("Request capability contains proxy {}, add this proxy in browser mob proxy.", proxy.toString());
                    urlCreateProxy += String.format("?httpProxy=%s", httpProxy); // TODO Utilisation de resource pour éviter les string param
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
                    seleniumProxy.setHttpProxy(String.format("127.0.0.1:%s", testProxyPort)); // TODO static var
                    seleniumProxy.setSslProxy(seleniumProxy.getHttpProxy());
                    seleniumProxy.setProxyType(Proxy.ProxyType.MANUAL);
                    requestedCapabilities.put(CapabilityType.PROXY, seleniumProxy); // TODO final à vérifier sur ce point !!?
                    testSubProxyPorts.add(testProxyPort);

                } else {
                    LOGGER.error("Error when creating proxy in browsermob proxy. {}.",
                            responseCreatedProxy.getBody() != null
                                    ? responseCreatedProxy.getBody().toString()
                                    : StringUtils.EMPTY);
                }
            } catch (RestClientException e) {
                e.printStackTrace();
                LOGGER.error("Error when creating proxy in browsermob proxy. {}.", e.getLocalizedMessage()); // TODO throw ou pas, reflexion sur le sujet
            }
        }

    }

    public void addPageRefCaptureForHarInSubProxy(final String urlPageRef) {
        if (!SetUtils.emptyIfNull(testSubProxyPorts).isEmpty() && StringUtils.isEmpty(urlPageRef)) {
            Integer testProxyPort = testSubProxyPorts.iterator().next();
            LOGGER.debug("Adding pageRef {} in browsermob proxy on port {}", url, testProxyPort);
            // If HAR not created
            ResponseEntity<String> har = getHarForCurrentSubProxy();
            if (har == null || StringUtils.isEmpty(har.getBody())) {
                // Create HAR with pageRef
                LOGGER.debug("Adding HAR with initial pageRef {} in browsermob proxy on port {}", url, testProxyPort);
                MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
                map.add("initialPageRef", urlPageRef); // TODO paramétrable ou pas ? méthode
                map.add("captureHeaders", true);
                map.add("captureCookies", true);
                map.add("captureContent", true);
                map.add("captureBinaryContent", true);
                putResource(map,
                        String.format("%s/proxy/%s/har", url, testProxyPort),
                        "Error when creating HAR in browsermob proxy.");
            } else {
                MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
                map.add("pageRef", urlPageRef); // TODO Bug on capture pas tout sur les pages suivantes ?
                putResource(map,
                        String.format("%s/proxy/%s/har/pageRef", url, testProxyPort),
                        "Error when creating pageRef in browsermob proxy.");
            }
        }

    }

    /**
     * Add filters in sub proxy for each browser's request.
     * @param capability : required capability
     */
    private void addFilterWhiteOrBlackListInSubProxy(final Map.Entry<String, Object> capability) {
        String regexType = capability.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_WHITE_LIST) ? WHITELIST : BLACKLIST;
        String regex = String.valueOf(capability.getValue());
        LOGGER.debug("Adding {} '{}' on browsermob proxy", regexType, regex);
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.add("regex", regex);
        putResource(map,
                String.format("%s/proxy/%s/%s", url, testSubProxyPorts.iterator().next(), regexType),
                "Error when adding black/white list in browsermob proxy.");
    }

    /**
     * Add headers in sub proxy for each browser's request.
     * @param capability : required capability
     */
    private void addHeadersInSubProxy(final Map.Entry<String, Object> capability) {
        Map<String, Object> overridedHeaders;
        try {
            overridedHeaders = (HashMap<String, Object>) capability.getValue();
        } catch (ClassCastException e) {
            throw new RuntimeException("Error when getting capabilities " + capability.getValue() + ". Type required 'HashMap<String, Object>'.", e);
        }
        LOGGER.debug("Adding headers '{}' on browsermob proxy", Collections.unmodifiableMap(overridedHeaders));
        HttpHeaders headers = new HttpHeaders(); // TODO à mutaliser
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestAddHeaders = new HttpEntity<>(overridedHeaders, headers);
        try {
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
                restTemplate.delete(this.url + "/proxy/" + subProxyPort.toString()); // TODO URL resource
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

    /**
     * Allows to put resource.
     * @param resource : resource
     * @param url : url to call
     * @param errorMessage : error message when put failed
     * @param <T> : type of resource
     */
    private <T> void putResource(T resource, final String url, final String errorMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<Object> requestCreateHar = new HttpEntity<>(resource, headers);
        try {
            restTemplate.put(url, requestCreateHar);
        } catch (RestClientException e) {
            e.printStackTrace();
            LOGGER.error(String.format("%s. %s", errorMessage, e.getLocalizedMessage()));
        }
    }

    /**
     * Mapped Proxy with Browser Mob Proxy server.
     */
    public static class BMProxy {

        private int port;

        public int getPort() {
            return port;
        }
    }
}
