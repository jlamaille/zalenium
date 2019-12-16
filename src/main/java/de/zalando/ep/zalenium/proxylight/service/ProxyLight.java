package de.zalando.ep.zalenium.proxylight.service;

import de.zalando.ep.zalenium.proxylight.FilterUrlType;

import java.util.Map;

public interface ProxyLight {

    String getProxyUrl();

    String getHarpAsJsonp();

    ProxyLight create();

    ProxyLight create(final String subProxy);

    ProxyLight delete();

    ProxyLight addOverridedHeaders(final Map<String, Object> overridedHeaders);

    ProxyLight addCapturePage(final String pageId);

    ProxyLight addBlackOrWhiteList(final FilterUrlType filterUrlType, final String regexPaternFilter);
}
