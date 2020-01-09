package de.zalando.ep.zalenium.lightproxy.service.impl;

import java.util.*;

import com.google.common.annotations.VisibleForTesting;
import de.zalando.ep.zalenium.lightproxy.UrlTypeFilter;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
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
import org.springframework.web.util.UriComponentsBuilder;

import de.zalando.ep.zalenium.lightproxy.service.LightProxy;

public class BrowserMobProxy implements LightProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserMobProxy.class.getName());

    private static final Set<String> DEFAULT_CAPTURE_SETTINGS = SetUtils.hashSet(
            "captureHeaders",
            "captureCookies",
            "captureContent",
            "captureBinaryContent");

    private static final String HTTP = "http";
    private static final String PROXY_PATH = "/proxy";
    private static final String HAR_PATH = "/har";
    private static final String PAGE_REF_PATH = "/pageRef";
    private static final String WHITELIST_PATH = "/whitelist";
    private static final String BLACKLIST_PATH = "/blacklist";
    private static final String HEADERS_PATH = "/headers";
    public static final String LOCALHOST = "127.0.0.1";

    // Object to manipulate Rest Service
    private RestTemplate restTemplate;

    private UriComponentsBuilder uriProxyServer;

    /**
     * Sub proxy port for test.
     */
    private Integer port;

    private String proxyUrl;

    private UriComponentsBuilder uriSubProxyServer;

    public BrowserMobProxy(final String proxyServerHost, final Integer proxyServerPort) {
        this.uriProxyServer = UriComponentsBuilder.newInstance().scheme(HTTP).host(proxyServerHost).port(proxyServerPort).path(PROXY_PATH);
        restTemplate = new RestTemplate();
    }

    @VisibleForTesting
    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getProxyUrl() {
        return proxyUrl;
    }

    @Override
    public String getHarAsJson() {
        LOGGER.debug("Getting HAR in browsermob proxy on port {}", port.toString());
        try {
            String harJson = Optional.ofNullable(restTemplate.getForEntity(
                    UriComponentsBuilder.fromUri(this.uriSubProxyServer.build().toUri()).path(HAR_PATH).build().toUriString(),
                    String.class).getBody()).orElse(StringUtils.EMPTY);
            return harJson;
        } catch (RestClientException e) {
            throw new RuntimeException("Error when get HAR in browsermob proxy. " + e.getLocalizedMessage(), e);
        }
    }

    @Override
    public LightProxy create() {
        return create(StringUtils.EMPTY);
    }

    @Override
    public LightProxy create(final String subProxy) {
        try {
            UriComponentsBuilder uriComponentsBuilderCreateProxy = UriComponentsBuilder.fromUri(uriProxyServer.build().toUri());
            if (StringUtils.isNotEmpty(subProxy)) {
                LOGGER.debug("Add sub proxy in browser mob proxy : {}", subProxy);
                uriComponentsBuilderCreateProxy.queryParam("httpProxy", subProxy);
            }

            ResponseEntity<BMProxy> responseCreatedProxy = restTemplate.postForEntity(uriComponentsBuilderCreateProxy.build().toUriString(),
                    null, BMProxy.class);
            if (responseCreatedProxy.getBody() != null
                    && responseCreatedProxy.getStatusCode().equals(HttpStatus.OK)) {
                port = responseCreatedProxy.getBody().getPort();
                proxyUrl = UriComponentsBuilder.newInstance().scheme(HTTP).host(LOCALHOST).port(port).build().toUriString();
                uriSubProxyServer = UriComponentsBuilder.fromUri(this.uriProxyServer.build().toUri()).path(String.format("/%s", port));
                LOGGER.debug("Browsermob proxy created on port {} and access url {}", port, proxyUrl);
            } else {
                throw new RuntimeException("Error when creating proxy in browsermob proxy. " + responseCreatedProxy.toString());
            }
        } catch (RestClientException e) {
            throw new RuntimeException("Error when creating proxy in browsermob proxy. " + e.getLocalizedMessage(), e);
        }

        return this;
    }

    @Override
    public LightProxy delete() {
        LOGGER.debug("Deleting Browsermob proxy on port {}.", port);
        try {
            restTemplate.delete(uriSubProxyServer.build().toUriString());
        } catch (RestClientException e) {
            throw new RuntimeException("Error when deleting proxy [port : " + port.toString() + "] in browsermob proxy. " + e.getLocalizedMessage(), e);
        }
        return this;
    }

    @Override
    public void addOverriddenHeaders(final Map<String, Object> overriddenHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestAddHeaders = new HttpEntity<>(overriddenHeaders, headers);
        try {
            restTemplate.postForObject(
                    UriComponentsBuilder.fromUri(this.uriSubProxyServer.build().toUri()).path(HEADERS_PATH).build().toUriString(), requestAddHeaders, String.class);
        } catch (RestClientException e) {
            throw new RuntimeException("Error when adding headers in browsermob proxy. " + e.getLocalizedMessage(), e);
        }
    }

    @Override
    public void addCapturePage(final String pageId, final MultiValueMap<String, Object> overriddenCaptureSetting) {
        // If HAR not created
        String har = getHarAsJson();
        if (StringUtils.isEmpty(har)) {
            // Create HAR with pageRef
            MultiValueMap<String, Object> captureSettings = getCaptureSettings(pageId, overriddenCaptureSetting, "initialPageRef");
            LOGGER.debug("Adding HAR capture with initial pageRef {} with settings {} in browsermob proxy on port {}", pageId, ToStringBuilder.reflectionToString(captureSettings), port);
            putResource(captureSettings, UriComponentsBuilder.fromUri(this.uriSubProxyServer.build().toUri()).path(HAR_PATH).build().toUriString(),
                    "Error when adding HAR capture in browsermob proxy.");
        } else {
            MultiValueMap<String, Object> captureSettings = getCaptureSettings(pageId, overriddenCaptureSetting, "pageRef");
            LOGGER.debug("Adding HAR capture with pageRef {} with settings {} in browsermob proxy on port {}", pageId, ToStringBuilder.reflectionToString(captureSettings), port);
            putResource(captureSettings, UriComponentsBuilder.fromUri(this.uriSubProxyServer.build().toUri()).path(HAR_PATH).path(PAGE_REF_PATH).build().toUriString(),
                    "Error when creating pageRef in browsermob proxy.");
        }
    }

    /**
     * Mapped Proxy with Browser Mob Proxy server.
     */
    private MultiValueMap<String, Object> getCaptureSettings(String pageId, MultiValueMap<String, Object> overriddenCaptureSetting, String pageType) {
        MultiValueMap<String, Object> captureSettings = Optional.ofNullable(overriddenCaptureSetting).orElse(new LinkedMultiValueMap<>());
        captureSettings.add(pageType, pageId);
        DEFAULT_CAPTURE_SETTINGS.stream().forEach(s -> {
            captureSettings.putIfAbsent(s, Collections.singletonList(Boolean.TRUE));
        });
        return captureSettings;
    }

    @Override
    public void addBlackOrWhiteList(final UrlTypeFilter urlTypeFilter, final String regexPaternFilter) {
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.add("regex", regexPaternFilter);
        putResource(map, UriComponentsBuilder.fromUri(this.uriSubProxyServer.build().toUri()).path(urlTypeFilter.equals(UrlTypeFilter.BLACKLIST) ? BLACKLIST_PATH : WHITELIST_PATH).
                        build().toUriString(),
                "Error when adding black/white list in browsermob proxy.");
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
            throw new RuntimeException(String.format("%s. %s", errorMessage, e.getLocalizedMessage()), e);
        }
    }
    public static class BMProxy {


        private int port;

        public int getPort() {
            return port;
        }

        public void setPort(final int port) {
            this.port = port;
        }
    }
}
