package de.zalando.ep.zalenium.lightproxy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.lightproxy.service.LightProxy;
import de.zalando.ep.zalenium.lightproxy.service.impl.BrowserMobProxy;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.grid.web.servlet.handler.SeleniumBasedRequest;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.remote.CapabilityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;

import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;

/**
 * Object allowing the manipulation of the light proxy with a processing of parts specifically related to Selenium.
 */
public class SeleniumLightProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeleniumLightProxy.class.getName());

    public static final String PROXY_TYPE = "proxyType";

    public static final String HTTP_PROXY = "httpProxy";

    private LightProxy lightProxy;

    private Map<String, Object> requestedCapabilities;

    public LightProxy getLightProxy() {
        return lightProxy;
    }

    /**
     * Build an element.
     *
     * @param host                  : host container
     * @param port                  : port container
     * @param requestedCapabilities : capabilities requested by the test
     */
    public SeleniumLightProxy(final String host, final Integer port, final Map<String, Object> requestedCapabilities) {
        lightProxy = new BrowserMobProxy(host, port);
        this.requestedCapabilities = requestedCapabilities;
    }

    /**
     * Allows to create sub proxy for current test execution.
     */
    public void createSubProxy() {
        // Create proxy with proxy service. One proxy for one session.
        LOGGER.debug("Creating proxy...");

        boolean proxyCreated = false;
        if (requestedCapabilities != null
                && requestedCapabilities.containsKey(CapabilityType.PROXY)
                && requestedCapabilities.get(CapabilityType.PROXY) instanceof TreeMap) {
            TreeMap proxy = (TreeMap) requestedCapabilities.get(CapabilityType.PROXY);
            if (proxy != null) {
                String proxyType;
                String httpProxy;
                try {
                    proxyType = (String) proxy.getOrDefault(PROXY_TYPE, StringUtils.EMPTY);
                    httpProxy = (String) proxy.getOrDefault(HTTP_PROXY, StringUtils.EMPTY);
                } catch (ClassCastException e) {
                    throw new RuntimeException("Error when getting proxyType or httpProxy in proxy : " + proxy + ". Type required 'String'.", e);
                }
                if (proxyType.equalsIgnoreCase(String.valueOf(Proxy.ProxyType.MANUAL)) && StringUtils.isNotEmpty(httpProxy)) {
                    LOGGER.debug("Request capability contains proxy {}.", proxy.toString());
                    lightProxy.create(httpProxy);
                    proxyCreated = true;
                }
            }

        }
        if (!proxyCreated) {
            lightProxy.create();
        }
    }

    /**
     * Add a reference page to capture traffic related to it.
     *
     * @param seleniumRequest : The selenium request captured by the hub
     */
    public void addPageRefCaptureForHar(final SeleniumBasedRequest seleniumRequest) {
        if (lightProxy != null
                && (!requestedCapabilities.containsKey(ZaleniumCapabilityType.LIGHT_PROXY_CAPTURE_HAR) ||
                    Boolean.parseBoolean(Optional.ofNullable(
                            requestedCapabilities.get(ZaleniumCapabilityType.LIGHT_PROXY_CAPTURE_HAR)).
                            orElse(Boolean.TRUE).toString()))
                && seleniumRequest != null
                && StringUtils.isNotEmpty(seleniumRequest.getPathInfo())
                && seleniumRequest.getPathInfo().endsWith("url")
                && StringUtils.isNotEmpty(seleniumRequest.getBody())) {
            JsonElement bodyRequest = new JsonParser().parse(seleniumRequest.getBody());
            if (bodyRequest != null && bodyRequest.getAsJsonObject() != null) {
                JsonElement urlObject = bodyRequest.getAsJsonObject().get("url");
                if (urlObject != null) {
                    LOGGER.debug("Adding capture with har file for url {}", urlObject.getAsString());
                    Object browserCaptureSettings = requestedCapabilities.get(ZaleniumCapabilityType.LIGHT_PROXY_CAPTURE_HAR_SETTINGS);
                    lightProxy.addCapturePage(urlObject.getAsString(),
                            browserCaptureSettings != null ? (MultiValueMap<String, Object>) browserCaptureSettings : null);
                }
            }
        }
    }

    /**
     * If needed, add filters in sub proxy for each browser's request.
     */
    public void addFilterWhiteOrBlackList() {
        if (lightProxy != null) {
            requestedCapabilities.entrySet().stream().filter(capability ->
                    capability.getKey().equals(ZaleniumCapabilityType.LIGHT_PROXY_WHITE_LIST) ||
                            capability.getKey().equals(ZaleniumCapabilityType.LIGHT_PROXY_BLACK_LIST))
                    .forEach(capFilter -> {
                        UrlTypeFilter urlTypeFilter = capFilter.getKey()
                                .equals(ZaleniumCapabilityType.LIGHT_PROXY_WHITE_LIST)
                                ? UrlTypeFilter.WHITELIST : UrlTypeFilter.BLACKLIST;
                        String regex = String.valueOf(capFilter.getValue());
                        LOGGER.debug("Adding {} '{}' on light proxy", urlTypeFilter.name(), regex);
                        lightProxy.addBlackOrWhiteList(urlTypeFilter, regex);

                    });
        }
    }

    /**
     * If needed, add headers in sub proxy for each browser's request.
     */
    public void addHeaders() {
        if (lightProxy != null) {
            requestedCapabilities.entrySet().stream().filter(capability -> capability.getKey().equals(ZaleniumCapabilityType.LIGHT_PROXY_HEADERS))
                    .findFirst()
                    .ifPresent(capability -> {
                        Map<String, Object> overriddenHeaders;
                        try {
                            overriddenHeaders = (Map<String, Object>) capability.getValue();
                        } catch (ClassCastException e) {
                            throw new RuntimeException("Error when getting capabilities " + capability.getValue() + ". Type required 'Map<String, Object>'.", e);
                        }
                        LOGGER.debug("Adding headers '{}'", Collections.unmodifiableMap(overriddenHeaders));
                        lightProxy.addOverriddenHeaders(overriddenHeaders);
                    });
        }
    }

    /**
     * Save HAR File for current test in after session.
     */
    public void saveHar(final TestInformation testInformation) {
        if (testInformation != null
                && testInformation.isHarCaptured()
                && StringUtils.isNotEmpty(testInformation.getHarFolderPath())
                && lightProxy != null) {
            try {
                if (!Files.exists(Paths.get(testInformation.getHarFolderPath()))) { // TODO Mutualisation ?
                    Path directories = Files.createDirectories(Paths.get(testInformation.getHarFolderPath()));
                    CommonProxyUtilities.setFilePermissions(directories);
                    CommonProxyUtilities.setFilePermissions(directories.getParent());
                }
                String har = lightProxy.getHarAsJson();
                if (StringUtils.isNotEmpty(har)) {
                    String fileName = String.format("%s/%s", testInformation.getHarFolderPath(), testInformation.getHarFileName());
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
