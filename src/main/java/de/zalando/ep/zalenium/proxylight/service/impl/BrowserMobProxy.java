package de.zalando.ep.zalenium.proxylight.service.impl;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import de.zalando.ep.zalenium.proxylight.FilterUrlType;
import org.apache.commons.lang3.StringUtils;
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

import de.zalando.ep.zalenium.proxylight.service.ProxyLight;

public class BrowserMobProxy implements ProxyLight {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserMobProxy.class.getName());

    private static final String HTTP = "http";
    private static final String PROXY_PATH = "proxy";
    private static final String HAR_PATH = "har";
    private static final String PATH_PAGE_REF = "pageRef";
    private static final String WHITELIST = "whitelist";
    private static final String BLACKLIST = "blacklist";
    private static final String PATH_HEADERS = "headers";
    public static final String LOCALHOST = "127.0.0.1";


    // Object to manipulate Rest Service
    private RestTemplate restTemplate;

    private UriComponentsBuilder uriProxyServer;

    /** Sub proxy port for test. */
    private Integer port;

    private String proxyUrl;

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
            return Optional.ofNullable(restTemplate.getForEntity(
                    uriProxyServer.path(port.toString()).path(HAR_PATH).build().toUriString(),
                    String.class).getBody()).get();
        } catch (RestClientException e) {
            throw new RuntimeException("Error when get HAR in browsermob proxy. " + e.getLocalizedMessage());
        }
    }

    @Override
    public ProxyLight create() {
        return create(StringUtils.EMPTY);
    }

    @Override
    public ProxyLight create(final String subProxy) {
        try {
            UriComponentsBuilder uriComponentsBuilderCreateProxy = uriProxyServer;
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
                LOGGER.debug("Browsermob proxy created on port {} and access url {}", port, proxyUrl);
            } else {
                throw new RuntimeException("Error when creating proxy in browsermob proxy. " + responseCreatedProxy.toString());
            }
        } catch (RestClientException e) {
            throw new RuntimeException("Error when creating proxy in browsermob proxy.", e);
        }

        return this;
    }

    @Override
    public ProxyLight delete() {
        LOGGER.debug("Deleting Browsermob proxy on port {}.", port);
        try {
            restTemplate.delete(uriProxyServer.path(port.toString()).build().toUriString());
        } catch (RestClientException e) {
            throw new RuntimeException("Error when deleting proxy [port : " + port.toString() + "] in browsermob proxy.", e);
        }
        return this;
    }

    @Override
    public ProxyLight addOverridedHeaders(final Map<String, Object> overridedHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestAddHeaders = new HttpEntity<>(overridedHeaders, headers);
        try {
            restTemplate.postForObject(
                    uriProxyServer.path(port.toString()).path(PATH_HEADERS).build().toUriString(), requestAddHeaders, String.class);
        } catch (RestClientException e) {
            throw new RuntimeException("Error when adding headers in browsermob proxy.", e);
        }
        return this;
    }

    @Override
    public ProxyLight addCapturePage(final String pageId) {
        // If HAR not created
        String har = getHarAsJson();
        if (StringUtils.isNotEmpty(har)) {
            // Create HAR with pageRef
            LOGGER.debug("Adding HAR capture with initial pageRef {} in browsermob proxy on port {}", pageId, port);
            addCaptureHarWithInitPageRef(pageId, port);
        } else {
            LOGGER.debug("Adding HAR capture with pageRef {} in browsermob proxy on port {}", pageId, port);
            addCaptureHarWithPageRef(pageId, port);
        }
        return this;
    }

    private void addCaptureHarWithPageRef(final String pageId, final Integer port) {
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.add("pageRef", pageId);
        putResource(map, uriProxyServer.path(port.toString()).path(HAR_PATH).path(PATH_PAGE_REF).build().toUriString(),
                "Error when creating pageRef in browsermob proxy.");
    }

    private void addCaptureHarWithInitPageRef(final String pageId, final Integer port) {
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.add("initialPageRef", pageId); // TODO paramétrable ou pas ? méthode
        map.add("captureHeaders", true);
        map.add("captureCookies", true);
        map.add("captureContent", true);
        map.add("captureBinaryContent", true);
        putResource(map, uriProxyServer.path(port.toString()).path(HAR_PATH).build().toUriString(),
                "Error when creating HAR in browsermob proxy.");
    }

    @Override
    public ProxyLight addBlackOrWhiteList(final FilterUrlType filterUrlType, final String regexPaternFilter) {
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.add("regex", regexPaternFilter);
        putResource(map, uriProxyServer.path(port.toString()).path(filterUrlType.equals(FilterUrlType.BLACKLIST) ? BLACKLIST : WHITELIST).
                        build().toUriString(),
                "Error when adding black/white list in browsermob proxy.");
        return this;
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
            throw new RuntimeException(String.format("%s.", errorMessage), e);
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

        public void setPort(final int port) {
            this.port = port;
        }
    }
}
