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

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.proxylight.service.ProxyLight;
import de.zalando.ep.zalenium.proxylight.service.impl.BrowserMobProxy;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.Arrays;
import org.openqa.grid.web.servlet.handler.SeleniumBasedRequest;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.remote.CapabilityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;

import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;

public class SeleniumProxyLight {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeleniumProxyLight.class.getName());

    public static final String PROXY_TYPE = "proxyType";

    public static final String HTTP_PROXY = "httpProxy";

    private ProxyLight proxyLight;

    private Map<String, Object> requestedCapabilities;

    public ProxyLight getProxyLight() {
        return proxyLight;
    }

    public SeleniumProxyLight(final String host, final Integer port, final Map<String, Object> requestedCapabilities) {
        proxyLight = new BrowserMobProxy(host, port);
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
                    proxyLight.create(httpProxy);
                    proxyCreated = true;
                }
            }

            if (!proxyCreated) {
                proxyLight.create();
            }
        }
    }

    public void addPageRefCaptureForHar(final SeleniumBasedRequest seleniumRequest) {
        if (proxyLight != null && seleniumRequest != null
                && StringUtils.isNotEmpty(seleniumRequest.getPathInfo())
                && seleniumRequest.getPathInfo().endsWith("url")
                && StringUtils.isNotEmpty(seleniumRequest.getBody())) {
            JsonElement bodyRequest = new JsonParser().parse(seleniumRequest.getBody());
            if (bodyRequest != null && bodyRequest.getAsJsonObject() != null) {
                JsonElement urlObject = bodyRequest.getAsJsonObject().get("url");
                if (urlObject != null) {
                    proxyLight.addCapturePage(urlObject.getAsString());
                }
            }
        }
    }

    /**
     * If needed, add filters in sub proxy for each browser's request.
     */
    public void addFilterWhiteOrBlackList() {
        if (proxyLight != null) {
            requestedCapabilities.entrySet().stream().filter(capability -> capability.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_WHITE_LIST) || capability.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_BLACK_LIST)).forEach(capability -> {
                FilterUrlType filterUrlType = capability.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_WHITE_LIST) ? FilterUrlType.WHITELIST : FilterUrlType.BLACKLIST;
                String regex = String.valueOf(capability.getValue());
                LOGGER.debug("Adding {} '{}' on light proxy", filterUrlType.name(), regex);
                proxyLight.addBlackOrWhiteList(filterUrlType, regex);

            });
        }
    }

    /**
     * If needed, add headers in sub proxy for each browser's request.
     */
    public void addHeaders() {
        if (proxyLight != null) {
            requestedCapabilities.entrySet().stream().filter(capability -> capability.getKey().equals(ZaleniumCapabilityType.BROWSERMOBPROXY_HEADERS))
                    .findFirst()
                    .ifPresent(capability -> {
                        Map<String, Object> overridedHeaders;
                        try {
                            overridedHeaders = (HashMap<String, Object>) capability.getValue();
                        } catch (ClassCastException e) {
                            throw new RuntimeException("Error when getting capabilities " + capability.getValue() + ". Type required 'HashMap<String, Object>'.", e);
                        }
                        LOGGER.debug("Adding headers '{}'", Collections.unmodifiableMap(overridedHeaders));
                        proxyLight.addOverridedHeaders(overridedHeaders);
                    });
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
