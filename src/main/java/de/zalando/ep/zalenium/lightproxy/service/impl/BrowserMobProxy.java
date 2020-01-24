package de.zalando.ep.zalenium.lightproxy.service.impl;

import com.google.common.annotations.VisibleForTesting;
import de.zalando.ep.zalenium.lightproxy.UrlTypeFilter;
import de.zalando.ep.zalenium.lightproxy.service.LightProxy;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * This class is a light proxy of type 'Browser Mob Proxy' linked with a selenium node
 * and provide methods to manipulate proxy resource.
 */
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
    public static final int MAX_ATTEMPTS = 3;
    public static final int BACK_OFF_PERIOD = 500;
    public static final int TIMEOUT = 3000;

    /**
     * Object to manipulate Rest Service.
      */
    private RestTemplate restTemplate;

    /**
     * Builder for proxy server url.
     */
    private UriComponentsBuilder uriProxyServer;

    /**
     * Sub proxy port for test.
     */
    private Integer port;

    /**
     * Url of proxy to put in browser settings.
     */
    private String proxyUrl;

    /**
     * Builder for sub proxy server url.
     */
    private UriComponentsBuilder uriSubProxyServer;

    /**
     * Template to manage retry on rest requests.
     */
    private RetryTemplate retryTemplate;

    public BrowserMobProxy(final String proxyServerHost, final Integer proxyServerPort) {
        this.uriProxyServer = UriComponentsBuilder.newInstance().scheme(HTTP).host(proxyServerHost).port(proxyServerPort).path(PROXY_PATH);
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(MAX_ATTEMPTS);
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(BACK_OFF_PERIOD);
        retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(backOffPolicy);
        retryTemplate.setRetryPolicy(retryPolicy);
        restTemplate = new RestTemplate(getRequestFactory());
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
            return Optional.ofNullable(uriSubProxyServer != null ? restTemplate.getForEntity(
                    UriComponentsBuilder.fromUri(this.uriSubProxyServer.build().toUri()).path(HAR_PATH).build().toUriString(),
                    String.class).getBody() : StringUtils.EMPTY).orElse(StringUtils.EMPTY);
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

            ResponseEntity<BMProxy> responseCreatedProxy
                    = retryTemplate.execute(context -> {
                return restTemplate.postForEntity(uriComponentsBuilderCreateProxy.build().toUriString(),
                        null, BMProxy.class);
            });

            if (responseCreatedProxy.getBody() != null
                    && responseCreatedProxy.getStatusCode().equals(HttpStatus.OK)) {
                port = responseCreatedProxy.getBody().getPort();
                // Firefox doesn't support Scheme for proxy
                proxyUrl = StringUtils.remove(UriComponentsBuilder.newInstance().host(LOCALHOST).port(port).build().toUriString(), "//");
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
    public void delete() {
        LOGGER.debug("Deleting Browsermob proxy on port {}.", port);
        try {
            if (uriSubProxyServer != null) {
                restTemplate.delete(uriSubProxyServer.build().toUriString());
            }
        } catch (RestClientException e) {
            e.printStackTrace();
            LOGGER.error("Error when deleting proxy [port : " + port.toString() + "] in browsermob proxy. {}", e.getLocalizedMessage());
        }
    }

    @Override
    public void addOverriddenHeaders(final Map<String, Object> overriddenHeaders) {
        if (uriSubProxyServer != null) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> requestAddHeaders = new HttpEntity<>(overriddenHeaders, headers);
            try {
                retryTemplate.execute(context -> {
                    return restTemplate.postForObject(
                            UriComponentsBuilder.fromUri(this.uriSubProxyServer.build().toUri()).path(HEADERS_PATH).build().toUriString(), requestAddHeaders, String.class);
                });
            } catch (RestClientException e) {
                throw new RuntimeException("Error when adding headers in browsermob proxy. " + e.getLocalizedMessage(), e);
            }
        }
    }

    @Override
    public void addCapturePage(final String pageId, final LinkedHashMap<String, Object> overriddenCaptureSetting) {
        if (uriSubProxyServer != null) {
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
    }


    @Override
    public void addBlackOrWhiteList(final UrlTypeFilter urlTypeFilter, final String regexPatternFilter) {
        if (uriSubProxyServer != null) {
            MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
            map.add("regex", regexPatternFilter);
            putResource(map, UriComponentsBuilder.fromUri(this.uriSubProxyServer.build().toUri()).path(urlTypeFilter.equals(UrlTypeFilter.BLACKLIST) ? BLACKLIST_PATH : WHITELIST_PATH).
                            build().toUriString(),
                    "Error when adding black/white list in browsermob proxy.");
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
            retryTemplate.execute(context -> {
                restTemplate.put(url, requestCreateHar);
                return null;
            });
        } catch (RestClientException e) {
            throw new RuntimeException(String.format("%s. %s", errorMessage, e.getLocalizedMessage()), e);
        }
    }

    /**
     * Represent Proxy object in BrowserMobProxy Server.
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

    /**
     * @return factory for rest requests configuration
     */
    private ClientHttpRequestFactory getRequestFactory() {
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory();
        factory.setReadTimeout(TIMEOUT);
        factory.setConnectTimeout(TIMEOUT);
        factory.setConnectionRequestTimeout(TIMEOUT);
        return factory;
    }

    /**
     * @return settings for capture mode
     */
    private MultiValueMap<String, Object> getCaptureSettings(String pageId, LinkedHashMap<String, Object> overriddenCaptureSetting, String pageType) {
        MultiValueMap<String, Object> captureSettings = new LinkedMultiValueMap<>();
        if (overriddenCaptureSetting != null) {
            overriddenCaptureSetting.forEach((key, value) -> captureSettings.putIfAbsent(key, Collections.singletonList(value)));
        }
        captureSettings.add(pageType, pageId);
        DEFAULT_CAPTURE_SETTINGS.forEach(s -> {
            captureSettings.putIfAbsent(s, Collections.singletonList(Boolean.TRUE));
        });
        return captureSettings;
    }
}
