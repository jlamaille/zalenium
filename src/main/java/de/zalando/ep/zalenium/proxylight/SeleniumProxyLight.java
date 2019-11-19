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
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

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
import org.springframework.web.client.RestClientException;

import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;

public class SeleniumProxyLight {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeleniumProxyLight.class.getName());

    private ProxyLight proxyLight;

    public SeleniumProxyLight(final String host, final Integer port, final Map<String, Object> requestedCapabilities) {
        createSubProxy(host, port, requestedCapabilities);

        if (proxyLight != null) {
            requestedCapabilities.entrySet().stream().filter(requestedCapability -> requestedCapability.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_WHITE_LIST) ||
                    requestedCapability.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_BLACK_LIST)).forEach(this::addFilterWhiteOrBlackList);
            requestedCapabilities.entrySet().stream().filter(r -> r.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_HEADERS)).findFirst().ifPresent(this::addHeaders);
        }
    }

    public String getProxyUrlForBrowser() {
        return proxyLight.getProxyUrl();
    }

    /**
     * Allows to create sub proxy for current test execution.
     *
     * @param host
     * @param port
     * @param requestedCapabilities : requested capability
     */
    private void createSubProxy(final String host, final Integer port, final Map<String, Object> requestedCapabilities) {

        // Create proxy with proxy service. One proxy for one session.
        LOGGER.debug("Creating proxy...");

        if (requestedCapabilities != null
                && requestedCapabilities.containsKey(CapabilityType.PROXY)
                && requestedCapabilities.get(CapabilityType.PROXY) instanceof TreeMap) {
            proxyLight = new BrowserMobProxy(host, port);
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
                    proxyLight.create(httpProxy);
                }
            }

            if (testProxyPort == null) {
                proxyLight.create();
            }
        }
    }

    public void addPageRefCaptureForHar(final String urlPageRef) { // TODO Utilité de la méthode ?
        if (proxyLight != null && StringUtils.isEmpty(urlPageRef)) {
            proxyLight.addCapturePage(urlPageRef);
        }
    }

    /**
     * Add filters in sub proxy for each browser's request.
     *
     * @param capability : required capability
     */
    private void addFilterWhiteOrBlackList(final Map.Entry<String, Object> capability) {
        FilterUrlType filterUrlType = capability.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_WHITE_LIST) ? FilterUrlType.WHITELIST : FilterUrlType.BLACKLIST;
        String regex = String.valueOf(capability.getValue());
        LOGGER.debug("Adding {} '{}' on light proxy", filterUrlType.name(), regex);
        proxyLight.addBlackOrWhiteList(filterUrlType, regex);
    }

    /**
     * Add headers in sub proxy for each browser's request.
     *
     * @param capability : required capability
     */
    private void addHeaders(final Map.Entry<String, Object> capability) {
        Map<String, Object> overridedHeaders;
        try {
            overridedHeaders = (HashMap<String, Object>) capability.getValue();
        } catch (ClassCastException e) {
            throw new RuntimeException("Error when getting capabilities " + capability.getValue() + ". Type required 'HashMap<String, Object>'.", e);
        }
        LOGGER.debug("Adding headers '{}'", Collections.unmodifiableMap(overridedHeaders));
        proxyLight.addOverridedHeaders(overridedHeaders);
    }

    /**
     * Delete proxy for current test in after session.
     */
    public void delete() {
        if (proxyLight != null) {
            proxyLight.delete();
        }
    }

    /**
     * Save HAR File for current test in after session.
     */
    public void saveHar(final TestInformation testInformation) {
        if (testInformation != null
                && StringUtils.isNotEmpty(testInformation.getHarsFolderPath())
                && proxyLight != null) {
            try {
                if (!Files.exists(Paths.get(testInformation.getHarsFolderPath()))) { // TODO Mutualisation ?
                    Path directories = Files.createDirectories(Paths.get(testInformation.getHarsFolderPath()));
                    CommonProxyUtilities.setFilePermissions(directories);
                    CommonProxyUtilities.setFilePermissions(directories.getParent());
                }
                String har = proxyLight.getHarAsJson();
                if (StringUtils.isNotEmpty(har)) {
                    String fileName = String.format("%s/%s", testInformation.getHarsFolderPath(), testInformation.getHarFileName());
                    FileUtils.writeStringToFile(new File(fileName), har, StandardCharsets.UTF_8);
                    Path harFile = Paths.get(fileName);
                    CommonProxyUtilities.setFilePermissions(harFile);
                }
            } catch (RestClientException | IOException e) {
                throw new RuntimeException("Error when getting HAR in proxy.", e);
            }
        }
    }

}
