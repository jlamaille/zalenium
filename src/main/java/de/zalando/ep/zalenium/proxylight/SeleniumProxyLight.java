package de.zalando.ep.zalenium.proxylight;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.proxylight.service.ProxyLight;
import de.zalando.ep.zalenium.proxylight.service.impl.BrowserMobProxy;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.remote.CapabilityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;

public class SeleniumProxyLight {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeleniumProxyLight.class.getName());

    // Object to manipulate Rest Service
    private RestTemplate restTemplate = new RestTemplate();

    private ProxyLight proxyLight;

    /**
     * Browser mob proxy url
     */
    private String url;

    /**
     * Set of ports for sub proxys.
     */
    private Set<Integer> testSubProxyPorts; // TODO clé/valeur avec test name obligatoire ?

    public SeleniumProxyLight(final String host, final Integer port, final Map<String, Object> requestedCapabilities) {
        proxyLight = new BrowserMobProxy(host, port);

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
     *
     * @param requestedCapabilities : requested capability
     */
    private void createSubProxy(final Map<String, Object> requestedCapabilities) {

        // Create proxy in browsermob proxy service. One proxy for one session.
        LOGGER.debug("Creating proxy on browsermob proxy...");

        if (requestedCapabilities != null
                && requestedCapabilities.containsKey(CapabilityType.PROXY)
                && requestedCapabilities.get(CapabilityType.PROXY) instanceof TreeMap) {
            Integer testProxyPort = null;
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
                    LOGGER.debug("Request capability contains proxy {}.", proxy.toString());
                    testProxyPort = proxyLight.create(httpProxy);
                }
            }

            if (testProxyPort == null) {
                testProxyPort = proxyLight.create();
            }

            // Set proxy on browser
            Proxy seleniumProxy = new Proxy();
            seleniumProxy.setHttpProxy(String.format("127.0.0.1:%s", testProxyPort)); // TODO static var
            seleniumProxy.setSslProxy(seleniumProxy.getHttpProxy());
            seleniumProxy.setProxyType(Proxy.ProxyType.MANUAL);
            requestedCapabilities.put(CapabilityType.PROXY, seleniumProxy); // TODO final à vérifier sur ce point !!?
            testSubProxyPorts.add(testProxyPort);

        }
    }

    public void addPageRefCaptureForHarInSubProxy(final String urlPageRef) {
        if (!SetUtils.emptyIfNull(testSubProxyPorts).isEmpty() && StringUtils.isEmpty(urlPageRef)) {
            Integer testProxyPort = testSubProxyPorts.iterator().next();
            LOGGER.debug("Adding pageRef {} in browsermob proxy on port {}", url, testProxyPort);
            proxyLight.addCapturePage(testProxyPort, urlPageRef);
        }
    }

    /**
     * Add filters in sub proxy for each browser's request.
     *
     * @param capability : required capability
     */
    private void addFilterWhiteOrBlackListInSubProxy(final Map.Entry<String, Object> capability) {
        FilterUrlType filterUrlType = capability.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_WHITE_LIST) ? 
                FilterUrlType.WHITELIST :
                FilterUrlType.BLACKLIST;
        String regex = String.valueOf(capability.getValue());
        LOGGER.debug("Adding {} '{}' on browsermob proxy", filterUrlType.name(), regex);
        proxyLight.addBlackOrWhiteList(testSubProxyPorts.iterator().next(), filterUrlType, regex);
    }

    /**
     * Add headers in sub proxy for each browser's request.
     *
     * @param capability : required capability
     */
    private void addHeadersInSubProxy(final Map.Entry<String, Object> capability) {
        Map<String, Object> overridedHeaders;
        try {
            overridedHeaders = (HashMap<String, Object>) capability.getValue();
        } catch (ClassCastException e) {
            throw new RuntimeException("Error when getting capabilities " + capability.getValue() + ". Type required 'HashMap<String, Object>'.", e);
        }
        LOGGER.debug("Adding headers '{}'", Collections.unmodifiableMap(overridedHeaders));
        proxyLight.addOverridedHeaders(testSubProxyPorts.iterator().next(), overridedHeaders);
    }

    /**
     * Delete proxy for current test in after session.
     */
    public void deleteCurrentSubProxy() {
        if (!SetUtils.emptyIfNull(testSubProxyPorts).isEmpty()) {
            Integer subProxyPort = testSubProxyPorts.iterator().next();
            proxyLight.delete(subProxyPort);
            testSubProxyPorts.remove(subProxyPort);
        }
    }

    /**
     * Save HAR File for current test in after session.
     */
    public void saveHar(final TestInformation testInformation) {
        if (testInformation != null
                && StringUtils.isNotEmpty(testInformation.getHarsFolderPath())
                && !testSubProxyPorts.isEmpty()) {
            Integer testProxyPort = testSubProxyPorts.iterator().next();
            // Get HAR
            LOGGER.debug("Getting HAR in browsermob proxy on port {}", testProxyPort);

            try {
                if (!Files.exists(Paths.get(testInformation.getHarsFolderPath()))) { // TODO Mutualisation ?
                    Path directories = Files.createDirectories(Paths.get(testInformation.getHarsFolderPath()));
                    CommonProxyUtilities.setFilePermissions(directories);
                    CommonProxyUtilities.setFilePermissions(directories.getParent());
                }
                String har = proxyLight.getHarAsJson(testProxyPort);
                String fileName = String.format("%s/%s", testInformation.getHarsFolderPath(), testInformation.getHarFileName());
                FileUtils.writeStringToFile(new File(fileName), har, StandardCharsets.UTF_8);
                Path harFile = Paths.get(fileName);
                CommonProxyUtilities.setFilePermissions(harFile);
            } catch (RestClientException | IOException e) {
                throw new RuntimeException("Error when getting HAR in proxy.", e);
            }
        }
    }

    /**
     * Allows to put resource.
     *
     * @param resource     : resource
     * @param url          : url to call
     * @param errorMessage : error message when put failed
     * @param <T>          : type of resource
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

}
