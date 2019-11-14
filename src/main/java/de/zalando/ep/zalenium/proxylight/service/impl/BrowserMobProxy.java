package de.zalando.ep.zalenium.proxylight.service.impl;

import java.util.Map;
import java.util.Optional;

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

    public static final String HTTP = "http";

    public static final String PROXY_PATH = "proxy";

    public static final String HAR_PATH = "har";

    public static final String PATH_PAGE_REF = "pageRef";

    private static final String WHITELIST = "whitelist";

    private static final String BLACKLIST = "blacklist";
    public static final String PATH_HEADERS = "headers";

    // Object to manipulate Rest Service
    private RestTemplate restTemplate = new RestTemplate();

    private UriComponentsBuilder uriComponentsBuilder;

    private Map<String, Integer> subProxy;

    public BrowserMobProxy(final String host, final Integer port) {
        uriComponentsBuilder = UriComponentsBuilder.newInstance().scheme(HTTP).host(host).port(port).path(PROXY_PATH);
    }

    @Override
    public String getHarAsJson(final Integer port) {
        return Optional.of(restTemplate.getForEntity(
                uriComponentsBuilder.path(port.toString()).path(HAR_PATH).build().toUriString(),
                String.class).getBody()).orElseGet(String::new);
    }

    @Override
    public Integer create() {
        return create(StringUtils.EMPTY);
    }

    @Override
    public Integer create(final String subProxy) {

        int testProxyPort;
        try {
            UriComponentsBuilder uriComponentsBuilderCreateProxy = uriComponentsBuilder;
            if (StringUtils.isNotEmpty(subProxy)) {
                LOGGER.debug("Add sub proxy in browser mob proxy : {}", subProxy);
                uriComponentsBuilderCreateProxy.queryParam("httpProxy", subProxy);
            }

            ResponseEntity<BMProxy> responseCreatedProxy = restTemplate.postForEntity(uriComponentsBuilderCreateProxy.build().toUriString(),
                    null, BMProxy.class);
            if (responseCreatedProxy.getBody() != null
                    && responseCreatedProxy.getStatusCode().equals(HttpStatus.OK)) {
                testProxyPort = responseCreatedProxy.getBody().getPort();
                LOGGER.debug("Browsermob proxy created on port {}", testProxyPort);
            } else {
                throw new RuntimeException("Error when creating proxy in browsermob proxy. " + responseCreatedProxy.getBody().toString());
            }
        } catch (RestClientException e) {
            throw new RuntimeException("Error when creating proxy in browsermob proxy. " + e.getLocalizedMessage());
        }

        return testProxyPort;
    }

    @Override
    public void delete(final Integer port) {
        LOGGER.debug("Deleting Browsermob proxy on port {}.", port);
        try {
            restTemplate.delete(uriComponentsBuilder.path(port.toString()).build().toUriString());
        } catch (RestClientException e) {
            throw new RuntimeException("Error when deleting proxy [port : " + port.toString() + "] in browsermob proxy.", e);
        }
    }

    @Override
    public void addOverridedHeaders(final Integer port, final Map<String, Object> overridedHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestAddHeaders = new HttpEntity<>(overridedHeaders, headers);
        try {
            restTemplate.postForObject(
                    uriComponentsBuilder.path(port.toString()).path(PATH_HEADERS).build().toUriString(), requestAddHeaders, String.class);
        } catch (RestClientException e) {
            throw new RuntimeException("Error when adding headers in browsermob proxy.", e);
        }
    }

    @Override
    public void addCapturePage(final Integer port, final String pageId) {
        // If HAR not created
        String har = getHarAsJson(port);
        if (StringUtils.isNotEmpty(har)) {
            // Create HAR with pageRef
            LOGGER.debug("Adding HAR capture with initial pageRef {} in browsermob proxy on port {}", pageId, port);
            addCaptureHarWithInitPageRef(pageId, port);
        } else {
            LOGGER.debug("Adding HAR capture with pageRef {} in browsermob proxy on port {}", pageId, port);
            addCaptureHarWithPageRef(pageId, port);
        }
    }

    private void addCaptureHarWithPageRef(final String pageId, final Integer port) {
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.add("pageRef", pageId); // TODO Bug on capture pas tout sur les pages suivantes ?
        putResource(map, uriComponentsBuilder.path(port.toString()).path(HAR_PATH).path(PATH_PAGE_REF).build().toUriString(),
                "Error when creating pageRef in browsermob proxy.");
    }

    private void addCaptureHarWithInitPageRef(final String pageId, final Integer port) {
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.add("initialPageRef", pageId); // TODO paramétrable ou pas ? méthode
        map.add("captureHeaders", true);
        map.add("captureCookies", true);
        map.add("captureContent", true);
        map.add("captureBinaryContent", true);
        putResource(map, uriComponentsBuilder.path(port.toString()).path(HAR_PATH).build().toUriString(),
                "Error when creating HAR in browsermob proxy.");
    }

    @Override
    public void addBlackOrWhiteList(final Integer port, final FilterUrlType filterUrlType, final String regexPaternFilter) {
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.add("regex", regexPaternFilter);
        putResource(map, uriComponentsBuilder.path(port.toString()).path(filterUrlType.equals(FilterUrlType.BLACKLIST) ? BLACKLIST : WHITELIST).
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
